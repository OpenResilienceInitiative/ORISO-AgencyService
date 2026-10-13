package de.caritas.cob.agencyservice.api.admin.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.exception.httpresponses.ConflictException;
import de.caritas.cob.agencyservice.api.service.ApplicationSettingsService;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.ApiClient;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.ApplicationsettingsControllerApi;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.ApplicationSettingsDTO;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.FeatureToggleDTO;
import de.caritas.cob.agencyservice.config.apiclient.ApplicationSettingsApiControllerFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The switch must not lag behind the shared settings cache (600 s): a changed
 * {@code oneTopicPerAgencyEnabled} applies to the next topic-adding write.
 */
@SpringBootTest
@ActiveProfiles("testing")
class OneTopicPerAgencyPolicySettingsFreshnessIT {

  @Autowired private OneTopicPerAgencyPolicy policy;
  @Autowired private ApplicationSettingsService applicationSettingsService;
  @MockitoBean private ApplicationSettingsApiControllerFactory controllerFactory;
  @MockitoBean private ApplicationsettingsControllerApi controllerApi;

  @BeforeEach
  void setUp() {
    when(controllerFactory.createControllerApi()).thenReturn(controllerApi);
    when(controllerApi.getApiClient()).thenReturn(new ApiClient());
  }

  @Test
  void check_Should_followTheSwitch_When_itChangesWhileTheSettingsCacheIsWarm() {
    switchOneTopicPerAgency(true);
    applicationSettingsService.getApplicationSettings(); // warms the shared cache with "on"
    assertThatThrownBy(() -> policy.check(List.of(1L), List.of(1L, 2L)))
        .isInstanceOf(ConflictException.class);

    switchOneTopicPerAgency(false);
    assertThatCode(() -> policy.check(List.of(1L), List.of(1L, 2L)))
        .doesNotThrowAnyException();

    switchOneTopicPerAgency(true);
    assertThatThrownBy(() -> policy.check(List.of(1L), List.of(1L, 2L)))
        .isInstanceOf(ConflictException.class);
  }

  private void switchOneTopicPerAgency(boolean enabled) {
    when(controllerApi.getApplicationSettings())
        .thenReturn(new ApplicationSettingsDTO()
            .oneTopicPerAgencyEnabled(new FeatureToggleDTO().value(enabled).readOnly(false)));
  }
}
