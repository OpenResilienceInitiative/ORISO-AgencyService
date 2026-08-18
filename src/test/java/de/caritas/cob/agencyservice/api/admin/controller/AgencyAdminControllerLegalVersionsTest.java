package de.caritas.cob.agencyservice.api.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.admin.service.AgencyAdminService;
import de.caritas.cob.agencyservice.api.admin.service.agency.AgencyAdminSearchService;
import de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol.AgencyAdminControlsFacade;
import de.caritas.cob.agencyservice.api.admin.service.agencypostcoderange.AgencyPostcodeRangeAdminService;
import de.caritas.cob.agencyservice.api.admin.service.allocation.AgencyIdAllocationService;
import de.caritas.cob.agencyservice.api.admin.service.department.DepartmentDetailsService;
import de.caritas.cob.agencyservice.api.admin.service.legal.DepartmentDataProtectionService;
import de.caritas.cob.agencyservice.api.admin.service.legal.DepartmentImprintService;
import de.caritas.cob.agencyservice.api.admin.service.legal.LegalTextAdminService;
import de.caritas.cob.agencyservice.api.admin.service.legal.LegalTextVersionAdminService;
import de.caritas.cob.agencyservice.api.admin.service.legal.LegalTextVersionView;
import de.caritas.cob.agencyservice.api.admin.validation.AgencyValidator;
import de.caritas.cob.agencyservice.api.model.LegalTextVersionDTO;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextLevel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** ADR-021 decision 3 endpoints: delegation + DTO mapping of the publication history. */
@ExtendWith(MockitoExtension.class)
class AgencyAdminControllerLegalVersionsTest {

  @Mock private AgencyAdminSearchService agencyAdminSearchService;
  @Mock private AgencyPostcodeRangeAdminService agencyPostcodeRangeAdminService;
  @Mock private AgencyAdminService agencyAdminService;
  @Mock private AgencyValidator agencyValidator;
  @Mock private AgencyAdminControlsFacade agencyAdminControlsFacade;
  @Mock private DepartmentDataProtectionService departmentDataProtectionService;
  @Mock private DepartmentDetailsService departmentDetailsService;
  @Mock private DepartmentImprintService departmentImprintService;
  @Mock private LegalTextAdminService legalTextAdminService;
  @Mock private LegalTextVersionAdminService legalTextVersionAdminService;
  @Mock private AgencyIdAllocationService agencyIdAllocationService;

  @InjectMocks private AgencyAdminController controller;

  private LegalTextVersionView view(LegalTextLevel level, LocalDateTime supersededAt) {
    return view(level, LocalDateTime.of(2026, 5, 1, 9, 0), supersededAt);
  }

  private LegalTextVersionView view(
      LegalTextLevel level, LocalDateTime publishedAt, LocalDateTime supersededAt) {
    return new LegalTextVersionView(
        100L,
        LegalTextKind.DPP,
        level,
        4711L,
        "{\"de\":\"<p>Fassung</p>\"}",
        "{\"de\":\"Ich habe die {{legal_links}} gelesen.\"}",
        publishedAt,
        "admin-uuid",
        supersededAt);
  }

  @Test
  void getDepartmentLegalTextVersions_Should_delegate_andMapEveryField() {
    when(legalTextVersionAdminService.listDepartmentVersions(7L, 42L, LegalTextKind.DPP))
        .thenReturn(List.of(view(LegalTextLevel.DEPARTMENT, LocalDateTime.of(2026, 9, 1, 0, 0))));

    var response = controller.getDepartmentLegalTextVersions(7L, 42L, "DPP");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).singleElement().satisfies(dto -> {
      assertThat(dto.getId()).isEqualTo(100L);
      assertThat(dto.getKind()).isEqualTo(LegalTextVersionDTO.KindEnum.DPP);
      assertThat(dto.getOwnerLevel()).isEqualTo(LegalTextVersionDTO.OwnerLevelEnum.DEPARTMENT);
      assertThat(dto.getOwnerId()).isEqualTo(4711L);
      assertThat(dto.getContent()).contains("Fassung");
      assertThat(dto.getPublishedAt()).isEqualTo("2026-05-01T09:00:00");
      assertThat(dto.getPublishedBy()).isEqualTo("admin-uuid");
      assertThat(dto.getSupersededAt()).isEqualTo("2026-09-01T00:00:00");
    });
  }

  @Test
  void getAgencyLegalTextVersions_Should_delegate_andReportStillInForceAsNullSupersededAt() {
    when(legalTextVersionAdminService.listAgencyVersions(7L, LegalTextKind.IMPRINT))
        .thenReturn(List.of(view(LegalTextLevel.AGENCY, null)));

    var response = controller.getAgencyLegalTextVersions(7L, "IMPRINT");

    assertThat(response.getBody()).singleElement().satisfies(dto ->
        assertThat(dto.getSupersededAt()).isNull());
  }

  /**
   * The wire shape is fixed at 19 characters whatever the seconds happen to be.
   * {@code LocalDateTime.toString()} used to drop a zero seconds component and append fractional
   * seconds when present, so the same field changed shape with its value and broke strict parsers
   * and string comparisons.
   */
  @ParameterizedTest(name = "{0} is serialised as {1}")
  @MethodSource("timestampsAndTheirWireForm")
  void getDepartmentLegalTextVersions_Should_serialiseTimestampsInOneFixedShape(
      LocalDateTime publishedAt, String expectedWireForm) {
    when(legalTextVersionAdminService.listDepartmentVersions(7L, 42L, LegalTextKind.DPP))
        .thenReturn(List.of(view(LegalTextLevel.DEPARTMENT, publishedAt, publishedAt)));

    var response = controller.getDepartmentLegalTextVersions(7L, 42L, "DPP");

    assertThat(response.getBody()).singleElement().satisfies(dto -> {
      assertThat(dto.getPublishedAt()).isEqualTo(expectedWireForm);
      assertThat(dto.getSupersededAt()).isEqualTo(expectedWireForm);
    });
  }

  private static Stream<Arguments> timestampsAndTheirWireForm() {
    return Stream.of(
        // zero seconds - the case LocalDateTime.toString() shortened to "2026-05-01T09:00"
        Arguments.of(LocalDateTime.of(2026, 5, 1, 9, 0), "2026-05-01T09:00:00"),
        // the OpenAPI example, already 19 characters
        Arguments.of(LocalDateTime.of(2026, 8, 16, 13, 12, 8), "2026-08-16T13:12:08"),
        // sub-second precision is truncated: published_at is a MariaDB datetime, which cannot
        // store it, so emitting it would advertise a precision the store does not have
        Arguments.of(
            LocalDateTime.of(2026, 8, 16, 13, 12, 8, 123_000_000), "2026-08-16T13:12:08"));
  }

  @Test
  void getLegalTextVersion_Should_returnTheArchivedWordingVerbatim() {
    when(legalTextVersionAdminService.getVersion(100L))
        .thenReturn(view(LegalTextLevel.SHARED, LocalDateTime.of(2026, 9, 1, 0, 0)));

    var response = controller.getLegalTextVersion(100L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getContent()).isEqualTo("{\"de\":\"<p>Fassung</p>\"}");
    assertThat(response.getBody().getOwnerLevel())
        .isEqualTo(LegalTextVersionDTO.OwnerLevelEnum.SHARED);
  }
}
