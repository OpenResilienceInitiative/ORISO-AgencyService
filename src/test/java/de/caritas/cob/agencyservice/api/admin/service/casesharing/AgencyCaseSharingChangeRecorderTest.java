package de.caritas.cob.agencyservice.api.admin.service.casesharing;

import static de.caritas.cob.agencyservice.api.model.AgencyCaseSharingMode.CASE_HANDOVER;
import static de.caritas.cob.agencyservice.api.model.AgencyCaseSharingMode.NONE;
import static de.caritas.cob.agencyservice.api.model.AgencyCaseSharingMode.TEAM_COUNSELLING;
import static de.caritas.cob.agencyservice.api.repository.casesharingchange.AgencyCaseSharingChangeSource.AGENCY_SETTINGS;
import static de.caritas.cob.agencyservice.api.repository.casesharingchange.AgencyCaseSharingChangeSource.AGENCY_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.repository.casesharingchange.AgencyCaseSharingChange;
import de.caritas.cob.agencyservice.api.repository.casesharingchange.AgencyCaseSharingChangeRepository;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.CaseSharingMode;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.CaseSharingModePermissionPolicy;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.CaseSharingPolicies;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.PermissionPolicyMode;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

/** ORISO-Frontend#2494: when a change of the effective case sharing model is logged. */
@ExtendWith(MockitoExtension.class)
class AgencyCaseSharingChangeRecorderTest {

  private static final long AGENCY_ID = 7L;
  private static final long TENANT_ID = 3L;

  @Mock AgencyCaseSharingChangeRepository repository;
  @Mock TenantService tenantService;
  @Mock AuthenticatedUser authenticatedUser;
  @InjectMocks AgencyCaseSharingChangeRecorder recorder;

  @BeforeEach
  void actor() {
    lenient().when(authenticatedUser.getUserId()).thenReturn("user-1");
    lenient().when(authenticatedUser.getUsername()).thenReturn("tenant-admin");
  }

  @Test
  void noRowAndNoTenantCall_When_NeitherOwnModeNorTeamAgencyChanged() {
    recorder.recordIfEffectiveModelChanged(
        AGENCY_ID, TENANT_ID, NONE, true, NONE, true, AGENCY_SETTINGS);

    verifyNoInteractions(tenantService, repository);
  }

  @Test
  void oneRowWithPreviousAndNewModel_When_OwnModeChangesTheEffectiveModel() {
    when(tenantService.getRestrictedTenantDataForPermissionPolicies(TENANT_ID))
        .thenReturn(tenant(CaseSharingMode.CASE_HANDOVER, PermissionPolicyMode.SUGGESTED));
    final var before = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);

    recorder.recordIfEffectiveModelChanged(
        AGENCY_ID, TENANT_ID, null, false, TEAM_COUNSELLING, false, AGENCY_SETTINGS);

    var row = savedRow();
    assertThat(row.getAgencyId()).isEqualTo(AGENCY_ID);
    assertThat(row.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(row.getPreviousModel()).isEqualTo("CASE_HANDOVER");
    assertThat(row.getNewModel()).isEqualTo("TEAM_COUNSELLING");
    assertThat(row.getSource()).isEqualTo(AGENCY_SETTINGS);
    assertThat(row.getChangedByUserId()).isEqualTo("user-1");
    assertThat(row.getChangedByName()).isEqualTo("tenant-admin");
    assertThat(row.getChangedAt()).isAfter(before);
  }

  @Test
  void noRow_When_OwnModeChangesButEffectiveModelStaysTheSame() {
    when(tenantService.getRestrictedTenantDataForPermissionPolicies(TENANT_ID))
        .thenReturn(tenant(CaseSharingMode.CASE_HANDOVER, PermissionPolicyMode.SUGGESTED));

    // Unset own mode under a Traeger with case handover already resolves to case handover.
    recorder.recordIfEffectiveModelChanged(
        AGENCY_ID, TENANT_ID, null, false, CASE_HANDOVER, false, AGENCY_SETTINGS);

    verify(repository, never()).save(any());
  }

  @Test
  void oneRow_When_TypeChangeFlipsTheEffectiveModel() {
    when(tenantService.getRestrictedTenantDataForPermissionPolicies(TENANT_ID))
        .thenReturn(tenant(CaseSharingMode.NONE, PermissionPolicyMode.SUGGESTED));

    recorder.recordIfEffectiveModelChanged(
        AGENCY_ID, TENANT_ID, null, false, null, true, AGENCY_TYPE);

    var row = savedRow();
    assertThat(row.getPreviousModel()).isEqualTo("NONE");
    assertThat(row.getNewModel()).isEqualTo("TEAM_COUNSELLING");
    assertThat(row.getSource()).isEqualTo(AGENCY_TYPE);
  }

