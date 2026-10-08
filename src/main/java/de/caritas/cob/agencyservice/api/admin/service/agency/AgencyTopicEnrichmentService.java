package de.caritas.cob.agencyservice.api.admin.service.agency;

import com.google.common.collect.Maps;
import de.caritas.cob.agencyservice.api.model.TopicDTO;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.service.TopicService;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${feature.topics.enabled:true}")
@Slf4j
public class AgencyTopicEnrichmentService {

  private final @NonNull TopicService topicService;

  @Value("${multitenancy.enabled:false}")
  private boolean multitenancy;

  /** Topics are per tenant: resolve them in the agency's tenant, not the caller's. */
  public Agency enrichAgencyWithTopics(Agency agency) {
    log.debug("Enriching agency with topics");
    var availableTopics = toTopicMap(topicsOfTenant(agency.getTenantId()));
    enrichAgency(agency, availableTopics);
    return agency;
  }

  /** Enriches a page of agencies, looking up the topics once per tenant. */
  public void enrichAgenciesWithTopics(List<Agency> agencies) {
    Map<Long, Map<Long, TopicDTO>> topicsByTenant = new HashMap<>();
    Map<Long, TopicDTO> topicsWithoutTenant = null;
    for (Agency agency : agencies) {
      Map<Long, TopicDTO> availableTopics;
      if (agency.getTenantId() == null) {
        if (topicsWithoutTenant == null) {
          topicsWithoutTenant = toTopicMap(topicsOfTenant(null));
        }
        availableTopics = topicsWithoutTenant;
      } else {
        availableTopics = topicsByTenant.computeIfAbsent(
            agency.getTenantId(), tenantId -> toTopicMap(topicsOfTenant(tenantId)));
      }
      enrichAgency(agency, availableTopics);
    }
  }

  /** Best effort: an unreachable ConsultingTypeService yields no topics instead of failing. */
  public List<de.caritas.cob.agencyservice.topicservice.generated.web.model.TopicDTO>
      topicsOfTenant(Long tenantId) {
    try {
      // Only a foreign tenant (the platform admin's view) is asked for explicitly.
      var topics = !multitenancy
          || tenantId == null
          || tenantId.equals(TenantContext.getCurrentTenant())
          ? topicService.getAllTopics()
          : topicService.getAllTopicsOfTenant(tenantId);
      return topics == null ? List.of() : topics;
    } catch (RuntimeException exception) {
      log.warn("Could not load the topics of tenant {}", tenantId, exception);
      return List.of();
    }
  }

  private void enrichAgency(Agency agency, Map<Long, TopicDTO> availableTopics) {
    var agencyTopics = agency.getAgencyTopics();
    if (agencyTopics == null) {
      return;
    }
    log.debug("Enriching agency with {} with information about the topics", agency.getId());
    log.debug("Available topics list has size: {} ", availableTopics.size());
    for (AgencyTopic agencyTopic : agencyTopics) {
      enrichSingleAgencyTopic(availableTopics, agencyTopic);
    }
  }

  private Map<Long, TopicDTO> toTopicMap(
      List<de.caritas.cob.agencyservice.topicservice.generated.web.model.TopicDTO> allTopics) {
    return isEmptyOrNull(allTopics) ? Maps.newHashMap() : getAvailableTopicsMap(allTopics);
  }

  private void enrichSingleAgencyTopic(Map<Long, TopicDTO> availableTopics,
      AgencyTopic agencyTopic) {
    var topicData = availableTopics.get(agencyTopic.getTopicId());
    if (topicData != null) {
      log.debug("Enriching agency with {} with topicData {}", agencyTopic.getAgency(), topicData);
      agencyTopic.setTopicData(topicData);
    } else {
      log.warn("Did not find matching topic for id: {} in the available topic list",
          agencyTopic.getTopicId());
    }
  }

  private Map<Long, TopicDTO> getAvailableTopicsMap(
      List<de.caritas.cob.agencyservice.topicservice.generated.web.model.TopicDTO> allTopics) {
    return allTopics.stream()
        .collect(Collectors.toMap(
            de.caritas.cob.agencyservice.topicservice.generated.web.model.TopicDTO::getId,
            this::convertToAgencyServiceTopicViewDTO,
            (first, duplicate) -> first));
  }

  private boolean isEmptyOrNull(
      List<de.caritas.cob.agencyservice.topicservice.generated.web.model.TopicDTO> allTopics) {
    return allTopics == null || allTopics.isEmpty();
  }

  private TopicDTO convertToAgencyServiceTopicViewDTO(
      de.caritas.cob.agencyservice.topicservice.generated.web.model.TopicDTO source) {
    return new TopicDTO().id(source.getId())
        .name(source.getName())
        .description(source.getDescription())
        .internalIdentifier(source.getInternalIdentifier())
        .status(source.getStatus());
  }
}
