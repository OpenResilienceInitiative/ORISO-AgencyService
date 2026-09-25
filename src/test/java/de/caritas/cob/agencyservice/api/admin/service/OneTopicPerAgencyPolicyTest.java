package de.caritas.cob.agencyservice.api.admin.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.service.ApplicationSettingsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
  void check_Should_failOpen_When_settingsCannotBeRead() {
    when(applicationSettingsService.getApplicationSettings())
        .thenThrow(new ResourceAccessException("ConsultingTypeService down"));

    assertThatCode(() -> policy.check(List.of(1L), List.of(1L, 2L))).doesNotThrowAnyException();
  }
}
