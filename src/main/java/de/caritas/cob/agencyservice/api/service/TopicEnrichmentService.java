package de.caritas.cob.agencyservice.api.service;

import de.caritas.cob.agencyservice.api.model.AgencyTopicsDTO;
import de.caritas.cob.agencyservice.topicservice.generated.web.model.TopicDTO;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${feature.topics.enabled:true}")
@Slf4j
public class TopicEnrichmentService {

  private final @NonNull TopicService topicService;

  public List<AgencyTopicsDTO> enrichTopicIdsWithTopicData(List<Integer> topicIds) {
    log.debug("Enriching topic ids with topics short titles");
    var availableTopics = topicService.getAllTopics();
    log.debug("Enriching topic ids list with size: {} ", topicIds.size());
    logAvailableTopicsList(availableTopics);
    return enrichTopicIds(availableTopics, topicIds);
  }

  private static void logAvailableTopicsList(List<TopicDTO> availableTopics) {
    if (availableTopics == null) {
      log.debug("Available topics list is null ");
    } else if (availableTopics.isEmpty()) {
      log.debug("Available topics list is empty");
    } else {
      log.debug("Available topics list has size: {} ", availableTopics.size());
    }
  }

  private List<AgencyTopicsDTO> enrichTopicIds(List<TopicDTO> availableTopics, List<Integer> topicIds) {

    if (availableTopics == null) {
      return Collections.emptyList();
    }
    // Create a map of availableTopics to quickly access TopicDTO by id
    Map<Long, String> topicMap = availableTopics.stream()
        .collect(Collectors.toMap(TopicDTO::getId, TopicDTO::getName));

    // Filter and map the topicIds to AgencyTopicsDTOs, using the topicMap for fast lookup
    return topicIds.stream()
        .map(id -> {
          Long topicId = Long.valueOf(id);
          String topicName = topicMap.get(topicId);

          // Only create an AgencyTopicsDTO if the topicName exists
          if (topicName != null) {
            return new AgencyTopicsDTO()
                .id(topicId)
                .name(topicName);
          }
          return null;
        })
        .filter(Objects::nonNull)
        .toList();
  }
}
