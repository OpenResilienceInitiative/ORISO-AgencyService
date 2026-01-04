package de.caritas.cob.agencyservice.api.admin.validation.validators;

import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.admin.validation.validators.model.ValidateAgencyDTO;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import org.assertj.core.api.Fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class AgencyTenantValidatorTest {

  @Mock
  AuthenticatedUser authenticatedUser;
  AgencyTenantValidator agencyTenantValidator;

  @BeforeEach
  public void setup() {
    TenantContext.clear();
    agencyTenantValidator = new AgencyTenantValidator(authenticatedUser);
  }

  @Test
  void validate_shouldNotThrowException_When_AuthenticatedUserIsNotSuperAdminAndTenantIdNull() {
    var validateAgencyDTO = ValidateAgencyDTO.builder().tenantId(null).build();
    try {
      agencyTenantValidator.validate(validateAgencyDTO);
    } catch (Exception e) {
      Fail.fail("Should not throw exception");
    }
  }

  @Test
  void validate_shouldNotThrowException_When_AuthenticatedUserIsNotSuperAdminAndTenantIdMatchesTenantContext() {
    TenantContext.setCurrentTenant(1L);
    var validateAgencyDTO = ValidateAgencyDTO.builder().tenantId(1L).build();
    try {
      agencyTenantValidator.validate(validateAgencyDTO);
    } catch (Exception e) {
      Fail.fail("Should not throw exception");
    }
  }

  @Test
  void validate_shouldThrowAccessDeniedException_When_AuthenticatedUserIsNotSuperAdminAndTenantIdDoesNotMatchTenantContext() {
    TenantContext.setCurrentTenant(2L);
    var validateAgencyDTO = ValidateAgencyDTO.builder().tenantId(1L).build();
    try {
      agencyTenantValidator.validate(validateAgencyDTO);
    } catch (AccessDeniedException e) {
      assert e.getMessage().equals("Access denied. Tenant id in the request does not match current tenant.");
    } catch (Exception ex) {
      Fail.fail("Unexpected exception: " + ex.getMessage());
    }
  }

  @Test
  void validate_shouldNotThrowException_When_AuthenticatedUserIsSuperAdminAndTenantIdNotMatchingTenantContext() {
    TenantContext.setCurrentTenant(0L);
    when(authenticatedUser.isTenantSuperAdmin()).thenReturn(true);
    var validateAgencyDTO = ValidateAgencyDTO.builder().tenantId(1L).build();
    try {
      agencyTenantValidator.validate(validateAgencyDTO);
    } catch (Exception e) {
      Fail.fail("Should not throw exception");
    }
  }



}