package de.caritas.cob.agencyservice.api.admin.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import de.caritas.cob.agencyservice.AgencyServiceApplication;
import de.caritas.cob.agencyservice.api.model.Settings;
import de.caritas.cob.agencyservice.api.model.Settings.CounsellorTopicPermissionEnum;
import de.caritas.cob.agencyservice.api.model.UpdateAgencyDTO;
import de.caritas.cob.agencyservice.api.service.TopicService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

/**
 * The agency default "Berater:innen dürfen sich selbst Themen hinzufügen" (ORISO-Admin#1026, slice
 * 6), a three-level setting: NONE, SELECT_EXISTING, CREATE. It prefills the topic permission of
 * every counsellor invited into the agency.
 *
 * <p>Agencies that existed before the setting keep today's behaviour (CREATE) without a data
 * migration: a stored settings document without the key reads as CREATE. New agencies start with
 * NONE. An update that does not mention the setting leaves it alone.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = AgencyServiceApplication.class)
@TestPropertySource(properties = "spring.profiles.active=testing")
@AutoConfigureTestDatabase(replace = Replace.ANY)
@DirtiesContext(classMode = ClassMode.BEFORE_CLASS)
@Transactional
@TestPropertySource(properties = "multitenancy.enabled=false")
@Sql(scripts = "/database/AgencyDatabase.sql")
public class AgencyCounsellorTopicPermissionIT extends AgencyAdminServiceITBase {

  /** A seeded agency whose stored settings predate the setting. */
  private static final long EXISTING_AGENCY = 0L;

  @MockitoBean private TopicService topicService;

  @Test
  public void anAgencyFromBeforeTheSetting_reportsCreate() {
    assertThat(permissionOf(EXISTING_AGENCY), is(CounsellorTopicPermissionEnum.CREATE));
  }

  @Test
  public void aNewAgency_startsWithNone() {
    var created = agencyAdminService.createAgency(createAgencyDTO());

    assertThat(
        permissionOf(created.getEmbedded().getId()), is(CounsellorTopicPermissionEnum.NONE));
  }

  @Test
  public void anUpdate_setsTheValue_andAnUpdateWithoutItKeepsIt() {
    UpdateAgencyDTO setting = createUpdateAgencyDtoFromExistingAgency();
    setting.setSettings(
        new Settings().counsellorTopicPermission(CounsellorTopicPermissionEnum.SELECT_EXISTING));
    agencyAdminService.updateAgency(EXISTING_AGENCY, setting);

    assertThat(permissionOf(EXISTING_AGENCY), is(CounsellorTopicPermissionEnum.SELECT_EXISTING));

    // An older Admin form sends its settings without the new key: nothing may flip back.
    UpdateAgencyDTO otherSettings = createUpdateAgencyDtoFromExistingAgency();
    otherSettings.setSettings(new Settings().featureStatisticsEnabled(true));
    agencyAdminService.updateAgency(EXISTING_AGENCY, otherSettings);

    assertThat(permissionOf(EXISTING_AGENCY), is(CounsellorTopicPermissionEnum.SELECT_EXISTING));
  }

  private CounsellorTopicPermissionEnum permissionOf(long agencyId) {
    return agencyAdminService
        .findAgency(agencyId)
        .getEmbedded()
        .getSettings()
        .getCounsellorTopicPermission();
  }
}
