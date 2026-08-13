package de.caritas.cob.agencyservice.api.admin.service.department;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.exception.httpresponses.AgencyAccessDeniedException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ORISO-Admin#197: a center may run several Fachbereiche at different floors/areas with different
 * hours — each department may override the Beratungsstelle's contact details. The service persists
 * only overrides (blank/absent clears back to "inherit"), guarded exactly like the department
 * legal-text writes (IDOR + cross-tenant, mirrors {@code DepartmentDataProtectionService}).
 */
@ExtendWith(MockitoExtension.class)
class DepartmentDetailsServiceTest {

  @Mock private AgencyTopicRepository agencyTopicRepository;
  @Mock private AuthenticatedUser authenticatedUser;
  @Mock private UserAdminService userAdminService;

  private DepartmentDetailsService service;

  @BeforeEach
  void setUp() {
    TenantContext.clear(); // no ThreadLocal tenant leaks into the tenant guard
    service =
        new DepartmentDetailsService(agencyTopicRepository, authenticatedUser, userAdminService);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private AgencyTopic existingDepartment() {
    var department = AgencyTopic.builder().topicId(42L).build();
    when(agencyTopicRepository.findByAgency_IdAndTopicId(7L, 42L))
        .thenReturn(Optional.of(department));
    return department;
  }

  private AgencyTopic existingDepartmentInTenant(Long agencyTenantId) {
    var department =
        AgencyTopic.builder()
            .topicId(42L)
            .agency(
                Agency.builder()
                    .id(7L)
                    .name("Test-Zentrum")
                    .consultingTypeId(1)
                    .tenantId(agencyTenantId)
                    .build())
            .build();
    when(agencyTopicRepository.findByAgency_IdAndTopicId(7L, 42L))
        .thenReturn(Optional.of(department));
    return department;
  }

  @Test
  void update_Should_storeOverrides_andReturnThem_ForFullAgencyAdmin() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    existingDepartment();

    var view =
        service.updateDepartmentDetails(7L, 42L, "Di+Do 14-18 Uhr", "-23", "3. OG, Raum 312");

    // a full admin is never scoped-checked against agency ids
    verifyNoInteractions(userAdminService);
    assertThat(view.openingHours()).isEqualTo("Di+Do 14-18 Uhr");
    assertThat(view.phoneExtension()).isEqualTo("-23");
    assertThat(view.floorLocation()).isEqualTo("3. OG, Raum 312");

    var saved = ArgumentCaptor.forClass(AgencyTopic.class);
    verify(agencyTopicRepository).save(saved.capture());
    assertThat(saved.getValue().getOpeningHours()).isEqualTo("Di+Do 14-18 Uhr");
    assertThat(saved.getValue().getPhoneExtension()).isEqualTo("-23");
    assertThat(saved.getValue().getFloorLocation()).isEqualTo("3. OG, Raum 312");
    assertThat(saved.getValue().getUpdateDate()).isNotNull();
  }

  @Test
  void update_Should_normalizeBlankAndWhitespaceToNull_soTheDepartmentInheritsAgain() {
    // persist only overrides: a cleared input must clear the column, not store "" — otherwise the
    // public resolution (Fachbereich ?? Beratungsstelle) would resolve to an empty override
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    var department = existingDepartment();
    department.setOpeningHours("Mo 8-12");
    department.setPhoneExtension("-9");
    department.setFloorLocation("EG");

    var view = service.updateDepartmentDetails(7L, 42L, "", "   ", null);

    assertThat(view.openingHours()).isNull();
    assertThat(view.phoneExtension()).isNull();
    assertThat(view.floorLocation()).isNull();

    var saved = ArgumentCaptor.forClass(AgencyTopic.class);
    verify(agencyTopicRepository).save(saved.capture());
    assertThat(saved.getValue().getOpeningHours()).isNull();
    assertThat(saved.getValue().getPhoneExtension()).isNull();
    assertThat(saved.getValue().getFloorLocation()).isNull();
  }

  @Test
  void update_Should_trimSurroundingWhitespace() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    existingDepartment();

    var view = service.updateDepartmentDetails(7L, 42L, "  Mo 9-12  ", null, null);