  @Test
  void noRow_When_TypeChangeIsHiddenByTheCentresOwnMode() {
    when(tenantService.getRestrictedTenantDataForPermissionPolicies(TENANT_ID))
        .thenReturn(tenant(CaseSharingMode.NONE, PermissionPolicyMode.SUGGESTED));

    recorder.recordIfEffectiveModelChanged(
        AGENCY_ID, TENANT_ID, NONE, false, NONE, true, AGENCY_TYPE);

    verify(repository, never()).save(any());
  }

  @Test
  void noRow_When_TraegerEnforcesTheModelAndOnlyTheOwnModeChanges() {
    when(tenantService.getRestrictedTenantDataForPermissionPolicies(TENANT_ID))
        .thenReturn(tenant(CaseSharingMode.CASE_HANDOVER, PermissionPolicyMode.ENFORCED));

    recorder.recordIfEffectiveModelChanged(
        AGENCY_ID, TENANT_ID, CASE_HANDOVER, false, TEAM_COUNSELLING, false, AGENCY_SETTINGS);

    verify(repository, never()).save(any());
  }

  @Test
  void rowDerivedWithoutTenant_When_TenantDataFails() {
    when(tenantService.getRestrictedTenantDataForPermissionPolicies(TENANT_ID))
        .thenThrow(new RestClientException("TenantService down"));

    recorder.recordIfEffectiveModelChanged(
        AGENCY_ID, TENANT_ID, CASE_HANDOVER, false, NONE, false, AGENCY_SETTINGS);

    var row = savedRow();
    assertThat(row.getPreviousModel()).isEqualTo("CASE_HANDOVER");
    assertThat(row.getNewModel()).isEqualTo("NONE");
  }

  @Test
  void rowDerivedWithoutTenant_When_TenantDataIsMissing() {
    when(tenantService.getRestrictedTenantDataForPermissionPolicies(TENANT_ID)).thenReturn(null);

    recorder.recordIfEffectiveModelChanged(
        AGENCY_ID, TENANT_ID, null, true, NONE, true, AGENCY_SETTINGS);

    var row = savedRow();
    assertThat(row.getPreviousModel()).isEqualTo("CASE_HANDOVER");
    assertThat(row.getNewModel()).isEqualTo("NONE");
  }

  @Test
  void noRow_When_TenantDataFailsAndOnlyTeamAgencyChanged() {
    // Without tenant data the resolver assumes a Traeger with case handover, under which
    // teamAgency does not count; the documented fallback therefore records no type change.
    when(tenantService.getRestrictedTenantDataForPermissionPolicies(TENANT_ID))
        .thenThrow(new RestClientException("TenantService down"));

    recorder.recordIfEffectiveModelChanged(
        AGENCY_ID, TENANT_ID, null, false, null, true, AGENCY_TYPE);

    verify(repository, never()).save(any());
  }

  @Test
  void rowWithoutActor_When_NoAuthenticatedRequest() {
    when(tenantService.getRestrictedTenantDataForPermissionPolicies(TENANT_ID)).thenReturn(null);
    when(authenticatedUser.getUserId()).thenThrow(new IllegalStateException("no request scope"));
    when(authenticatedUser.getUsername()).thenThrow(new IllegalStateException("no request scope"));

    recorder.recordIfEffectiveModelChanged(
        AGENCY_ID, TENANT_ID, CASE_HANDOVER, false, NONE, false, AGENCY_SETTINGS);

    var row = savedRow();
    assertThat(row.getChangedByUserId()).isNull();
    assertThat(row.getChangedByName()).isNull();
  }

  private AgencyCaseSharingChange savedRow() {
    var captor = ArgumentCaptor.forClass(AgencyCaseSharingChange.class);
    verify(repository).save(captor.capture());
    return captor.getValue();
  }

  private static RestrictedTenantDTO tenant(CaseSharingMode value, PermissionPolicyMode mode) {
    return new RestrictedTenantDTO(
            Map.of(),
            null,
            new CaseSharingPolicies()
                .mode(new CaseSharingModePermissionPolicy().value(value).mode(mode)))
        .id(TENANT_ID);
  }
}
