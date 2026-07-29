package de.caritas.cob.agencyservice.api.workflow;

import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.agencypostcoderange.AgencyPostcodeRangeRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purges one agency and its restricting dependencies inside its own transaction.
 *
 * <p>This lives in a separate bean on purpose. The purge previously ran for the whole batch inside
 * one class-level transaction, so {@code agencyRepository.delete(...)} only queued the delete and
 * Hibernate flushed it at commit — outside the caller's {@code try/catch} and outside the method. A
 * constraint violation therefore produced no per-agency log line at all and took the entire batch
 * down with it.
 *
 * <p>With {@code REQUIRES_NEW} the commit happens when this method returns, so the flush is inside
 * the transaction the caller can observe: a failing agency raises an exception the caller catches
 * and logs, and the agencies already purged stay purged. A self-invocation would bypass the proxy
 * and silently restore the old behaviour, which is why this is not a private method on the service.
 */
@Component
@RequiredArgsConstructor
public class AgencyPurgeTransaction {

  private final @NonNull AgencyRepository agencyRepository;
  private final @NonNull AgencyPostcodeRangeRepository agencyPostcodeRangeRepository;
  private final @NonNull AgencyTopicRepository agencyTopicRepository;

  /**
   * Deletes the agency together with every row holding a restricting foreign key on it.
   *
   * @param agency the {@link Agency} to purge
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void purge(Agency agency) {
    var agencyTopics = agencyTopicRepository.findAllByAgencyId(agency.getId());
    agencyTopicRepository.deleteAll(agencyTopics);
    agencyPostcodeRangeRepository.deleteAllByAgencyId(agency.getId());
    agencyRepository.delete(agency);
  }
}
