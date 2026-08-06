package de.caritas.cob.agencyservice.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalText;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
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
  void getPublishedDepartmentLegal_Should_preferReferencedLegalText_OverInlineContent() {
    // ADR-014: when the department references a shared legal-text object, that object is the
    // truth — the leftover inline copy must be ignored entirely.
    var department =
        department(
            "{\"de\":\"<p>alte Inline-DSE</p>\"}",
            PublicationStatus.PUBLISHED,
            null,
            PublicationStatus.DRAFT);
    department.setDpp(
        LegalText.builder()
            .id(100L)
            .kind(LegalTextKind.DPP)
            .label("Geteilte DSE")
            .content("{\"de\":\"<p>geteilte DSE</p>\"}")
            .publicationStatus(PublicationStatus.PUBLISHED)
            .build());

    var view = service.getPublishedDepartmentLegal(7L, 42L);

    assertThat(view.dppContent()).isEqualTo("{\"de\":\"<p>geteilte DSE</p>\"}");
  }

  @Test
  void getPublishedDepartmentLegal_Should_returnNull_When_referencedTextIsDraft() {
    // a DRAFT shared object must not fall back to a published inline leftover — the reference,
    // once set, fully replaces the inline column
    var department =
        department(
            "{\"de\":\"<p>alte Inline-DSE</p>\"}",
            PublicationStatus.PUBLISHED,
            null,
            PublicationStatus.DRAFT);
    department.setDpp(
        LegalText.builder()
            .id(100L)
            .kind(LegalTextKind.DPP)
            .label("Entwurf")
            .content("{\"de\":\"<p>Entwurf</p>\"}")
            .publicationStatus(PublicationStatus.DRAFT)
            .build());

    var view = service.getPublishedDepartmentLegal(7L, 42L);

    assertThat(view.dppContent()).isNull();
  }

  @Test
  void getPublishedDepartmentLegal_Should_resolveImprintReferenceIndependently() {
    var department =
        department(null, PublicationStatus.DRAFT, null, PublicationStatus.DRAFT);
    department.setImprint(
        LegalText.builder()
            .id(101L)
            .kind(LegalTextKind.IMPRINT)
            .label("Geteiltes Impressum")
            .content("{\"de\":\"<p>Impressum</p>\"}")
            .publicationStatus(PublicationStatus.PUBLISHED)
            .build());

    var view = service.getPublishedDepartmentLegal(7L, 42L);

    assertThat(view.dppContent()).isNull();
    assertThat(view.imprintContent()).isEqualTo("{\"de\":\"<p>Impressum</p>\"}");
  }

  @Test
  void getPublishedDepartmentLegal_Should_fallBackToAgencyWideText_When_departmentHasNoneOfItsOwn() {
    // ADR-014 chain tenant -> agency -> department: a Fachbereich that never authored anything
    // shows what its Beratungsstelle publishes, not nothing.
    var department = department(null, PublicationStatus.DRAFT, null, PublicationStatus.DRAFT);
    department.getAgency().setContentDpp("{\"de\":\"<p>DSE der Stelle</p>\"}");
    department.getAgency().setContentImprint("{\"de\":\"<p>Impressum der Stelle</p>\"}");

    var view = service.getPublishedDepartmentLegal(7L, 42L);

    assertThat(view.dppContent()).isEqualTo("{\"de\":\"<p>DSE der Stelle</p>\"}");
    assertThat(view.imprintContent()).isEqualTo("{\"de\":\"<p>Impressum der Stelle</p>\"}");
  }

  @Test
  void getPublishedDepartmentLegal_Should_preferOwnPublishedText_OverAgencyWideText() {
    // Publishing is what leaves the inheritance for good.
    var department =
        department(
            "{\"de\":\"<p>eigene DSE</p>\"}", PublicationStatus.PUBLISHED, null,
            PublicationStatus.DRAFT);
    department.getAgency().setContentDpp("{\"de\":\"<p>DSE der Stelle</p>\"}");

    var view = service.getPublishedDepartmentLegal(7L, 42L);

    assertThat(view.dppContent()).isEqualTo("{\"de\":\"<p>eigene DSE</p>\"}");
  }

  @Test
  void getPublishedDepartmentLegal_Should_keepShowingAgencyWideText_While_ownTextIsStillDraft() {
    // The draft copy is invisible to users until it is published — until then the inherited text
    // stays in force. A draft must never blank out a legally required document.
    var department =
        department(
            "{\"de\":\"<p>Entwurf</p>\"}", PublicationStatus.DRAFT, null, PublicationStatus.DRAFT);
    department.getAgency().setContentDpp("{\"de\":\"<p>DSE der Stelle</p>\"}");

    var view = service.getPublishedDepartmentLegal(7L, 42L);

    assertThat(view.dppContent()).isEqualTo("{\"de\":\"<p>DSE der Stelle</p>\"}");
  }

  @Test
  void getPublishedDepartmentLegal_Should_fallBackToAgencyWideText_When_referencedTextIsDraft() {
    var department = department(null, PublicationStatus.DRAFT, null, PublicationStatus.DRAFT);
    department.getAgency().setContentDpp("{\"de\":\"<p>DSE der Stelle</p>\"}");
    department.setDpp(
        LegalText.builder()
            .id(100L)
            .kind(LegalTextKind.DPP)
            .label("Entwurf")
            .content("{\"de\":\"<p>Entwurf</p>\"}")
            .publicationStatus(PublicationStatus.DRAFT)
            .build());

    var view = service.getPublishedDepartmentLegal(7L, 42L);

    assertThat(view.dppContent()).isEqualTo("{\"de\":\"<p>DSE der Stelle</p>\"}");
  }

  @Test
  void getPublishedDepartmentLegal_Should_returnNull_When_neitherLevelHasText() {
    // A blank agency column is an absent text, not an empty-but-present document.
    var department = department(null, PublicationStatus.DRAFT, null, PublicationStatus.DRAFT);
    department.getAgency().setContentDpp("   ");

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
