package de.caritas.cob.agencyservice.api.controller;

import static de.caritas.cob.agencyservice.testHelper.PathConstants.PATH_GET_LIST_OF_AGENCIES_TOPICS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.api.service.AgencyService;
import de.caritas.cob.agencyservice.api.service.TopicEnrichmentService;
import jakarta.servlet.http.Cookie;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

@RunWith(SpringRunner.class)
@TestPropertySource(properties = {
    "spring.profiles.active=testing",
    "csrf.header.property=csrfHeader",
    "csrf.cookie.property=csrfCookie"
})
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.ANY)
public class AgencyControllerAuthorizationIT {

  private static final String CSRF_HEADER = "csrfHeader";
  private static final String CSRF_VALUE = "test";
  private static final Cookie CSRF_COOKIE = new Cookie("csrfCookie", CSRF_VALUE);

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private AgencyService agencyService;

  @MockitoBean
  private TopicEnrichmentService topicEnrichmentService;

  @Test
  public void getAgenciesTopics_Should_ReturnUnauthorizedAndCallNoMethods_When_noKeycloakAuthorizationIsPresent()
      throws Exception {

    mvc.perform(get(PATH_GET_LIST_OF_AGENCIES_TOPICS)
        .cookie(CSRF_COOKIE)
        .header(CSRF_HEADER, CSRF_VALUE))
        .andExpect(status().isUnauthorized());

    verifyNoMoreInteractions(this.agencyService);
    verifyNoMoreInteractions(this.topicEnrichmentService);
  }

  @Test
  public void getAgenciesTopics_Should_ReturnNoContentAndCallAgencyService_When_authenticated()
      throws Exception {

    when(agencyService.getAgenciesTopics()).thenReturn(Collections.emptyList());
    when(topicEnrichmentService.enrichTopicIdsWithTopicData(any()))
        .thenReturn(Collections.emptyList());

    mvc.perform(get(PATH_GET_LIST_OF_AGENCIES_TOPICS)
        .with(SecurityMockMvcRequestPostProcessors.jwt())
        .cookie(CSRF_COOKIE)
        .header(CSRF_HEADER, CSRF_VALUE))
        .andExpect(status().isNoContent());

    verify(this.agencyService, times(1)).getAgenciesTopics();
  }

  @Test
  public void getAgencies_Should_RemainPubliclyAccessible_When_noKeycloakAuthorizationIsPresent()
      throws Exception {

    when(agencyService.getAgencies(any(), anyInt(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());

    mvc.perform(get("/agencies?consultingType=1&postcode=10115")
        .cookie(CSRF_COOKIE)
        .header(CSRF_HEADER, CSRF_VALUE))
        .andExpect(status().isOk());

    verify(this.agencyService, times(1))
        .getAgencies(any(), anyInt(), any(), any(), any(), any());
  }
}
