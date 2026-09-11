package de.caritas.cob.agencyservice.api.admin.validation.validators;

import static de.caritas.cob.agencyservice.api.model.DataProtectionDTO.DataProtectionResponsibleEntityEnum.AGENCY_RESPONSIBLE;
import static de.caritas.cob.agencyservice.api.model.DataProtectionDTO.DataProtectionResponsibleEntityEnum.ALTERNATIVE_REPRESENTATIVE;
import static de.caritas.cob.agencyservice.api.model.DataProtectionDTO.DataProtectionResponsibleEntityEnum.DATA_PROTECTION_OFFICER;
import static org.apache.commons.lang3.StringUtils.isBlank;

import de.caritas.cob.agencyservice.api.admin.validation.validators.model.ValidateAgencyDTO;
import de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason;
import de.caritas.cob.agencyservice.api.exception.httpresponses.InvalidOfflineStatusException;
import de.caritas.cob.agencyservice.api.model.DataProtectionContactDTO;
import de.caritas.cob.agencyservice.api.repository.agency.DataProtectionPlaceHolderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AgencyDataProtectionValidationService {

  /**
   * ADR-003 "dev mode" switch. The data protection officer contact is a legal imprint/DPP
   * requirement (ADR-003) and stays MANDATORY in production, so this defaults to {@code true}. It
   * is set to {@code false} in the {@code testing} (and local/dev) profiles so tests and dev flows
   * can create an agency without a fully populated DPO contact — the DB column itself is nullable
   * (changeset 0016/0024). Decided in #oriso-codereview: "brauchen einen dev modus, sonst koennen
   * wir nichts testen".
   */
  @Value("${agency.department.require-dpo-contact:true}")
  private boolean requireDataProtectionOfficerContact;

  public void validate(ValidateAgencyDTO validateAgencyDto) {
    validateThatDataProtectionDtoExists(validateAgencyDto);
    validateIfDataProtectionOfficer(validateAgencyDto);
    validateIfAgencyResponsible(validateAgencyDto);
    validateIfAlternativeRepresentative(validateAgencyDto);
  }

  private void validateThatDataProtectionDtoExists(ValidateAgencyDTO validateAgencyDto) {
    if (validateAgencyDto.getDataProtectionDTO() == null) {
      logValidationFailure(validateAgencyDto, HttpStatusExceptionReason.DATA_PROTECTION_DTO_IS_NULL);
      throw new InvalidOfflineStatusException(
          HttpStatusExceptionReason.DATA_PROTECTION_DTO_IS_NULL);
    }
  }

  private void validateIfDataProtectionOfficer(ValidateAgencyDTO validateAgencyDto) {
    if (!requireDataProtectionOfficerContact) {
      // ADR-003 dev mode: requirement relaxed (testing/dev profiles) so agencies can be created
      // without a complete DPO contact. Production keeps requireDataProtectionOfficerContact=true.
      return;
    }
    if (DATA_PROTECTION_OFFICER.equals(
        validateAgencyDto.getDataProtectionDTO().getDataProtectionResponsibleEntity())
        && areFieldsEmpty(
        validateAgencyDto.getDataProtectionDTO().getDataProtectionOfficerContact())) {
      logValidationFailure(
          validateAgencyDto, HttpStatusExceptionReason.DATA_PROTECTION_OFFICER_IS_EMPTY);
      throw new InvalidOfflineStatusException(
          HttpStatusExceptionReason.DATA_PROTECTION_OFFICER_IS_EMPTY);
    }
  }

  private void validateIfAgencyResponsible(ValidateAgencyDTO validateAgencyDto) {
    if (AGENCY_RESPONSIBLE.equals(
        validateAgencyDto.getDataProtectionDTO().getDataProtectionResponsibleEntity())
        && areFieldsEmpty(
        validateAgencyDto.getDataProtectionDTO().getAgencyDataProtectionResponsibleContact())) {
      logValidationFailure(
          validateAgencyDto, HttpStatusExceptionReason.DATA_PROTECTION_RESPONSIBLE_IS_EMPTY);
      throw new InvalidOfflineStatusException(
          HttpStatusExceptionReason.DATA_PROTECTION_RESPONSIBLE_IS_EMPTY);
    }
  }

  private void validateIfAlternativeRepresentative(ValidateAgencyDTO validateAgencyDto) {
    if (ALTERNATIVE_REPRESENTATIVE.equals(
        validateAgencyDto.getDataProtectionDTO().getDataProtectionResponsibleEntity())
        && areFieldsEmpty(validateAgencyDto.getDataProtectionDTO()
        .getAlternativeDataProtectionRepresentativeContact())) {
      logValidationFailure(
          validateAgencyDto,
          HttpStatusExceptionReason.DATA_PROTECTION_ALTERNATIVE_RESPONSIBLE_IS_EMPTY);
      throw new InvalidOfflineStatusException(
          HttpStatusExceptionReason.DATA_PROTECTION_ALTERNATIVE_RESPONSIBLE_IS_EMPTY);
    }
  }

  private void logValidationFailure(
      ValidateAgencyDTO validateAgencyDto, HttpStatusExceptionReason reason) {
    log.warn(
        "Agency validation failed: agencyId={}, field=dataProtection, reason={}",
        validateAgencyDto.getId(),
        reason);
  }

  private boolean areFieldsEmpty(DataProtectionContactDTO dataProtectionOfficerContact) {
    return dataProtectionOfficerContact == null
        || isBlank(dataProtectionOfficerContact.getNameAndLegalForm())
        || isBlank(dataProtectionOfficerContact.getCity())
        || isBlank(dataProtectionOfficerContact.getPostcode())
        || isBlank(dataProtectionOfficerContact.getEmail());
  }
}
