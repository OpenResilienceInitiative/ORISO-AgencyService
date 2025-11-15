package de.caritas.cob.agencyservice.api.controller;

import static de.caritas.cob.agencyservice.testHelper.PathConstants.PATH_GET_AGENCIES_WITH_IDS;
import static de.caritas.cob.agencyservice.testHelper.PathConstants.PATH_GET_LIST_OF_AGENCIES;
import static de.caritas.cob.agencyservice.testHelper.PathConstants.PATH_GET_LIST_OF_AGENCIES_BY_TENANT;
import static de.caritas.cob.agencyservice.testHelper.PathConstants.PATH_GET_LIST_OF_AGENCIES_TOPICS;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.AGENCY_ID;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.AGENCY_RESPONSE_DTO;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.AGENCY_RESPONSE_DTO_LIST;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.AGENCY_TOPICS_DTO;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.FULL_AGENCY_RESPONSE_DTO;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.INVALID_AGENCY_ID;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.INVALID_CONSULTING_TYPE_QUERY;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.INVALID_POSTCODE_QUERY;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.VALID_AGE_QUERY;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.VALID_CONSULTING_TYPE_QUERY;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.VALID_POSTCODE_QUERY;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.VALID_TOPIC_ID_QUERY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.api.authorization.RoleAuthorizationAuthorityMapper;
import de.caritas.cob.agencyservice.api.exception.httpresponses.InternalServerErrorException;
import de.caritas.cob.agencyservice.api.model.AgencyTopicsDTO;
import de.caritas.cob.agencyservice.api.model.FullAgencyResponseDTO;
import de.caritas.cob.agencyservice.api.service.AgencyService;
import de.caritas.cob.agencyservice.api.service.LogService;
import de.caritas.cob.agencyservice.api.service.TopicEnrichmentService;
import de.caritas.cob.agencyservice.config.security.AuthorisationService;
import de.caritas.cob.agencyservice.config.security.JwtAuthConverter;
import de.caritas.cob.agencyservice.config.security.JwtAuthConverterProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.hateoas.client.LinkDiscoverers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AgencyController.class)
@AutoConfigureMockMvc(addFilters = false)
class AgencyControllerTest {

  static final String PATH_GET_AGENCIES_BY_CONSULTINGTYPE = "/agencies/consultingtype/1";

  @Autowired
  private MockMvc mvc;

  @MockBean
  private TopicEnrichmentService topicEnrichmentService;

  @MockBean
  private AgencyService agencyService;

  @MockBean
  private LinkDiscoverers linkDiscoverers;

  @MockBean
  private RoleAuthorizationAuthorityMapper roleAuthorizationAuthorityMapper;

  @MockBean
  private JwtAuthConverter jwtAuthConverter;

  @MockBean
  private AuthorisationService authorisationService;

  @MockBean
  private JwtAuthConverterProperties jwtAuthConverterProperties;

  @Mock
  private Logger logger;

  @Test
  void getAgencies_Should_ReturnNoContent_When_ServiceReturnsEmptyList() throws Exception {

    when(agencyService.getAgencies(any(Optional.class), anyInt(), any(Optional.class), any(Optional.class), any(Optional.class), any(Optional.class)))
        .thenReturn(null);

    mvc.perform(
        get(PATH_GET_LIST_OF_AGENCIES + "?" + VALID_POSTCODE_QUERY + "&"
            + VALID_CONSULTING_TYPE_QUERY + "&" + VALID_AGE_QUERY)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());
  }

