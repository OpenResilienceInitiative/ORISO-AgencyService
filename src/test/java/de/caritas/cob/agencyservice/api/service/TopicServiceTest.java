package de.caritas.cob.agencyservice.api.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TopicServiceTest {

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

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void getAllTopics_Should_ReturnTopicsFromApi() {
    when(topicServiceApiControllerFactory.createControllerApi()).thenReturn(topicControllerApi);
    when(topicControllerApi.getApiClient()).thenReturn(apiClient);
    when(authenticatedUser.getAccessToken()).thenReturn("test-token");
    when(topicControllerApi.getAllTopics()).thenReturn(List.of(new TopicDTO()));

    var result = topicService.getAllTopics();

    assertEquals(1, result.size());
  }

  @Test
  void getAllTopics_Should_AddAuthorizationHeader() {
    when(authenticatedUser.getAccessToken()).thenReturn("test-bearer-token");
    when(topicServiceApiControllerFactory.createControllerApi()).thenReturn(topicControllerApi);
    when(topicControllerApi.getApiClient()).thenReturn(apiClient);
    when(topicControllerApi.getAllTopics()).thenReturn(List.of());

    topicService.getAllTopics();

    HttpHeaders apiClientHeaders = (HttpHeaders) ReflectionTestUtils
        .getField(apiClient, "defaultHeaders");
    assertEquals("Bearer test-bearer-token", apiClientHeaders.get("Authorization").get(0));
  }

  @Test
  void getAllTopics_Should_AddTenantHeader_When_MultitenancyEnabled() {
    TenantContext.setCurrentTenant(1L);
    ReflectionTestUtils.setField(tenantHeaderSupplier, "multitenancy", true);
    when(authenticatedUser.getAccessToken()).thenReturn("token");
    when(topicServiceApiControllerFactory.createControllerApi()).thenReturn(topicControllerApi);
    when(topicControllerApi.getApiClient()).thenReturn(apiClient);
    when(topicControllerApi.getAllTopics()).thenReturn(List.of());

    topicService.getAllTopics();

    HttpHeaders apiClientHeaders = (HttpHeaders) ReflectionTestUtils
        .getField(apiClient, "defaultHeaders");
    assertEquals("1", apiClientHeaders.get("tenantId").get(0));
  }

  @Test
  void getAllTopics_Should_NotAddTenantHeader_When_MultitenancyDisabled() {
    TenantContext.setCurrentTenant(1L);
    ReflectionTestUtils.setField(tenantHeaderSupplier, "multitenancy", false);
    when(authenticatedUser.getAccessToken()).thenReturn("token");
    when(topicServiceApiControllerFactory.createControllerApi()).thenReturn(topicControllerApi);
    when(topicControllerApi.getApiClient()).thenReturn(apiClient);
    when(topicControllerApi.getAllTopics()).thenReturn(List.of());

    topicService.getAllTopics();

    HttpHeaders apiClientHeaders = (HttpHeaders) ReflectionTestUtils
        .getField(apiClient, "defaultHeaders");
    assertNull(apiClientHeaders.get("tenantId"));
  }

}
