package de.caritas.cob.agencyservice.api.admin.service;

import static de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason.ONE_TOPIC_PER_AGENCY;

import de.caritas.cob.agencyservice.api.exception.httpresponses.ConflictException;
import de.caritas.cob.agencyservice.api.service.ApplicationSettingsService;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.FeatureToggleDTO;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Platform policy "one topic per agency" (ADR-014 amendment 2026-09-25). The data model stays
 * multi-topic; with the global switch on, a write may not leave an agency with several topics
 * when one of them is new. Legacy multi-topic agencies may keep or shed topics, never gain one.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OneTopicPerAgencyPolicy {

  private final @NonNull ApplicationSettingsService applicationSettingsService;

  /**
   * Rejects the write with 409 {@code ONE_TOPIC_PER_AGENCY} if it violates the policy.
   *
   * @param existingTopicIds  topic ids the agency holds today (empty on create)
   * @param resultingTopicIds topic ids the agency would hold after the write
   */
  public void check(Collection<Long> existingTopicIds, Collection<Long> resultingTopicIds) {
    var resulting = Set.copyOf(resultingTopicIds);
    if (resulting.size() <= 1 || existingTopicIds.containsAll(resulting)) {
      return;
    }
    if (isEnabled()) {
      throw new ConflictException(ONE_TOPIC_PER_AGENCY);
    }
  }

  private boolean isEnabled() {
    try {
      return Optional.ofNullable(
              applicationSettingsService.getApplicationSettings().getOneTopicPerAgencyEnabled())
          .map(FeatureToggleDTO::getValue)
          .orElse(false);
    } catch (RestClientException e) {
      // Fail open: a settings outage must not block agency edits (see the ADR amendment).
      log.warn("Could not read oneTopicPerAgencyEnabled; treating the policy as off.", e);
      return false;
    }
  }
}
