package de.caritas.cob.agencyservice.api.admin.service.legal;

import de.caritas.cob.agencyservice.api.admin.service.legal.TraegerLegalTextClient.TraegerLegalText;
import de.caritas.cob.agencyservice.api.exception.httpresponses.AgencyAccessDeniedException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.ConflictException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalDraft;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalDraftArchive;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalDraftArchiveRepository;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalDraftRepository;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposal;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalAdoptionMode;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalAudience;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalDistribution;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalDistributionRepository;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalRepository;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalSource;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalStatus;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Träger → Beratungsstelle legal templates (ORISO-AgencyService#303), with the semantics of
 * TenantService#266 one rung up: every recipient gets an immutable copy, adoption copies it into
 * the agency draft and never publishes, and a replaced draft is archived in the same transaction.
 * Authorisation is the caller's job ({@link AgencyLegalProposalFacade}).
 */
@Service
@RequiredArgsConstructor
public class AgencyLegalProposalService {

  private static final List<AgencyLegalProposalStatus> OPEN =
      List.of(AgencyLegalProposalStatus.PENDING, AgencyLegalProposalStatus.DISMISSED);

  private final @NonNull AgencyLegalProposalDistributionRepository distributionRepository;
  private final @NonNull AgencyLegalProposalRepository proposalRepository;
  private final @NonNull AgencyLegalDraftRepository draftRepository;
  private final @NonNull AgencyLegalDraftArchiveRepository archiveRepository;
  private final @NonNull AgencyRepository agencyRepository;
  private final @NonNull LegalContentSanitizer contentSanitizer;
  private final @NonNull ConsentTextService consentTextService;

  public record DistributionCommand(
      String requestKey,
      Long tenantId,
      LegalTextKind kind,
      AgencyLegalProposalSource source,
      String sourceRevision,
      AgencyLegalProposalAudience audience,
      Set<Long> agencyIds,
      String actorId) {}

  public record DeliveryResult(
      AgencyLegalProposalDistribution distribution,
      List<AgencyLegalProposal> proposals,
      boolean created) {}

  public record AdoptionResult(
      AgencyLegalDraft draft,
      AgencyLegalProposal proposal,
      Optional<AgencyLegalDraftArchive> archivedDraft) {}

  /** A retried request answers from its stored distribution, without asking TenantService again. */
  @Transactional(readOnly = true)
  public Optional<DeliveryResult> findRetry(DistributionCommand command) {
    validate(command);
    return distributionRepository
        .findByRequestKey(command.requestKey())
        .map(existing -> retryResult(existing, command));
  }

  /** Recipients are checked before TenantService is asked for the text: a foreign id is a 403. */
  @Transactional(readOnly = true)
  public void checkRecipients(DistributionCommand command) {
    validate(command);
    resolveRecipients(command);
  }

  @Transactional
  public DeliveryResult deliver(DistributionCommand command, TraegerLegalText source) {
    validate(command);
    var retry = distributionRepository.findLockedByRequestKey(command.requestKey());
    if (retry.isPresent()) {
      return retryResult(retry.get(), command);
    }
    if (!Objects.equals(source.revision(), command.sourceRevision())) {
      throw conflict();
    }
    SortedSet<Long> recipients = resolveRecipients(command);
    var now = now();
    var distribution =
        AgencyLegalProposalDistribution.builder()
            .id(UUID.randomUUID().toString())
            .requestKey(command.requestKey())
            .tenantId(command.tenantId())
            .kind(command.kind())
            .audience(command.audience())
            .source(command.source())
            .sourceRevision(command.sourceRevision())
            .requestFingerprint(fingerprint(command))
            .recipientIds(recipients.stream().map(String::valueOf).collect(Collectors.joining(",")))
            .content(contentSanitizer.sanitizeToJson(source.content()))
            .consentText(
                command.kind() == LegalTextKind.DPP
                    ? consentTextService.sanitizeAndValidate(source.consentText(), false)
                    : null)
            .createdBy(command.actorId())
            .createdAt(now)
            .build();
    try {
      distributionRepository.saveAndFlush(distribution);
      Map<Long, AgencyLegalProposal> byRecipient =
          proposalRepository
              .findLockedBySourceRevisionAndRecipients(
                  command.source(), command.sourceRevision(), recipients)
              .stream()
              .collect(
                  Collectors.toMap(AgencyLegalProposal::getRecipientAgencyId, Function.identity()));
      var missing =
          recipients.stream()
              .filter(agencyId -> !byRecipient.containsKey(agencyId))
              .map(agencyId -> proposal(distribution, agencyId, now))
              .toList();
      proposalRepository
          .saveAllAndFlush(missing)
          .forEach(p -> byRecipient.put(p.getRecipientAgencyId(), p));
      // Last on purpose: it locks every recipient's open offer, which a recipient's own adopt or
      // dismiss waits for; as the final write that wait is the commit alone.
      supersedeOlder(recipients, command.kind(), new HashSet<>(byRecipient.values()), now);
      return new DeliveryResult(
          distribution, recipients.stream().map(byRecipient::get).toList(), true);
    } catch (DataIntegrityViolationException | OptimisticLockingFailureException e) {
      throw conflict();
    }
  }

  @Transactional(readOnly = true)
  public List<AgencyLegalProposalDistribution> sentHistory(Long tenantId, LegalTextKind kind) {
    return distributionRepository.findByTenantIdAndKindOrderByCreatedAtDescIdDesc(tenantId, kind);
  }

  @Transactional(readOnly = true)
  public List<AgencyLegalProposal> list(Long agencyId, LegalTextKind kind) {
    return kind == null
        ? proposalRepository.findByRecipientAgencyIdOrderByCreatedAtDescIdDesc(agencyId)
        : proposalRepository.findByRecipientAgencyIdAndKindOrderByCreatedAtDescIdDesc(
            agencyId, kind);
  }

  @Transactional(readOnly = true)
  public AgencyLegalProposal get(Long agencyId, Long proposalId) {
    return proposalRepository
        .findByIdAndRecipientAgencyId(proposalId, agencyId)
        .orElseThrow(NotFoundException::new);
  }

  @Transactional
  public AgencyLegalProposal dismiss(
      Long agencyId, Long proposalId, String expectedProposalRevision, String actorId) {
    var proposal = locked(agencyId, proposalId);
    if (proposal.getStatus() != AgencyLegalProposalStatus.PENDING) {
      throw conflict();
    }
    requireRevision(revision(proposal), expectedProposalRevision);
    proposal.setStatus(AgencyLegalProposalStatus.DISMISSED);
    proposal.setDecidedBy(actorId);
    proposal.setDecidedAt(now());
    try {
      return proposalRepository.saveAndFlush(proposal);
    } catch (OptimisticLockingFailureException e) {
      throw conflict();
    }
  }

  @Transactional
  public AdoptionResult adopt(
      Long agencyId,
      Long proposalId,
      AgencyLegalProposalAdoptionMode mode,
      String expectedProposalRevision,
      String expectedDraftRevision,
      String actorId) {
    if (mode == null) {
      throw new BadRequestException("Adoption mode is required");
    }
    var proposal = locked(agencyId, proposalId);
    if (proposal.getStatus() == AgencyLegalProposalStatus.ADOPTED
        || proposal.getStatus() == AgencyLegalProposalStatus.SUPERSEDED) {
      throw conflict();
    }
    requireRevision(revision(proposal), expectedProposalRevision);
    var current = draftRepository.findLockedByAgencyIdAndKind(agencyId, proposal.getKind());
    var now = now();
    AgencyLegalDraft draft;
    Optional<AgencyLegalDraftArchive> archived = Optional.empty();
    if (mode == AgencyLegalProposalAdoptionMode.CREATE_IF_EMPTY) {
      if (expectedDraftRevision != null) {
        throw new BadRequestException("CREATE_IF_EMPTY does not accept a draft revision");
      }
      if (current.isPresent()) {
        throw draftConflict();
      }
      draft =
          AgencyLegalDraft.builder()
              .rowId(UUID.randomUUID().toString())
              .agencyId(agencyId)
              .kind(proposal.getKind())
              .build();
    } else {
      if (expectedDraftRevision == null || expectedDraftRevision.isBlank()) {
        throw new BadRequestException("ARCHIVE_AND_REPLACE requires the current draft revision");
      }
      draft = current.orElseThrow(AgencyLegalProposalService::draftConflict);
      if (!draftRevision(draft).equals(expectedDraftRevision)) {
        throw draftConflict();
      }
      archived = Optional.of(archiveRepository.saveAndFlush(archiveOf(draft, proposal, actorId, now)));
    }
    draft.setContent(proposal.getContent());
    draft.setConsentText(proposal.getConsentText());
    draft.setSavedAt(now);
    draft.setOriginProposalId(proposal.getId());
    try {
      final var adopted = draftRepository.saveAndFlush(draft);
      proposal.setStatus(AgencyLegalProposalStatus.ADOPTED);
      proposal.setDecidedBy(actorId);
      proposal.setDecidedAt(now);
      return new AdoptionResult(adopted, proposalRepository.saveAndFlush(proposal), archived);
    } catch (DataIntegrityViolationException | OptimisticLockingFailureException e) {
      throw draftConflict();
    }
  }

  @Transactional(readOnly = true)
  public List<AgencyLegalDraftArchive> archives(Long agencyId, LegalTextKind kind) {
    return kind == null
        ? archiveRepository.findByAgencyIdOrderByArchivedAtDescIdDesc(agencyId)
        : archiveRepository.findByAgencyIdAndKindOrderByArchivedAtDescIdDesc(agencyId, kind);
  }

  @Transactional(readOnly = true)
  public AgencyLegalDraftArchive archive(Long agencyId, Long archiveId) {
    return archiveRepository
        .findByIdAndAgencyId(archiveId, agencyId)
        .orElseThrow(NotFoundException::new);
  }

  public static String revision(AgencyLegalProposal proposal) {
    return proposal.getId() + ":" + proposal.getVersion();
  }

  public static String draftRevision(AgencyLegalDraft draft) {
    return draft.getRowId() + ":" + draft.getVersion();
  }

  public static SortedSet<Long> recipients(AgencyLegalProposalDistribution distribution) {
    SortedSet<Long> ids = new TreeSet<>();
    if (distribution.getRecipientIds() != null && !distribution.getRecipientIds().isBlank()) {
      Arrays.stream(distribution.getRecipientIds().split(",")).map(Long::valueOf).forEach(ids::add);
    }
    return ids;
  }

  private DeliveryResult retryResult(
      AgencyLegalProposalDistribution existing, DistributionCommand command) {
    if (!existing.getTenantId().equals(command.tenantId())
        || !existing.getRequestFingerprint().equals(fingerprint(command))) {
      throw conflict();
    }
    var proposals =
        proposalRepository
            .findBySourceAndSourceRevisionAndRecipientAgencyIdIn(
                existing.getSource(), existing.getSourceRevision(), recipients(existing))
            .stream()
            .sorted(Comparator.comparing(AgencyLegalProposal::getRecipientAgencyId))
            .toList();
    return new DeliveryResult(existing, proposals, false);
  }

  private SortedSet<Long> resolveRecipients(DistributionCommand command) {
    Set<Long> requested = command.agencyIds() == null ? Set.of() : command.agencyIds();
    SortedSet<Long> recipients;
    if (command.audience() == AgencyLegalProposalAudience.ALL) {
      if (!requested.isEmpty()) {
        throw new BadRequestException("ALL does not accept agency ids");
      }
      recipients =
          agencyRepository.findByTenantIdAndDeleteDateNullOrderByIdAsc(command.tenantId()).stream()
              .map(Agency::getId)
              .collect(Collectors.toCollection(TreeSet::new));
    } else {
      if (requested.isEmpty()) {
        throw new BadRequestException("SELECTED requires agency ids");
      }
      var found = agencyRepository.findAllById(requested);
      if (found.size() != requested.size()
          || found.stream().anyMatch(agency -> agency.getDeleteDate() != null)) {
        throw new BadRequestException("Unknown recipient agency");
      }
      if (found.stream().anyMatch(agency -> !command.tenantId().equals(agency.getTenantId()))) {
        throw new AgencyAccessDeniedException();
      }
      recipients = new TreeSet<>(requested);
    }
    if (recipients.isEmpty()) {
      throw new BadRequestException("The Träger has no Beratungsstelle to forward to");
    }
    return recipients;
  }

  private AgencyLegalProposal proposal(
      AgencyLegalProposalDistribution distribution, Long agencyId, LocalDateTime now) {
    return AgencyLegalProposal.builder()
        .recipientAgencyId(agencyId)
        .tenantId(distribution.getTenantId())
        .kind(distribution.getKind())
        .distributionId(distribution.getId())
        .audience(distribution.getAudience())
        .source(distribution.getSource())
        .sourceRevision(distribution.getSourceRevision())
        .content(distribution.getContent())
        .consentText(distribution.getConsentText())
        .status(AgencyLegalProposalStatus.PENDING)
        .createdBy(distribution.getCreatedBy())
        .createdAt(now)
        .build();
  }

  private void supersedeOlder(
      Collection<Long> recipients,
      LegalTextKind kind,
      Set<AgencyLegalProposal> newest,
      LocalDateTime now) {
    Map<Long, Long> newestByRecipient =
        newest.stream()
            .collect(
                Collectors.toMap(
                    AgencyLegalProposal::getRecipientAgencyId, AgencyLegalProposal::getId));
    Set<Long> newestIds = new HashSet<>(newestByRecipient.values());
    var older =
        proposalRepository.findLockedOpenByRecipients(recipients, kind, OPEN).stream()
            .filter(p -> !newestIds.contains(p.getId()))
            .toList();
    older.forEach(
        p -> {
          p.setStatus(AgencyLegalProposalStatus.SUPERSEDED);
          p.setSupersededByProposalId(newestByRecipient.get(p.getRecipientAgencyId()));
          p.setSupersededAt(now);
        });
    if (!older.isEmpty()) {
      proposalRepository.saveAllAndFlush(older);
    }
  }

  private AgencyLegalDraftArchive archiveOf(
      AgencyLegalDraft draft, AgencyLegalProposal proposal, String actorId, LocalDateTime now) {
    return AgencyLegalDraftArchive.builder()
        .agencyId(draft.getAgencyId())
        .kind(draft.getKind())
        .draftRowId(draft.getRowId())
        .draftRevision(draftRevision(draft))
        .content(draft.getContent())
        .consentText(draft.getConsentText())
        .draftSavedAt(draft.getSavedAt())
        .originProposalId(draft.getOriginProposalId())
        .replacedByProposalId(proposal.getId())
        .archivedBy(actorId)
        .archivedAt(now)
        .build();
  }

  private AgencyLegalProposal locked(Long agencyId, Long proposalId) {
    return proposalRepository
        .findLockedByIdAndRecipientAgencyId(proposalId, agencyId)
        .orElseThrow(NotFoundException::new);
  }

  private void validate(DistributionCommand command) {
    var key = command.requestKey();
    if (key == null || key.isBlank() || key.length() > 128) {
      throw new BadRequestException("Invalid request key");
    }
    if (command.kind() == null || command.audience() == null || command.source() == null) {
      throw new BadRequestException("Kind, audience and source are required");
    }
    if (command.sourceRevision() == null
        || command.sourceRevision().isBlank()
        || command.sourceRevision().length() > 64) {
      throw new BadRequestException("Invalid source revision");
    }
  }

  private static void requireRevision(String current, String submitted) {
    if (!current.equals(submitted)) {
      throw conflict();
    }
  }

  private String fingerprint(DistributionCommand command) {
    var agencies =
        (command.agencyIds() == null ? new TreeSet<Long>() : new TreeSet<>(command.agencyIds()))
            .stream().map(String::valueOf).collect(Collectors.joining(","));
    var canonical =
        String.join(
            "|",
            String.valueOf(command.tenantId()),
            command.kind().name(),
            command.source().name(),
            command.sourceRevision(),
            command.audience().name(),
            agencies);
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private static LocalDateTime now() {
    return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
  }

  private static ConflictException conflict() {
    return new ConflictException(HttpStatusExceptionReason.LEGAL_PROPOSAL_CONFLICT);
  }

  private static ConflictException draftConflict() {
    return new ConflictException(HttpStatusExceptionReason.LEGAL_DRAFT_REVISION_CONFLICT);
  }
}
