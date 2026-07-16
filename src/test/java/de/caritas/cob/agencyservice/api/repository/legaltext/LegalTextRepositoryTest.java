package de.caritas.cob.agencyservice.api.repository.legaltext;

import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.DataProtectionResponsibleEntity;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * ADR-014 persistence model: legal texts (DPP / Impressum) are first-class shared objects owned by
 * a Träger (tenant); a department (Fachbereich = agency × topic) references one per kind, and
 * several departments may share the same text instead of maintaining N inline copies.
 */
@TestPropertySource(properties = {"spring.profiles.active=testing"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ExtendWith(SpringExtension.class)
@DataJpaTest(excludeAutoConfiguration = LiquibaseAutoConfiguration.class)
class LegalTextRepositoryTest {

  @Autowired private TestEntityManager em;
  @Autowired private LegalTextRepository legalTextRepository;
  @Autowired private AgencyTopicRepository agencyTopicRepository;

  private Agency persistAgency(String name) {
    var now = LocalDateTime.now();
    return em.persistFlushFind(
        Agency.builder()
            .name(name)
            .consultingTypeId(1)
            .dataProtectionResponsibleEntity(DataProtectionResponsibleEntity.AGENCY_RESPONSIBLE)
            .dataProtectionOfficerContactData("officer")
            .dataProtectionAlternativeContactData("alternative")
            .dataProtectionAgencyResponsibleContactData("agency")
            .createDate(now)
            .updateDate(now)
            .build());
  }

  private AgencyTopic persistDepartment(Agency agency, long topicId) {
    var now = LocalDateTime.now();
    return em.persistFlushFind(
        AgencyTopic.builder().agency(agency).topicId(topicId).createDate(now).updateDate(now)
            .build());
  }

  @Test
  void persist_Should_storeLegalTextWithKindLabelContentAndStatus() {
    var now = LocalDateTime.now();
    LegalText text =
        LegalText.builder()
            .tenantId(1L)
            .kind(LegalTextKind.DPP)
            .label("Standard-DSE Träger")
            .content("{\"de\":\"<p>DSE</p>\"}")
            .publicationStatus(PublicationStatus.PUBLISHED)
            .createDate(now)
            .updateDate(now)
            .build();

    Long id = em.persistFlushFind(text).getId();
    em.clear();

    LegalText reloaded = em.find(LegalText.class, id);
    assertThat(reloaded.getTenantId()).isEqualTo(1L);
    assertThat(reloaded.getKind()).isEqualTo(LegalTextKind.DPP);
    assertThat(reloaded.getLabel()).isEqualTo("Standard-DSE Träger");
    assertThat(reloaded.getContent()).isEqualTo("{\"de\":\"<p>DSE</p>\"}");
    assertThat(reloaded.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
  }

  @Test
  void publicationStatus_Should_defaultToDraft_When_notSet() {
    var now = LocalDateTime.now();
    LegalText text =
        LegalText.builder()
            .tenantId(1L)
            .kind(LegalTextKind.IMPRINT)
            .label("Impressum")
            .content("{\"de\":\"<p>Impressum</p>\"}")
            .createDate(now)
            .updateDate(now)
            .build();

    Long id = em.persistFlushFind(text).getId();
    em.clear();

    assertThat(em.find(LegalText.class, id).getPublicationStatus())
        .isEqualTo(PublicationStatus.DRAFT);
  }

  @Test
  void twoDepartments_Should_shareOneLegalText_And_usageCountReflectsIt() {
    // given one shared DSE referenced by two Fachbereiche of the same agency
    var now = LocalDateTime.now();
    Agency agency = persistAgency("Beratungszentrum Süd");
    LegalText sharedDpp =
        em.persistFlushFind(
            LegalText.builder()
                .tenantId(1L)
                .kind(LegalTextKind.DPP)
                .label("Gemeinsame DSE")
                .content("{\"de\":\"<p>geteilt</p>\"}")
                .publicationStatus(PublicationStatus.PUBLISHED)
                .createDate(now)
                .updateDate(now)
                .build());

    AgencyTopic debtCounselling = persistDepartment(agency, 10L);
    AgencyTopic pregnancy = persistDepartment(agency, 20L);
    debtCounselling.setDpp(sharedDpp);
    pregnancy.setDpp(sharedDpp);
    em.persistAndFlush(debtCounselling);
    em.persistAndFlush(pregnancy);
    em.clear();

    // then both departments resolve to the same text and the usage count is 2
    AgencyTopic reloaded =
        agencyTopicRepository.findByAgency_IdAndTopicId(agency.getId(), 10L).orElseThrow();
    assertThat(reloaded.getDpp().getId()).isEqualTo(sharedDpp.getId());
    assertThat(reloaded.getDpp().getContent()).isEqualTo("{\"de\":\"<p>geteilt</p>\"}");
    assertThat(agencyTopicRepository.countByDpp_Id(sharedDpp.getId())).isEqualTo(2);
    assertThat(agencyTopicRepository.countByImprint_Id(sharedDpp.getId())).isZero();
  }

  @Test
  void findByTenantIdAndKind_Should_listOnlyThatTenantsTextsOfThatKind() {
    var now = LocalDateTime.now();
    em.persist(
        LegalText.builder().tenantId(1L).kind(LegalTextKind.DPP).label("t1 dpp")
            .content("{}").createDate(now).updateDate(now).build());
    em.persist(
        LegalText.builder().tenantId(1L).kind(LegalTextKind.IMPRINT).label("t1 imprint")
            .content("{}").createDate(now).updateDate(now).build());
    em.persist(
        LegalText.builder().tenantId(2L).kind(LegalTextKind.DPP).label("t2 dpp")
            .content("{}").createDate(now).updateDate(now).build());
    em.flush();
    em.clear();

    assertThat(legalTextRepository.findByTenantIdAndKindOrderByLabelAsc(1L, LegalTextKind.DPP))
        .extracting(LegalText::getLabel)
        .containsExactly("t1 dpp");
  }

  @Test
  void departmentWithoutReferences_Should_loadWithNullDppAndImprint() {
    Agency agency = persistAgency("Zentrum ohne Texte");
    AgencyTopic department = persistDepartment(agency, 30L);
    em.clear();

    AgencyTopic reloaded = em.find(AgencyTopic.class, department.getId());
    assertThat(reloaded.getDpp()).isNull();
    assertThat(reloaded.getImprint()).isNull();
  }
}
