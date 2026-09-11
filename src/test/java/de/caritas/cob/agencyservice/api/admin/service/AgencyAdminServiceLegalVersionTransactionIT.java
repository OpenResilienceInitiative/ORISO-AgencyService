package de.caritas.cob.agencyservice.api.admin.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.AgencyServiceApplication;
import de.caritas.cob.agencyservice.api.admin.service.legal.LegalTextVersionService;
import de.caritas.cob.agencyservice.api.model.AgencyLegalContentDTO;
import de.caritas.cob.agencyservice.api.service.TopicService;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * The agency row and its ADR-021 publication history commit together or not at all.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional} at class level, unlike the sibling
 * {@code AgencyAdminServiceIT}: a test-managed transaction would wrap the service call and roll
 * everything back at the end regardless, so the assertion would hold whether or not
 * {@code updateAgency} declares a transaction of its own — it would pass against the very bug it
 * is meant to pin. Letting {@code updateAgency} own the outermost transaction is the whole point.
 * The fixture script recreates its tables, so leaving no test-managed rollback is safe here.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = AgencyServiceApplication.class)
@TestPropertySource(properties = "spring.profiles.active=testing")
@AutoConfigureTestDatabase(replace = Replace.ANY)
@DirtiesContext(classMode = ClassMode.BEFORE_CLASS)
@TestPropertySource(properties = "multitenancy.enabled=false")
@Sql(scripts = "/database/AgencyDatabase.sql")
public class AgencyAdminServiceLegalVersionTransactionIT extends AgencyAdminServiceITBase {

  /** Keeps createAgency/updateAgency off the real ConsultingTypeService on localhost:8083 (#204). */
  @MockitoBean private TopicService topicService;

  @MockitoBean private LegalTextVersionService legalTextVersionService;

  /**
   * Before the fix, the agency save committed in its own transaction and
   * {@code recordPublication} opened a second one afterwards. A failure writing the snapshot then
   * rolled back the snapshot alone: the caller got a 500 for an update that had actually landed,
   * and the agency was left carrying new legal wording that no history row accounted for.
   */
  @Test
  public void updateAgency_Should_RollBackTheAgencyChange_WhenTheHistoryWriteFails() {
    var storedAgency = agencyRepository.findById(0L).orElseThrow();
    final var originalName = storedAgency.getName();
    final var originalDpp = storedAgency.getContentDpp();

    var updateAgencyDTO = createUpdateAgencyDtoFromExistingAgency();
    updateAgencyDTO.setContent(
        new AgencyLegalContentDTO().privacy(Map.of("de", "<p>Neue Fassung</p>")));
    when(legalTextVersionService.recordPublication(any(), any(), any(), any(), any(), any()))
        .thenThrow(new DataIntegrityViolationException("history write failed"));

    assertThrows(
        DataIntegrityViolationException.class,
        () -> agencyAdminService.updateAgency(0L, updateAgencyDTO));

    var afterFailedUpdate = agencyRepository.findById(0L).orElseThrow();
    assertEquals(originalName, afterFailedUpdate.getName());
    assertEquals(originalDpp, afterFailedUpdate.getContentDpp());
  }
}
