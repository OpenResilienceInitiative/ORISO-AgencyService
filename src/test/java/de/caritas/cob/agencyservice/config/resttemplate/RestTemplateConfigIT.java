package de.caritas.cob.agencyservice.config.resttemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    classes = {RestTemplateConfig.class, RestTemplateAutoConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RestTemplateConfigIT {

  private static final int SERVER_DELAY_MS = 15_000;
  private static final long MAX_ELAPSED_MS = 8_000;

  @Autowired private RestTemplate restTemplate;

  private WireMockServer wireMockServer;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
    wireMockServer.start();
    wireMockServer.stubFor(
        get(urlEqualTo("/slow"))
            .willReturn(
                aResponse().withStatus(200).withFixedDelay(SERVER_DELAY_MS).withBody("ok")));
  }

  @AfterEach
  void tearDown() {
    wireMockServer.stop();
  }

  @Test
  void restTemplate_shouldFailWithinReadTimeoutWhenServerIsSlow() {
    String url = "http://localhost:" + wireMockServer.port() + "/slow";
    long start = System.currentTimeMillis();

    assertThrows(ResourceAccessException.class, () -> restTemplate.getForObject(url, String.class));

    long elapsed = System.currentTimeMillis() - start;
    assertThat(elapsed).isLessThan(MAX_ELAPSED_MS);
  }

  @Test
  void restTemplate_shouldPreserveForbiddenStatusFromDownstream() {
    wireMockServer.stubFor(
        get(urlEqualTo("/forbidden"))
            .willReturn(aResponse().withStatus(HttpStatus.FORBIDDEN.value()).withBody("denied")));
    String url = "http://localhost:" + wireMockServer.port() + "/forbidden";

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> restTemplate.getForObject(url, String.class));

    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
