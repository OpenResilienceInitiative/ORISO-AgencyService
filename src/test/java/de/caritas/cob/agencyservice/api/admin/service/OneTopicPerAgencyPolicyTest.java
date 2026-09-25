package de.caritas.cob.agencyservice.api.admin.service;

import static de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason.SETTINGS_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.exception.httpresponses.ServiceUnavailableException;
import de.caritas.cob.agencyservice.api.service.ApplicationSettingsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class OneTopicPerAgencyPolicyTest {

  @Mock ApplicationSettingsService applicationSettingsService;
  @InjectMocks OneTopicPerAgencyPolicy policy;

  @Test
  void check_Should_notAskSettings_When_agencyEndsWithOneTopic() {
    policy.check(List.of(1L, 2L), List.of(3L));

    verifyNoInteractions(applicationSettingsService);
  }

  @Test
  void check_Should_failClosed_When_settingsCannotBeReadAndATopicIsAdded() {
    when(applicationSettingsService.fetchApplicationSettings())
        .thenThrow(new ResourceAccessException("ConsultingTypeService down"));

    assertThatThrownBy(() -> policy.check(List.of(1L), List.of(1L, 2L)))
        .isInstanceOfSatisfying(ServiceUnavailableException.class, e -> {
          assertThat(e.getHttpStatus())
              .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
          assertThat(e.getHttpStatusExceptionReason())
              .isEqualTo(SETTINGS_UNAVAILABLE);
        });
  }

  @Test
  void check_Should_failClosed_When_settingsServiceAnswersWithoutBody() {
    when(applicationSettingsService.fetchApplicationSettings()).thenReturn(null);

    assertThatThrownBy(() -> policy.check(List.of(1L), List.of(1L, 2L)))
        .isInstanceOf(ServiceUnavailableException.class);
  }

  @Test
  void check_Should_notAskSettings_When_writeAddsNoTopic() {
    assertThatCode(() -> policy.check(List.of(1L, 2L), List.of(2L, 1L)))
        .doesNotThrowAnyException();

    verifyNoInteractions(applicationSettingsService);
  }
}
