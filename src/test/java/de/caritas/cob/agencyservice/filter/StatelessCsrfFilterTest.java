package de.caritas.cob.agencyservice.filter;

import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.filter.StatelessCsrfFilter.DefaultRequiresCsrfMatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class StatelessCsrfFilterTest {

  private static final String CSRF_COOKIE = "csrfCookie";
  private static final String CSRF_HEADER = "csrfHeader";
  private static final String CSRF_TOKEN = "token";

  private final DefaultRequiresCsrfMatcher matcher = new DefaultRequiresCsrfMatcher();

  @Test
  void matches_ShouldRequireCsrf_WhenAgencyAdminMutationIsRequested() {
    MockHttpServletRequest request = request("POST", "/agencyadmin/agencies");

    assertThat(matcher.matches(request)).isTrue();
  }

  @Test
  void matches_ShouldNotRequireCsrf_WhenAgencyAdminSafeMethodIsRequested() {
    MockHttpServletRequest request = request("GET", "/agencyadmin/agencies");

    assertThat(matcher.matches(request)).isFalse();
  }

  @Test
  void matches_ShouldNotBypassCsrf_WhenWhitelistPatternIsOnlyContainedInPath() {
    MockHttpServletRequest request = request("POST", "/foo/internal/agencies/bar");

    assertThat(matcher.matches(request)).isTrue();
  }

  @Test
  void matches_ShouldNotRequireCsrf_WhenWhitelistedPathIsRequested() {
    MockHttpServletRequest request = request("POST", "/swagger-ui/index.html");

    assertThat(matcher.matches(request)).isFalse();
  }

  @Test
  void matches_ShouldMatchWhitelistAgainstPathWithinContext() {
    MockHttpServletRequest request = request("POST", "/agency-service/swagger-ui/index.html");
    request.setContextPath("/agency-service");

    assertThat(matcher.matches(request)).isFalse();
  }

  @Test
  void doFilter_ShouldRejectAgencyAdminMutation_WhenCsrfTokenIsMissing()
      throws ServletException, IOException {
    StatelessCsrfFilter filter = new StatelessCsrfFilter(CSRF_COOKIE, CSRF_HEADER);
    MockHttpServletRequest request = request("POST", "/agencyadmin/agencies");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(filterChain.getRequest()).isNull();
  }

  @Test
  void doFilter_ShouldAllowAgencyAdminMutation_WhenCsrfCookieAndHeaderMatch()
      throws ServletException, IOException {
    StatelessCsrfFilter filter = new StatelessCsrfFilter(CSRF_COOKIE, CSRF_HEADER);
    MockHttpServletRequest request = request("POST", "/agencyadmin/agencies");
    request.addHeader(CSRF_HEADER, CSRF_TOKEN);
    request.setCookies(new Cookie(CSRF_COOKIE, CSRF_TOKEN));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(filterChain.getRequest()).isSameAs(request);
  }

  private MockHttpServletRequest request(String method, String requestUri) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, requestUri);
    request.setRequestURI(requestUri);
    return request;
  }
}
