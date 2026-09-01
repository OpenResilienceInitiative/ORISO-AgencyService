package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.caritas.cob.agencyservice.api.model.AgencyAdminAllowedPermissionToggles;
import de.caritas.cob.agencyservice.api.model.AgencyAdminControls;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.BooleanPermissionPolicy;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.PermissionPolicyMode;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.TenantPermissionPolicies;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Bridges the typed TenantService policy contract to the transition-only legacy control maps. */
@Component
public class AgencyPermissionPolicyMapper {

  private static final Map<String, String> FEATURE_TO_LEGACY_KEY =
      Map.ofEntries(
          Map.entry("appearance", "appearance"),
          Map.entry("featureAnonymousChatEnabled", "anonymousChat"),
          Map.entry("featureGroupChatV2Enabled", "groupChat"),
          Map.entry("featureCallsEnabled", "calls"),
          Map.entry("featureSupervisionEnabled", "supervision"),
          Map.entry("featureSupervisionAnonymousChatsEnabled", "supervisionAnonymousChats"),
          Map.entry("featureSupervisionOneOnOneChatsEnabled", "supervisionOneOnOneChats"),
          Map.entry("featureAudioCallsEnabled", "audioCalls"),
          Map.entry("featureAudioCallsAnonymousChatsEnabled", "audioCallsAnonymousChats"),
          Map.entry("featureAudioCallsOneOnOneChatsEnabled", "audioCallsOneOnOneChats"),
          Map.entry("featureAudioCallsGroupChatsEnabled", "audioCallsGroupChats"),
          Map.entry("featureAudioCallsSupervisionChatsEnabled", "audioCallsSupervisionChats"),
          Map.entry("featureVideoCallsEnabled", "videoCalls"),
          Map.entry("featureVideoCallsAnonymousChatsEnabled", "videoCallsAnonymousChats"),
          Map.entry("featureVideoCallsOneOnOneChatsEnabled", "videoCallsOneOnOneChats"),
          Map.entry("featureVideoCallsGroupChatsEnabled", "videoCallsGroupChats"),
          Map.entry("featureVideoCallsSupervisionChatsEnabled", "videoCallsSupervisionChats"),
          Map.entry("featureThreadsEnabled", "threads"),
          Map.entry("featureThreadsAnonymousChatsEnabled", "threadsAnonymousChats"),
          Map.entry("featureThreadsOneOnOneEnabled", "threadsOneOnOneChats"),
          Map.entry("featureThreadsGroupChatsEnabled", "threadsGroupChats"),
          Map.entry("featureThreadsSupervisionChatsEnabled", "threadsSupervisionChats"),
          Map.entry("featureVoiceMessagesEnabled", "voiceMessages"),
          Map.entry("featureVoiceMessagesAnonymousChatsEnabled", "voiceMessagesAnonymousChats"),
          Map.entry("featureVoiceMessagesOneOnOneChatsEnabled", "voiceMessagesOneOnOneChats"),
          Map.entry("featureVoiceMessagesGroupChatsEnabled", "voiceMessagesGroupChats"),
          Map.entry("featureVoiceMessagesSupervisionChatsEnabled", "voiceMessagesSupervisionChats"),
          Map.entry("featureMediaUploadEnabled", "mediaUpload"),
          Map.entry("featureMediaUploadAnonymousChatsEnabled", "mediaUploadAnonymousChats"),
          Map.entry("featureMediaUploadOneOnOneChatsEnabled", "mediaUploadOneOnOneChats"),
          Map.entry("featureMediaUploadGroupChatsEnabled", "mediaUploadGroupChats"),
          Map.entry("featureMediaUploadSupervisionChatsEnabled", "mediaUploadSupervisionChats"),
          Map.entry("featureMediaInlineDisplayEnabled", "mediaInlineDisplay"),
          Map.entry(
              "featureMediaInlineDisplayAnonymousChatsEnabled",
              "mediaInlineDisplayAnonymousChats"),
          Map.entry(
              "featureMediaInlineDisplayOneOnOneChatsEnabled",
              "mediaInlineDisplayOneOnOneChats"),
          Map.entry("featureMediaInlineDisplayGroupChatsEnabled", "mediaInlineDisplayGroupChats"),
          Map.entry(
              "featureMediaInlineDisplaySupervisionChatsEnabled",
              "mediaInlineDisplaySupervisionChats"),
          Map.entry("featureMediaAiScanEnabled", "mediaAiScan"),
          Map.entry("featureMediaAiScanAnonymousChatsEnabled", "mediaAiScanAnonymousChats"),
          Map.entry("featureMediaAiScanOneOnOneChatsEnabled", "mediaAiScanOneOnOneChats"),
          Map.entry("featureMediaAiScanGroupChatsEnabled", "mediaAiScanGroupChats"),
          Map.entry("featureMediaAiScanSupervisionChatsEnabled", "mediaAiScanSupervisionChats"));

