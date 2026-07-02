package de.caritas.cob.agencyservice.api.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.service.securityheader.SecurityHeaderSupplier;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.ApiClient;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.ApplicationsettingsControllerApi;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.ApplicationSettingsDTO;
import de.caritas.cob.agencyservice.config.apiclient.ApplicationSettingsApiControllerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ApplicationSettingsServiceTest {

  @InjectMocks
  ApplicationSettingsService applicationSettingsService;

  @Mock
  ApplicationSettingsApiControllerFactory applicationSettingsApiControllerFactory;

  @Mock
  ApplicationsettingsControllerApi applicationsettingsControllerApi;

  @Mock
  SecurityHeaderSupplier securityHeaderSupplier;

  @Spy
  TenantHeaderSupplier tenantHeaderSupplier;

  @Spy
  ApiClient apiClient;

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void getApplicationSettings_Should_ReturnSettingsFromApi() {
    var expectedDto = new ApplicationSettingsDTO();
    var headers = new HttpHeaders();
    when(applicationSettingsApiControllerFactory.createControllerApi())
        .thenReturn(applicationsettingsControllerApi);
    when(applicationsettingsControllerApi.getApiClient()).thenReturn(apiClient);
    when(securityHeaderSupplier.getCsrfHttpHeaders()).thenReturn(headers);
    when(applicationsettingsControllerApi.getApplicationSettings()).thenReturn(expectedDto);

    assertEquals(expectedDto, applicationSettingsService.getApplicationSettings());
  }

  @Test
  void getApplicationSettings_Should_AddCsrfHeaders() {
    var headers = new HttpHeaders();
    headers.add("X-CSRF-TOKEN", "test-csrf-token");
    when(applicationSettingsApiControllerFactory.createControllerApi())
        .thenReturn(applicationsettingsControllerApi);
    when(applicationsettingsControllerApi.getApiClient()).thenReturn(apiClient);
    when(securityHeaderSupplier.getCsrfHttpHeaders()).thenReturn(headers);
    when(applicationsettingsControllerApi.getApplicationSettings())
        .thenReturn(new ApplicationSettingsDTO());

    applicationSettingsService.getApplicationSettings();

    HttpHeaders apiClientHeaders = (HttpHeaders) ReflectionTestUtils
        .getField(apiClient, "defaultHeaders");
    assertEquals("test-csrf-token", apiClientHeaders.get("X-CSRF-TOKEN").get(0));
  }

  @Test
  void getApplicationSettings_Should_AddTenantHeader_When_MultitenancyEnabled() {
    TenantContext.setCurrentTenant(1L);
    var headers = new HttpHeaders();
    when(applicationSettingsApiControllerFactory.createControllerApi())
        .thenReturn(applicationsettingsControllerApi);
    when(applicationsettingsControllerApi.getApiClient()).thenReturn(apiClient);
    when(securityHeaderSupplier.getCsrfHttpHeaders()).thenReturn(headers);
    ReflectionTestUtils.setField(tenantHeaderSupplier, "multitenancy", true);
    when(applicationsettingsControllerApi.getApplicationSettings())
        .thenReturn(new ApplicationSettingsDTO());

    applicationSettingsService.getApplicationSettings();

    HttpHeaders apiClientHeaders = (HttpHeaders) ReflectionTestUtils
        .getField(apiClient, "defaultHeaders");
    assertEquals("1", apiClientHeaders.get("tenantId").get(0));
  }

  @Test
  void getApplicationSettings_Should_NotAddTenantHeader_When_MultitenancyDisabled() {
    TenantContext.setCurrentTenant(1L);
    var headers = new HttpHeaders();
    when(applicationSettingsApiControllerFactory.createControllerApi())
        .thenReturn(applicationsettingsControllerApi);
    when(applicationsettingsControllerApi.getApiClient()).thenReturn(apiClient);
    when(securityHeaderSupplier.getCsrfHttpHeaders()).thenReturn(headers);
    ReflectionTestUtils.setField(tenantHeaderSupplier, "multitenancy", false);
    when(applicationsettingsControllerApi.getApplicationSettings())
        .thenReturn(new ApplicationSettingsDTO());

    applicationSettingsService.getApplicationSettings();

    HttpHeaders apiClientHeaders = (HttpHeaders) ReflectionTestUtils
        .getField(apiClient, "defaultHeaders");
    assertNull(apiClientHeaders.get("tenantId"));
  }

}
