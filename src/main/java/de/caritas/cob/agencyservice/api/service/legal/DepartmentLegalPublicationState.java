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
 * <p>This is the <b>admin</b> predicate (the Fachbereich switcher in the legal editors,
 * ORISO-Admin#583): an admin publishing a correction to the agency-wide text needs to know who
 * will <em>not</em> receive it.
 *
 * <p><b>Not to be used for the public agency read.</b> That one asks the opposite question — "is a
 * document in force for this department at all", resolved across the four ADR-021 levels — because
 * its consumer decides whether to offer the text to a help-seeker; see
 * {@code AgencyService#hasPublishedDpp}. Answering "of its own" there was the defect
 * CONTEXT-legal-documents records: a department reported {@code false} while {@code /legal}
 * returned an inherited document. Conversely, answering "in force" here would mark every
 * department of an agency that has any agency-wide text and make the marker useless. Both OpenAPI
 * specs state which of the two their field is.
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
