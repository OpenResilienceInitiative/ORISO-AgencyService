package de.caritas.cob.agencyservice.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.ApplicationSettingsDTO;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.ApplicationSettingsDTOMainTenantSubdomainForSingleDomainMultitenancy;
import de.caritas.cob.agencyservice.config.apiclient.TenantServiceApiControllerFactory;
import de.caritas.cob.agencyservice.tenantservice.generated.web.TenantControllerApi;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

  @InjectMocks
  TenantService tenantService;

  @Mock
  TenantServiceApiControllerFactory tenantServiceApiControllerFactory;

  @Mock
  ApplicationSettingsService applicationSettingsService;

  @Mock
  TenantControllerApi tenantControllerApi;

  @AfterEach
  void tearDown() {
    ReflectionTestUtils.setField(tenantService, "multitenancyWithSingleDomain", false);
  }

  @Test
  void getRestrictedTenantDataBySubdomain_Should_ReturnTenantData_When_SubdomainProvided() {
    when(tenantServiceApiControllerFactory.createControllerApi()).thenReturn(tenantControllerApi);
    when(tenantControllerApi.getRestrictedTenantDataBySubdomain("app", null))
        .thenReturn(new RestrictedTenantDTO().id(1L));

    RestrictedTenantDTO result = tenantService.getRestrictedTenantDataBySubdomain("app");

    assertEquals(1L, result.getId());
    verify(tenantControllerApi).getRestrictedTenantDataBySubdomain("app", null);
    verify(tenantServiceApiControllerFactory).createControllerApi();
  }

  @Test
  void getRestrictedTenantDataByTenantId_Should_ReturnTenantData_When_TenantIdProvided() {
    when(tenantServiceApiControllerFactory.createControllerApi()).thenReturn(tenantControllerApi);
    when(tenantControllerApi.getRestrictedTenantDataByTenantId(1L))
        .thenReturn(new RestrictedTenantDTO().id(1L));

    RestrictedTenantDTO result = tenantService.getRestrictedTenantDataByTenantId(1L);

    assertEquals(1L, result.getId());
    verify(tenantControllerApi).getRestrictedTenantDataByTenantId(1L);
  }

  @Test
  void getRestrictedTenantDataForSingleTenant_Should_ReturnTenantData() {
    when(tenantServiceApiControllerFactory.createControllerApi()).thenReturn(tenantControllerApi);
    when(tenantControllerApi.getRestrictedSingleTenancyTenantData())
        .thenReturn(new RestrictedTenantDTO().id(1L));

    RestrictedTenantDTO result = tenantService.getRestrictedTenantDataForSingleTenant();

    assertEquals(1L, result.getId());
    verify(tenantControllerApi).getRestrictedSingleTenancyTenantData();
  }

  @Test
  void getMainTenant_Should_ThrowIllegalStateException_When_SingleDomainDisabled() {
    ReflectionTestUtils.setField(tenantService, "multitenancyWithSingleDomain", false);

    assertThrows(IllegalStateException.class, () -> tenantService.getMainTenant());

    verify(tenantServiceApiControllerFactory, never()).createControllerApi();
    verify(tenantControllerApi, never()).getRestrictedTenantDataBySubdomain(any(), any());
    verify(applicationSettingsService, never()).getApplicationSettings();
  }

  @Test
  void getMainTenant_Should_ReturnTenantData_When_SingleDomainEnabled() {
    ReflectionTestUtils.setField(tenantService, "multitenancyWithSingleDomain", true);
    when(tenantServiceApiControllerFactory.createControllerApi()).thenReturn(tenantControllerApi);
    when(applicationSettingsService.getApplicationSettings())
        .thenReturn(new ApplicationSettingsDTO().mainTenantSubdomainForSingleDomainMultitenancy(
            new ApplicationSettingsDTOMainTenantSubdomainForSingleDomainMultitenancy().value("app")));
    when(tenantControllerApi.getRestrictedTenantDataBySubdomain("app", null))
        .thenReturn(new RestrictedTenantDTO().id(2L));

    RestrictedTenantDTO result = tenantService.getMainTenant();

    assertEquals(2L, result.getId());
    verify(applicationSettingsService).getApplicationSettings();
    verify(tenantControllerApi).getRestrictedTenantDataBySubdomain("app", null);
  }
}
