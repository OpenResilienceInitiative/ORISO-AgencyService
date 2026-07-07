package de.caritas.cob.agencyservice.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.service.securityheader.SecurityHeaderSupplier;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.config.apiclient.TopicServiceApiControllerFactory;
import de.caritas.cob.agencyservice.topicservice.generated.ApiClient;
import de.caritas.cob.agencyservice.topicservice.generated.web.TopicControllerApi;
import de.caritas.cob.agencyservice.topicservice.generated.web.model.TopicDTO;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link TopicService}.
 *
 * <p>{@link TopicService} wraps the generated {@link TopicControllerApi} and is the single point
 * where the outgoing call to the (consulting-)topic service is decorated with the Keycloak bearer
 * token and — when multitenancy is on — the {@code tenantId} header. These tests pin down that
 * header-propagation contract with a real {@link TenantHeaderSupplier} and a real
 * {@link ApiClient} (both {@code @Spy}) so the actual propagation logic runs; only the factory,
 * the generated controller and the auth source are mocked.
 *
 * <p>The endpoint-level auth behaviour (anonymous {@code GET /agencies/topics} must not reach this
 * service and gets a 401 rather than NPE-ing into a 500) is covered by
 * {@link de.caritas.cob.agencyservice.api.controller.AgencyControllerAuthorizationIT}.
 *
 * <p>The {@code @Cacheable} annotation on {@link TopicService#getAllTopics()} is intentionally not
 * asserted here — it is inert without a Spring proxy and belongs to a context-level test.
 */
@ExtendWith(MockitoExtension.class)
class TopicServiceTest {

  private static final String ACCESS_TOKEN = "test-bearer-token";

  @InjectMocks
  TopicService topicService;

  @Mock
  TopicServiceApiControllerFactory topicServiceApiControllerFactory;

  @Mock
  TopicControllerApi topicControllerApi;

  @Mock
  SecurityHeaderSupplier securityHeaderSupplier;

  @Mock
  AuthenticatedUser authenticatedUser;

  @Spy
  TenantHeaderSupplier tenantHeaderSupplier;

  @Spy
  ApiClient apiClient;

  @BeforeEach
  void wireControllerApi() {
    when(topicServiceApiControllerFactory.createControllerApi()).thenReturn(topicControllerApi);
    when(topicControllerApi.getApiClient()).thenReturn(apiClient);
    when(authenticatedUser.getAccessToken()).thenReturn(ACCESS_TOKEN);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void getAllTopics_Should_ReturnTopicListFromDownstreamApiUnchanged() {
    var downstreamTopics = List.of(new TopicDTO().id(1L).name("addiction"),
        new TopicDTO().id(2L).name("debt"));
    when(topicControllerApi.getAllTopics()).thenReturn(downstreamTopics);

    var result = topicService.getAllTopics();

    assertThat(result).isEqualTo(downstreamTopics);
    verify(topicControllerApi).getAllTopics();
  }

  @Test
  void getAllTopics_Should_ReturnEmptyList_When_DownstreamReturnsEmpty() {
    when(topicControllerApi.getAllTopics()).thenReturn(List.of());

    var result = topicService.getAllTopics();

    assertThat(result).isEmpty();
  }

  @Test
  void getAllTopics_Should_AddKeycloakBearerToken_ToApiClientDefaultHeaders() {
    when(topicControllerApi.getAllTopics()).thenReturn(List.of());

    topicService.getAllTopics();

    assertThat(defaultHeaders().get(HttpHeaders.AUTHORIZATION))
        .containsExactly("Bearer " + ACCESS_TOKEN);
  }

  @Test
  void getAllTopics_Should_AddTenantHeader_When_MultitenancyEnabled() {
    TenantContext.setCurrentTenant(1L);
    ReflectionTestUtils.setField(tenantHeaderSupplier, "multitenancy", true);
    when(topicControllerApi.getAllTopics()).thenReturn(List.of());

    topicService.getAllTopics();

    assertThat(defaultHeaders().get("tenantId")).containsExactly("1");
  }

  @Test
  void getAllTopics_Should_NotAddTenantHeader_When_MultitenancyDisabled() {
    TenantContext.setCurrentTenant(1L);
    ReflectionTestUtils.setField(tenantHeaderSupplier, "multitenancy", false);
    when(topicControllerApi.getAllTopics()).thenReturn(List.of());

    topicService.getAllTopics();

    assertThat(defaultHeaders().get("tenantId")).isNull();
  }

  /**
   * {@link SecurityHeaderSupplier} is injected into {@link TopicService} but never used in
   * {@code addDefaultHeaders()} — this call uses the Keycloak bearer token directly, not the CSRF
   * header set. This test documents that the dependency is dead so a future cleanup that removes it
   * has a guard proving no behaviour change. See the cleanup note on issue #75.
   */
  @Test
  void getAllTopics_Should_NotUseSecurityHeaderSupplier() {
    when(topicControllerApi.getAllTopics()).thenReturn(List.of());

    topicService.getAllTopics();

    verifyNoInteractions(securityHeaderSupplier);
  }

  @SuppressWarnings("unchecked")
  private HttpHeaders defaultHeaders() {
    return (HttpHeaders) ReflectionTestUtils.getField(apiClient, "defaultHeaders");
  }
}
