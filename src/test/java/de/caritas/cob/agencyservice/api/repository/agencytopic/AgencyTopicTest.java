package de.caritas.cob.agencyservice.api.repository.agencytopic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pure unit test for the @PrePersist NOT-NULL defaulting of publication_status (no DB). */
class AgencyTopicTest {

  @Test
  void applyDefaults_Should_defaultPublicationStatusToDraft_When_null() {
    var topic = new AgencyTopic();
    topic.setPublicationStatus(null);

    topic.applyDefaults();

    assertThat(topic.getPublicationStatus()).isEqualTo(PublicationStatus.DRAFT);
  }

  @Test
  void applyDefaults_Should_notOverwrite_When_alreadySet() {
    var topic = new AgencyTopic();
    topic.setPublicationStatus(PublicationStatus.PUBLISHED);

    topic.applyDefaults();

    assertThat(topic.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
  }
}
