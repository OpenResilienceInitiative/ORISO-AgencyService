package de.caritas.cob.agencyservice.api.service;

import de.caritas.cob.agencyservice.api.service.securityheader.AccessTokenSupplier;
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
  private final @NonNull TenantHeaderSupplier tenantHeaderSupplier;
  private final @NonNull AccessTokenSupplier accessTokenSupplier;

  @Cacheable(cacheNames = CacheManagerConfig.TOPICS_CACHE)
  public List<TopicDTO> getAllTopics() {
    TopicControllerApi controllerApi = topicServiceApiControllerFactory.createControllerApi();
    addDefaultHeaders(controllerApi.getApiClient());
    return controllerApi.getAllTopics();
  }

  private void addDefaultHeaders(ApiClient apiClient) {
    // Send only Keycloak auth header for internal service calls
    var headers = new HttpHeaders();
    headers.add("Authorization", "Bearer " + accessTokenSupplier.getAccessToken());
    tenantHeaderSupplier.addTenantHeader(headers);
    headers.forEach((key, value) -> apiClient.addDefaultHeader(key, value.iterator().next()));
  }

}
