package de.caritas.cob.agencyservice.api.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.admin.service.AgencyAdminService;
import de.caritas.cob.agencyservice.api.admin.service.agency.AgencyAdminSearchService;
import de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol.AgencyAdminControlsFacade;
import de.caritas.cob.agencyservice.api.admin.service.agencypostcoderange.AgencyPostcodeRangeAdminService;
import de.caritas.cob.agencyservice.api.admin.service.allocation.AgencyIdAllocationService;
import de.caritas.cob.agencyservice.api.admin.service.department.DepartmentDetailsService;
import de.caritas.cob.agencyservice.api.admin.service.department.DepartmentDetailsView;
import de.caritas.cob.agencyservice.api.admin.service.legal.DepartmentDataProtectionService;
import de.caritas.cob.agencyservice.api.admin.service.legal.LegalTextVersionAdminService;
import de.caritas.cob.agencyservice.api.admin.service.legal.DepartmentImprintService;
import de.caritas.cob.agencyservice.api.admin.service.legal.LegalTextAdminService;
import de.caritas.cob.agencyservice.api.admin.validation.AgencyValidator;
import de.caritas.cob.agencyservice.api.model.DepartmentDetailsDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AgencyAdminControllerDepartmentDetailsTest {

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

  @Test
  void updateDepartmentDetails_Should_delegate_andMapStoredOverridesTo200() {
    var body =
        new DepartmentDetailsDTO()
            .openingHours("Di+Do 14-18 Uhr")
            .phoneExtension("-23")
            .floorLocation("3. OG, Raum 312");
    when(departmentDetailsService.updateDepartmentDetails(
            7L, 42L, "Di+Do 14-18 Uhr", "-23", "3. OG, Raum 312"))
        .thenReturn(new DepartmentDetailsView("Di+Do 14-18 Uhr", "-23", "3. OG, Raum 312"));

    var response = controller.updateDepartmentDetails(7L, 42L, body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getOpeningHours()).isEqualTo("Di+Do 14-18 Uhr");
    assertThat(response.getBody().getPhoneExtension()).isEqualTo("-23");
    assertThat(response.getBody().getFloorLocation()).isEqualTo("3. OG, Raum 312");
  }

  @Test
  void getDepartmentDetails_Should_delegate_andMapNullOverridesAsInherited() {
    when(departmentDetailsService.getDepartmentDetails(7L, 42L))
        .thenReturn(new DepartmentDetailsView(null, null, "EG"));

    var response = controller.getDepartmentDetails(7L, 42L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getOpeningHours()).isNull();
    assertThat(response.getBody().getPhoneExtension()).isNull();
    assertThat(response.getBody().getFloorLocation()).isEqualTo("EG");
  }
}
