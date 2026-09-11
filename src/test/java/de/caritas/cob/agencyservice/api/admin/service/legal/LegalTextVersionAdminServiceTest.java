package de.caritas.cob.agencyservice.api.admin.service.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.exception.httpresponses.AgencyAccessDeniedException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextLevel;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextVersion;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextVersionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ADR-021 decision 3 read side. The wording of a published document is not secret, but which
 * Beratungsstelle runs what — and what its drafts looked like — is, so the history endpoints run
 * the same guards as the publish endpoints they mirror.
 */
@ExtendWith(MockitoExtension.class)
class LegalTextVersionAdminServiceTest {

  @Mock private LegalTextVersionService legalTextVersionService;
  @Mock private LegalTextVersionRepository legalTextVersionRepository;
  @Mock private AgencyTopicRepository agencyTopicRepository;
  @Mock private AgencyRepository agencyRepository;
  @Mock private LegalAdminAccessGuard accessGuard;

  @InjectMocks private LegalTextVersionAdminService service;

  private Agency agency() {
    return Agency.builder().id(7L).name("BS").consultingTypeId(1).tenantId(3L).build();
  }

  private LegalTextVersion version(LegalTextLevel level, Long ownerId) {
    return LegalTextVersion.builder()
        .id(100L)
        .kind(LegalTextKind.DPP)
        .ownerLevel(level)
        .ownerId(ownerId)
        .tenantId(3L)
        .content("{\"de\":\"<p>Fassung</p>\"}")
        .publishedAt(LocalDateTime.of(2026, 5, 1, 9, 0))
        .publishedBy("admin-uuid")
        .build();
  }

  @Test
  void listDepartmentVersions_Should_guard_andDelegateOnTheDepartmentRowId() {
    var department = AgencyTopic.builder().id(4711L).topicId(42L).agency(agency()).build();
    when(agencyTopicRepository.findByAgency_IdAndTopicId(7L, 42L))
        .thenReturn(Optional.of(department));
    when(legalTextVersionService.listVersions(LegalTextLevel.DEPARTMENT, 4711L, LegalTextKind.DPP))
        .thenReturn(List.of(version(LegalTextLevel.DEPARTMENT, 4711L)));

    var views = service.listDepartmentVersions(7L, 42L, LegalTextKind.DPP);

    verify(accessGuard).assertRestrictedAdminOwnsAgency(7L);
    verify(accessGuard).assertCallerTenantMatches(department.getAgency());
    assertThat(views).singleElement().satisfies(view -> {
      assertThat(view.id()).isEqualTo(100L);
      assertThat(view.publishedBy()).isEqualTo("admin-uuid");
      assertThat(view.supersededAt()).isNull();
      assertThat(view.content()).contains("Fassung");
    });
  }

  @Test
  void listDepartmentVersions_Should_throwNotFound_When_departmentMissing() {
    when(agencyTopicRepository.findByAgency_IdAndTopicId(7L, 99L)).thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.listDepartmentVersions(7L, 99L, LegalTextKind.DPP));
  }

  @Test
  void listAgencyVersions_Should_guard_andDelegateOnTheAgencyId() {
    when(agencyRepository.findById(7L)).thenReturn(Optional.of(agency()));
    when(legalTextVersionService.listVersions(LegalTextLevel.AGENCY, 7L, LegalTextKind.IMPRINT))
        .thenReturn(List.of());

    assertThat(service.listAgencyVersions(7L, LegalTextKind.IMPRINT)).isEmpty();

    verify(accessGuard).assertRestrictedAdminOwnsAgency(7L);
  }

  @Test
  void getVersion_Should_authoriseAgainstTheStoredOwner_notAgainstTheCaller() {
    var stored = version(LegalTextLevel.DEPARTMENT, 4711L);
    var department = AgencyTopic.builder().id(4711L).topicId(42L).agency(agency()).build();
    when(legalTextVersionRepository.findById(100L)).thenReturn(Optional.of(stored));
    when(agencyTopicRepository.findById(4711L)).thenReturn(Optional.of(department));

    var view = service.getVersion(100L);

    // A version id must not be a bearer token for someone else's document: the owner is re-derived
    // from the row and the standard guards run against that.
    verify(accessGuard).assertRestrictedAdminOwnsAgency(7L);
    verify(accessGuard).assertCallerTenantMatches(department.getAgency());
    assertThat(view.content()).isEqualTo("{\"de\":\"<p>Fassung</p>\"}");
  }

  @Test
  void getVersion_Should_rejectAForeignAgencysVersion() {
    var stored = version(LegalTextLevel.AGENCY, 7L);
    when(legalTextVersionRepository.findById(100L)).thenReturn(Optional.of(stored));
    when(agencyRepository.findById(7L)).thenReturn(Optional.of(agency()));
    doThrow(new AgencyAccessDeniedException())
        .when(accessGuard)
        .assertCallerTenantMatches(any(Agency.class));

    assertThatExceptionOfType(AgencyAccessDeniedException.class)
        .isThrownBy(() -> service.getVersion(100L));
  }

  @Test
  void getVersion_Should_authoriseASharedVersionByOwningTenant() {
    var stored = version(LegalTextLevel.SHARED, 55L);
    when(legalTextVersionRepository.findById(100L)).thenReturn(Optional.of(stored));

    service.getVersion(100L);

    // A shared ADR-014 text belongs to a Träger, not to one agency, so it is guarded like the
    // library itself: full agency admins only, plus the owning-tenant check.
    verify(accessGuard).assertFullAgencyAdmin();
    verify(accessGuard).assertCallerTenantIs(3L);
  }

  @Test
  void getVersion_Should_rejectARestrictedAdminReadingASharedVersion() {
    var stored = version(LegalTextLevel.SHARED, 55L);
    when(legalTextVersionRepository.findById(100L)).thenReturn(Optional.of(stored));
    doThrow(new AgencyAccessDeniedException()).when(accessGuard).assertFullAgencyAdmin();

    // A shared text spans agencies a restricted admin does not administer, so guessing a version id
    // must not hand them tenant-wide wording and publisher identities.
    assertThatExceptionOfType(AgencyAccessDeniedException.class)
        .isThrownBy(() -> service.getVersion(100L));
  }

  @Test
  void getVersion_Should_throwNotFound_When_versionMissing() {
    when(legalTextVersionRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> service.getVersion(404L));
  }
}
