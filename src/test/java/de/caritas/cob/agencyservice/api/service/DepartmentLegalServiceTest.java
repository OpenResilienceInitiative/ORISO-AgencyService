package de.caritas.cob.agencyservice.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The public (unauthenticated) read side of ADR-003: users may only ever see PUBLISHED department
 * legal texts. The central invariant tested here is that DRAFT or never-authored content is
 * returned as {@code null} - drafts must never leak to clients.
 */
@ExtendWith(MockitoExtension.class)
class DepartmentLegalServiceTest {

  @Mock private AgencyTopicRepository agencyTopicRepository;

  @InjectMocks private DepartmentLegalService service;

  private AgencyTopic department(
      String dppContent,
      PublicationStatus dppStatus,
      String imprintContent,
      PublicationStatus imprintStatus) {
    var department =
        AgencyTopic.builder()
            .topicId(42L)
            .agency(Agency.builder().id(7L).name("Zentrum").consultingTypeId(1).build())
            .contentDpp(dppContent)
            .publicationStatus(dppStatus)
            .contentImprint(imprintContent)
            .publicationStatusImprint(imprintStatus)
            .build();
    when(agencyTopicRepository.findByAgency_IdAndTopicId(7L, 42L))
        .thenReturn(Optional.of(department));
    return department;
  }

  @Test
  void getPublishedDepartmentLegal_Should_returnBothContents_When_bothPublished() {
    department(
        "{\"de\":\"<p>DSE</p>\"}",
        PublicationStatus.PUBLISHED,
        "{\"de\":\"<p>Impressum</p>\"}",
        PublicationStatus.PUBLISHED);

    var view = service.getPublishedDepartmentLegal(7L, 42L);

    assertThat(view.dppContent()).isEqualTo("{\"de\":\"<p>DSE</p>\"}");
    assertThat(view.imprintContent()).isEqualTo("{\"de\":\"<p>Impressum</p>\"}");
  }

  @Test
  void getPublishedDepartmentLegal_Should_neverLeakDraftContent() {
    // both texts exist in the database but are still drafts - the public API must return null
    department(
        "{\"de\":\"<p>DSE Entwurf</p>\"}",
        PublicationStatus.DRAFT,
        "{\"de\":\"<p>Impressum Entwurf</p>\"}",
        PublicationStatus.DRAFT);

    var view = service.getPublishedDepartmentLegal(7L, 42L);

    assertThat(view.dppContent()).isNull();
    assertThat(view.imprintContent()).isNull();
  }

  @Test
  void getPublishedDepartmentLegal_Should_treatEachTextsLifecycleIndependently() {
    // published DPP next to a draft imprint: only the published half is exposed
    department(
        "{\"de\":\"<p>DSE</p>\"}",
        PublicationStatus.PUBLISHED,
        "{\"de\":\"<p>Impressum Entwurf</p>\"}",
        PublicationStatus.DRAFT);

    var view = service.getPublishedDepartmentLegal(7L, 42L);

    assertThat(view.dppContent()).isEqualTo("{\"de\":\"<p>DSE</p>\"}");
    assertThat(view.imprintContent()).isNull();
  }

  @Test
  void getPublishedDepartmentLegal_Should_returnNull_When_publishedButNeverAuthored() {
    // PUBLISHED status without stored content must not turn into an empty-but-present document
    department(null, PublicationStatus.PUBLISHED, "   ", PublicationStatus.PUBLISHED);

    var view = service.getPublishedDepartmentLegal(7L, 42L);

    assertThat(view.dppContent()).isNull();
    assertThat(view.imprintContent()).isNull();
  }

  @Test
  void getPublishedDepartmentLegal_Should_throwNotFound_When_departmentMissing() {
    when(agencyTopicRepository.findByAgency_IdAndTopicId(7L, 99L)).thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.getPublishedDepartmentLegal(7L, 99L));
  }

  @Test
  void getPublishedDepartmentLegal_Should_throwNotFound_When_agencyIsDeleted() {
    var department =
        department(
            "{\"de\":\"<p>DSE</p>\"}",
            PublicationStatus.PUBLISHED,
            "{\"de\":\"<p>Impressum</p>\"}",
            PublicationStatus.PUBLISHED);
    department.getAgency().setDeleteDate(LocalDateTime.now());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.getPublishedDepartmentLegal(7L, 42L));
  }
}
