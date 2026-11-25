package de.caritas.cob.agencyservice.filter;

import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.config.CorsConfig;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class TenantContextCleanupFilterTest {

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @Test
  void cleanupRunsBeforeCorsCanEndTheRequest() {
    var cors = new CorsConfig();
    ReflectionTestUtils.setField(cors, "allowedOrigins", new String[] {"https://agency.test"});
    ReflectionTestUtils.setField(cors, "allowedPaths", List.of("/agencies/**"));

    assertThat(OrderUtils.getOrder(TenantContextCleanupFilter.class))
        .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    assertThat(cors.corsFilterRegistrationBean().getOrder())
        .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
  }

  @Test
  void clearsTenantAcrossRequestsWhenSecurityReturnsEarly() throws Exception {
    var filter = new TenantContextCleanupFilter();
    var response = new MockHttpServletResponse();
    TenantContext.setCurrentTenant(99L);

    filter.doFilter(new MockHttpServletRequest(), response, (request, result) -> {
      assertThat(TenantContext.getCurrentTenant()).isNull();
      TenantContext.setCurrentTenant(7L);
      ((HttpServletResponse) result).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    });

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    assertThat(TenantContext.getCurrentTenant()).isNull();

    filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
        (request, result) -> assertThat(TenantContext.getCurrentTenant()).isNull());
  }
}
