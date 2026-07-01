package de.caritas.cob.agencyservice.config.resttemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.server.ResponseStatusException;

/**
 * Custom rest template error handler to deal with unexpected client errors.
 */
public class CustomResponseErrorHandler extends DefaultResponseErrorHandler {

  @Override
  public void handleError(URI url, HttpMethod method, ClientHttpResponse response)
      throws IOException {
    String body =
        response.getBody() != null
            ? new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)
            : "";
    throw new ResponseStatusException(
        response.getStatusCode(), method.name() + " " + url.toString() + " " + body);
  }

}
