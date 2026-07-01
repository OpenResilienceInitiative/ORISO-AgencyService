package de.caritas.cob.agencyservice.api.admin.service.legal;

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
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.api.validation.InputSanitizer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepartmentDataProtectionServiceTest {

  @Mock private AgencyTopicRepository agencyTopicRepository;
  @Mock private AuthenticatedUser authenticatedUser;
  @Mock private UserAdminService userAdminService;

  private DepartmentDataProtectionService service;

  @BeforeEach
  void setUp() {
    // real sanitizer so we actually verify markup stripping, not a mock
    service =
        new DepartmentDataProtectionService(
            agencyTopicRepository, new InputSanitizer(), authenticatedUser, userAdminService);
  }

  private AgencyTopic existingDepartment() {
    var department = AgencyTopic.builder().topicId(42L).build();
    when(agencyTopicRepository.findByAgency_IdAndTopicId(7L, 42L))
        .thenReturn(Optional.of(department));
    return department;
  }

  @Test
  void publish_Should_sanitizeStoreAsJsonMap_andSetPublished_ForFullAgencyAdmin() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    existingDepartment();

    var status =
        service.publishDepartmentDataPrivacy(
            7L,
            42L,
            Map.of("de", "<p onclick=\"steal()\">Datenschutz <script>bad()</script></p>"),
            true);

    // a full admin is never scoped-checked against agency ids
    verifyNoInteractions(userAdminService);
    assertThat(status).isEqualTo(PublicationStatus.PUBLISHED);

    var saved = ArgumentCaptor.forClass(AgencyTopic.class);
    verify(agencyTopicRepository).save(saved.capture());
    assertThat(saved.getValue().getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
    var storedJson = saved.getValue().getContentDpp();
    assertThat(storedJson).startsWith("{").contains("\"de\":");
    // dangerous markup removed, text kept
    assertThat(storedJson).contains("Datenschutz").doesNotContain("script").doesNotContain("onclick");
    assertThat(saved.getValue().getUpdateDate()).isNotNull();
  }

  @Test
  void publish_Should_setDraft_When_publishFalse() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    existingDepartment();

    var status =
        service.publishDepartmentDataPrivacy(7L, 42L, Map.of("de", "<p>Entwurf</p>"), false);

    assertThat(status).isEqualTo(PublicationStatus.DRAFT);
  }

  @Test
  void publish_Should_allow_When_restrictedAdminOwnsTheAgency() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);
    when(authenticatedUser.getUserId()).thenReturn("admin-1");
    when(userAdminService.getAdminUserAgencyIds("admin-1")).thenReturn(List.of(7L, 9L));
    existingDepartment();

    var status =
        service.publishDepartmentDataPrivacy(7L, 42L, Map.of("de", "<p>ok</p>"), true);

    assertThat(status).isEqualTo(PublicationStatus.PUBLISHED);
    verify(agencyTopicRepository).save(any());
  }

  @Test
  void publish_Should_throwAccessDenied_When_restrictedAdminDoesNotOwnTheAgency() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);
    when(authenticatedUser.getUserId()).thenReturn("admin-1");
    when(userAdminService.getAdminUserAgencyIds("admin-1")).thenReturn(List.of(9L));

    assertThatExceptionOfType(AgencyAccessDeniedException.class)
        .isThrownBy(
            () ->
                service.publishDepartmentDataPrivacy(7L, 42L, Map.of("de", "<p>x</p>"), true));

    // IDOR guard runs before any load or write
    verify(agencyTopicRepository, never()).findByAgency_IdAndTopicId(any(), any());
    verify(agencyTopicRepository, never()).save(any());
  }

  @Test
  void publish_Should_throwNotFound_When_departmentMissing() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    when(agencyTopicRepository.findByAgency_IdAndTopicId(7L, 99L)).thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(
            () ->
                service.publishDepartmentDataPrivacy(7L, 99L, Map.of("de", "<p>x</p>"), true));
    verify(agencyTopicRepository, never()).save(any());
  }
}
