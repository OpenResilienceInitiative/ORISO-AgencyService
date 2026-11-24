package de.caritas.cob.agencyservice.api.converter;

import static de.caritas.cob.agencyservice.api.model.AgencyCaseSharingMode.CASE_HANDOVER;
import static de.caritas.cob.agencyservice.api.model.AgencyCaseSharingMode.NONE;
import static de.caritas.cob.agencyservice.api.model.AgencyCaseSharingMode.TEAM_COUNSELLING;
import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.api.model.AgencyCaseSharingMode;
import de.caritas.cob.agencyservice.api.model.AgencyCaseSharingPolicy;
import de.caritas.cob.agencyservice.api.model.Settings;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.BooleanPermissionPolicy;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.CaseSharingMode;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.CaseSharingModePermissionPolicy;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.CaseSharingPolicies;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.PermissionPolicyMode;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** ORISO-TenantService#374: one case sharing mode per counselling centre. */
class AgencyCaseSharingResolverTest {

  @Test
  void requirementOfTheTraeger_winsOverTheCentresOwnChoice() {
    var tenant = tenant(CaseSharingMode.NONE, PermissionPolicyMode.ENFORCED);

    var resolved = resolve(own(TEAM_COUNSELLING), true, tenant);

    assertThat(resolved.getValue()).isEqualTo(NONE);
    assertThat(resolved.getMode()).isEqualTo(AgencyCaseSharingPolicy.ModeEnum.ENFORCED);
    assertThat(resolved.getInherited()).isTrue();
  }

  @Test
  void ownChoice_appliesWhileTheTraegerOnlySuggests() {
    var tenant = tenant(CaseSharingMode.CASE_HANDOVER, PermissionPolicyMode.SUGGESTED);

    var resolved = resolve(own(TEAM_COUNSELLING), false, tenant);

    assertThat(resolved.getValue()).isEqualTo(TEAM_COUNSELLING);
    assertThat(resolved.getInherited()).isFalse();
    assertThat(AgencyCaseSharingResolver.isTeamCounselling(resolved)).isTrue();
  }

  @Test
  void teamCentreUnderCaseHandover_migratesToCaseHandover() {
    var tenant = tenant(CaseSharingMode.CASE_HANDOVER, PermissionPolicyMode.SUGGESTED);

    var resolved = resolve(new Settings(), true, tenant);

    assertThat(resolved.getValue()).isEqualTo(CASE_HANDOVER);
    assertThat(AgencyCaseSharingResolver.isTeamCounselling(resolved)).isFalse();
  }

  @Test
  void teamCentreWithoutCaseHandover_staysTeamCounselling() {
    var tenant = tenant(CaseSharingMode.NONE, PermissionPolicyMode.SUGGESTED);

    assertThat(resolve(null, true, tenant).getValue()).isEqualTo(TEAM_COUNSELLING);
    assertThat(resolve(null, false, tenant).getValue()).isEqualTo(NONE);
  }

  @Test
  void olderTenantService_readsCaseHandoverEnabled() {
    var tenant =
        new RestrictedTenantDTO(
            Map.of(
                "caseHandoverEnabled",
                new BooleanPermissionPolicy().value(false).mode(PermissionPolicyMode.ENFORCED)),
            null,
            null);

    var resolved = resolve(own(CASE_HANDOVER), false, tenant);

    assertThat(resolved.getValue()).isEqualTo(NONE);
    assertThat(resolved.getMode()).isEqualTo(AgencyCaseSharingPolicy.ModeEnum.ENFORCED);
  }

  @Test
  void noTenantData_suggestsCaseHandover() {
    assertThat(resolve(null, false, null).getValue()).isEqualTo(CASE_HANDOVER);
  }

  @Test
  void conflictingRequirement_onlyForADifferentEnforcedValue() {
    var enforced = tenant(CaseSharingMode.TEAM_COUNSELLING, PermissionPolicyMode.ENFORCED);
    var suggested = tenant(CaseSharingMode.TEAM_COUNSELLING, PermissionPolicyMode.SUGGESTED);

    assertThat(AgencyCaseSharingResolver.conflictingRequirement(enforced, NONE))
        .isEqualTo(TEAM_COUNSELLING);
    assertThat(AgencyCaseSharingResolver.conflictingRequirement(enforced, TEAM_COUNSELLING))
        .isNull();
    assertThat(AgencyCaseSharingResolver.conflictingRequirement(suggested, NONE)).isNull();
  }

  private static AgencyCaseSharingPolicy resolve(
      Settings own, boolean teamAgency, RestrictedTenantDTO tenant) {
    return AgencyCaseSharingResolver.resolve(own, teamAgency, tenant);
  }

  private static Settings own(AgencyCaseSharingMode mode) {
    return new Settings().caseSharingMode(mode);
  }

  private static RestrictedTenantDTO tenant(CaseSharingMode value, PermissionPolicyMode mode) {
    return new RestrictedTenantDTO(
            Map.of(
                "caseHandoverEnabled",
                new BooleanPermissionPolicy()
                    .value(value == CaseSharingMode.CASE_HANDOVER)
                    .mode(mode)),
            null,
            new CaseSharingPolicies().mode(new CaseSharingModePermissionPolicy().value(value).mode(mode)))
        .id(8L);
  }
}
