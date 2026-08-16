package de.caritas.cob.agencyservice.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.service.legal.LegalTextInheritanceResolver;
import de.caritas.cob.agencyservice.api.service.legal.LegalTextSourceLevel;
import de.caritas.cob.agencyservice.api.service.legal.ResolvedLegalText;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The public read side after ADR-021 decision 9: this service no longer resolves anything itself,
 * it looks the department up, refuses deleted agencies, and hands both kinds to the one resolver
 * that walks the whole ladder. The resolution rules themselves are covered by {@link
 * de.caritas.cob.agencyservice.api.service.legal.LegalTextInheritanceResolverTest}.
 */
@ExtendWith(MockitoExtension.class)
class DepartmentLegalServiceTest {

  @Mock private AgencyTopicRepository agencyTopicRepository;
  @Mock private LegalTextInheritanceResolver legalTextInheritanceResolver;

  @InjectMocks private DepartmentLegalService service;

  private AgencyTopic department(LocalDateTime agencyDeleteDate) {
    var department =
        AgencyTopic.builder()
            .id(4711L)
            .topicId(42L)
            .agency(
                Agency.builder()
                    .id(7L)
                    .name("Zentrum")
                    .consultingTypeId(1)
                    .deleteDate(agencyDeleteDate)
                    .build())
            .build();
    when(agencyTopicRepository.findByAgency_IdAndTopicId(7L, 42L))
        .thenReturn(Optional.of(department));
    return department;
  }

  @Test
  void getPublishedDepartmentLegal_Should_returnBothResolvedTexts() {
    var department = department(null);
    when(legalTextInheritanceResolver.resolveDpp(department))
        .thenReturn(
            new ResolvedLegalText(
                "{\"de\":\"<p>DSE</p>\"}",
                "{\"de\":\"Ich habe die {{legal_links}} gelesen.\"}",
                LegalTextSourceLevel.DEPARTMENT,
                100L));
    when(legalTextInheritanceResolver.resolveImprint(department))
        .thenReturn(
            new ResolvedLegalText(
                "{\"de\":\"<p>Impressum</p>\"}", null, LegalTextSourceLevel.AGENCY, null));

    var view = service.getPublishedDepartmentLegal(7L, 42L);

    assertThat(view.dpp().content()).contains("DSE");
    assertThat(view.dpp().consentText()).contains("{{legal_links}}");
    assertThat(view.dpp().sourceLevel()).isEqualTo(LegalTextSourceLevel.DEPARTMENT);
    // The version id is what ORISO-UserService pins a recorded consent to (ADR-022 decision 2).
    assertThat(view.dpp().versionId()).isEqualTo(100L);
    // An inherited imprint is legitimately reachable and reported as coming from the agency level.
    assertThat(view.imprint().sourceLevel()).isEqualTo(LegalTextSourceLevel.AGENCY);
  }

  @Test
  void getPublishedDepartmentLegal_Should_returnNoContent_When_nothingIsAuthoredAnywhere() {
    var department = department(null);
    when(legalTextInheritanceResolver.resolveDpp(department)).thenReturn(ResolvedLegalText.none());
    when(legalTextInheritanceResolver.resolveImprint(department))
        .thenReturn(ResolvedLegalText.none());

    var view = service.getPublishedDepartmentLegal(7L, 42L);

    assertThat(view.dppContent()).isNull();
    assertThat(view.imprintContent()).isNull();
    assertThat(view.dpp().sourceLevel()).isEqualTo(LegalTextSourceLevel.NONE);
  }

  @Test
  void getPublishedDepartmentLegal_Should_throwNotFound_When_departmentMissing() {
    when(agencyTopicRepository.findByAgency_IdAndTopicId(7L, 99L)).thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.getPublishedDepartmentLegal(7L, 99L));
  }

  @Test
  void getPublishedDepartmentLegal_Should_throwNotFound_When_agencyIsDeleted() {
    department(LocalDateTime.of(2026, 1, 1, 0, 0));

    // A deleted Beratungsstelle has no public surface at all, legal texts included.
    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.getPublishedDepartmentLegal(7L, 42L));
  }

  @Test
  void hasResolvableDpp_Should_followTheSameChainAsTheEndpoint() {
    var department = AgencyTopic.builder().id(4711L).topicId(42L).build();
    when(legalTextInheritanceResolver.resolveDpp(department))
        .thenReturn(
            new ResolvedLegalText("{\"de\":\"x\"}", null, LegalTextSourceLevel.AGENCY, null));

    // The measured defect: the flag used to look only at the department level while the endpoint
    // already inherited, so a department reported false while /legal returned content.
    assertThat(service.hasResolvableDpp(department)).isTrue();
  }
}
