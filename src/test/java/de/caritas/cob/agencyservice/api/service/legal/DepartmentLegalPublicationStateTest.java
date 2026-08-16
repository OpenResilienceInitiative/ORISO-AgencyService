package de.caritas.cob.agencyservice.api.service.legal;

import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalText;
import org.junit.jupiter.api.Test;

/**
 * The single answer to "has this Fachbereich left the inherited text?", used by both the public
 * agency read and the admin read (ORISO-Admin#583). The two must never disagree.
 */
class DepartmentLegalPublicationStateTest {

  private AgencyTopic inline(String dpp, PublicationStatus dppStatus) {
    var topic = new AgencyTopic();
    topic.setContentDpp(dpp);
    topic.setPublicationStatus(dppStatus);
    return topic;
  }

  @Test
  void hasPublishedDpp_isTrue_forPublishedInlineContent() {
    assertThat(DepartmentLegalPublicationState.hasPublishedDpp(
        inline("<p>eigene Richtlinie</p>", PublicationStatus.PUBLISHED))).isTrue();
  }

  @Test
  void hasPublishedDpp_isFalse_whileTheOwnTextIsStillADraft() {
    assertThat(DepartmentLegalPublicationState.hasPublishedDpp(
        inline("<p>eigene Richtlinie</p>", PublicationStatus.DRAFT))).isFalse();
  }

  @Test
  void hasPublishedDpp_isFalse_forAPublishedButEmptyDocument() {
    // Marking a department as "has its own text" while help-seekers are shown nothing
    // would send an admin looking for a document that does not exist.
    assertThat(DepartmentLegalPublicationState.hasPublishedDpp(
        inline("   ", PublicationStatus.PUBLISHED))).isFalse();
  }

  @Test
  void hasPublishedDpp_isFalse_whenNothingIsStoredAtAll() {
    assertThat(DepartmentLegalPublicationState.hasPublishedDpp(inline(null, null))).isFalse();
  }

  /**
   * ADR-014: a referenced shared legal text fully REPLACES the inline column. Reading the inline
   * column first would report stale state after a write-through publish or draft-save.
   */
  @Test
  void hasPublishedDpp_prefersTheReferencedTextOverAStaleInlineColumn() {
    var topic = inline("<p>alt, noch als veröffentlicht markiert</p>", PublicationStatus.PUBLISHED);
    var referenced = new LegalText();
    referenced.setContent("<p>neu</p>");
    referenced.setPublicationStatus(PublicationStatus.DRAFT);
    topic.setDpp(referenced);

    assertThat(DepartmentLegalPublicationState.hasPublishedDpp(topic)).isFalse();
  }

  @Test
  void hasPublishedDpp_followsAPublishedReferencedTextEvenWithAnEmptyInlineColumn() {
    var topic = inline(null, null);
    var referenced = new LegalText();
    referenced.setContent("<p>geteilte Richtlinie</p>");
    referenced.setPublicationStatus(PublicationStatus.PUBLISHED);
    topic.setDpp(referenced);

    assertThat(DepartmentLegalPublicationState.hasPublishedDpp(topic)).isTrue();
  }

  @Test
  void hasPublishedImprint_readsTheImprintColumns_notTheDppOnes() {
    var topic = new AgencyTopic();
    topic.setContentDpp("<p>Richtlinie</p>");
    topic.setPublicationStatus(PublicationStatus.PUBLISHED);
    topic.setContentImprint(null);
    topic.setPublicationStatusImprint(null);

    assertThat(DepartmentLegalPublicationState.hasPublishedImprint(topic)).isFalse();
  }

  @Test
  void hasPublishedImprint_isTrue_forItsOwnPublishedContent() {
    var topic = new AgencyTopic();
    topic.setContentImprint("<p>eigenes Impressum</p>");
    topic.setPublicationStatusImprint(PublicationStatus.PUBLISHED);

    assertThat(DepartmentLegalPublicationState.hasPublishedImprint(topic)).isTrue();
  }
}
