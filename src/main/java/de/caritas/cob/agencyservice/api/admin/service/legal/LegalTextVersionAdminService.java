package de.caritas.cob.agencyservice.api.admin.service.legal;

import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextLevel;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextVersion;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextVersionRepository;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the ADR-021 publication history for the admin panel: list the versions of one
 * document, and fetch one of them verbatim.
 *
 * <p>Every entry point resolves the owning agency first and runs the same two guards as the
 * publish endpoints ({@link LegalAdminAccessGuard}). A version id alone is not a capability: {@link
 * #getVersion} re-derives the owner from the stored row and authorises against <em>that</em>, so
 * guessing ids cannot walk out of the caller's agencies or Träger.
 */
@Service
@RequiredArgsConstructor
public class LegalTextVersionAdminService {

  private final @NonNull LegalTextVersionService legalTextVersionService;
  private final @NonNull LegalTextVersionRepository legalTextVersionRepository;
  private final @NonNull AgencyTopicRepository agencyTopicRepository;
  private final @NonNull AgencyRepository agencyRepository;
  private final @NonNull LegalAdminAccessGuard accessGuard;

  /** The publication history of one department's (Fachbereich) DPP or imprint, newest first. */
  @Transactional(readOnly = true)
  public List<LegalTextVersionView> listDepartmentVersions(
      Long agencyId, Long topicId, LegalTextKind kind) {
    accessGuard.assertRestrictedAdminOwnsAgency(agencyId);
    var department =
        agencyTopicRepository
            .findByAgency_IdAndTopicId(agencyId, topicId)
            .orElseThrow(NotFoundException::new);
    accessGuard.assertCallerTenantMatches(department.getAgency());
    return toViews(
        legalTextVersionService.listVersions(
            LegalTextLevel.DEPARTMENT, department.getId(), kind));
  }

  /** The publication history of one Beratungsstelle's agency-wide DPP or imprint, newest first. */
  @Transactional(readOnly = true)
  public List<LegalTextVersionView> listAgencyVersions(Long agencyId, LegalTextKind kind) {
    accessGuard.assertRestrictedAdminOwnsAgency(agencyId);
    var agency = agencyRepository.findById(agencyId).orElseThrow(NotFoundException::new);
    accessGuard.assertCallerTenantMatches(agency);
    return toViews(legalTextVersionService.listVersions(LegalTextLevel.AGENCY, agencyId, kind));
  }

  /**
   * One archived version, verbatim. Authorisation is derived from the stored owner, so the version
   * id is not a bearer token for someone else's document.
   */
  @Transactional(readOnly = true)
  public LegalTextVersionView getVersion(Long versionId) {
    var version = legalTextVersionRepository.findById(versionId).orElseThrow(NotFoundException::new);
    authoriseOwner(version);
    return toView(version);
  }

  /**
   * Resolves the agency behind a version row and applies the standard guards. A {@link
   * LegalTextLevel#SHARED} version belongs to no single agency — it is a tenant-wide ADR-014
   * object, so it is authorised the way the library itself is: by owning Träger.
   */
  private void authoriseOwner(LegalTextVersion version) {
    switch (version.getOwnerLevel()) {
      case DEPARTMENT -> {
        var department =
            agencyTopicRepository.findById(version.getOwnerId()).orElseThrow(NotFoundException::new);
        accessGuard.assertRestrictedAdminOwnsAgency(department.getAgency().getId());
        accessGuard.assertCallerTenantMatches(department.getAgency());
      }
      case AGENCY -> {
        var agency =
            agencyRepository.findById(version.getOwnerId()).orElseThrow(NotFoundException::new);
        accessGuard.assertRestrictedAdminOwnsAgency(agency.getId());
        accessGuard.assertCallerTenantMatches(agency);
      }
      case SHARED -> accessGuard.assertCallerTenantIs(version.getTenantId());
      default -> throw new NotFoundException();
    }
  }

  private List<LegalTextVersionView> toViews(List<LegalTextVersion> versions) {
    return versions.stream().map(LegalTextVersionAdminService::toView).toList();
  }

  private static LegalTextVersionView toView(LegalTextVersion version) {
    return new LegalTextVersionView(
        version.getId(),
        version.getKind(),
        version.getOwnerLevel(),
        version.getOwnerId(),
        version.getContent(),
        version.getPublishedAt(),
        version.getPublishedBy(),
        version.getSupersededAt());
  }
}
