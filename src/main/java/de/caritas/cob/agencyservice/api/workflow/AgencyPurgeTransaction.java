package de.caritas.cob.agencyservice.api.workflow;

import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.agencypostcoderange.AgencyPostcodeRangeRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purges one agency inside its own transaction.
 *
 * <p>The transaction boundary is the entire point of this class. The purge previously ran for the
 * whole batch inside one class-level transaction on {@link DeleteAgencyService}, so {@code
 * agencyRepository.delete(...)} only queued the delete and Hibernate flushed it at commit — outside
 * the caller's {@code try/catch} and outside the method. A failure therefore produced no per-agency
 * log line and took the whole batch down with it.
 *
 * <p>With {@code REQUIRES_NEW} the commit happens when this method returns, so the flush is inside
 * the transaction the caller can observe: a failing agency raises an exception the caller catches
 * and logs, and the agencies already purged stay purged. A self-invocation would bypass the proxy
 * and silently restore the old behaviour, which is why this is not a private method on the service.
 *
 * <p>Departments ({@code agency_topic}) are deliberately <em>not</em> deleted here. {@code
 * Agency.agencyTopics} is mapped {@code cascade = ALL, orphanRemoval = true}, so Hibernate removes
 * them before the parent on its own. An earlier revision added an explicit delete believing the
 * {@code RESTRICT} constraint blocked the purge; a before/after test on PreDev proved it never did.
 * Postcode ranges carry no such cascade and do need the explicit call.
 */
@Component
@RequiredArgsConstructor
public class AgencyPurgeTransaction {

  private final @NonNull AgencyRepository agencyRepository;
  private final @NonNull AgencyPostcodeRangeRepository agencyPostcodeRangeRepository;

  /**
   * Deletes the agency together with the dependencies that JPA does not cascade.
   *
   * @param agency the {@link Agency} to purge
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void purge(Agency agency) {
    agencyPostcodeRangeRepository.deleteAllByAgencyId(agency.getId());
    agencyRepository.delete(agency);
  }
}
