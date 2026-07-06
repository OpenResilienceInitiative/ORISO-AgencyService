package de.caritas.cob.agencyservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caching configuration for the agency service.
 *
 * <p>Backed by Caffeine, which is the native cache provider on Spring Boot 4. Previously this class
 * defined only a {@code net.sf.ehcache.CacheManager} (ehcache2) bean and relied on the Spring bridge
 * {@code org.springframework.cache.ehcache.EhCacheCacheManager} to expose it as a Spring
 * {@link CacheManager}. That bridge was removed in Spring Framework 6 (Spring Boot 3+), so under
 * Spring Boot 4 no Spring {@link CacheManager} SPI bean was produced and every {@code @Cacheable}
 * call failed at runtime with {@code NoSuchBeanDefinitionException: No qualifying bean of type
 * 'org.springframework.cache.CacheManager' available}. Caffeine restores a correct SPI bean while
 * preserving every cache name and its time-to-live / time-to-idle / maximum-size semantics.
 *
 * <p>ehcache-to-Caffeine mapping: {@code maxEntriesLocalHeap} -&gt; {@code maximumSize},
 * {@code timeToLiveSeconds} -&gt; {@code expireAfterWrite}, {@code timeToIdleSeconds} -&gt;
 * {@code expireAfterAccess}. A value of {@code 0} for a TTL means "no expiry" in ehcache and is
 * therefore not applied. When {@code eternal=true}, no expiry policy is applied at all.
 */
@Configuration
@EnableCaching
public class CacheManagerConfig {

  public static final String CONSULTING_TYPE_CACHE = "consultingTypeCache";

  public static final String APPLICATION_SETTINGS_CACHE = "applicationSettingsCache";
  public static final String TENANT_CACHE = "tenantCache";
  public static final String TOPICS_CACHE = "topicsCache";

  @Value("${cache.consulting.type.configuration.maxEntriesLocalHeap}")
  private long consultingTypeMaxEntriesLocalHeap;

  @Value("${cache.consulting.type.configuration.eternal}")
  private boolean consultingTypeEternal;

  @Value("${cache.consulting.type.configuration.timeToIdleSeconds}")
  private long consultingTypeTimeToIdleSeconds;

  @Value("${cache.consulting.type.configuration.timeToLiveSeconds}")
  private long consultingTypeTimeToLiveSeconds;

  @Value("${cache.tenant.configuration.maxEntriesLocalHeap}")
  private long tenantMaxEntriesLocalHeap;

  @Value("${cache.tenant.configuration.eternal}")
  private boolean tenantEternal;

  @Value("${cache.tenant.configuration.timeToIdleSeconds}")
  private long tenantTimeToIdleSeconds;

  @Value("${cache.tenant.configuration.timeToLiveSeconds}")
  private long tenantTimeToLiveSeconds;

  @Value("${cache.topic.configuration.maxEntriesLocalHeap}")
  private long topicMaxEntriesLocalHeap;

  @Value("${cache.topic.configuration.eternal}")
  private boolean topicEternal;

  @Value("${cache.topic.configuration.timeToIdleSeconds}")
  private long topicTimeToIdleSeconds;

  @Value("${cache.topic.configuration.timeToLiveSeconds}")
  private long topicTimeToLiveSeconds;

  @Value("${cache.applicationsettings.configuration.maxEntriesLocalHeap}")
  private long applicationSettingsMaxEntriesLocalHeap;

  @Value("${cache.applicationsettings.configuration.eternal}")
  private boolean applicationSettingsEternal;

  @Value("${cache.applicationsettings.configuration.timeToIdleSeconds}")
  private long applicationSettingsTimeToIdleSeconds;

  @Value("${cache.applicationsettings.configuration.timeToLiveSeconds}")
  private long applicationSettingsTimeToLiveSeconds;

  @Bean
  public CacheManager cacheManager() {
    var cacheManager = new CaffeineCacheManager();
    // Restrict to the explicitly registered caches; dynamic creation of unknown cache names is
    // disabled, mirroring the previous ehcache behaviour where only declared caches existed.
    cacheManager.setCacheNames(
        List.of(CONSULTING_TYPE_CACHE, TENANT_CACHE, TOPICS_CACHE, APPLICATION_SETTINGS_CACHE));
    cacheManager.registerCustomCache(
        CONSULTING_TYPE_CACHE,
        buildCache(
                consultingTypeMaxEntriesLocalHeap,
                consultingTypeEternal,
                consultingTypeTimeToIdleSeconds,
                consultingTypeTimeToLiveSeconds)
            .build());
    cacheManager.registerCustomCache(
        TENANT_CACHE,
        buildCache(
                tenantMaxEntriesLocalHeap,
                tenantEternal,
                tenantTimeToIdleSeconds,
                tenantTimeToLiveSeconds)
            .build());
    cacheManager.registerCustomCache(
        TOPICS_CACHE,
        buildCache(
                topicMaxEntriesLocalHeap,
                topicEternal,
                topicTimeToIdleSeconds,
                topicTimeToLiveSeconds)
            .build());
    cacheManager.registerCustomCache(
        APPLICATION_SETTINGS_CACHE,
        buildCache(
                applicationSettingsMaxEntriesLocalHeap,
                applicationSettingsEternal,
                applicationSettingsTimeToIdleSeconds,
                applicationSettingsTimeToLiveSeconds)
            .build());
    return cacheManager;
  }

  /**
   * Builds a Caffeine spec preserving the ehcache semantics: a {@code maxEntriesLocalHeap} bound,
   * and — unless the cache is eternal — a time-to-live ({@code expireAfterWrite}) and a
   * time-to-idle ({@code expireAfterAccess}). A TTL of {@code 0} means "no expiry" in ehcache and
   * is therefore skipped.
   */
  private Caffeine<Object, Object> buildCache(
      long maxEntriesLocalHeap, boolean eternal, long timeToIdleSeconds, long timeToLiveSeconds) {
    var caffeine = Caffeine.newBuilder().maximumSize(maxEntriesLocalHeap);
    if (!eternal) {
      if (timeToLiveSeconds > 0) {
        caffeine.expireAfterWrite(Duration.ofSeconds(timeToLiveSeconds));
      }
      if (timeToIdleSeconds > 0) {
        caffeine.expireAfterAccess(Duration.ofSeconds(timeToIdleSeconds));
      }
    }
    return caffeine;
  }
}
