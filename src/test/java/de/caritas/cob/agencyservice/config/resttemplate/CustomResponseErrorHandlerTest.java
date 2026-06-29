package de.caritas.cob.agencyservice.config.resttemplate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.server.ResponseStatusException;

class CustomResponseErrorHandlerTest {

  CustomResponseErrorHandler customResponseErrorHandler = new CustomResponseErrorHandler();

  @Test
  void handleError_Should_throwExpectedResponseStatusException() throws IOException {
    URI uri = URI.create("/access/endpoint");
    HttpMethod httpMethod = HttpMethod.GET;
    ClientHttpResponse clientHttpResponse = mock(ClientHttpResponse.class);

    when(clientHttpResponse.getStatusCode()).thenReturn(HttpStatus.I_AM_A_TEAPOT);
    when(clientHttpResponse.getBody())
        .thenReturn(new ByteArrayInputStream(new byte[0]));

    try {
      this.customResponseErrorHandler.handleError(uri, httpMethod, clientHttpResponse);
      fail("Exception was not thrown");
    } catch (ResponseStatusException e) {
      assertThat(e.getStatusCode(), is(HttpStatus.I_AM_A_TEAPOT));
      assertThat(e.getReason(), is("GET /access/endpoint "));
      assertThat(e.getMessage(), is("418 I_AM_A_TEAPOT \"GET /access/endpoint \""));
    }
  }

  @Test
  void handleError_Should_includeResponseBodyInReason_When_StatusIs400WithUserInUse()
      throws IOException {
    URI uri = URI.create("/matrix/register");
    HttpMethod httpMethod = HttpMethod.POST;
    ClientHttpResponse clientHttpResponse = mock(ClientHttpResponse.class);
    String matrixBody = "{\"errcode\":\"M_USER_IN_USE\"}";

    when(clientHttpResponse.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
    when(clientHttpResponse.getBody())
        .thenReturn(new ByteArrayInputStream(matrixBody.getBytes(StandardCharsets.UTF_8)));

    try {
      this.customResponseErrorHandler.handleError(uri, httpMethod, clientHttpResponse);
      fail("Exception was not thrown");
    } catch (ResponseStatusException e) {
      assertThat(e.getStatusCode(), is(HttpStatus.BAD_REQUEST));
      assertThat(e.getReason(), is("POST /matrix/register " + matrixBody));
      assertThat(e.getReason(), containsString("M_USER_IN_USE"));
    }
  }
}
