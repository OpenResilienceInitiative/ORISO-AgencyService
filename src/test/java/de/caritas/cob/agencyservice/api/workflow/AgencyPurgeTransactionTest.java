package de.caritas.cob.agencyservice.api.workflow;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.agencypostcoderange.AgencyPostcodeRangeRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgencyPurgeTransactionTest {

  @Mock AgencyRepository agencyRepository;

  @Mock AgencyPostcodeRangeRepository agencyPostcodeRangeRepository;

  @Mock AgencyTopicRepository agencyTopicRepository;

  @InjectMocks AgencyPurgeTransaction agencyPurgeTransaction;

  @Test
  void purge_Should_deleteAgencyTopicsAndPostcodeRangesBeforeTheAgency() {
    // given — agency_topic holds a RESTRICT foreign key on agency and was never deleted
    var agency = new Agency();
    agency.setId(268L);
    var agencyTopic = new AgencyTopic();
    when(agencyTopicRepository.findAllByAgencyId(268L))
        .thenReturn(Lists.newArrayList(agencyTopic));

    // when
    agencyPurgeTransaction.purge(agency);

    // then
    var inOrder =
        inOrder(agencyTopicRepository, agencyPostcodeRangeRepository, agencyRepository);
    inOrder.verify(agencyTopicRepository).deleteAll(List.of(agencyTopic));
    inOrder.verify(agencyPostcodeRangeRepository).deleteAllByAgencyId(268L);
    inOrder.verify(agencyRepository).delete(agency);
  }
}
