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
   * content_imprint}. The backfill deliberately keeps the inline columns for one release as this
   * read-fallback and as the rollback anchor; they are not cleared on unassign.
   *
   * <p>Whatever the department resolves to, an unpublished or absent result then inherits the
   * agency-wide text (#222) — the middle level of the chain tenant → agency → department. The
   * tenant remains the last fallback and is still resolved client-side (see ADR-014 / #134).
   */
  private String resolveDpp(AgencyTopic department) {
    LegalText referenced = department.getDpp();
    var own =
        referenced != null
            ? publishedContentOrNull(referenced.getContent(), referenced.getPublicationStatus())
            : publishedContentOrNull(department.getContentDpp(), department.getPublicationStatus());
    return own != null ? own : agencyWideOrNull(department.getAgency().getContentDpp());
  }

  private String resolveImprint(AgencyTopic department) {
    LegalText referenced = department.getImprint();
    var own =
        referenced != null
            ? publishedContentOrNull(referenced.getContent(), referenced.getPublicationStatus())
            : publishedContentOrNull(
                department.getContentImprint(), department.getPublicationStatusImprint());
    return own != null ? own : agencyWideOrNull(department.getAgency().getContentImprint());
  }

  /**
   * The inherited middle level of the ADR-014 chain tenant → agency → department. It carries no
   * publication status of its own: a Beratungsstelle's stored text is the text in force, so any
   * department that has not published its own keeps showing it. That is deliberate — a department
   * still drafting must not blank out a legally required document, so a DRAFT falls through to
   * here rather than resolving to nothing.
   */
  private String agencyWideOrNull(String agencyWideContent) {
    return agencyWideContent == null || agencyWideContent.isBlank() ? null : agencyWideContent;
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
