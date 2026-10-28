package de.caritas.cob.agencyservice.api.service.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.topicservice.generated.web.model.TopicDTO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.owasp.html.HtmlPolicyBuilder;
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
  void render_Should_fillTheAddressOfTheBeratungsstelle() {
    var department =
        AgencyTopic.builder()
            .topicId(42L)
            .agency(
                Agency.builder()
                    .id(7L)
                    .name("Caritas Freiburg")
                    .street("Musterstraße 1")
                    .postCode("79098")
                    .city("Freiburg")
                    .consultingTypeId(1)
                    .build())
            .build();
    var text =
        new ResolvedLegalText(
            "{\"de\":\"<p>{{Beratungsstelle}}, {{Adresse}}</p>\"}",
            null,
            LegalTextSourceLevel.TENANT,
            1L);

    var rendered = renderer(false).render(text, department);

    assertThat(rendered.content()).contains("Caritas Freiburg, Musterstraße 1, 79098 Freiburg");
  }

  @Test
  void render_Should_leaveTheAddressToken_When_theAgencyHasNoAddress() {
    var text =
        new ResolvedLegalText(
            "{\"de\":\"<p>{{Adresse}}</p>\"}", null, LegalTextSourceLevel.TENANT, 1L);

    var rendered = renderer(false).render(text, department());

    assertThat(rendered.content()).contains("{{Adresse}}");
  }

  @Test
  void render_Should_substituteTokensThatTheTenantSanitizerSplit() {
    // Träger texts are stored by TenantService, whose OWASP sanitiser turns {{ into {<!-- -->{.
    var split =
        new HtmlPolicyBuilder()
            .allowElements("p")
            .toFactory()
            .sanitize("<p>{{Beratungsstelle}} in {{Adresse}}</p>");
    assertThat(split).contains("{<!-- -->{");
    var department =
        AgencyTopic.builder()
            .topicId(42L)
            .agency(
                Agency.builder()
                    .id(7L)
                    .name("Caritas Freiburg")
                    .street("Musterstraße 1")
                    .postCode("79098")
                    .city("Freiburg")
                    .consultingTypeId(1)
                    .build())
            .build();
    var text =
        new ResolvedLegalText(
            "{\"de\":\"" + split.replace("\"", "\\\"") + "\"}",
            null,
            LegalTextSourceLevel.TENANT,
            1L);

    var rendered = renderer(false).render(text, department);

    assertThat(rendered.content())
        .contains("Caritas Freiburg in Musterstraße 1, 79098 Freiburg")
        .doesNotContain("<!-- -->");
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
  void render_Should_escapeMarkupInSubstitutedNames() {
    when(topicService.getAllTopics())
        .thenReturn(List.of(new TopicDTO().id(42L).name("Sucht<script>alert(1)</script>")));
    var department = department();
    department.setAgency(
        Agency.builder()
            .id(7L)
            .name("<img src=x onerror=alert(1)>")
            .consultingTypeId(1)
            .build());

    var rendered = renderer(true).render(resolved(), department);

    // Substitution happens AFTER LegalContentSanitizer, so an unescaped name would land in HTML
    // that the frontend renders with dangerouslySetInnerHTML - stored XSS reaching every
    // help-seeker of that Beratungsstelle.
    assertThat(rendered.content()).doesNotContain("<img").doesNotContain("<script>");
    assertThat(rendered.content()).contains("&lt;img").contains("&lt;script&gt;");
  }

  @Test
  void render_Should_produceValidJson_When_aNameContainsQuotes() throws Exception {
    var department = department();
    department.setAgency(
        Agency.builder().id(7L).name("Caritas \"Mitte\"").consultingTypeId(1).build());

    var rendered = renderer(false).render(resolved(), department);

    // Substituting into the serialized JSON would break out of the string and hand every client an
    // unparseable legal document. Round-tripping proves it stayed a map.
    var parsed =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(
                rendered.content(),
                new com.fasterxml.jackson.core.type.TypeReference<
                    java.util.LinkedHashMap<String, String>>() {});
    assertThat(parsed).containsKey("de");
    assertThat(parsed.get("de")).contains("Caritas &quot;Mitte&quot;");
  }

  @Test
  void render_Should_leaveTranslationMetadataUntouched() throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var metaValue = "{\"mt\":true,\"src\":\"de\"}";
    var stored =
        mapper.writeValueAsString(
            java.util.Map.of("de", "<p>{{Beratungsstelle}}</p>", "de__meta", metaValue));

    var rendered =
        renderer(false)
            .render(
                new ResolvedLegalText(stored, null, LegalTextSourceLevel.DEPARTMENT, 1L),
                department());

    var parsed =
        mapper.readValue(
            rendered.content(),
            new com.fasterxml.jackson.core.type.TypeReference<
                java.util.LinkedHashMap<String, String>>() {});
    // __meta carries JSON, not prose; it must survive byte-identically.
    assertThat(parsed.get("de__meta")).isEqualTo(metaValue);
    assertThat(parsed.get("de")).contains("Caritas Freiburg");
  }

  @Test
  void render_Should_serveUnsubstituted_When_theStoredContentIsNotAMap() {
    var broken =
        new ResolvedLegalText("not json at all", null, LegalTextSourceLevel.DEPARTMENT, 1L);

    var rendered = renderer(false).render(broken, department());

    // An already-damaged legal document must not additionally be handed over half-rewritten, and
    // this is a public read path that must not fail.
    assertThat(rendered.content()).isEqualTo("not json at all");
  }

  @Test
  void render_Should_passThrough_When_thereIsNoDepartment() {
    var resolved = resolved();

    assertThat(renderer(false).render(resolved, null)).isSameAs(resolved);
    assertThat(renderer(false).render(null, department())).isNull();
  }

  // --- {{Datenschutzbeauftragte}} (ORISO-Admin#1067): Beratungsstelle → Träger → empty. The
  // platform DPO is never inherited down; it has its own token that TenantService fills.

  @Mock private de.caritas.cob.agencyservice.api.service.TenantService tenantService;

  private PublicLegalTextRenderer rendererWithTenants() {
    var instance = renderer(false);
    ReflectionTestUtils.setField(instance, "tenantService", tenantService);
    return instance;
  }

  private static final String OWN_DPO_JSON =
      "{\"nameAndLegalForm\":\"Anna Agentur\",\"email\":\"dsb@beratungsstelle.de\"}";

  private AgencyTopic departmentOfTenant(
      de.caritas.cob.agencyservice.api.repository.agency.DataProtectionResponsibleEntity entity,
      String dpoJson) {
    return AgencyTopic.builder()
        .topicId(42L)
        .agency(
            Agency.builder()
                .id(7L)
                .tenantId(3L)
                .name("Caritas Freiburg")
                .consultingTypeId(1)
                .dataProtectionResponsibleEntity(entity)
                .dataProtectionOfficerContactData(dpoJson)
                .build())
        .build();
  }

  private static ResolvedLegalText dppWithDpoToken(boolean splitBySanitizer) {
    var token =
        splitBySanitizer ? "{<!-- -->{Datenschutzbeauftragte}}" : "{{Datenschutzbeauftragte}}";
    return new ResolvedLegalText(
        "{\"de\":\"<p>DSB: " + token + "</p>\"}", null, LegalTextSourceLevel.TENANT, 1L);
  }

  private void traegerDpo(String name) {
    when(tenantService.getRestrictedTenantDataByTenantId(3L))
        .thenReturn(
            new de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO()
                .id(3L)
                .dataProtectionOfficer(
                    name == null
                        ? null
                        : new de.caritas.cob.agencyservice.tenantservice.generated.web.model
                                .TenantDataProtectionOfficerDTO()
                            .nameAndLegalForm(name)
                            .postcode("79106")
                            .city("Freiburg")
                            .email("datenschutz@traeger.de")));
  }

  @Test
  void render_Should_fillTheAgencysOwnDpo_When_theBeratungsstelleHasOne() {
    var department =
        departmentOfTenant(
            de.caritas.cob.agencyservice.api.repository.agency.DataProtectionResponsibleEntity
                .DATA_PROTECTION_OFFICER,
            OWN_DPO_JSON);

    var rendered = rendererWithTenants().render(dppWithDpoToken(false), department);

    assertThat(rendered.content())
        .contains("DSB: Anna Agentur, dsb@beratungsstelle.de")
        .doesNotContain("Datenschutzbeauftragte}}");
    // Its own DPO overrides the Träger's, so the Träger is not even asked.
    org.mockito.Mockito.verifyNoInteractions(tenantService);
  }

  @Test
  void render_Should_inheritTheTraegerDpo_When_theBeratungsstelleHasNone() {
    traegerDpo("Dr. Maria Muster");
    // Chose the alternative representative: that is not a DPO, so the Träger's applies.
    var department =
        departmentOfTenant(
            de.caritas.cob.agencyservice.api.repository.agency.DataProtectionResponsibleEntity
                .ALTERNATIVE_REPRESENTATIVE,
            OWN_DPO_JSON);

    var rendered = rendererWithTenants().render(dppWithDpoToken(true), department);

    assertThat(rendered.content())
        .contains("DSB: Dr. Maria Muster, 79106 Freiburg, datenschutz@traeger.de")
        .doesNotContain("<!-- -->");
  }

  @Test
  void render_Should_leaveTheDpoEmpty_When_neitherBeratungsstelleNorTraegerHasOne() {
    traegerDpo(null);
    var department = departmentOfTenant(null, null);

    var rendered = rendererWithTenants().render(dppWithDpoToken(false), department);

    // Not a required field anywhere: nothing entered renders nothing, not a raw token.
    assertThat(rendered.content()).contains("<p>DSB: </p>").doesNotContain("{{");
  }

  @Test
  void render_Should_leaveTheDpoEmpty_When_theTraegerCannotBeRead() {
    when(tenantService.getRestrictedTenantDataByTenantId(3L))
        .thenThrow(new IllegalStateException("TenantService down"));

    var rendered =
        rendererWithTenants().render(dppWithDpoToken(false), departmentOfTenant(null, null));

    assertThat(rendered.content()).contains("<p>DSB: </p>");
  }

  @Test
  void render_Should_escapeMarkupInTheDpo() {
    traegerDpo("<img src=x onerror=alert(1)>");

    var rendered =
        rendererWithTenants().render(dppWithDpoToken(false), departmentOfTenant(null, null));

    assertThat(rendered.content()).doesNotContain("<img").contains("&lt;img");
  }

  @Test
  void render_Should_notAskTheTraeger_When_theTextHasNoDpoToken() {
    rendererWithTenants().render(resolved(), departmentOfTenant(null, null));

    org.mockito.Mockito.verifyNoInteractions(tenantService);
  }
}
