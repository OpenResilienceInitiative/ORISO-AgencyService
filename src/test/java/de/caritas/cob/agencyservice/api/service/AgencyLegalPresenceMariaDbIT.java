package de.caritas.cob.agencyservice.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.AgencyServiceApplication;
import de.caritas.cob.agencyservice.api.controller.AgencyController;
import de.caritas.cob.agencyservice.api.exception.MissingConsultingTypeException;
import de.caritas.cob.agencyservice.api.manager.consultingtype.ConsultingTypeManager;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextLevel;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextVersion;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextVersionRepository;
import de.caritas.cob.agencyservice.consultingtypeservice.generated.web.model.ExtendedConsultingTypeResponseDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

@SpringBootTest(classes = {AgencyServiceApplication.class, AgencyLegalPresenceMariaDbIT.Config.class})
@ActiveProfiles("testing")
@Testcontainers
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.mariadb.jdbc.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MariaDBDialect",
    "spring.liquibase.enabled=true",
    "spring.liquibase.contexts=seed",
    "spring.jpa.hibernate.ddl-auto=validate",
    "multitenancy.enabled=false",
    "feature.multitenancy.with.single.domain.enabled=false",
    "feature.topics.enabled=false"})
class AgencyLegalPresenceMariaDbIT {

  private static final long AGENCY_ID = 980001L;
  private static final long TENANT_ID = 71L;
  private static final int CONSULTING_TYPE = 17;
  private static final String POSTCODE = "08629";
  private static final String PRIVACY = "{\"de\":\"<p>Synthetic policy</p>\"}";
  private static final String IMPRINT = "{\"de\":\"<p>Synthetic imprint</p>\"}";

  @Container
  static final MariaDBContainer MARIA_DB = new MariaDBContainer("mariadb:10.11.18")
      .withDatabaseName("agencyservice");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MARIA_DB::getJdbcUrl);
    registry.add("spring.datasource.username", MARIA_DB::getUsername);
    registry.add("spring.datasource.password", MARIA_DB::getPassword);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Config {
    @Bean
    LegalVersionReads legalVersionReads() {
      return new LegalVersionReads();
    }

    @Bean
    HibernatePropertiesCustomizer versionReadInspector(LegalVersionReads reads) {
      return properties -> properties.put("hibernate.session_factory.statement_inspector", reads);
    }
  }

  static class LegalVersionReads implements StatementInspector {
    final AtomicInteger count = new AtomicInteger();

    @Override
    public String inspect(String sql) {
      var normalized = sql.toLowerCase(Locale.ROOT).stripLeading();
      if (normalized.startsWith("select") && normalized.contains("legal_text_version")) {
        count.incrementAndGet();
      }
      return sql;
    }
  }

  @Autowired private AgencyService agencyService;
  @Autowired private AgencyController agencyController;
  @Autowired private LegalTextVersionRepository versions;
  @Autowired private LegalVersionReads versionReads;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private ConsultingTypeManager consulting;
  @MockitoBean private TenantService tenants;
  @MockitoBean private TopicEnrichmentService topicEnrichment;

  @ParameterizedTest
  @ValueSource(ints = {1, 12})
  void searchesSkipVersionReadsWhilePublicDetailsRetainPublishedVersions(int departments)
      throws MissingConsultingTypeException {
    when(consulting.getConsultingTypeSettings(CONSULTING_TYPE))
        .thenReturn(new ExtendedConsultingTypeResponseDTO());
    when(tenants.getRestrictedTenantDataByTenantId(TENANT_ID))
        .thenReturn(new RestrictedTenantDTO().id(TENANT_ID).active(true));
    jdbc.update("""
        insert into agency (id, id_old, tenant_id, name, consulting_type, content_dpp, content_imprint)
        values (?,?,?,'Synthetic centre',?,?,?)
        """, AGENCY_ID, AGENCY_ID, TENANT_ID, CONSULTING_TYPE, PRIVACY, IMPRINT);
    jdbc.update("""
        insert into agency_postcode_range (id, agency_id, postcode_from, postcode_to)
        values (?,?,?,?)
        """, AGENCY_ID, AGENCY_ID, POSTCODE, POSTCODE);
    for (long topic = 1; topic <= departments; topic++) {
      jdbc.update("""
          insert into agency_topic (id, agency_id, topic_id)
          values (?,?,?)
          """, AGENCY_ID + topic, AGENCY_ID, topic);
    }
    final long privacyId = publish(LegalTextKind.DPP, PRIVACY);
    final long imprintId = publish(LegalTextKind.IMPRINT, IMPRINT);
    versionReads.count.set(0);

    var result = agencyService.getAgencies(Optional.of(POSTCODE), CONSULTING_TYPE, Optional.empty());

    assertThat(result).singleElement().satisfies(agency ->
        assertThat(agency.getDepartments()).hasSize(departments).allSatisfy(department -> {
          assertThat(department.getHasPublishedDpp()).isTrue();
          assertThat(department.getHasPublishedImprint()).isTrue();
        }));
    final int searchVersionReads = versionReads.count.getAndSet(0);

    var detail = agencyController.getDepartmentLegal(AGENCY_ID, 1L).getBody();

    assertThat(detail).isNotNull();
    assertThat(detail.getDpp().getVersionId()).isEqualTo(privacyId);
    assertThat(detail.getImprint().getVersionId()).isEqualTo(imprintId);
    assertThat(detail.getDpp().getContent()).contains("Synthetic policy");
    assertThat(detail.getImprint().getContent()).contains("Synthetic imprint");
    assertThat(detail.getDpp().getSourceLevel().getValue()).isEqualTo("AGENCY");
    assertThat(detail.getImprint().getSourceLevel().getValue()).isEqualTo("AGENCY");
    assertThat(versionReads.count.get()).as("detail legal version SELECTs").isEqualTo(2);
    assertThat(searchVersionReads).as("registration legal version SELECTs").isZero();
  }

  private long publish(LegalTextKind kind, String content) {
    return versions.saveAndFlush(LegalTextVersion.builder()
        .tenantId(TENANT_ID)
        .kind(kind)
        .ownerLevel(LegalTextLevel.AGENCY)
        .ownerId(AGENCY_ID)
        .content(content)
        .publishedAt(LocalDateTime.of(2026, 11, 22, 0, 0))
        .publishedBy("synthetic-fixture")
        .build()).getId();
  }
}
