package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.model.AgencyAdminControls;
import de.caritas.cob.agencyservice.api.model.Settings;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.BooleanPermissionPolicy;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.PermissionPolicyMode;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.TenantPermissionPolicies;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class AgencyAdminControlsServiceTest {

  @InjectMocks private AgencyAdminControlsService service;
  @Mock private TenantService tenantService;
  @Mock private AgencyPermissionPolicyMapper policyMapper;

  @Test
  void getControls_shouldReadOnlyTheAuthenticatedTenantsResolvedPolicy() {
    var policies = new TenantPermissionPolicies().tenantId(7L).policies(Map.of());
    var expected = new AgencyAdminControls();
    when(tenantService.getPermissionPolicies(7L)).thenReturn(policies);
    when(policyMapper.toLegacyControls(policies.getPolicies())).thenReturn(expected);

    assertThat(service.getControls(7L)).isSameAs(expected);
    verify(tenantService).getPermissionPolicies(7L);
  }

  @Test
  void getResolvedControls_shouldUseThePublicResolvedTenantContract() {
    var policy = new BooleanPermissionPolicy().value(false).mode(PermissionPolicyMode.ENFORCED);
    var restricted =
        new RestrictedTenantDTO(Map.of("featureVideoCallsEnabled", policy))
            .id(7L)
            .name("Tenant");
    var expected = new AgencyAdminControls();
    when(tenantService.getRestrictedTenantDataByTenantId(7L)).thenReturn(restricted);
    when(policyMapper.toLegacyControls(restricted.getPermissionPolicies())).thenReturn(expected);

    assertThat(service.getResolvedControls(7L)).isSameAs(expected);
  }

  @Test
  void updateControls_shouldRoundTripThroughTenantServiceWithoutLocalSingleton() {
    var controls = new AgencyAdminControls();
    var current = new TenantPermissionPolicies().tenantId(7L).policies(Map.of());
    var updated = new TenantPermissionPolicies().tenantId(7L).policies(Map.of());
    var expected = new AgencyAdminControls();
    when(tenantService.getPermissionPolicies(7L)).thenReturn(current);
    when(policyMapper.applyLegacyUpdate(current, controls)).thenReturn(updated);
    when(tenantService.updatePermissionPolicies(7L, updated)).thenReturn(updated);
    when(policyMapper.toLegacyControls(updated.getPolicies())).thenReturn(expected);

    assertThat(service.updateControls(7L, controls)).isSameAs(expected);
    verify(tenantService).updatePermissionPolicies(7L, updated);
  }

  @Test
  void tenantZero_shouldNeverResolveToAnotherTenantsPolicies() {
    assertThatThrownBy(() -> service.getControls(0L)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void enrichSettings_shouldKeepSettingsAndAttachTenantSpecificControls() {
    var settings = new Settings().featureStatisticsEnabled(true);
    var controls = new AgencyAdminControls();
    var policies = new TenantPermissionPolicies().tenantId(8L).policies(Map.of());
    when(tenantService.getPermissionPolicies(8L)).thenReturn(policies);
    when(policyMapper.toLegacyControls(policies.getPolicies())).thenReturn(controls);

    assertThat(service.enrichSettingsWithAgencyAdminControls(settings, 8L)).isSameAs(settings);
    assertThat(settings.getAgencyAdminControls()).isSameAs(controls);
  }
}
