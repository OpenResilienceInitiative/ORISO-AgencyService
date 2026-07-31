package de.caritas.cob.agencyservice.api.controller;

import static de.caritas.cob.agencyservice.testHelper.PathConstants.PATH_GET_LIST_OF_AGENCIES_TOPICS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.api.service.AgencyService;
import de.caritas.cob.agencyservice.config.apiclient.TopicServiceApiControllerFactory;
import de.caritas.cob.agencyservice.topicservice.generated.ApiClient;
import de.caritas.cob.agencyservice.topicservice.generated.web.TopicControllerApi;
import de.caritas.cob.agencyservice.topicservice.generated.web.model.TopicDTO;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.Before;
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
@TestPropertySource(
    properties = {
      "spring.profiles.active=testing",
      "csrf.header.property=csrfHeader",
      "csrf.cookie.property=csrfCookie"
    })
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.ANY)
public class AgencyTopicsConsultantTokenIT {

  private static final String CSRF_HEADER = "csrfHeader";
  private static final String CSRF_VALUE = "test";
  private static final Cookie CSRF_COOKIE = new Cookie("csrfCookie", CSRF_VALUE);

  @Autowired private MockMvc mvc;

  @MockitoBean private AgencyService agencyService;

  @MockitoBean private TopicServiceApiControllerFactory topicServiceApiControllerFactory;

  @Before
  public void setUp() {
    var topicControllerApi = mock(TopicControllerApi.class);
    when(topicServiceApiControllerFactory.createControllerApi()).thenReturn(topicControllerApi);
    when(topicControllerApi.getApiClient()).thenReturn(new ApiClient());
    when(topicControllerApi.getAllTopics())
        .thenReturn(List.of(new TopicDTO().id(1L).name("Addiction")));
    when(agencyService.getAgenciesTopics()).thenReturn(List.of(1));
  }

  @Test
  public void getAgenciesTopics_Should_Succeed_When_ConsultantTokenHasNoUserIdClaim()
      throws Exception {
    mvc.perform(
            get(PATH_GET_LIST_OF_AGENCIES_TOPICS)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(
                            jwt ->
                                jwt.claim("username", "consultant")
                                    .claim("tenantId", 1L)
                                    .claims(claims -> claims.remove("userId"))))
                .cookie(CSRF_COOKIE)
                .header(CSRF_HEADER, CSRF_VALUE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].name").value("Addiction"));
  }
}
