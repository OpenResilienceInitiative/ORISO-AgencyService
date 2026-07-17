package de.caritas.cob.agencyservice.api.admin.service.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.exception.httpresponses.AgencyAccessDeniedException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalText;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextRepository;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.api.validation.InputSanitizer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ADR-014 legal-text library: tenant-scoped CRUD over shared legal-text objects plus the
 * per-department assignment ("share this text" / "own text" / "unassign = tenant fallback").
 */
@ExtendWith(MockitoExtension.class)
class LegalTextAdminServiceTest {

  @Mock private LegalTextRepository legalTextRepository;
  @Mock private AgencyTopicRepository agencyTopicRepository;
  @Mock private AuthenticatedUser authenticatedUser;
  @Mock private UserAdminService userAdminService;

  private LegalTextAdminService service;

  @BeforeEach
  void setUp() {
    TenantContext.clear();
    service =
        new LegalTextAdminService(
            legalTextRepository,
            agencyTopicRepository,
            new LegalContentSanitizer(new InputSanitizer()),
            authenticatedUser,
            userAdminService);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private LegalText text(Long id, Long tenantId, LegalTextKind kind) {
    return LegalText.builder()
        .id(id)
        .tenantId(tenantId)
        .kind(kind)
        .label("Text " + id)
        .content("{\"de\":\"<p>x</p>\"}")
        .publicationStatus(PublicationStatus.PUBLISHED)
        .build();
  }

  private AgencyTopic departmentOfTenant(Long agencyTenantId) {
    var department =
        AgencyTopic.builder()
            .topicId(42L)
            .agency(
                Agency.builder()
                    .id(7L)
                    .name("Zentrum")
                    .consultingTypeId(1)
                    .tenantId(agencyTenantId)
                    .build())
            .build();
    when(agencyTopicRepository.findByAgency_IdAndTopicId(7L, 42L))
        .thenReturn(Optional.of(department));
    return department;
  }

  // --- list ---

  @Test
  void list_Should_returnCallerTenantsTexts_withUsageCounts() {
    when(authenticatedUser.getTenantId()).thenReturn(5L);
    when(legalTextRepository.findByTenantIdAndKindOrderByLabelAsc(5L, LegalTextKind.DPP))
        .thenReturn(List.of(text(1L, 5L, LegalTextKind.DPP)));
    when(agencyTopicRepository.countByDpp_Id(1L)).thenReturn(3L);

    var views = service.listLegalTexts(LegalTextKind.DPP);

    assertThat(views).hasSize(1);
    assertThat(views.get(0).id()).isEqualTo(1L);
    assertThat(views.get(0).usageCount()).isEqualTo(3L);
  }

  @Test
  void list_Should_countImprintUsage_ForImprintKind() {
    when(authenticatedUser.getTenantId()).thenReturn(5L);
    when(legalTextRepository.findByTenantIdAndKindOrderByLabelAsc(5L, LegalTextKind.IMPRINT))
        .thenReturn(List.of(text(2L, 5L, LegalTextKind.IMPRINT)));
    when(agencyTopicRepository.countByImprint_Id(2L)).thenReturn(1L);

    var views = service.listLegalTexts(LegalTextKind.IMPRINT);

    assertThat(views.get(0).usageCount()).isEqualTo(1L);
  }

  // --- create ---

  @Test
  void create_Should_sanitizeContent_stampCallerTenant_andStorePublished() {
    when(authenticatedUser.getTenantId()).thenReturn(5L);
    when(legalTextRepository.save(any(LegalText.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var view =
        service.createLegalText(
            LegalTextKind.DPP,
            "Standard-DSE",
            Map.of("de", "<p onclick=\"x()\">DSE <script>bad()</script></p>"),
            true);

    var saved = ArgumentCaptor.forClass(LegalText.class);
    verify(legalTextRepository).save(saved.capture());
    assertThat(saved.getValue().getTenantId()).isEqualTo(5L);
    assertThat(saved.getValue().getKind()).isEqualTo(LegalTextKind.DPP);
    assertThat(saved.getValue().getLabel()).isEqualTo("Standard-DSE");
    assertThat(saved.getValue().getContent())
        .contains("DSE")
        .doesNotContain("script")
        .doesNotContain("onclick");
    assertThat(saved.getValue().getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
    assertThat(saved.getValue().getCreateDate()).isNotNull();
    assertThat(view.usageCount()).isZero();
  }

  @Test
  void create_Should_rejectBlankLabel() {
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(
            () -> service.createLegalText(LegalTextKind.DPP, "  ", Map.of("de", "x"), false));
    verify(legalTextRepository, never()).save(any());
  }

  // --- update ---

  @Test
  void update_Should_updateLabelContentAndStatus() {
    when(authenticatedUser.getTenantId()).thenReturn(5L);
    var existing = text(1L, 5L, LegalTextKind.DPP);
    existing.setPublicationStatus(PublicationStatus.DRAFT);
    when(legalTextRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(legalTextRepository.save(any(LegalText.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(agencyTopicRepository.countByDpp_Id(1L)).thenReturn(2L);

    var view = service.updateLegalText(1L, "Neu", Map.of("de", "<p>neu</p>"), true);

    assertThat(existing.getLabel()).isEqualTo("Neu");
    assertThat(existing.getContent()).contains("neu");
    assertThat(existing.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
    assertThat(existing.getUpdateDate()).isNotNull();
    assertThat(view.usageCount()).isEqualTo(2L);
  }

  @Test
  void update_Should_throwAccessDenied_When_textBelongsToAnotherTenant() {
    when(authenticatedUser.getTenantId()).thenReturn(5L);
    when(legalTextRepository.findById(1L))
        .thenReturn(Optional.of(text(1L, 9L, LegalTextKind.DPP)));

    assertThatExceptionOfType(AgencyAccessDeniedException.class)
        .isThrownBy(() -> service.updateLegalText(1L, "x", Map.of("de", "x"), false));
    verify(legalTextRepository, never()).save(any());
  }

  @Test
  void update_Should_throwNotFound_When_textMissing() {
    when(legalTextRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.updateLegalText(99L, "x", Map.of("de", "x"), false));
  }

  // --- library CRUD is full-admin only (review: a restricted admin must not be able to change
  // tenant-wide shared texts that agencies outside their scope reference) ---

  @Test
  void list_Should_throwAccessDenied_When_restrictedAgencyAdmin() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);

    assertThatExceptionOfType(AgencyAccessDeniedException.class)
        .isThrownBy(() -> service.listLegalTexts(LegalTextKind.DPP));
  }

  @Test
  void create_Should_throwAccessDenied_When_restrictedAgencyAdmin() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);

    assertThatExceptionOfType(AgencyAccessDeniedException.class)
        .isThrownBy(
            () -> service.createLegalText(LegalTextKind.DPP, "x", Map.of("de", "x"), false));
    verify(legalTextRepository, never()).save(any());
  }

  @Test
  void update_Should_throwAccessDenied_When_restrictedAgencyAdmin() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);

    assertThatExceptionOfType(AgencyAccessDeniedException.class)
        .isThrownBy(() -> service.updateLegalText(1L, "x", Map.of("de", "x"), false));
    verify(legalTextRepository, never()).save(any());
  }

  // --- publish semantics on update ---

  @Test
  void update_Should_preservePublicationStatus_When_publishOmitted() {
    // review: a label/content-only update must not silently unpublish a published text
    when(authenticatedUser.getTenantId()).thenReturn(5L);
    var existing = text(1L, 5L, LegalTextKind.DPP);
    existing.setPublicationStatus(PublicationStatus.PUBLISHED);
    when(legalTextRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(legalTextRepository.save(any(LegalText.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(agencyTopicRepository.countByDpp_Id(1L)).thenReturn(1L);

    service.updateLegalText(1L, "Nur Label", Map.of("de", "<p>x</p>"), null);

    assertThat(existing.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
  }

  // --- assignment ---

  @Test
  void assign_Should_setDppReference_When_kindAndTenantMatch() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    var department = departmentOfTenant(5L);
    when(legalTextRepository.findById(1L))
        .thenReturn(Optional.of(text(1L, 5L, LegalTextKind.DPP)));

    service.assignDepartmentLegalText(7L, 42L, LegalTextKind.DPP, 1L);

    assertThat(department.getDpp()).isNotNull();
    assertThat(department.getDpp().getId()).isEqualTo(1L);
    verify(agencyTopicRepository).save(department);
  }

  @Test
  void assign_Should_clearReference_When_legalTextIdIsNull() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    var department = departmentOfTenant(5L);
    department.setImprint(text(2L, 5L, LegalTextKind.IMPRINT));

    service.assignDepartmentLegalText(7L, 42L, LegalTextKind.IMPRINT, null);

    assertThat(department.getImprint()).isNull();
    verify(agencyTopicRepository).save(department);
  }

  @Test
  void assign_Should_rejectKindMismatch() {
    // an IMPRINT object must never end up in the DPP slot — the consent screen would show the
    // wrong document kind
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    departmentOfTenant(5L);
    when(legalTextRepository.findById(2L))
        .thenReturn(Optional.of(text(2L, 5L, LegalTextKind.IMPRINT)));

    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> service.assignDepartmentLegalText(7L, 42L, LegalTextKind.DPP, 2L));
    verify(agencyTopicRepository, never()).save(any());
  }

  @Test
  void assign_Should_rejectTextOfAnotherTenant() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    departmentOfTenant(5L);
    when(legalTextRepository.findById(3L))
        .thenReturn(Optional.of(text(3L, 9L, LegalTextKind.DPP)));

    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> service.assignDepartmentLegalText(7L, 42L, LegalTextKind.DPP, 3L));
    verify(agencyTopicRepository, never()).save(any());
  }

  @Test
  void assign_Should_rejectNullTenantText_When_agencyIsTenantScoped() {
    // review: a null-tenant (global/orphan) text must not bypass the cross-tenant guard and leak
    // into a specific Träger's department in multi-tenant mode
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    departmentOfTenant(5L);
    when(legalTextRepository.findById(4L))
        .thenReturn(Optional.of(text(4L, null, LegalTextKind.DPP)));

    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> service.assignDepartmentLegalText(7L, 42L, LegalTextKind.DPP, 4L));
    verify(agencyTopicRepository, never()).save(any());
  }

  @Test
  void assign_Should_throwAccessDenied_When_restrictedAdminDoesNotOwnTheAgency() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);
    when(authenticatedUser.getUserId()).thenReturn("admin-1");
    when(userAdminService.getAdminUserAgencyIds("admin-1")).thenReturn(List.of(99L));

    assertThatExceptionOfType(AgencyAccessDeniedException.class)
        .isThrownBy(() -> service.assignDepartmentLegalText(7L, 42L, LegalTextKind.DPP, 1L));
    verify(agencyTopicRepository, never()).save(any());
  }

  @Test
  void assign_Should_throwNotFound_When_assignedTextMissing() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    departmentOfTenant(5L);
    when(legalTextRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.assignDepartmentLegalText(7L, 42L, LegalTextKind.DPP, 99L));
  }
}
