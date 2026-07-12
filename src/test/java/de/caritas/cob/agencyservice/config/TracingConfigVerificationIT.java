package de.caritas.cob.agencyservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Regression test for {@link TracingConfig}: without the manually registered Slf4JEventListener
 * bean, Micrometer Tracing does NOT copy traceId/spanId into the MDC on its own (verified
 * empirically - Spring Boot's OpenTelemetry autoconfiguration never wires that listener itself),
 * so log lines would never correlate to traces in SigNoz. This guards against that bean being
 * removed later.
 */
@SpringBootTest(
    properties = {
      "spring.liquibase.enabled=false",
      "management.otlp.tracing.export.enabled=false"
    })
@ActiveProfiles("testing")
class TracingConfigVerificationIT {

  @Autowired private Tracer tracer;

  @Test
  void mdcContainsTraceAndSpanIdWhileSpanIsOpen() {
    assertThat(MDC.get("traceId")).isNull();

    Span span = tracer.nextSpan().name("test-span").start();
    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
      String traceId = MDC.get("traceId");
      String spanId = MDC.get("spanId");
      assertThat(traceId).isNotBlank();
      assertThat(spanId).isNotBlank();
    } finally {
      span.end();
    }

    assertThat(MDC.get("traceId")).isNull();
  }
}
