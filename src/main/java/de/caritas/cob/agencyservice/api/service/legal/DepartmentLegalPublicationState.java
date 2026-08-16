package de.caritas.cob.agencyservice.api.service.legal;

import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalText;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Whether a department (Fachbereich = agency × topic) has left the inherited legal text, i.e.
 * carries a published one of its own.
 *
 * <p>Two reads answer this question: the public agency read (registration search) and the admin
 * read (the Fachbereich switcher in the legal editors, ORISO-Admin#583). They must agree — an
 * admin publishing a correction to the agency-wide text needs to know who will <em>not</em>
 * receive it, and a switcher that disagrees with what help-seekers actually see is worse than no
 * marker at all. The resolution therefore lives here once instead of in each caller.
 *
 * <p>ADR-014 resolution order: a referenced shared {@link LegalText} fully replaces the inline
 * column. Reading the inline column first would report stale state after a write-through
 * publish or draft-save.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DepartmentLegalPublicationState {

  /** True when the department has a published data-protection policy of its own. */
  public static boolean hasPublishedDpp(AgencyTopic agencyTopic) {
    LegalText referenced = agencyTopic.getDpp();
    if (referenced != null) {
      return hasPublishedContent(referenced.getContent(), referenced.getPublicationStatus());
    }
    return hasPublishedContent(agencyTopic.getContentDpp(), agencyTopic.getPublicationStatus());
  }

  /** True when the department has a published imprint of its own. */
  public static boolean hasPublishedImprint(AgencyTopic agencyTopic) {
    LegalText referenced = agencyTopic.getImprint();
    if (referenced != null) {
      return hasPublishedContent(referenced.getContent(), referenced.getPublicationStatus());
    }
    return hasPublishedContent(
        agencyTopic.getContentImprint(), agencyTopic.getPublicationStatusImprint());
  }

  /**
   * A document counts as published only when it is both marked PUBLISHED and actually has
   * content: a PUBLISHED row with an empty body would mark a department as having left the
   * inherited text while showing help-seekers nothing.
   */
  private static boolean hasPublishedContent(String content, PublicationStatus status) {
    var hasContent = content != null && !content.isBlank();
    return PublicationStatus.PUBLISHED == status && hasContent;
  }
}
