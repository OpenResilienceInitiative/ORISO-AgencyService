package de.caritas.cob.agencyservice.api.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.admin.service.AgencyAdminService;
import de.caritas.cob.agencyservice.api.admin.service.agency.AgencyAdminSearchService;
import de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol.AgencyAdminControlsFacade;
import de.caritas.cob.agencyservice.api.admin.service.agencypostcoderange.AgencyPostcodeRangeAdminService;
import de.caritas.cob.agencyservice.api.admin.service.legal.DepartmentDataProtectionService;
import de.caritas.cob.agencyservice.api.admin.service.legal.DepartmentImprintService;
import de.caritas.cob.agencyservice.api.admin.service.legal.LegalTextAdminService;
import de.caritas.cob.agencyservice.api.admin.service.legal.LegalTextAdminView;
import de.caritas.cob.agencyservice.api.admin.validation.AgencyValidator;
import de.caritas.cob.agencyservice.api.model.CreateLegalTextDTO;
import de.caritas.cob.agencyservice.api.model.LegalTextAdminDTO;
import de.caritas.cob.agencyservice.api.model.LegalTextAssignmentDTO;
import de.caritas.cob.agencyservice.api.model.UpdateLegalTextDTO;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** ADR-014 legal-text library endpoints: delegation + DTO mapping. */
@ExtendWith(MockitoExtension.class)
class AgencyAdminControllerLegalTextTest {

  @Mock private AgencyAdminSearchService agencyAdminSearchService;
  @Mock private AgencyPostcodeRangeAdminService agencyPostcodeRangeAdminService;
  @Mock private AgencyAdminService agencyAdminService;
  @Mock private AgencyValidator agencyValidator;
  @Mock private AgencyAdminControlsFacade agencyAdminControlsFacade;
  @Mock private DepartmentDataProtectionService departmentDataProtectionService;
  @Mock private DepartmentImprintService departmentImprintService;
  @Mock private LegalTextAdminService legalTextAdminService;

  @InjectMocks private AgencyAdminController controller;

  private LegalTextAdminView view(long id, long usage) {
    return new LegalTextAdminView(
        id,
        LegalTextKind.DPP,
        "Standard-DSE",
        "{\"de\":\"<p>x</p>\"}",
        PublicationStatus.PUBLISHED,
        usage);
  }

  @Test
  void getLegalTexts_Should_delegate_andMapViewsWithUsageCount() {
    when(legalTextAdminService.listLegalTexts(LegalTextKind.DPP))
        .thenReturn(List.of(view(1L, 3L)));

    var response = controller.getLegalTexts("DPP");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(1);
    var dto = response.getBody().get(0);
    assertThat(dto.getId()).isEqualTo(1L);
    assertThat(dto.getKind()).isEqualTo(LegalTextAdminDTO.KindEnum.DPP);
    assertThat(dto.getLabel()).isEqualTo("Standard-DSE");
    assertThat(dto.getUsageCount()).isEqualTo(3L);
    assertThat(dto.getPublicationStatus())
        .isEqualTo(LegalTextAdminDTO.PublicationStatusEnum.PUBLISHED);
  }

  @Test
  void createLegalText_Should_delegateWithPublishFlag() {
    when(legalTextAdminService.createLegalText(
            eq(LegalTextKind.DPP), eq("Neu"), eq(Map.of("de", "<p>x</p>")), eq(true)))
        .thenReturn(view(2L, 0L));

    var body =
        new CreateLegalTextDTO()
            .kind(CreateLegalTextDTO.KindEnum.DPP)
            .label("Neu")
            .content(Map.of("de", "<p>x</p>"))
            .publish(true);
    var response = controller.createLegalText(body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getId()).isEqualTo(2L);
  }

  @Test
  void updateLegalText_Should_passNullPublish_ToPreserveCurrentStatus() {
    // omitted publish flag = keep the current publication status (review regression)
    when(legalTextAdminService.updateLegalText(
            eq(1L), eq("Umbenannt"), eq(Map.of("de", "<p>y</p>")), isNull()))
        .thenReturn(view(1L, 2L));

    var body =
        new UpdateLegalTextDTO().label("Umbenannt").content(Map.of("de", "<p>y</p>"));
    var response = controller.updateLegalText(1L, body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getUsageCount()).isEqualTo(2L);
  }

  @Test
  void updateLegalText_Should_passExplicitPublishFlag() {
    when(legalTextAdminService.updateLegalText(
            eq(1L), eq("Umbenannt"), eq(Map.of("de", "<p>y</p>")), eq(Boolean.TRUE)))
        .thenReturn(view(1L, 2L));

    var body =
        new UpdateLegalTextDTO()
            .label("Umbenannt")
            .content(Map.of("de", "<p>y</p>"))
            .publish(true);
    var response = controller.updateLegalText(1L, body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void assignDepartmentLegalText_Should_delegate_andReturn204() {
    var body =
        new LegalTextAssignmentDTO().kind(LegalTextAssignmentDTO.KindEnum.IMPRINT).legalTextId(9L);

    var response = controller.assignDepartmentLegalText(7L, 42L, body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(legalTextAdminService)
        .assignDepartmentLegalText(7L, 42L, LegalTextKind.IMPRINT, 9L);
  }

  @Test
  void assignDepartmentLegalText_Should_passNullId_ForUnassign() {
    var body = new LegalTextAssignmentDTO().kind(LegalTextAssignmentDTO.KindEnum.DPP);

    var response = controller.assignDepartmentLegalText(7L, 42L, body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(legalTextAdminService)
        .assignDepartmentLegalText(eq(7L), eq(42L), eq(LegalTextKind.DPP), isNull());
  }
}