  private final ObjectMapper objectMapper = new ObjectMapper();

  public Set<String> supportedFeatures() {
    return FEATURE_TO_LEGACY_KEY.keySet();
  }

  public AgencyAdminControls toLegacyControls(Map<String, BooleanPermissionPolicy> policies) {
    Map<String, Boolean> allowed = new LinkedHashMap<>();
    Map<String, Boolean> enforced = new LinkedHashMap<>();
    FEATURE_TO_LEGACY_KEY.values().forEach(key -> {
      allowed.put(key, true);
      enforced.put(key, false);
    });
    if (policies != null) {
      policies.forEach(
          (feature, policy) -> {
            String legacyKey = FEATURE_TO_LEGACY_KEY.get(feature);
            if (legacyKey == null || policy == null || policy.getMode() != PermissionPolicyMode.ENFORCED) {
              return;
            }
            if (Boolean.TRUE.equals(policy.getValue())) {
              enforced.put(legacyKey, true);
            } else {
              allowed.put(legacyKey, false);
            }
          });
    }
    return new AgencyAdminControls()
        .permissionsPageEnabled(true)
        .allowedPermissionToggles(
            objectMapper.convertValue(allowed, AgencyAdminAllowedPermissionToggles.class))
        .enforcedPermissionToggles(
            objectMapper.convertValue(enforced, AgencyAdminAllowedPermissionToggles.class));
  }

  public TenantPermissionPolicies applyLegacyUpdate(
      TenantPermissionPolicies existing, AgencyAdminControls controls) {
    Map<String, BooleanPermissionPolicy> updated =
        new LinkedHashMap<>(existing.getPolicies() == null ? Map.of() : existing.getPolicies());
    Map<String, Boolean> allowed = asMap(controls.getAllowedPermissionToggles());
    Map<String, Boolean> enforced = asMap(controls.getEnforcedPermissionToggles());
    FEATURE_TO_LEGACY_KEY.forEach(
        (feature, legacyKey) -> {
          BooleanPermissionPolicy current = updated.get(feature);
          boolean currentValue = current == null || Boolean.TRUE.equals(current.getValue());
          if (Boolean.TRUE.equals(enforced.get(legacyKey))) {
            updated.put(
                feature, policy(true, PermissionPolicyMode.ENFORCED));
          } else if (Boolean.FALSE.equals(allowed.get(legacyKey))) {
            updated.put(
                feature, policy(false, PermissionPolicyMode.ENFORCED));
          } else {
            updated.put(
                feature, policy(currentValue, PermissionPolicyMode.SUGGESTED));
          }
        });
    existing.setPolicies(updated);
    return existing;
  }

  private Map<String, Boolean> asMap(AgencyAdminAllowedPermissionToggles toggles) {
    return toggles == null
        ? Map.of()
        : objectMapper.convertValue(toggles, new TypeReference<Map<String, Boolean>>() {});
  }

  private BooleanPermissionPolicy policy(boolean value, PermissionPolicyMode mode) {
    return new BooleanPermissionPolicy().value(value).mode(mode);
  }
}
