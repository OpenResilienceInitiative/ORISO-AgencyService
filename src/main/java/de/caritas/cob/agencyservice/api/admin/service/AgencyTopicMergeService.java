package de.caritas.cob.agencyservice.api.admin.service;

import com.google.common.collect.Lists;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AgencyTopicMergeService {

  public List<AgencyTopic> getMergedTopics(Agency agency, List<Long> requestTopicIds) {
    if (requestTopicIds == null || requestTopicIds.isEmpty()) {
      return Lists.newArrayList();
    } else {
      return getMergedTopicsForNonEmptyTopicList(agency, requestTopicIds);
    }
  }

  /**
   * Resolves the agency topics to persist on an <b>update</b>, distinguishing a topic field that
   * was omitted from the request from one that was explicitly cleared. This prevents an update that
   * does not carry topic information (e.g. toggling the agency online, changing the postcode) from
   * silently wiping all existing agency&#8211;topic links.
   *
   * <ul>
   *   <li>{@code requestTopicIds == null} (field omitted) &rarr; keep the existing links.</li>
   *   <li>{@code requestTopicIds} empty (field explicitly cleared) &rarr; remove all links.</li>
   *   <li>{@code requestTopicIds} non-empty &rarr; set exactly to the requested topics.</li>
   * </ul>
   *
   * @param targetAgency    the agency the resulting {@link AgencyTopic}s are attached to
   * @param existingTopics  the agency's current topics, loaded from the database
   * @param requestTopicIds the topic ids from the update request ({@code null} if not provided)
   * @return the list of {@link AgencyTopic}s to persist
   */
  public List<AgencyTopic> getMergedTopicsForUpdate(Agency targetAgency,
      List<AgencyTopic> existingTopics, List<Long> requestTopicIds) {
    if (requestTopicIds == null) {
      return createAgencyTopicList(targetAgency, extractTopicIds(existingTopics));
    }
    return getMergedTopics(targetAgency, requestTopicIds);
  }

  private List<AgencyTopic> getMergedTopicsForNonEmptyTopicList(Agency agency, List<Long> requestTopicIds) {
    List<AgencyTopic> agencyTopics = agency.getAgencyTopics();
    if (agencyTopics != null) {
      return getAgencyTopics(agency, requestTopicIds, agencyTopics);
    } else {
      return createAgencyTopicList(agency, requestTopicIds);
    }
  }

  private List<AgencyTopic> getAgencyTopics(Agency agency, List<Long> requestTopicIds,
      List<AgencyTopic> existingAgencyTopics) {
    var topicsIdsToAdd = getTopicIdsToAdd(requestTopicIds, existingAgencyTopics);
    var topicsToUpdate = existingAgencyTopics.stream()
        .filter(topicWithIdExistInTheRequest(requestTopicIds)).collect(
            Collectors.toList());

    List<AgencyTopic> resultList = Lists.newArrayList();
    resultList.addAll(topicsToUpdate);
    resultList.addAll(createAgencyTopicList(agency, topicsIdsToAdd));
    return resultList;
  }

  private List<Long> getTopicIdsToAdd(List<Long> requestTopicIds, List<AgencyTopic> existingAgencyTopics) {
    return requestTopicIds.stream()
        .filter(topicId -> !extractTopicIds(existingAgencyTopics).contains(topicId)).collect(
            Collectors.toList());
  }

  private List<Long> extractTopicIds(List<AgencyTopic> agencyTopics) {
    if (agencyTopics == null) {
      return Lists.newArrayList();
    }
    return agencyTopics.stream().map(AgencyTopic::getTopicId).collect(Collectors.toList());
  }

  private Predicate<AgencyTopic> topicWithIdExistInTheRequest(List<Long> topicIds) {
    return agencyTopic -> topicIds.contains(agencyTopic.getTopicId());
  }

  private List<AgencyTopic> createAgencyTopicList(Agency agency, List<Long> topicsToAdd) {
    return topicsToAdd.stream().map(topicId -> createNewAgencyTopic(agency, topicId))
        .collect(Collectors.toList());
  }

  private AgencyTopic createNewAgencyTopic(Agency agency, Long topicId) {
    var agencyTopic = new AgencyTopic();
    agencyTopic.setAgency(agency);
    agencyTopic.setTopicId(topicId);
    agencyTopic.setCreateDate(LocalDateTime.now());
    agencyTopic.setUpdateDate(LocalDateTime.now());
    return agencyTopic;
  }
}
