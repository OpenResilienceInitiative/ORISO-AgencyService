package de.caritas.cob.agencyservice.api.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.admin.service.legal.TraegerLegalTextClient;
import de.caritas.cob.agencyservice.api.admin.service.legal.TraegerLegalTextClient.TraegerLegalText;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Träger → Beratungsstelle templates over HTTP with the real security chain and database
 * (ORISO-AgencyService#303). Fixture: every agency belongs to Träger 1 except 1734 (Träger 2);
 * agency 0 has departments with topic 0 and 1.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.ANY)
@TestPropertySource(
    properties = {
      "spring.profiles.active=testing",
      "multitenancy.enabled=false",
      "csrf.header.property=csrfHeader",
      "csrf.cookie.property=csrfCookie"
    })
@Sql(scripts = {"/database/AgencyDatabase.sql", "/setTenants.sql"})
class AgencyLegalProposalControllerIT {

  private static final String DISTRIBUTIONS = "/agencyadmin/legal-proposal-distributions";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private TraegerLegalTextClient traegerLegalTextClient;
  @MockitoBean private UserAdminService userAdminService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM agency_legal_draft_archive");
    jdbcTemplate.update("DELETE FROM agency_legal_proposal");
    jdbcTemplate.update("DELETE FROM agency_legal_proposal_distribution");
    jdbcTemplate.update("DELETE FROM agency_legal_draft");
    // Department (agency 0, topic 0) has left the inherited privacy policy.
    jdbcTemplate.update(
        "UPDATE AGENCY_TOPIC SET CONTENT_DPP = '{\"de\":\"eigene\"}', PUBLICATION_STATUS ="
            + " 'PUBLISHED' WHERE ID = 0");
    token("traeger-1", 1L, "traeger-admin-1", List.of("tenant-admin", "agency-admin"));
    token("traeger-2", 2L, "traeger-admin-2", List.of("tenant-admin", "agency-admin"));
    token("agency-admin-1", 1L, "agency-admin-1", List.of("agency-admin"));
    token("restricted-0", 1L, "restricted-0", List.of("restricted-agency-admin"));
    when(userAdminService.getAdminUserAgencyIds("restricted-0")).thenReturn(List.of(0L));
    when(traegerLegalTextClient.draft(1L, LegalTextKind.DPP))
        .thenReturn(
            new TraegerLegalText(
                "12:3",
                Map.of("de", "<p>Träger-Fassung</p><script>x()</script>"),
                Map.of("de", "Ich habe die {{legal_links}} gelesen.")));
  }

  @Test
  void traegerForwards_agencyAdopts_nothingIsPublished() throws Exception {
    String publishedBefore =
        jdbcTemplate.queryForObject("SELECT CONTENT_DPP FROM AGENCY WHERE ID = 0", String.class);

    JsonNode distribution =
        json(
            mvc.perform(
                    as("traeger-1", post(DISTRIBUTIONS))
                        .content(distributionBody("req-1", "SELECTED", "[0,1]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipientAgencyIds.length()").value(2))
                .andExpect(jsonPath("$.kind").value("DPP"))
                .andExpect(jsonPath("$.source").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(distribution.get("proposals").get(0).get("content").get("de").asText())
        .isEqualTo("<p>Träger-Fassung</p>");

    // The same request again is the same distribution, and TenantService is not asked again.
    mvc.perform(
            as("traeger-1", post(DISTRIBUTIONS))
                .content(distributionBody("req-1", "SELECTED", "[0,1]")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.distributionId").value(distribution.get("distributionId").asText()));

    JsonNode inbox =
        json(
            mvc.perform(as("agency-admin-1", get("/agencyadmin/agencies/0/legal-proposals")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].departmentImpact.affected").value(1))
                .andExpect(jsonPath("$[0].departmentImpact.notAffected").value(1))
                .andExpect(jsonPath("$[0].departmentImpact.notAffectedTopicIds[0]").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString());
    long proposalId = inbox.get(0).get("id").asLong();
    String revision = inbox.get(0).get("revision").asText();

    mvc.perform(
            as("agency-admin-1", post("/agencyadmin/agencies/0/legal-proposals/" + proposalId + "/adopt"))
                .content(
                    "{\"mode\":\"CREATE_IF_EMPTY\",\"expectedProposalRevision\":\""
                        + revision
                        + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.draft.content.de").value("<p>Träger-Fassung</p>"))
        .andExpect(jsonPath("$.draft.consentText.de").value("Ich habe die {{legal_links}} gelesen."))
        .andExpect(jsonPath("$.draft.originProposalId").value(proposalId))
        .andExpect(jsonPath("$.proposal.status").value("ADOPTED"))
        .andExpect(jsonPath("$.departmentImpact.notAffected").value(1));

    // A copy in the draft; the live agency text is untouched.
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT CONTENT_DPP FROM AGENCY WHERE ID = 0", String.class))
        .isEqualTo(publishedBefore);
    mvc.perform(as("agency-admin-1", get("/agencyadmin/agencies/0/legal-drafts/DPP")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.originProposalId").value(proposalId));
  }

  @Test
  void adoptOverAnEditedDraftArchivesItAndStaleRevisionsConflict() throws Exception {
    long proposalId = forwardToAgencyZero("req-archive");
    String proposalRevision = proposalRevision(proposalId);
    JsonNode draft =
        json(
            mvc.perform(
                    as("agency-admin-1", put("/agencyadmin/agencies/0/legal-drafts/DPP"))
                        .content("{\"content\":{\"de\":\"<p>eigene Arbeit</p>\"}}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    mvc.perform(
            as("agency-admin-1", post("/agencyadmin/agencies/0/legal-proposals/" + proposalId + "/adopt"))
                .content(
                    "{\"mode\":\"CREATE_IF_EMPTY\",\"expectedProposalRevision\":\""
                        + proposalRevision
                        + "\"}"))
        .andExpect(status().isConflict());
    mvc.perform(
            as("agency-admin-1", post("/agencyadmin/agencies/0/legal-proposals/" + proposalId + "/adopt"))
                .content(
                    "{\"mode\":\"ARCHIVE_AND_REPLACE\",\"expectedProposalRevision\":\""
                        + proposalRevision
                        + "\",\"expectedDraftRevision\":\"00000000-0000-0000-0000-000000000000:0\"}"))
        .andExpect(status().isConflict());
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agency_legal_draft_archive", Integer.class))
        .isZero();

    mvc.perform(
            as("agency-admin-1", post("/agencyadmin/agencies/0/legal-proposals/" + proposalId + "/adopt"))
                .content(
                    "{\"mode\":\"ARCHIVE_AND_REPLACE\",\"expectedProposalRevision\":\""
                        + proposalRevision
                        + "\",\"expectedDraftRevision\":\""
                        + draft.get("revision").asText()
                        + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedDraft.content.de").value("<p>eigene Arbeit</p>"))
        .andExpect(jsonPath("$.draft.content.de").value("<p>Träger-Fassung</p>"));

    mvc.perform(as("agency-admin-1", get("/agencyadmin/agencies/0/legal-draft-archives?kind=DPP")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].replacedByProposalId").value(proposalId));
  }

  @Test
  void dismissKeepsTheOfferAndANewerForwardSupersedesIt() throws Exception {
    long first = forwardToAgencyZero("req-first");
    mvc.perform(
            as("agency-admin-1", post("/agencyadmin/agencies/0/legal-proposals/" + first + "/dismiss"))
                .content("{\"expectedProposalRevision\":\"" + proposalRevision(first) + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISMISSED"));

    when(traegerLegalTextClient.draft(1L, LegalTextKind.DPP))
        .thenReturn(new TraegerLegalText("12:4", Map.of("de", "<p>Korrektur</p>"), null));
    mvc.perform(
            as("traeger-1", post(DISTRIBUTIONS))
                .content(
                    distributionBody("req-second", "SELECTED", "[0]").replace("12:3", "12:4")))
        .andExpect(status().isCreated());

    mvc.perform(as("agency-admin-1", get("/agencyadmin/agencies/0/legal-proposals?kind=DPP")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].status").value("PENDING"))
        .andExpect(jsonPath("$[0].content.de").value("<p>Korrektur</p>"))
        .andExpect(jsonPath("$[1].status").value("SUPERSEDED"));
  }

  @Test
  void crossTenantAndNonTraegerCallersAreForbidden() throws Exception {
    long proposalId = forwardToAgencyZero("req-security");

    // Träger 2 may neither forward to Träger 1's Beratungsstelle nor read its inbox.
    mvc.perform(
            as("traeger-2", post(DISTRIBUTIONS))
                .content(distributionBody("req-foreign", "SELECTED", "[0]")))
        .andExpect(status().isForbidden());
    mvc.perform(as("traeger-2", get("/agencyadmin/agencies/0/legal-proposals")))
        .andExpect(status().isForbidden());
    mvc.perform(as("traeger-2", get("/agencyadmin/agencies/0/legal-proposals/" + proposalId)))
        .andExpect(status().isForbidden());
    mvc.perform(
            as("traeger-2", post("/agencyadmin/agencies/0/legal-proposals/" + proposalId + "/adopt"))
                .content("{\"mode\":\"CREATE_IF_EMPTY\",\"expectedProposalRevision\":\"x\"}"))
        .andExpect(status().isForbidden());
    // Träger 1 naming Träger 2 as the source is not acting for its own Träger.
    mvc.perform(
            as("traeger-1", post(DISTRIBUTIONS))
                .content(
                    distributionBody("req-other-tenant", "SELECTED", "[0]")
                        .replace("}", ",\"tenantId\":2}")))
        .andExpect(status().isForbidden());
    // Beratungsstellen admins cannot forward at all.
    mvc.perform(
            as("agency-admin-1", post(DISTRIBUTIONS))
                .content(distributionBody("req-agency", "SELECTED", "[0]")))
        .andExpect(status().isForbidden());
    // A restricted admin sees only its own Beratungsstelle.
    mvc.perform(as("restricted-0", get("/agencyadmin/agencies/0/legal-proposals")))
        .andExpect(status().isOk());
    mvc.perform(as("restricted-0", get("/agencyadmin/agencies/1/legal-proposals")))
        .andExpect(status().isForbidden());
    // Another Beratungsstelle cannot reach this offer through its own path.
    mvc.perform(as("agency-admin-1", get("/agencyadmin/agencies/1/legal-proposals/" + proposalId)))
        .andExpect(status().isNotFound());
    verify(traegerLegalTextClient, never()).draft(any(), any());
  }

  @Test
  void staleTraegerRevisionIsAConflictAndNothingIsSent() throws Exception {
    mvc.perform(
            as("traeger-1", post(DISTRIBUTIONS))
                .content(distributionBody("req-stale", "SELECTED", "[0]").replace("12:3", "12:2")))
        .andExpect(status().isConflict());
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agency_legal_proposal", Integer.class))
        .isZero();
  }

  private long forwardToAgencyZero(String requestKey) throws Exception {
    JsonNode distribution =
        json(
            mvc.perform(
                    as("traeger-1", post(DISTRIBUTIONS))
                        .content(distributionBody(requestKey, "SELECTED", "[0]")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    org.mockito.Mockito.clearInvocations(traegerLegalTextClient);
    return distribution.get("proposals").get(0).get("id").asLong();
  }

  private String proposalRevision(long proposalId) throws Exception {
    return json(
            mvc.perform(
                    as("agency-admin-1", get("/agencyadmin/agencies/0/legal-proposals/" + proposalId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("revision")
        .asText();
  }

  private static String distributionBody(String requestKey, String audience, String agencyIds) {
    return "{\"requestKey\":\""
        + requestKey
        + "\",\"kind\":\"PRIVACY\",\"sourceRevision\":\"12:3\",\"audience\":\""
        + audience
        + "\",\"agencyIds\":"
        + agencyIds
        + "}";
  }

  private static MockHttpServletRequestBuilder put(String url) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url);
  }

  private MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
    return request
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON);
  }

  private JsonNode json(String body) throws Exception {
    return objectMapper.readTree(body);
  }

  private void token(String value, Long tenantId, String userId, List<String> roles) {
    Jwt jwt =
        Jwt.withTokenValue(value)
            .header("alg", "none")
            .claim("sub", userId)
            .claim("userId", userId)
            .claim("username", userId)
            .claim("tenantId", tenantId)
            .claim("realm_access", Map.of("roles", roles))
            .build();
    when(jwtDecoder.decode(value)).thenReturn(jwt);
  }
}
