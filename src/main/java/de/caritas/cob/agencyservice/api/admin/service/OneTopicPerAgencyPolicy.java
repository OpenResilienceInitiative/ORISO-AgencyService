package de.caritas.cob.agencyservice.api.admin.service;

import static de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason.ONE_TOPIC_PER_AGENCY;
import static de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason.SETTINGS_UNAVAILABLE;

import de.caritas.cob.agencyservice.api.exception.httpresponses.ConflictException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.ServiceUnavailableException;
import de.caritas.cob.agencyservice.api.service.ApplicationSettingsService;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.ApplicationSettingsDTO;
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
 * Only such topic-adding writes read the switch; if it cannot be read they fail closed (503).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OneTopicPerAgencyPolicy {

  private final @NonNull ApplicationSettingsService applicationSettingsService;

  /**
   * Rejects the write with 409 {@code ONE_TOPIC_PER_AGENCY} if it violates the policy, or with
   * 503 {@code SETTINGS_UNAVAILABLE} if it adds a topic while the switch cannot be read.
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
    ApplicationSettingsDTO settings;
    try {
      // Uncached: the shared settings cache lags up to 600 s behind a switch change.
      settings = applicationSettingsService.fetchApplicationSettings();
    } catch (RestClientException e) {
      log.warn("Could not read oneTopicPerAgencyEnabled; refusing the topic-adding write.", e);
      throw new ServiceUnavailableException(SETTINGS_UNAVAILABLE);
    }
    if (settings == null) {
      throw new ServiceUnavailableException(SETTINGS_UNAVAILABLE);
    }
    return Optional.ofNullable(settings.getOneTopicPerAgencyEnabled())
        .map(FeatureToggleDTO::getValue)
        .orElse(false);
  }
}
