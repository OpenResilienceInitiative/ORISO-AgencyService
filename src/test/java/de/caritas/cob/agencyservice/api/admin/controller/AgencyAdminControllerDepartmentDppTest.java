package de.caritas.cob.agencyservice.api.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.admin.service.AgencyAdminService;
import de.caritas.cob.agencyservice.api.admin.service.agency.AgencyAdminSearchService;
import de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol.AgencyAdminControlsFacade;
import de.caritas.cob.agencyservice.api.admin.service.agencypostcoderange.AgencyPostcodeRangeAdminService;
import de.caritas.cob.agencyservice.api.admin.service.legal.DepartmentDataProtectionService;
import de.caritas.cob.agencyservice.api.admin.service.legal.DepartmentDataProtectionView;
import de.caritas.cob.agencyservice.api.admin.validation.AgencyValidator;
import de.caritas.cob.agencyservice.api.model.DepartmentDataProtectionContentDTO;
import de.caritas.cob.agencyservice.api.model.DepartmentDataProtectionDTO;
import de.caritas.cob.agencyservice.api.model.DepartmentDataProtectionResponseDTO;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AgencyAdminControllerDepartmentDppTest {

  @Mock private AgencyAdminSearchService agencyAdminSearchService;
  @Mock private AgencyPostcodeRangeAdminService agencyPostcodeRangeAdminService;
  @Mock private AgencyAdminService agencyAdminService;
  @Mock private AgencyValidator agencyValidator;
  @Mock private AgencyAdminControlsFacade agencyAdminControlsFacade;
  @Mock private DepartmentDataProtectionService departmentDataProtectionService;

  @InjectMocks private AgencyAdminController controller;

  @Test
  void publishDepartmentDataProtection_Should_delegate_andMapStatusTo200() {
    var body = new DepartmentDataProtectionDTO().content(Map.of("de", "<p>DSE</p>")).publish(true);
    when(departmentDataProtectionService.publishDepartmentDataPrivacy(
            eq(7L), eq(42L), eq(Map.of("de", "<p>DSE</p>")), eq(true)))
        .thenReturn(PublicationStatus.PUBLISHED);

    var response = controller.publishDepartmentDataProtection(7L, 42L, body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getPublicationStatus())
        .isEqualTo(DepartmentDataProtectionResponseDTO.PublicationStatusEnum.PUBLISHED);
  }

  @Test
  void getDepartmentDataProtection_Should_delegate_andMapContentAndStatus() {
    when(departmentDataProtectionService.getDepartmentDataPrivacy(7L, 42L))
        .thenReturn(
            new DepartmentDataProtectionView(
                "{\"de\":\"<p>x</p>\"}", PublicationStatus.PUBLISHED));

    var response = controller.getDepartmentDataProtection(7L, 42L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getContent().get()).isEqualTo("{\"de\":\"<p>x</p>\"}");
    assertThat(response.getBody().getPublicationStatus())
        .isEqualTo(DepartmentDataProtectionContentDTO.PublicationStatusEnum.PUBLISHED);
  }

  @Test
  void publishDepartmentDataProtection_Should_treatAbsentPublishFlagAsDraftSave() {
    // publish flag omitted -> Boolean null -> service receives false (draft-save)
    var body = new DepartmentDataProtectionDTO().content(Map.of("de", "<p>Entwurf</p>"));
    when(departmentDataProtectionService.publishDepartmentDataPrivacy(
            eq(7L), eq(42L), eq(Map.of("de", "<p>Entwurf</p>")), eq(false)))
        .thenReturn(PublicationStatus.DRAFT);

    var response = controller.publishDepartmentDataProtection(7L, 42L, body);

    assertThat(response.getBody().getPublicationStatus())
        .isEqualTo(DepartmentDataProtectionResponseDTO.PublicationStatusEnum.DRAFT);
  }
}