    assertThat(view.openingHours()).isEqualTo("Mo 9-12");
  }

  @Test
  void update_Should_allow_When_restrictedAdminOwnsTheAgency() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);
    when(authenticatedUser.requireUserId()).thenReturn("admin-1");
    when(userAdminService.getAdminUserAgencyIds("admin-1")).thenReturn(List.of(7L, 9L));
    existingDepartment();

    var view = service.updateDepartmentDetails(7L, 42L, "Mo 9-12", null, null);

    assertThat(view.openingHours()).isEqualTo("Mo 9-12");
    verify(agencyTopicRepository).save(any());
  }

  @Test
  void update_Should_throwAccessDenied_When_restrictedAdminDoesNotOwnTheAgency() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);
    when(authenticatedUser.requireUserId()).thenReturn("admin-1");
    when(userAdminService.getAdminUserAgencyIds("admin-1")).thenReturn(List.of(9L));

    assertThatExceptionOfType(AgencyAccessDeniedException.class)
        .isThrownBy(() -> service.updateDepartmentDetails(7L, 42L, "Mo 9-12", null, null));

    // IDOR guard runs before any load or write
    verify(agencyTopicRepository, never()).findByAgency_IdAndTopicId(any(), any());
    verify(agencyTopicRepository, never()).save(any());
  }

  @Test
  void update_Should_throwAccessDenied_When_callerTenantDiffersFromAgencyTenant() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    when(authenticatedUser.getTenantId()).thenReturn(2L);
    existingDepartmentInTenant(1L);

    assertThatExceptionOfType(AgencyAccessDeniedException.class)
        .isThrownBy(() -> service.updateDepartmentDetails(7L, 42L, "Mo 9-12", null, null));
    verify(agencyTopicRepository, never()).save(any());
  }

  @Test
  void update_Should_allow_When_callerTenantMatchesAgencyTenant() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    when(authenticatedUser.getTenantId()).thenReturn(1L);
    existingDepartmentInTenant(1L);

    var view = service.updateDepartmentDetails(7L, 42L, "Mo 9-12", null, null);

    assertThat(view.openingHours()).isEqualTo("Mo 9-12");
    verify(agencyTopicRepository).save(any());
  }

  @Test
  void update_Should_throwNotFound_When_departmentMissing() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    when(agencyTopicRepository.findByAgency_IdAndTopicId(7L, 99L)).thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.updateDepartmentDetails(7L, 99L, "Mo 9-12", null, null));
    verify(agencyTopicRepository, never()).save(any());
  }

  @Test
  void get_Should_returnStoredOverridesRaw_withNullMeaningInherited() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    var department = existingDepartment();
    department.setOpeningHours("Di+Do 14-18 Uhr");
    // phoneExtension and floorLocation stay null = inherited / none

    var view = service.getDepartmentDetails(7L, 42L);

    assertThat(view.openingHours()).isEqualTo("Di+Do 14-18 Uhr");
    assertThat(view.phoneExtension()).isNull();
    assertThat(view.floorLocation()).isNull();
  }

  @Test
  void get_Should_throwAccessDenied_When_restrictedAdminDoesNotOwnTheAgency() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);
    when(authenticatedUser.requireUserId()).thenReturn("admin-1");
    when(userAdminService.getAdminUserAgencyIds("admin-1")).thenReturn(List.of(9L));

    assertThatExceptionOfType(AgencyAccessDeniedException.class)
        .isThrownBy(() -> service.getDepartmentDetails(7L, 42L));
  }

  @Test
  void get_Should_throwAccessDenied_When_callerTenantDiffersFromAgencyTenant() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    when(authenticatedUser.getTenantId()).thenReturn(2L);
    existingDepartmentInTenant(1L);

    assertThatExceptionOfType(AgencyAccessDeniedException.class)
        .isThrownBy(() -> service.getDepartmentDetails(7L, 42L));
  }

  @Test
  void get_Should_throwNotFound_When_departmentMissing() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    when(agencyTopicRepository.findByAgency_IdAndTopicId(7L, 99L)).thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.getDepartmentDetails(7L, 99L));
  }
}
