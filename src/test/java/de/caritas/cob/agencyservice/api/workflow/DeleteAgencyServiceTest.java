package de.caritas.cob.agencyservice.api.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.Lists;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class DeleteAgencyServiceTest {

  @Mock AgencyRepository agencyRepository;

  @Mock AgencyPurgeTransaction agencyPurgeTransaction;

  @InjectMocks DeleteAgencyService deleteAgencyService;

  private final ch.qos.logback.classic.Logger logger =
      (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(DeleteAgencyService.class);
  private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();

  @BeforeEach
  void setup() {
    logAppender.start();
    logger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(logAppender);
  }

  @Test
  void deleteAgenciesMarkedForDeletion_Should_purgeEveryAgencyMarkedForDeletion() {
    // given
    Agency agency1 = new Agency();
    agency1.setId(1L);
    Agency agency2 = new Agency();
    agency2.setId(2L);
    Mockito.when(agencyRepository.findAllByDeleteDateNotNull())
        .thenReturn(Lists.newArrayList(agency1, agency2));

    // when
    deleteAgencyService.deleteAgenciesMarkedForDeletion();

    // then
    Mockito.verify(agencyPurgeTransaction).purge(agency1);
    Mockito.verify(agencyPurgeTransaction).purge(agency2);
  }

  @Test
  void deleteAgenciesMarkedForDeletion_Should_continueWithOtherAgencies_When_OnePurgeFails() {
    // given
    Agency agency1 = new Agency();
    agency1.setId(1L);
    Agency agency2 = new Agency();
    agency2.setId(2L);
    Mockito.when(agencyRepository.findAllByDeleteDateNotNull())
        .thenReturn(List.of(agency1, agency2));
    Mockito.doThrow(new RuntimeException("constraint violation"))
        .when(agencyPurgeTransaction)
        .purge(agency1);

    // when
    deleteAgencyService.deleteAgenciesMarkedForDeletion();

    // then - the failure is attributed and logged, and it does not stop the batch
    Mockito.verify(agencyPurgeTransaction).purge(agency2);
    assertThat(
        logAppender.list.stream()
            .anyMatch(
                event ->
                    event.getLevel() == Level.ERROR
                        && event.getFormattedMessage().contains("Error while deleting agency")),
        is(true));
  }

  @Test
  void deleteAgenciesMarkedForDeletion_Should_logRunWithMatchCount_When_nothingMatches() {
    // given - a silent job and an empty job used to look identical in the logs
    Mockito.when(agencyRepository.findAllByDeleteDateNotNull()).thenReturn(List.of());

    // when
    deleteAgencyService.deleteAgenciesMarkedForDeletion();

    // then
    assertThat(
        logAppender.list.stream()
            .anyMatch(
                event ->
                    event
                        .getFormattedMessage()
                        .equals(
                            "Agency deletion workflow started, 0 agencies marked for deletion.")),
        is(true));
  }
}
