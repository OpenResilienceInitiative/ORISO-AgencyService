package de.caritas.cob.agencyservice.api.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.admin.service.AgencyAdminService;
import de.caritas.cob.agencyservice.api.admin.service.agency.AgencyAdminSearchService;
import de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol.AgencyAdminControlsFacade;
import de.caritas.cob.agencyservice.api.admin.service.agencypostcoderange.AgencyPostcodeRangeAdminService;
import de.caritas.cob.agencyservice.api.admin.service.legal.DepartmentDataProtectionService;
import de.caritas.cob.agencyservice.api.admin.service.legal.DepartmentImprintService;
import de.caritas.cob.agencyservice.api.admin.service.legal.DepartmentImprintView;
import de.caritas.cob.agencyservice.api.admin.validation.AgencyValidator;
import de.caritas.cob.agencyservice.api.model.DepartmentImprintContentDTO;
import de.caritas.cob.agencyservice.api.model.DepartmentImprintDTO;
import de.caritas.cob.agencyservice.api.model.DepartmentImprintResponseDTO;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AgencyAdminControllerDepartmentImprintTest {

  @Mock private AgencyAdminSearchService agencyAdminSearchService;
  @Mock private AgencyPostcodeRangeAdminService agencyPostcodeRangeAdminService;
  @Mock private AgencyAdminService agencyAdminService;
  @Mock private AgencyValidator agencyValidator;
  @Mock private AgencyAdminControlsFacade agencyAdminControlsFacade;
  @Mock private DepartmentDataProtectionService departmentDataProtectionService;
  @Mock private DepartmentImprintService departmentImprintService;

  @InjectMocks private AgencyAdminController controller;

  @Test
  void publishDepartmentImprint_Should_delegate_andMapStatusTo200() {
    var body = new DepartmentImprintDTO().content(Map.of("de", "<p>Impressum</p>")).publish(true);
    when(departmentImprintService.publishDepartmentImprint(
            eq(7L), eq(42L), eq(Map.of("de", "<p>Impressum</p>")), eq(true)))
        .thenReturn(PublicationStatus.PUBLISHED);

    var response = controller.publishDepartmentImprint(7L, 42L, body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getPublicationStatus())
        .isEqualTo(DepartmentImprintResponseDTO.PublicationStatusEnum.PUBLISHED);
  }

  @Test
  void getDepartmentImprint_Should_delegate_andMapContentAndStatus() {
    when(departmentImprintService.getDepartmentImprint(7L, 42L))
        .thenReturn(new DepartmentImprintView("{\"de\":\"<p>x</p>\"}", PublicationStatus.PUBLISHED));

    var response = controller.getDepartmentImprint(7L, 42L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getContent()).isEqualTo("{\"de\":\"<p>x</p>\"}");
    assertThat(response.getBody().getPublicationStatus())
        .isEqualTo(DepartmentImprintContentDTO.PublicationStatusEnum.PUBLISHED);
  }

  @Test
  void publishDepartmentImprint_Should_treatAbsentPublishFlagAsDraftSave() {
    // publish flag omitted -> Boolean null -> service receives false (draft-save)
    var body = new DepartmentImprintDTO().content(Map.of("de", "<p>Entwurf</p>"));
    when(departmentImprintService.publishDepartmentImprint(
            eq(7L), eq(42L), eq(Map.of("de", "<p>Entwurf</p>")), eq(false)))
        .thenReturn(PublicationStatus.DRAFT);

    var response = controller.publishDepartmentImprint(7L, 42L, body);

    assertThat(response.getBody().getPublicationStatus())
        .isEqualTo(DepartmentImprintResponseDTO.PublicationStatusEnum.DRAFT);
  }
}
