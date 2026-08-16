package de.caritas.cob.agencyservice.api.service.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.topicservice.generated.web.model.TopicDTO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ADR-021 decision 5: the server substitutes what it owns and stops there. This closes the gap
 * CONTEXT-legal-documents records against the public {@code /legal} path, which returned raw,
 * unrendered text so a placeholder could reach a help-seeker verbatim.
 */
@ExtendWith(MockitoExtension.class)
class PublicLegalTextRendererTest {

  @Mock private de.caritas.cob.agencyservice.api.service.TopicService topicService;

  private PublicLegalTextRenderer renderer(boolean withTopicService) {
    var instance = new PublicLegalTextRenderer();
    if (withTopicService) {
      ReflectionTestUtils.setField(instance, "topicService", topicService);
    }
    return instance;
  }

  private AgencyTopic department() {
    return AgencyTopic.builder()
        .id(4711L)
        .topicId(42L)
        .agency(Agency.builder().id(7L).name("Caritas Freiburg").consultingTypeId(1).build())
        .build();
  }

  private ResolvedLegalText resolved() {
    return new ResolvedLegalText(
        "{\"de\":\"<p>DSE der {{Beratungsstelle}}, Fachbereich {{Thema}}.</p>\"}",
        "{\"de\":\"Ich habe die {{legal_links}} der {{Beratungsstelle}} zum Thema {{Thema}}"
            + " gelesen.\"}",
        LegalTextSourceLevel.DEPARTMENT,
        100L);
  }

  @Test
  void render_Should_substituteTheServerOwnedTokens_andLeaveLegalLinksForTheClient() {
    when(topicService.getAllTopics())
        .thenReturn(List.of(new TopicDTO().id(42L).name("Suchtberatung")));

    var rendered = renderer(true).render(resolved(), department());

    assertThat(rendered.content()).contains("Caritas Freiburg").contains("Suchtberatung");
    assertThat(rendered.content()).doesNotContain("{{Beratungsstelle}}").doesNotContain("{{Thema}}");
    assertThat(rendered.consentText()).contains("Caritas Freiburg").contains("Suchtberatung");
    // The link targets live in the frontend deployment configuration; the backend does not know
    // them, so its token survives on purpose.
    assertThat(rendered.consentText()).contains("{{legal_links}}");
  }

  @Test
  void render_Should_keepTheLevelAndVersionId() {
    when(topicService.getAllTopics()).thenReturn(List.of());

    var rendered = renderer(true).render(resolved(), department());

    // Substitution must not lose the two facts a consumer needs: which level governs, and which
    // version the wording is (ORISO-UserService session.consented_legal_version_id).
    assertThat(rendered.sourceLevel()).isEqualTo(LegalTextSourceLevel.DEPARTMENT);
    assertThat(rendered.versionId()).isEqualTo(100L);
  }

  @Test
  void render_Should_leaveTheTopicToken_When_theTopicNameCannotBeResolved() {
    when(topicService.getAllTopics()).thenThrow(new IllegalStateException("no bearer token"));

    var rendered = renderer(true).render(resolved(), department());

    // The Fachbereich name lives in ORISO-TopicService behind the caller's bearer token, which the
    // public endpoint does not have. Leaving the token lets the client — which knows the topic it
    // navigated to — finish the job. Substituting an empty string would silently corrupt a legal
    // sentence into "zum Thema  gelesen".
    assertThat(rendered.content()).contains("Caritas Freiburg").contains("{{Thema}}");
  }

  @Test
  void render_Should_workWithoutTheOptionalTopicService() {
    var rendered = renderer(false).render(resolved(), department());

    // The topics feature is toggleable; the public legal path must not depend on it.
    assertThat(rendered.content()).contains("Caritas Freiburg").contains("{{Thema}}");
  }

  @Test
  void render_Should_tolerateNothingToSubstitute() {
    var rendered = renderer(false).render(ResolvedLegalText.none(), department());

    assertThat(rendered.content()).isNull();
    assertThat(rendered.sourceLevel()).isEqualTo(LegalTextSourceLevel.NONE);
  }

  @Test
  void render_Should_passThrough_When_thereIsNoDepartment() {
    var resolved = resolved();

    assertThat(renderer(false).render(resolved, null)).isSameAs(resolved);
    assertThat(renderer(false).render(null, department())).isNull();
  }
}
