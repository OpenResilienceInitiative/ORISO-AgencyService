package de.caritas.cob.agencyservice.config;

import io.micrometer.tracing.otel.bridge.Slf4JEventListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer Tracing does not register the SLF4J/OTel bridge automatically: without this bean,
 * traceId/spanId are never copied into the MDC, so log lines can't be correlated to traces in
 * SigNoz. Registering {@link Slf4JEventListener} is picked up by Spring Boot's OpenTelemetry
 * event-publisher wiring and puts the current span's "traceId"/"spanId" into the MDC for the
 * lifetime of the span.
 */
@Configuration
public class TracingConfig {

  @Bean
  public Slf4JEventListener slf4JEventListener() {
    return new Slf4JEventListener();
  }
}
