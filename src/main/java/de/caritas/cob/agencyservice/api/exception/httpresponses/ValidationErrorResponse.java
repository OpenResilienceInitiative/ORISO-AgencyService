package de.caritas.cob.agencyservice.api.exception.httpresponses;

/** Stable field-level validation contract for Admin clients. */
public record ValidationErrorResponse(
    String field, HttpStatusExceptionReason reason, String message) {

  /** Builds a response without exposing submitted field values. */
  public static ValidationErrorResponse from(HttpStatusExceptionReason reason) {
    return new ValidationErrorResponse(fieldFor(reason), reason, messageFor(reason));
  }

  private static String fieldFor(HttpStatusExceptionReason reason) {
    return switch (reason) {
      case INVALID_POSTCODE -> "postcode";
      case INVALID_CONSULTING_TYPE -> "consultingType";
      case INVALID_DEMOGRAPHICS_EMPTY_GENDERS,
          INVALID_DEMOGRAPHICS_EMPTY_AGE_FROM,
          INVALID_DEMOGRAPHICS_NULL_OBJECT -> "demographics";
      case INVALID_OFFLINE_STATUS,
          AGENCY_CONTAINS_CONSULTANTS,
          AGENCY_CONTAINS_NO_CONSULTANTS -> "offline";
      case DATA_PROTECTION_OFFICER_IS_EMPTY,
          DATA_PROTECTION_RESPONSIBLE_IS_EMPTY,
          DATA_PROTECTION_ALTERNATIVE_RESPONSIBLE_IS_EMPTY,
          DATA_PROTECTION_DTO_IS_NULL -> "dataProtection";
      default -> "agency";
    };
  }

  private static String messageFor(HttpStatusExceptionReason reason) {
    return switch (reason) {
      case DATA_PROTECTION_OFFICER_IS_EMPTY ->
          "A data protection officer contact is required.";
      case DATA_PROTECTION_RESPONSIBLE_IS_EMPTY ->
          "An agency data protection contact is required.";
      case DATA_PROTECTION_ALTERNATIVE_RESPONSIBLE_IS_EMPTY ->
          "An alternative data protection representative contact is required.";
      case DATA_PROTECTION_DTO_IS_NULL -> "Data protection details are required.";
      case INVALID_POSTCODE -> "The postcode is invalid.";
      case INVALID_CONSULTING_TYPE -> "The consulting type is invalid.";
      case INVALID_DEMOGRAPHICS_EMPTY_GENDERS -> "At least one gender is required.";
      case INVALID_DEMOGRAPHICS_EMPTY_AGE_FROM -> "The minimum age is required.";
      case INVALID_DEMOGRAPHICS_NULL_OBJECT -> "Demographic details are required.";
      case INVALID_OFFLINE_STATUS -> "The offline status is invalid.";
      case AGENCY_CONTAINS_CONSULTANTS -> "The agency still contains consultants.";
      case AGENCY_CONTAINS_NO_CONSULTANTS -> "The agency contains no consultants.";
      case AGENCY_IS_ALREADY_TEAM_AGENCY -> "The agency is already a team agency.";
      case AGENCY_IS_ALREADY_DEFAULT_AGENCY -> "The agency is already a default agency.";
      case AGENCY_ACCESS_DENIED -> "Access to the agency is denied.";
      case AGENCY_ID_NOT_AVAILABLE -> "The agency ID is not available.";
    };
  }
}
