package de.caritas.cob.agencyservice.api.service;

import de.caritas.cob.agencyservice.api.service.securityheader.SecurityHeaderSupplier;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.config.CacheManagerConfig;
import de.caritas.cob.agencyservice.config.apiclient.TopicServiceApiControllerFactory;
import de.caritas.cob.agencyservice.topicservice.generated.web.TopicControllerApi;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import de.caritas.cob.agencyservice.topicservice.generated.web.model.TopicDTO;
import de.caritas.cob.agencyservice.topicservice.generated.ApiClient;

@Service
@RequiredArgsConstructor
public class TopicService {

  private final @NonNull TopicServiceApiControllerFactory topicServiceApiControllerFactory;
  private final @NonNull SecurityHeaderSupplier securityHeaderSupplier;
  private final @NonNull TenantHeaderSupplier tenantHeaderSupplier;
  private final @NonNull AuthenticatedUser authenticatedUser;

  /**
   * The topics of the current tenant context. Topics are tenant-scoped in ConsultingTypeService, so
   * the cache key carries the tenant — without it the first tenant to fill the cache answered for
   * every other tenant.
   */
  @Cacheable(cacheNames = CacheManagerConfig.TOPICS_CACHE,
      key = "'current:' + T(de.caritas.cob.agencyservice.api.tenant.TenantContext).getCurrentTenant()")
  public List<TopicDTO> getAllTopics() {
    TopicControllerApi controllerApi = topicServiceApiControllerFactory.createControllerApi();
    addDefaultHeaders(controllerApi.getApiClient());
    return controllerApi.getAllTopics();
  }

  /**
   * The topics of one given tenant, independent of the caller's own tenant — the platform admin
   * (tenant 0) looks at agencies of every tenant (ORISO-Admin#1026 agency search).
   */
  @Cacheable(cacheNames = CacheManagerConfig.TOPICS_CACHE, key = "'tenant:' + #tenantId")
  public List<TopicDTO> getAllTopicsOfTenant(Long tenantId) {
    TopicControllerApi controllerApi = topicServiceApiControllerFactory.createControllerApi();
    var headers = new HttpHeaders();
    headers.add("Authorization", "Bearer " + authenticatedUser.getAccessToken());
    if (tenantId != null) {
      headers.add("tenantId", tenantId.toString());
    }
    headers.forEach(
        (key, value) -> controllerApi.getApiClient().addDefaultHeader(key, value.iterator().next()));
    return controllerApi.getAllTopics();
  }

  private void addDefaultHeaders(ApiClient apiClient) {
    // Send only Keycloak auth header for internal service calls
    var headers = new HttpHeaders();
    headers.add("Authorization", "Bearer " + authenticatedUser.getAccessToken());
    tenantHeaderSupplier.addTenantHeader(headers);
    headers.forEach((key, value) -> apiClient.addDefaultHeader(key, value.iterator().next()));
  }

}
