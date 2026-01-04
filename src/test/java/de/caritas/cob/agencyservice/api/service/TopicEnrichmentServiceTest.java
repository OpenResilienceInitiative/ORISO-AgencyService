package de.caritas.cob.agencyservice.api.service;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.model.AgencyTopicsDTO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import de.caritas.cob.agencyservice.topicservice.generated.web.model.TopicDTO;

@ExtendWith(MockitoExtension.class)
class TopicEnrichmentServiceTest {

  @InjectMocks
  TopicEnrichmentService topicEnrichmentService;

  @Mock
  TopicService topicService;

  @Test
  void enrichTopicIdsWithTopicData_Should_EnrichAgencyWithTopicDataFromTopicService() {
    // given
    when(topicService.getAllTopics()).thenReturn(
        newArrayList(new TopicDTO().id(1L).name("first topic").description("desc"),
            new TopicDTO().id(2L).name("second topic").description("desc")));
    List<Integer> topicIds = newArrayList(1, 2);

    // when
    var result = topicEnrichmentService.enrichTopicIdsWithTopicData(topicIds);

    // then
    assertThat(result).isNotEmpty();
    assertThat(result)
        .extracting(AgencyTopicsDTO::getName)
        .contains("first topic", "second topic");
  }


  @Test
  void enrichTopicIdsWithTopicData_Should_ReturnEmptyListIfNoTopicsAreDefined() {
    // given
    when(topicService.getAllTopics()).thenReturn(
        newArrayList());
    List<Integer> topicIds = newArrayList(1, 2);

    // when
    var result = topicEnrichmentService.enrichTopicIdsWithTopicData(topicIds);

    // then
    assertThat(result).isEmpty();
  }


  @Test
  void enrichTopicIdsWithTopicData_Should_ReturnEmptyListIfTopicsListIsNull() {
    // given
    when(topicService.getAllTopics()).thenReturn(
        null);
    List<Integer> topicIds = newArrayList(1, 2);

    // when
    var result = topicEnrichmentService.enrichTopicIdsWithTopicData(topicIds);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void enrichTopicIdsWithTopicData_Should_ReturnEmptyListIfTopicIdDoNotMatch() {
    // given
    when(topicService.getAllTopics()).thenReturn(
        newArrayList(new TopicDTO().id(3L).name("third topic").description("desc"),
            new TopicDTO().id(4L).name("fourth topic").description("desc")));
    List<Integer> topicIds = newArrayList(1, 2);

    // when
    var result = topicEnrichmentService.enrichTopicIdsWithTopicData(topicIds);

    // then
    assertThat(result).isEmpty();
  }
}
