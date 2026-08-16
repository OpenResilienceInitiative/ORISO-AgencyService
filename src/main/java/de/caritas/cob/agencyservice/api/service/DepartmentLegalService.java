package de.caritas.cob.agencyservice.api.service;

import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import de.caritas.cob.agencyservice.api.service.legal.LegalTextInheritanceResolver;
import de.caritas.cob.agencyservice.api.service.legal.PublicLegalTextRenderer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public (unauthenticated) read side of the department's (Fachbereich = agency × topic) legal
 * texts: what a help-seeker is shown before and at Gate 2.
 *
 * <p>Since ADR-021 decision 9 the resolution is not this class's own logic any more — it delegates
 * to {@link LegalTextInheritanceResolver}, which walks the whole ladder department → agency →
 * Träger → platform operator in one place. Before that, this class stopped at the agency level and
 * the two upper rungs were reimplemented on the clients (Admin {@code mergeTranslatedContent},
 * Frontend {@code pickConsentPrivacyContent}), so which document a help-seeker saw depended on
 * which client rendered it.
 *
 * <p>Draft or never-authored texts still resolve past the department level rather than to nothing —
 * a draft is a provisional working basis, never a document shown to clients, and a Fachbereich
 * still drafting must not blank out a legally required document.
 */
@Service
@RequiredArgsConstructor
public class DepartmentLegalService {

  private final @NonNull AgencyTopicRepository agencyTopicRepository;
  private final @NonNull LegalTextInheritanceResolver legalTextInheritanceResolver;
  private final @NonNull PublicLegalTextRenderer publicLegalTextRenderer;

  /**
   * Loads the legal texts in force for the department, resolved across all four ADR-021 levels.
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

    // ADR-021 decision 5: substitute what the server owns, leave {{legal_links}} for the client.
    return new DepartmentLegalView(
        publicLegalTextRenderer.render(
            legalTextInheritanceResolver.resolveDpp(department), department),
        publicLegalTextRenderer.render(
            legalTextInheritanceResolver.resolveImprint(department), department));
  }

  /**
   * Whether the department has a data protection policy in force at all, across the whole chain.
   *
   * <p>This is the flag the registration search reports as {@code hasPublishedDpp}. It used to be
   * computed from the department level alone while this service already fell back to the
   * agency-wide text, so a department could report {@code false} while {@code /legal} returned
   * content — the client then hid a document that existed.
   */
  @Transactional(readOnly = true)
  public boolean hasResolvableDpp(AgencyTopic department) {
    return legalTextInheritanceResolver.resolveDpp(department).isPresent();
  }

  private void assertAgencyIsNotDeleted(Agency agency) {
    if (agency == null || agency.getDeleteDate() != null) {
      throw new NotFoundException();
    }
  }

  /** Kept for callers that only care whether a raw stored text counts as published. */
  static boolean isPublished(String content, PublicationStatus status) {
    var hasContent = content != null && !content.isBlank();
    return PublicationStatus.PUBLISHED == status && hasContent;
  }
}
