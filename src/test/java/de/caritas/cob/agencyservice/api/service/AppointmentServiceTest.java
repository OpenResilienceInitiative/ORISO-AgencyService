package de.caritas.cob.agencyservice.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.service.securityheader.SecurityHeaderSupplier;
import de.caritas.cob.agencyservice.appointmentservice.generated.web.model.AgencyMasterDataSyncRequestDTO;
import de.caritas.cob.agencyservice.config.apiclient.AppointmentServiceAgencyApiControllerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // To allow "UnnecessaryStubbing" to keep tests clean
class AppointmentServiceTest {

  private static final String FIELD_NAME_APPOINTMENTS_ENABLED = "appointmentFeatureEnabled";

  @Spy
  @InjectMocks
  AppointmentService appointmentService;

  @Mock
  de.caritas.cob.agencyservice.appointmentservice.generated.web.AgencyApi appointmentAgencyApi;
  @Mock
  SecurityHeaderSupplier securityHeaderSupplier;
  @Mock
  TenantHeaderSupplier tenantHeaderSupplier;
  @Mock
  Agency agency;
  @Mock
  AppointmentServiceAgencyApiControllerFactory appointmentServiceAgencyApiControllerFactory;

  @Spy
  de.caritas.cob.agencyservice.appointmentservice.generated.ApiClient apiClient;

  @BeforeEach
  public void beforeEach() {
    when(securityHeaderSupplier.getKeycloakAndCsrfHttpHeaders()).thenReturn(new HttpHeaders());
    when(appointmentServiceAgencyApiControllerFactory.createControllerApi()).thenReturn(appointmentAgencyApi);
    when(appointmentAgencyApi.getApiClient()).thenReturn(apiClient);
  }

  @Test
  void syncAgencyDataToAppointmentService_Should_NotCallAppointmentService_WhenAppointmentsIsDisabled() {
    setField(appointmentService, FIELD_NAME_APPOINTMENTS_ENABLED, false);
    appointmentService.syncAgencyDataToAppointmentService(agency);
    verify(appointmentAgencyApi, never()).agencyMasterDataSync(any(
        AgencyMasterDataSyncRequestDTO.class));
  }

  @Test
  void deleteAgency_Should_NotCallAppointmentService_WhenAppointmentsIsDisabled() {
    setField(appointmentService, FIELD_NAME_APPOINTMENTS_ENABLED, false);
    appointmentService.deleteAgency(agency);
    verify(appointmentAgencyApi, never()).deleteAgency(anyLong());
  }

  @Test
  void syncAgencyDataToAppointmentService_Should_CallAppointmentService_WhenAppointmentsIsEnabled() {
    setField(appointmentService, FIELD_NAME_APPOINTMENTS_ENABLED, true);
    appointmentService.syncAgencyDataToAppointmentService(agency);
    verify(appointmentAgencyApi, times(1)).agencyMasterDataSync(any(
        AgencyMasterDataSyncRequestDTO.class));
  }

  @Test
  void deleteAgency_Should_CallAppointmentService_WhenAppointmentsIsEnabled() {
    setField(appointmentService, FIELD_NAME_APPOINTMENTS_ENABLED, true);
    appointmentService.deleteAgency(agency);
    verify(appointmentAgencyApi, times(1)).deleteAgency(anyLong());
  }

  @Test
  void syncAgencyDataToAppointmentService_Should_SendCorrectAgencyIdAndName_When_AppointmentEnabled() {
    setField(appointmentService, FIELD_NAME_APPOINTMENTS_ENABLED, true);
    when(agency.getId()).thenReturn(42L);
    when(agency.getName()).thenReturn("Test Agency");

    appointmentService.syncAgencyDataToAppointmentService(agency);

    ArgumentCaptor<AgencyMasterDataSyncRequestDTO> captor =
        ArgumentCaptor.forClass(AgencyMasterDataSyncRequestDTO.class);
    verify(appointmentAgencyApi).agencyMasterDataSync(captor.capture());
    assertThat(captor.getValue().getId()).isEqualTo(42L);
    assertThat(captor.getValue().getName()).isEqualTo("Test Agency");
  }

  @Test
  void syncAgencyDataToAppointmentService_Should_AddSecurityHeaders_When_AppointmentEnabled() {
    setField(appointmentService, FIELD_NAME_APPOINTMENTS_ENABLED, true);
    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", "Bearer token");
    when(securityHeaderSupplier.getKeycloakAndCsrfHttpHeaders()).thenReturn(headers);

    appointmentService.syncAgencyDataToAppointmentService(agency);

    verify(securityHeaderSupplier, times(1)).getKeycloakAndCsrfHttpHeaders();
    verify(tenantHeaderSupplier, times(1)).addTenantHeader(any(HttpHeaders.class));
    HttpHeaders apiClientHeaders =
        (HttpHeaders) ReflectionTestUtils.getField(apiClient, "defaultHeaders");
    assertThat(apiClientHeaders.get("Authorization").get(0)).isEqualTo("Bearer token");
  }
}
