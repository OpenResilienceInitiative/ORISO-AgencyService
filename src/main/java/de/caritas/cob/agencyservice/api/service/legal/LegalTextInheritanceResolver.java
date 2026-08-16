package de.caritas.cob.agencyservice.api.service.legal;

import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalText;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextLevel;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextVersionRepository;
import de.caritas.cob.agencyservice.api.service.ApplicationSettingsService;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.util.JsonConverter;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import java.util.Map;
import java.util.function.Function;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * ADR-021 decision 9: the whole department → agency → Träger → platform-operator chain resolved in
 * <b>one place, on the server</b>.
 *
 * <p>Before this, only the lower half was server-side ({@code DepartmentLegalService} did
 * department → agency) and the upper half was reimplemented twice on clients — Admin's {@code
 * mergeTranslatedContent} and Frontend's {@code pickConsentPrivacyContent}. Two independent
 * implementations of a legal-document fallback is a defect source in its own right, and the consent
 * sentence would otherwise have inherited it: the sentence a help-seeker ticks would be picked by
 * whichever client happened to render it.
 *
 * <h2>The four rungs</h2>
 *
 * <ol>
 *   <li><b>Fachbereich</b> ({@code agency_topic}) — its own text, but only once PUBLISHED. A draft
 *       falls through rather than blanking out a legally required document. Per ADR-014 a
 *       referenced shared {@code legal_text} object, once assigned, fully replaces the inline
 *       column including its DRAFT state.
 *   <li><b>Beratungsstelle</b> ({@code agency}) — no publication status at all: what is stored,
 *       applies.
 *   <li><b>Träger</b> / <b>platform operator</b> — both live in ORISO-TenantService and are read
 *       through the existing {@link TenantService} client rather than a new one. Which of the two
 *       answers is decided by {@code legalContentChangesBySingleTenantAdminsAllowed}: when it is
 *       on, a Träger <em>replaces</em> the platform text; when it is off, the platform's own
 *       document governs and the Träger's is not consulted. That is the same rule {@code
 *       CentralDataProtectionTemplateService} already applies to the DPP template, kept identical
 *       on purpose — two different answers to "whose text governs" is exactly the bug class this
 *       ADR closes.
 * </ol>
 *
 * <h2>Failure behaviour</h2>
 *
 * <p>The upper two rungs are a cross-service call. If TenantService is unreachable the resolution
 * degrades to what AgencyService knows locally and logs it; it never turns a help-seeker's request
 * for a privacy policy into a 500. An unreachable Träger document is a gap the caller can detect
 * (level {@link LegalTextSourceLevel#NONE}) and handle, whereas a failed request is not.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LegalTextInheritanceResolver {

  private final @NonNull LegalTextVersionRepository legalTextVersionRepository;
  private final @NonNull TenantService tenantService;
  private final @NonNull ApplicationSettingsService applicationSettingsService;

  @Value("${feature.multitenancy.with.single.domain.enabled}")
  private boolean multitenancyWithSingleDomain;

  /**
   * Resolves the data protection policy in force for a department, together with the consent
   * sentence stored with it.
   */
  public ResolvedLegalText resolveDpp(AgencyTopic department) {
    return resolve(department, LegalTextKind.DPP);
  }

  /**
   * Resolves the imprint in force for a department. The consent field is always {@code null} here:
   * an imprint is an information duty, never a consent gate (ADR-021 decision 7).
   */
  public ResolvedLegalText resolveImprint(AgencyTopic department) {
    return resolve(department, LegalTextKind.IMPRINT);
  }

  private ResolvedLegalText resolve(AgencyTopic department, LegalTextKind kind) {
    if (department == null) {
      return ResolvedLegalText.none();
    }
    var fromDepartment = fromDepartment(department, kind);
    if (fromDepartment != null) {
      return fromDepartment;
    }
    var agency = department.getAgency();
    var fromAgency = fromAgency(agency, kind);
    if (fromAgency != null) {
      return fromAgency;
    }
    return fromTenantChain(agency, kind);
  }

  /**
   * Rung 4. A referenced shared object wins over the inline column entirely (ADR-014), so its own
   * publication status decides; a reference-less department falls back to its inline text.
   */
  private ResolvedLegalText fromDepartment(AgencyTopic department, LegalTextKind kind) {
    LegalText referenced = kind == LegalTextKind.DPP ? department.getDpp() : department.getImprint();
    if (referenced != null) {
      if (!isPublished(referenced.getContent(), referenced.getPublicationStatus())) {
        return null;
      }
      return new ResolvedLegalText(
          referenced.getContent(),
          consentOf(kind, referenced.getConsentText()),
          LegalTextSourceLevel.DEPARTMENT,
          currentVersionId(LegalTextLevel.SHARED, referenced.getId(), kind));
    }

    var content =
        kind == LegalTextKind.DPP ? department.getContentDpp() : department.getContentImprint();
    var status =
        kind == LegalTextKind.DPP
            ? department.getPublicationStatus()
            : department.getPublicationStatusImprint();
    if (!isPublished(content, status)) {
      return null;
    }
    return new ResolvedLegalText(
        content,
        consentOf(kind, department.getConsentText()),
        LegalTextSourceLevel.DEPARTMENT,
        currentVersionId(LegalTextLevel.DEPARTMENT, department.getId(), kind));
  }

  /** Rung 3. No publication status here — a stored Beratungsstelle text is the text in force. */
  private ResolvedLegalText fromAgency(Agency agency, LegalTextKind kind) {
    if (agency == null) {
      return null;
    }
    var content = kind == LegalTextKind.DPP ? agency.getContentDpp() : agency.getContentImprint();
    if (isBlank(content)) {
      return null;
    }
    return new ResolvedLegalText(
        content,
        consentOf(kind, agency.getConsentText()),
        LegalTextSourceLevel.AGENCY,
        currentVersionId(LegalTextLevel.AGENCY, agency.getId(), kind));
  }

  /**
   * Rungs 2 and 1. Which one answers is the {@code legalContentChangesBySingleTenantAdminsAllowed}
   * decision described in the class comment.
   *
   * <p>The tenant API exposes both a resolved string and the raw {@code <lang>Languages} map. The
   * map is preferred because every level below stores a language→HTML map, and handing callers two
   * different shapes depending on which rung answered would push the multilingual pick back onto
   * the client — the very split this decision removes.
   */
  private ResolvedLegalText fromTenantChain(Agency agency, LegalTextKind kind) {
    RestrictedTenantDTO tenant = loadGoverningTenant(agency);
    if (tenant == null || tenant.getContent() == null) {
      return ResolvedLegalText.none();
    }
    var level =
        multitenancyWithSingleDomain && !isTenantLevelLegalContentOverrideAllowed()
            ? LegalTextSourceLevel.PLATFORM
            : LegalTextSourceLevel.TENANT;

    var languageMap =
        kind == LegalTextKind.DPP
            ? tenant.getContent().getPrivacyLanguages()
            : tenant.getContent().getImpressumLanguages();
    if (languageMap != null && !languageMap.isEmpty()) {
      return new ResolvedLegalText(JsonConverter.convertToJson(languageMap), null, level, null);
    }

    var single =
        kind == LegalTextKind.DPP
            ? tenant.getContent().getPrivacy()
            : tenant.getContent().getImpressum();
    if (isBlank(single)) {
      return ResolvedLegalText.none();
    }
    // A tenant that only ever stored one language still has to come back in the map shape.
    return new ResolvedLegalText(
        JsonConverter.convertToJson(Map.of("de", single)), null, level, null);
  }

  /**
   * The Träger's own document when it may replace the platform's, the platform's own otherwise.
   * Mirrors {@code CentralDataProtectionTemplateService} deliberately.
   */
  private RestrictedTenantDTO loadGoverningTenant(Agency agency) {
    return callTenantService(
        ignored -> {
          if (multitenancyWithSingleDomain && !isTenantLevelLegalContentOverrideAllowed()) {
            return tenantService.getMainTenant();
          }
          if (agency != null && agency.getTenantId() != null) {
            return tenantService.getRestrictedTenantDataByTenantId(agency.getTenantId());
          }
          return tenantService.getRestrictedTenantDataForSingleTenant();
        },
        agency);
  }

  /**
   * A legal-text read must never fail because a neighbouring service is down. The caller sees a gap
   * it can react to; a 500 on the public {@code /legal} path would just be an outage.
   */
  private RestrictedTenantDTO callTenantService(
      Function<Agency, RestrictedTenantDTO> call, Agency agency) {
    try {
      return call.apply(agency);
    } catch (Exception e) {
      log.warn(
          "Could not resolve the Träger/platform legal text for agency {}: {}",
          agency == null ? null : agency.getId(),
          e.getMessage());
      return null;
    }
  }

  /**
   * Only consulted in single-domain multitenancy, where levels 1 and 2 are distinguishable at all.
   * An unavailable settings service is treated as "no override": defaulting the other way would
   * silently promote a Träger's text over the platform's on a transient failure.
   */
  private boolean isTenantLevelLegalContentOverrideAllowed() {
    var settings = applicationSettingsService.getApplicationSettings();
    var toggle =
        settings == null ? null : settings.getLegalContentChangesBySingleTenantAdminsAllowed();
    return toggle != null && Boolean.TRUE.equals(toggle.getValue());
  }

  /** ADR-021 decision 7: an imprint never carries a consent sentence. */
  private String consentOf(LegalTextKind kind, String storedConsent) {
    return kind == LegalTextKind.DPP ? storedConsent : null;
  }

  private Long currentVersionId(LegalTextLevel level, Long ownerId, LegalTextKind kind) {
    if (ownerId == null) {
      return null;
    }
    return legalTextVersionRepository
        .findFirstByOwnerLevelAndOwnerIdAndKindAndSupersededAtIsNullOrderByIdDesc(
            level, ownerId, kind)
        .map(version -> version.getId())
        .orElse(null);
  }

  private boolean isPublished(String content, PublicationStatus status) {
    return PublicationStatus.PUBLISHED == status && !isBlank(content);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