  @Test
  void getAgencies_Should_ReturnBadRequest_When_PostcodeParamIsInvalid() throws Exception {

    mvc.perform(
        get(PATH_GET_LIST_OF_AGENCIES + "?" + INVALID_POSTCODE_QUERY + "&"
            + VALID_CONSULTING_TYPE_QUERY)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getAgencies_Should_ReturnBadRequest_When_ConsultingTypeParamIsInvalid()
      throws Exception {

    mvc.perform(
        get(PATH_GET_LIST_OF_AGENCIES + "?" + VALID_POSTCODE_QUERY + "&"
            + INVALID_CONSULTING_TYPE_QUERY)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getAgencies_Should_ReturnRespondWith2XXResponseCode_When_PostcodeParamIsNotProvided()
      throws Exception {

    mvc.perform(get(PATH_GET_LIST_OF_AGENCIES + "?" + VALID_CONSULTING_TYPE_QUERY)
        .accept(MediaType.APPLICATION_JSON)).andExpect(status().isNoContent());
  }

  @Test
  void getAgencies_Should_ReturnBadRequest_When_ConsultingTypeParamIsNotProvided()
      throws Exception {

    mvc.perform(get(PATH_GET_LIST_OF_AGENCIES + "?" + VALID_POSTCODE_QUERY)
        .accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
  }

  @Test
  void getAgencies_Should_ReturnListAndOk_When_ServiceReturnsList() throws Exception {

    List<FullAgencyResponseDTO> agencies = new ArrayList<>();
    agencies.add(FULL_AGENCY_RESPONSE_DTO);

    when(agencyService.getAgencies(any(Optional.class), anyInt(), any(Optional.class), any(Optional.class), any(Optional.class), any(Optional.class)))
        .thenReturn(agencies);

    mvc.perform(
        get(PATH_GET_LIST_OF_AGENCIES + "?" + VALID_POSTCODE_QUERY + "&"
            + VALID_CONSULTING_TYPE_QUERY)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("[0].name").value(AGENCY_RESPONSE_DTO.getName()));

    verify(agencyService, atLeastOnce()).getAgencies(any(Optional.class),
        anyInt(), any(Optional.class), any(Optional.class), any(Optional.class), any(Optional.class));
  }

  @Test
  void getAgencies_With_Ids_Should_ReturnNoContent_When_ServiceReturnsNoAgency()
      throws Exception {

    when(agencyService.getAgencies(anyList())).thenReturn(Collections.emptyList());

    mvc.perform(get(PATH_GET_AGENCIES_WITH_IDS + AGENCY_ID).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  void getAgencies_With_Ids_Should_ReturnBadRequest_When_IdInvalid() throws Exception {

    mvc.perform(
        get(PATH_GET_AGENCIES_WITH_IDS + INVALID_AGENCY_ID).contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
  }

  @Test
  void getAgencies_With_Ids_Should_ReturnAgencyAndOk_When_ServiceReturnsAgency()
      throws Exception {

    when(agencyService.getAgencies(anyList())).thenReturn(AGENCY_RESPONSE_DTO_LIST);

    mvc.perform(get(PATH_GET_AGENCIES_WITH_IDS + AGENCY_ID + "," + AGENCY_ID)
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("[0].name").value(AGENCY_RESPONSE_DTO.getName()));

    verify(agencyService, atLeastOnce()).getAgencies(anyList());
  }

  @Test
  void getListOfAgencies_Should_ReturnServerErrorAndLogDatabaseError_When_AgencyServiceThrowsServerErrorException()
      throws Exception {

    InternalServerErrorException dbEx = new InternalServerErrorException(
        LogService::logDatabaseError, "message");
    when(agencyService.getAgencies(any(), anyInt(), any(Optional.class), any(Optional.class), any(Optional.class), any(Optional.class))).thenThrow(dbEx);

    mvc.perform(get(PATH_GET_LIST_OF_AGENCIES + "?" + VALID_POSTCODE_QUERY + "&"
        + VALID_CONSULTING_TYPE_QUERY)
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError());
  }

  @Test
  void getAgencies_With_Ids_Should_ReturnServerErrorAndLogDatabaseError_OnAgencyServiceThrowsServerErrorException()
      throws Exception {

    InternalServerErrorException dbEx = new InternalServerErrorException(
        LogService::logDatabaseError, "message");
    when(agencyService.getAgencies(any())).thenThrow(dbEx);

    mvc.perform(get(PATH_GET_AGENCIES_WITH_IDS + AGENCY_ID + "," + AGENCY_ID)
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError());
  }

  @Test
  void getListOfAgencies_Should_ReturnServerErrorAndLogNumberFormatError_OnAgencyServiceThrowsServerErrorException()
      throws Exception {

    InternalServerErrorException nfEx = new InternalServerErrorException(
        LogService::logNumberFormatException, "message");

    when(agencyService.getAgencies(any(), anyInt(), any(Optional.class), any(Optional.class), any(Optional.class), any(Optional.class))).thenThrow(nfEx);

    mvc.perform(get(PATH_GET_LIST_OF_AGENCIES + "?" + VALID_POSTCODE_QUERY + "&"
        + VALID_CONSULTING_TYPE_QUERY)
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError());

  }

  @Test
  void getAgencies_Should_ReturnBadRequest_When_ConsultingTypeIsNull() throws Exception {
    mvc.perform(
        get(PATH_GET_LIST_OF_AGENCIES + "?" + VALID_POSTCODE_QUERY + "&"
            + "consultingType=").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getAgenciesByConsultingType_Should_ReturnBadRequest_When_consultingTypeIsInvalid()
      throws Exception {
    mvc.perform(
        get(PATH_GET_AGENCIES_BY_CONSULTINGTYPE.replace("1", "invalid"))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getAgenciesByConsultingType_Should_ReturnOk_When_consultingTypeIsValid()
      throws Exception {
    mvc.perform(
        get(PATH_GET_AGENCIES_BY_CONSULTINGTYPE)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  void getAgenciesTopics_Should_ReturnNoContent_When_ServiceReturnsEmptyList() throws Exception {

    when(agencyService.getAgenciesTopics())
        .thenReturn(null);

    mvc.perform(
            get(PATH_GET_LIST_OF_AGENCIES_TOPICS)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());
  }

  @Test
  void getAgenciesTopics_Should_ReturnListAndOk_When_ServiceReturnsList() throws Exception {

    List<AgencyTopicsDTO> agenciesTopics = new ArrayList<>();
    agenciesTopics.add(AGENCY_TOPICS_DTO);

    when(topicEnrichmentService.enrichTopicIdsWithTopicData(anyList()))
        .thenReturn(agenciesTopics);

    mvc.perform(
            get(PATH_GET_LIST_OF_AGENCIES_TOPICS))
        .andExpect(status().isOk());

    verify(topicEnrichmentService, atLeastOnce()).enrichTopicIdsWithTopicData(anyList());
  }

  @Test
  void getTenantAgencies_Should_ReturnNoContent_When_ServiceReturnsEmptyList() throws Exception {

    when(agencyService.getAgencies(anyString(), anyInt()))
        .thenReturn(null);

    mvc.perform(
            get(PATH_GET_LIST_OF_AGENCIES_BY_TENANT + "?" + VALID_POSTCODE_QUERY + "&"
                + VALID_TOPIC_ID_QUERY)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());
  }

  @Test
  void getTenantAgencies_Should_ReturnBadRequest_When_PostcodeParamIsInvalid() throws Exception {

    mvc.perform(
            get(PATH_GET_LIST_OF_AGENCIES_BY_TENANT + "?" + INVALID_POSTCODE_QUERY + "&"
                + VALID_TOPIC_ID_QUERY)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getTenantAgencies_Should_ReturnBadRequest_When_topicIdParamIsNotProvided()
      throws Exception {

    mvc.perform(get(PATH_GET_LIST_OF_AGENCIES_BY_TENANT + "?" + VALID_POSTCODE_QUERY)
        .accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
  }

  @Test
  void getTenantAgencies_Should_ReturnBadRequest_When_PostCodeParamIsNotProvided()
      throws Exception {

    mvc.perform(get(PATH_GET_LIST_OF_AGENCIES_BY_TENANT + "?" + VALID_TOPIC_ID_QUERY)
        .accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
  }

  @Test
  void getTenantAgencies_Should_ReturnListAndOk_When_ServiceReturnsList() throws Exception {

    List<FullAgencyResponseDTO> agencies = new ArrayList<>();
    agencies.add(FULL_AGENCY_RESPONSE_DTO);

    when(agencyService.getAgencies(anyString(), anyInt()))
        .thenReturn(agencies);

    mvc.perform(
            get(PATH_GET_LIST_OF_AGENCIES_BY_TENANT + "?" + VALID_POSTCODE_QUERY + "&"
                + VALID_TOPIC_ID_QUERY)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("[0].name").value(AGENCY_RESPONSE_DTO.getName()));

    verify(agencyService, atLeastOnce()).getAgencies(anyString(), anyInt());
  }

}
