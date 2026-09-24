package de.caritas.cob.agencyservice.api.admin.service.legal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.ConflictException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason;
import de.caritas.cob.agencyservice.api.exception.httpresponses.InternalServerErrorException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalDraft;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalDraftRepository;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRUD for agency-owned drafts. No method touches the published {@code Agency} legal fields. */
@Service
@RequiredArgsConstructor
public class AgencyLegalDraftService {

  private static final TypeReference<LinkedHashMap<String, String>> LANGUAGE_MAP =
      new TypeReference<>() {};

  private final @NonNull AgencyLegalDraftRepository draftRepository;
  private final @NonNull AgencyRepository agencyRepository;
  private final @NonNull LegalAdminAccessGuard accessGuard;
  private final @NonNull LegalContentSanitizer contentSanitizer;
  private final @NonNull ConsentTextService consentTextService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Transactional(readOnly = true)
  public AgencyLegalDraftView get(Long agencyId, LegalTextKind kind) {
    authoriseAgency(agencyId);
    return draftRepository
        .findByAgencyIdAndKind(agencyId, kind)
        .map(this::toView)
        .orElseThrow(NotFoundException::new);
  }

  /**
   * Creates with no revision, or atomically replaces the exact revision supplied by the client.
   * A stale or different create-lifetime token always conflicts.
   */
  @Transactional
  public AgencyLegalDraftView save(
      Long agencyId,
      LegalTextKind kind,
      Map<String, String> content,
      Map<String, String> consentText,
      String revision) {
    authoriseAgency(agencyId);
    assertConsentAllowed(kind, consentText);
    var sanitizedContent = contentSanitizer.sanitizeToJson(content);
    var sanitizedConsent =
        kind == LegalTextKind.DPP
            ? consentTextService.sanitizeAndValidate(consentText, false)
            : null;
    var now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

    if (revision == null || revision.isBlank()) {
      if (draftRepository.findByAgencyIdAndKind(agencyId, kind).isPresent()) {
        throw conflict();
      }
      var draft =
          AgencyLegalDraft.builder()
              .rowId(UUID.randomUUID().toString())
              .agencyId(agencyId)
              .kind(kind)
              .content(sanitizedContent)
              .consentText(sanitizedConsent)
              .savedAt(now)
              .build();
      try {
        return toView(draftRepository.saveAndFlush(draft));
      } catch (DataIntegrityViolationException e) {
        // A simultaneous create won the unique (agency, kind) slot.
        throw conflict();
      }
    }

    var expected = DraftRevision.parse(revision);
    if (draftRepository.compareAndSwap(
            agencyId,
            kind,
            expected.rowId(),
            expected.version(),
            sanitizedContent,
            sanitizedConsent,
            now)
        != 1) {
      throw conflict();
    }
    return draftRepository
        .findByAgencyIdAndKind(agencyId, kind)
        .map(this::toView)
        .orElseThrow(AgencyLegalDraftService::conflict);
  }

  @Transactional
  public void delete(Long agencyId, LegalTextKind kind, String revision) {
    authoriseAgency(agencyId);
    var expected = DraftRevision.parse(revision);
    if (draftRepository.compareAndDelete(
            agencyId, kind, expected.rowId(), expected.version())
        != 1) {
      throw conflict();
    }
  }

  /** Ownership and tenant scope are checked before any draft lookup to avoid existence leaks. */
  private void authoriseAgency(Long agencyId) {
    accessGuard.assertRestrictedAdminOwnsAgency(agencyId);
    var agency = agencyRepository.findById(agencyId).orElseThrow(NotFoundException::new);
    accessGuard.assertCallerTenantMatches(agency);
  }

  private void assertConsentAllowed(LegalTextKind kind, Map<String, String> consentText) {
    if (kind == LegalTextKind.IMPRINT && consentText != null && !consentText.isEmpty()) {
      throw new BadRequestException("An imprint draft cannot carry privacy-consent wording");
    }
  }

  private AgencyLegalDraftView toView(AgencyLegalDraft draft) {
    return new AgencyLegalDraftView(
        draft.getKind(),
        readLanguageMap(draft.getContent()),
        readLanguageMap(draft.getConsentText()),
        DraftRevision.format(draft.getRowId(), draft.getVersion()),
        draft.getSavedAt());
  }

  private Map<String, String> readLanguageMap(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, LANGUAGE_MAP);
    } catch (JsonProcessingException e) {
      throw new InternalServerErrorException("Could not read stored agency legal draft", e);
    }
  }

  private static ConflictException conflict() {
    return new ConflictException(HttpStatusExceptionReason.LEGAL_DRAFT_REVISION_CONFLICT);
  }

  /** Wire token. Clients must echo it unchanged and must not infer ordering from its contents. */
  private record DraftRevision(String rowId, long version) {
    static DraftRevision parse(String token) {
      if (token == null) {
        throw new BadRequestException("Draft revision is required");
      }
      int separator = token.lastIndexOf(':');
      try {
        if (separator <= 0 || separator == token.length() - 1) {
          throw new IllegalArgumentException();
        }
        String rowId = UUID.fromString(token.substring(0, separator)).toString();
        long version = Long.parseLong(token.substring(separator + 1));
        if (version < 0) {
          throw new IllegalArgumentException();
        }
        return new DraftRevision(rowId, version);
      } catch (IllegalArgumentException e) {
        throw new BadRequestException("Draft revision is malformed");
      }
    }

    static String format(String rowId, Long version) {
      return rowId + ":" + version;
    }
  }
}
