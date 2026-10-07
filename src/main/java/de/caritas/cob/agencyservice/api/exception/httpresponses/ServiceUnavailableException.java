package de.caritas.cob.agencyservice.api.exception.httpresponses;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/**
 * Representation of a 503 - SERVICE UNAVAILABLE with an {@code X-Reason}.
 */
public class ServiceUnavailableException extends CustomValidationHttpStatusException {

  /**
   * Service unavailable exception.
   *
   * @param httpStatusExceptionReason the reason why the exception is thrown
   */
  public ServiceUnavailableException(HttpStatusExceptionReason httpStatusExceptionReason) {
    super(httpStatusExceptionReason, SERVICE_UNAVAILABLE);
  }
}
