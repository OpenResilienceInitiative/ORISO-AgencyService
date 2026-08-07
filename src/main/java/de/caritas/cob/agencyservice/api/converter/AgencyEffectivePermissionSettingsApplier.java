package de.caritas.cob.agencyservice.api.converter;

import de.caritas.cob.agencyservice.api.model.AgencyAdminAllowedPermissionToggles;
import de.caritas.cob.agencyservice.api.model.AgencyAdminControls;
import de.caritas.cob.agencyservice.api.model.Settings;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Applies an upper role's effective permission constraints ({@link AgencyAdminControls}) to the
 * public feature flags served to the counselling app. {@code allowedPermissionToggles == false}
 * forces the feature off; {@code enforcedPermissionToggles == true} forces it on; anything else is
 * left as the agency set it. Mirrors ORISO-TenantService's {@code
 * EffectivePermissionSettingsApplier} (ADR-013 P4) at the agency level. This is the server-side
 * single source of truth so the Frontend needs no awareness of the controls (which must not be
 * attached to the public agency response — see the callers of this class).
 */
@Component
public class AgencyEffectivePermissionSettingsApplier {

  private record ToggleBinding(
      Function<AgencyAdminAllowedPermissionToggles, Boolean> getter,
      BiConsumer<Settings, Boolean> setter) {}

  // One binding per conversation/media feature: the toggle getter <-> the Settings feature-flag
  // setter it governs. `appearance` has no Settings counterpart (governs theme customization
  // visibility, not a chat feature flag) and is intentionally not bound here.
  private static final List<ToggleBinding> BINDINGS =
      List.of(
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getAnonymousChat,
              Settings::setFeatureAnonymousChatEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getGroupChat,
              Settings::setFeatureGroupChatV2Enabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getCalls, Settings::setFeatureCallsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getSupervision,
              Settings::setFeatureSupervisionEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getSupervisionAnonymousChats,
              Settings::setFeatureSupervisionAnonymousChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getSupervisionOneOnOneChats,
              Settings::setFeatureSupervisionOneOnOneChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getAudioCalls,
              Settings::setFeatureAudioCallsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getAudioCallsAnonymousChats,
              Settings::setFeatureAudioCallsAnonymousChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getAudioCallsOneOnOneChats,
              Settings::setFeatureAudioCallsOneOnOneChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getAudioCallsGroupChats,
              Settings::setFeatureAudioCallsGroupChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getAudioCallsSupervisionChats,
              Settings::setFeatureAudioCallsSupervisionChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getVideoCalls,
              Settings::setFeatureVideoCallsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getVideoCallsAnonymousChats,
              Settings::setFeatureVideoCallsAnonymousChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getVideoCallsOneOnOneChats,
              Settings::setFeatureVideoCallsOneOnOneChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getVideoCallsGroupChats,
              Settings::setFeatureVideoCallsGroupChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getVideoCallsSupervisionChats,
              Settings::setFeatureVideoCallsSupervisionChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getThreads, Settings::setFeatureThreadsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getThreadsAnonymousChats,
              Settings::setFeatureThreadsAnonymousChatsEnabled),
          new ToggleBinding(
              // Irregular: threadsOneOnOneChats -> featureThreadsOneOnOneEnabled (mirrors
              // ORISO-TenantService's binding).
              AgencyAdminAllowedPermissionToggles::getThreadsOneOnOneChats,
              Settings::setFeatureThreadsOneOnOneEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getThreadsGroupChats,
              Settings::setFeatureThreadsGroupChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getThreadsSupervisionChats,
              Settings::setFeatureThreadsSupervisionChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getVoiceMessages,
              Settings::setFeatureVoiceMessagesEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getVoiceMessagesAnonymousChats,
              Settings::setFeatureVoiceMessagesAnonymousChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getVoiceMessagesOneOnOneChats,
              Settings::setFeatureVoiceMessagesOneOnOneChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getVoiceMessagesGroupChats,
              Settings::setFeatureVoiceMessagesGroupChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getVoiceMessagesSupervisionChats,
              Settings::setFeatureVoiceMessagesSupervisionChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getMediaUpload,
              Settings::setFeatureMediaUploadEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getMediaUploadAnonymousChats,
              Settings::setFeatureMediaUploadAnonymousChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getMediaUploadOneOnOneChats,
              Settings::setFeatureMediaUploadOneOnOneChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getMediaUploadGroupChats,
              Settings::setFeatureMediaUploadGroupChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getMediaUploadSupervisionChats,
              Settings::setFeatureMediaUploadSupervisionChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getMediaInlineDisplay,
              Settings::setFeatureMediaInlineDisplayEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getMediaInlineDisplayAnonymousChats,
              Settings::setFeatureMediaInlineDisplayAnonymousChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getMediaInlineDisplayOneOnOneChats,
              Settings::setFeatureMediaInlineDisplayOneOnOneChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getMediaInlineDisplayGroupChats,
              Settings::setFeatureMediaInlineDisplayGroupChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getMediaInlineDisplaySupervisionChats,
              Settings::setFeatureMediaInlineDisplaySupervisionChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getMediaAiScan,
              Settings::setFeatureMediaAiScanEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getMediaAiScanAnonymousChats,
              Settings::setFeatureMediaAiScanAnonymousChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getMediaAiScanOneOnOneChats,
              Settings::setFeatureMediaAiScanOneOnOneChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getMediaAiScanGroupChats,
              Settings::setFeatureMediaAiScanGroupChatsEnabled),
          new ToggleBinding(
              AgencyAdminAllowedPermissionToggles::getMediaAiScanSupervisionChats,
              Settings::setFeatureMediaAiScanSupervisionChatsEnabled));

  public void applyTo(Settings settings, AgencyAdminControls controls) {
    if (settings == null || controls == null) {
      return;
    }
    AgencyAdminAllowedPermissionToggles allowed = controls.getAllowedPermissionToggles();
    AgencyAdminAllowedPermissionToggles enforced = controls.getEnforcedPermissionToggles();
    for (ToggleBinding binding : BINDINGS) {
      if (allowed != null && Boolean.FALSE.equals(binding.getter().apply(allowed))) {
        binding.setter().accept(settings, false);
      }
      if (enforced != null && Boolean.TRUE.equals(binding.getter().apply(enforced))) {
        binding.setter().accept(settings, true);
      }
    }
  }
}
