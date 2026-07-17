package de.caritas.cob.agencyservice.api.service;

import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalText;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public (unauthenticated) read side of ADR-003's department legal texts: exposes a department's
 * (Fachbereich = agency × topic) data privacy policy and imprint to end users, but only once they
 * are {@link PublicationStatus#PUBLISHED}. Draft or never-authored texts resolve to {@code null}
 * content — a draft is a provisional working basis recorded in the audit trail, never a document
 * shown to clients.
 */
@Service
@RequiredArgsConstructor
public class DepartmentLegalService {

  private final @NonNull AgencyTopicRepository agencyTopicRepository;

  /**
   * Loads the department's published legal texts.
   *
   * @throws NotFoundException when the (agency, topic) pairing does not exist or the agency is
   *     deleted
   */
  @Transactional(readOnly = true)
  public DepartmentLegalView getPublishedDepartmentLegal(Long agencyId, Long topicId) {
    AgencyTopic department =
        agencyTopicRepository
            .findByAgency_IdAndTopicId(agencyId, topicId)
            .orElseThrow(NotFoundException::new);

    assertAgencyIsNotDeleted(department.getAgency());

    return new DepartmentLegalView(
        resolveDpp(department), resolveImprint(department));
  }

  /**
   * ADR-014 resolution order: a referenced shared legal-text object, once assigned, fully replaces
   * the legacy inline column — including its DRAFT state. Only reference-less rows fall back to the
   * inline content.
   *
   * <p><b>Unassign semantics (this release):</b> clearing {@code dpp_id}/{@code imprint_id} makes a
   * department reference-less again, so it falls back to its own inline {@code content_dpp}/{@code
   * content_imprint} — NOT to a tenant-level fallback document. The backfill deliberately keeps the
   * inline columns for one release as this read-fallback and as the rollback anchor; they are not
   * cleared on unassign. Tenant-level fallback is future work (see ADR-014 / #134).
   */
  private String resolveDpp(AgencyTopic department) {
    LegalText referenced = department.getDpp();
    if (referenced != null) {
      return publishedContentOrNull(referenced.getContent(), referenced.getPublicationStatus());
    }
    return publishedContentOrNull(
        department.getContentDpp(), department.getPublicationStatus());
  }

  private String resolveImprint(AgencyTopic department) {
    LegalText referenced = department.getImprint();
    if (referenced != null) {
      return publishedContentOrNull(referenced.getContent(), referenced.getPublicationStatus());
    }
    return publishedContentOrNull(
        department.getContentImprint(), department.getPublicationStatusImprint());
  }

  private void assertAgencyIsNotDeleted(Agency agency) {
    if (agency == null || agency.getDeleteDate() != null) {
      throw new NotFoundException();
    }
  }

  private String publishedContentOrNull(String content, PublicationStatus status) {
    var hasContent = content != null && !content.isBlank();
    return PublicationStatus.PUBLISHED == status && hasContent ? content : null;
  }
}
