package de.caritas.cob.agencyservice.api.controller;

import static de.caritas.cob.agencyservice.testHelper.PathConstants.PATH_GET_LIST_OF_AGENCIES_BY_TENANT;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.AGENCY_RESPONSE_DTO;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.FULL_AGENCY_RESPONSE_DTO;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.INVALID_POSTCODE_QUERY;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.VALID_POSTCODE_QUERY;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.VALID_TOPIC_ID_QUERY;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.api.authorization.Authority.AuthorityValue;
import de.caritas.cob.agencyservice.api.model.FullAgencyResponseDTO;
import de.caritas.cob.agencyservice.api.service.AgencyService;
import de.caritas.cob.agencyservice.api.service.TopicEnrichmentService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.liquibase.enabled=false")
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
class AgencyControllerIT {

  @Autowired
  private MockMvc mvc;

  @MockBean
  private TopicEnrichmentService topicEnrichmentService;

  @MockBean
  private AgencyService agencyService;

  @Test
  @WithMockUser(authorities = {AuthorityValue.SEARCH_AGENCIES_WITHIN_TENANT})
  void getTenantAgencies_Should_ReturnNoContent_When_ServiceReturnsEmptyList() throws Exception {

    when(agencyService.getAgencies(Mockito.anyString(), Mockito.anyInt()))
        .thenReturn(null);

    mvc.perform(
            get(PATH_GET_LIST_OF_AGENCIES_BY_TENANT + "?" + VALID_POSTCODE_QUERY + "&"
                + VALID_TOPIC_ID_QUERY)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockUser(authorities = {AuthorityValue.SEARCH_AGENCIES_WITHIN_TENANT})
  void getTenantAgencies_Should_ReturnBadRequest_When_PostcodeParamIsInvalid() throws Exception {

    mvc.perform(
            get(PATH_GET_LIST_OF_AGENCIES_BY_TENANT + "?" + INVALID_POSTCODE_QUERY + "&"
                + VALID_TOPIC_ID_QUERY)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(authorities = {AuthorityValue.SEARCH_AGENCIES_WITHIN_TENANT})
  void getTenantAgencies_Should_ReturnBadRequest_When_topicIdParamIsNotProvided()
      throws Exception {

    mvc.perform(get(PATH_GET_LIST_OF_AGENCIES_BY_TENANT + "?" + VALID_POSTCODE_QUERY)
        .accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(authorities = {AuthorityValue.SEARCH_AGENCIES_WITHIN_TENANT})
  void getTenantAgencies_Should_ReturnBadRequest_When_PostCodeParamIsNotProvided()
      throws Exception {

    mvc.perform(get(PATH_GET_LIST_OF_AGENCIES_BY_TENANT + "?" + VALID_TOPIC_ID_QUERY)
        .accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(authorities = {AuthorityValue.SEARCH_AGENCIES_WITHIN_TENANT})
  void getTenantAgencies_Should_ReturnListAndOk_When_ServiceReturnsList() throws Exception {

    List<FullAgencyResponseDTO> agencies = new ArrayList<>();
    agencies.add(FULL_AGENCY_RESPONSE_DTO);

    when(agencyService.getAgencies(Mockito.anyString(), Mockito.anyInt()))
        .thenReturn(agencies);

    mvc.perform(
            get(PATH_GET_LIST_OF_AGENCIES_BY_TENANT + "?" + VALID_POSTCODE_QUERY + "&"
                + VALID_TOPIC_ID_QUERY)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("[0].name").value(AGENCY_RESPONSE_DTO.getName()));

    verify(agencyService, atLeastOnce()).getAgencies(Mockito.anyString(), Mockito.anyInt());
  }

  @Test
  void getTenantAgencies_Should_ReturnUnauthorized_When_UserHasNoAuthority() throws Exception {

    mvc.perform(get(PATH_GET_LIST_OF_AGENCIES_BY_TENANT + "?" + VALID_TOPIC_ID_QUERY)
        .accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(authorities = {AuthorityValue.SEARCH_AGENCIES})
  void getTenantAgencies_Should_ReturnForbidden_When_UserHasWrongAuthority() throws Exception {

    mvc.perform(get(PATH_GET_LIST_OF_AGENCIES_BY_TENANT + "?" + VALID_TOPIC_ID_QUERY)
        .accept(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden());
  }
}
