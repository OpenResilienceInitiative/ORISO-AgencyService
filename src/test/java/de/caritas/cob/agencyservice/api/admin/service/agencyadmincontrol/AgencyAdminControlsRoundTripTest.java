package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.BooleanPermissionPolicy;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.PermissionPolicyMode;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.TenantPermissionPolicies;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Typed parent policy changes are reflected by compatibility reads without a second write model. */
class AgencyAdminControlsRoundTripTest {

  private AgencyAdminControlsService service;
  private TenantService tenantService;
  private AtomicReference<TenantPermissionPolicies> stored;

  @BeforeEach
  void setUp() {
    tenantService = mock(TenantService.class);
    TenantContext.setCurrentTenant(7L);
    stored = new AtomicReference<>(new TenantPermissionPolicies().tenantId(7L).policies(Map.of()));
    when(tenantService.getPermissionPolicies(7L)).thenAnswer(i -> stored.get());
    service = new AgencyAdminControlsService(tenantService, new AgencyPermissionPolicyMapper());
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void typedParentChanges_shouldKeepIndependentGroupChatFormatRequirements() {
    stored.set(new TenantPermissionPolicies().tenantId(7L).policies(Map.of(
        "featureGroupChatV2Enabled", policy(true, PermissionPolicyMode.SUGGESTED),
        "featureInternalGroupChatEnabled", policy(true, PermissionPolicyMode.ENFORCED),
        "featureSelfHelpGroupsEnabled", policy(false, PermissionPolicyMode.ENFORCED))));

    var reloaded = service.getControls();

    assertThat(reloaded.getAllowedPermissionToggles().getGroupChat()).isTrue();
    assertThat(reloaded.getAllowedPermissionToggles().getInternalGroupChat()).isTrue();
    assertThat(reloaded.getAllowedPermissionToggles().getSelfHelpGroups()).isFalse();
    assertThat(reloaded.getEnforcedPermissionToggles().getInternalGroupChat()).isTrue();

    stored.set(new TenantPermissionPolicies().tenantId(7L).policies(Map.of(
        "featureInternalGroupChatEnabled", policy(false, PermissionPolicyMode.SUGGESTED))));
    assertThat(service.getControls().getEnforcedPermissionToggles().getInternalGroupChat()).isFalse();
    assertThat(service.getControls().getAllowedPermissionToggles().getInternalGroupChat()).isTrue();
  }

  @Test
  void typedParentChanges_shouldNotLeakIntoAnotherTenantsCompatibilityRead() {
    when(tenantService.getPermissionPolicies(8L))
        .thenReturn(new TenantPermissionPolicies().tenantId(8L).policies(Map.of()));
    stored.set(new TenantPermissionPolicies().tenantId(7L).policies(Map.of(
        "featureGroupChatV2Enabled", policy(false, PermissionPolicyMode.ENFORCED))));

    assertThat(service.getControls(7L).getAllowedPermissionToggles().getGroupChat()).isFalse();
    assertThat(service.getControls(8L).getAllowedPermissionToggles().getGroupChat()).isTrue();
  }

  private static BooleanPermissionPolicy policy(boolean value, PermissionPolicyMode mode) {
    return new BooleanPermissionPolicy().value(value).mode(mode);
  }
}
