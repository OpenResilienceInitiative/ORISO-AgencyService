package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;

import de.caritas.cob.agencyservice.api.model.AgencyAdminAllowedPermissionToggles;
import de.caritas.cob.agencyservice.api.model.AgencyAdminControls;
import org.springframework.stereotype.Component;

@Component
public class AgencyAdminControlsConverter {

  public AgencyAdminControls toAgencyAdminControls(AgencyAdminControlsSettings settings) {
    if (settings == null) {
      return new AgencyAdminControls();
    }
    return new AgencyAdminControls()
        .permissionsPageEnabled(settings.getPermissionsPageEnabled())
        .allowedPermissionToggles(toAllowedPermissionToggles(settings.getAllowedPermissionToggles()))
        .enforcedPermissionToggles(
            toAllowedPermissionToggles(settings.getEnforcedPermissionToggles()));
  }

  public AgencyAdminControlsSettings toAgencyAdminControlsSettings(AgencyAdminControls controls) {
    if (controls == null) {
      return null;
    }
    return AgencyAdminControlsSettings.builder()
        .permissionsPageEnabled(nullAsTrue(controls.getPermissionsPageEnabled()))
        .allowedPermissionToggles(
            toAllowedPermissionTogglesSettings(controls.getAllowedPermissionToggles()))
        .enforcedPermissionToggles(
            toEnforcedPermissionTogglesSettings(controls.getEnforcedPermissionToggles()))
        .build();
  }

  public AgencyAdminControlsSettings createDefaultControlsSettings() {
    return toAgencyAdminControlsSettings(new AgencyAdminControls());
  }

  private Boolean nullAsTrue(Boolean value) {
    return value != null ? value : Boolean.TRUE;
  }

  private Boolean nullAsFalse(Boolean value) {
    return value != null ? value : Boolean.FALSE;
  }

  private AgencyAdminAllowedPermissionToggles toAllowedPermissionToggles(
      AgencyAdminAllowedPermissionTogglesSettings settings) {
    if (settings == null) {
      return null;
    }
    return new AgencyAdminAllowedPermissionToggles()
        .appearance(settings.getAppearance())
        .anonymousChat(settings.getAnonymousChat())
        .calls(settings.getCalls())
        .groupChat(settings.getGroupChat())
        .supervision(settings.getSupervision())
        .supervisionAnonymousChats(settings.getSupervisionAnonymousChats())
        .supervisionOneOnOneChats(settings.getSupervisionOneOnOneChats())
        .audioCalls(settings.getAudioCalls())
        .audioCallsAnonymousChats(settings.getAudioCallsAnonymousChats())
        .audioCallsOneOnOneChats(settings.getAudioCallsOneOnOneChats())
        .audioCallsGroupChats(settings.getAudioCallsGroupChats())
        .audioCallsSupervisionChats(settings.getAudioCallsSupervisionChats())
        .videoCalls(settings.getVideoCalls())
        .videoCallsAnonymousChats(settings.getVideoCallsAnonymousChats())
        .videoCallsOneOnOneChats(settings.getVideoCallsOneOnOneChats())
        .videoCallsGroupChats(settings.getVideoCallsGroupChats())
        .videoCallsSupervisionChats(settings.getVideoCallsSupervisionChats())
        .threads(settings.getThreads())
        .threadsAnonymousChats(settings.getThreadsAnonymousChats())
        .threadsOneOnOneChats(settings.getThreadsOneOnOneChats())
        .threadsGroupChats(settings.getThreadsGroupChats())
        .threadsSupervisionChats(settings.getThreadsSupervisionChats())
        .voiceMessages(settings.getVoiceMessages())
        .voiceMessagesAnonymousChats(settings.getVoiceMessagesAnonymousChats())
        .voiceMessagesOneOnOneChats(settings.getVoiceMessagesOneOnOneChats())
        .voiceMessagesGroupChats(settings.getVoiceMessagesGroupChats())
        .voiceMessagesSupervisionChats(settings.getVoiceMessagesSupervisionChats());
  }

  private AgencyAdminAllowedPermissionTogglesSettings toAllowedPermissionTogglesSettings(
      AgencyAdminAllowedPermissionToggles toggles) {
    if (toggles == null) {
      return null;
    }
    return AgencyAdminAllowedPermissionTogglesSettings.builder()
        .appearance(nullAsTrue(toggles.getAppearance()))
        .anonymousChat(nullAsTrue(toggles.getAnonymousChat()))
        .calls(nullAsTrue(toggles.getCalls()))
        .groupChat(nullAsTrue(toggles.getGroupChat()))
        .supervision(nullAsTrue(toggles.getSupervision()))
        .supervisionAnonymousChats(nullAsTrue(toggles.getSupervisionAnonymousChats()))
        .supervisionOneOnOneChats(nullAsTrue(toggles.getSupervisionOneOnOneChats()))
        .audioCalls(nullAsTrue(toggles.getAudioCalls()))
        .audioCallsAnonymousChats(nullAsTrue(toggles.getAudioCallsAnonymousChats()))
        .audioCallsOneOnOneChats(nullAsTrue(toggles.getAudioCallsOneOnOneChats()))
        .audioCallsGroupChats(nullAsTrue(toggles.getAudioCallsGroupChats()))
        .audioCallsSupervisionChats(nullAsTrue(toggles.getAudioCallsSupervisionChats()))
        .videoCalls(nullAsTrue(toggles.getVideoCalls()))
        .videoCallsAnonymousChats(nullAsTrue(toggles.getVideoCallsAnonymousChats()))
        .videoCallsOneOnOneChats(nullAsTrue(toggles.getVideoCallsOneOnOneChats()))
        .videoCallsGroupChats(nullAsTrue(toggles.getVideoCallsGroupChats()))
        .videoCallsSupervisionChats(nullAsTrue(toggles.getVideoCallsSupervisionChats()))
        .threads(nullAsTrue(toggles.getThreads()))
        .threadsAnonymousChats(nullAsTrue(toggles.getThreadsAnonymousChats()))
        .threadsOneOnOneChats(nullAsTrue(toggles.getThreadsOneOnOneChats()))
        .threadsGroupChats(nullAsTrue(toggles.getThreadsGroupChats()))
        .threadsSupervisionChats(nullAsTrue(toggles.getThreadsSupervisionChats()))
        .voiceMessages(nullAsTrue(toggles.getVoiceMessages()))
        .voiceMessagesAnonymousChats(nullAsTrue(toggles.getVoiceMessagesAnonymousChats()))
        .voiceMessagesOneOnOneChats(nullAsTrue(toggles.getVoiceMessagesOneOnOneChats()))
        .voiceMessagesGroupChats(nullAsTrue(toggles.getVoiceMessagesGroupChats()))
        .voiceMessagesSupervisionChats(nullAsTrue(toggles.getVoiceMessagesSupervisionChats()))
        .build();
  }

  private AgencyAdminAllowedPermissionTogglesSettings toEnforcedPermissionTogglesSettings(
      AgencyAdminAllowedPermissionToggles toggles) {
    if (toggles == null) {
      return null;
    }
    return AgencyAdminAllowedPermissionTogglesSettings.builder()
        .appearance(nullAsFalse(toggles.getAppearance()))
        .anonymousChat(nullAsFalse(toggles.getAnonymousChat()))
        .calls(nullAsFalse(toggles.getCalls()))
        .groupChat(nullAsFalse(toggles.getGroupChat()))
        .supervision(nullAsFalse(toggles.getSupervision()))
        .supervisionAnonymousChats(nullAsFalse(toggles.getSupervisionAnonymousChats()))
        .supervisionOneOnOneChats(nullAsFalse(toggles.getSupervisionOneOnOneChats()))
        .audioCalls(nullAsFalse(toggles.getAudioCalls()))
        .audioCallsAnonymousChats(nullAsFalse(toggles.getAudioCallsAnonymousChats()))
        .audioCallsOneOnOneChats(nullAsFalse(toggles.getAudioCallsOneOnOneChats()))
        .audioCallsGroupChats(nullAsFalse(toggles.getAudioCallsGroupChats()))
        .audioCallsSupervisionChats(nullAsFalse(toggles.getAudioCallsSupervisionChats()))
        .videoCalls(nullAsFalse(toggles.getVideoCalls()))
        .videoCallsAnonymousChats(nullAsFalse(toggles.getVideoCallsAnonymousChats()))
        .videoCallsOneOnOneChats(nullAsFalse(toggles.getVideoCallsOneOnOneChats()))
        .videoCallsGroupChats(nullAsFalse(toggles.getVideoCallsGroupChats()))
        .videoCallsSupervisionChats(nullAsFalse(toggles.getVideoCallsSupervisionChats()))
        .threads(nullAsFalse(toggles.getThreads()))
        .threadsAnonymousChats(nullAsFalse(toggles.getThreadsAnonymousChats()))
        .threadsOneOnOneChats(nullAsFalse(toggles.getThreadsOneOnOneChats()))
        .threadsGroupChats(nullAsFalse(toggles.getThreadsGroupChats()))
        .threadsSupervisionChats(nullAsFalse(toggles.getThreadsSupervisionChats()))
        .voiceMessages(nullAsFalse(toggles.getVoiceMessages()))
        .voiceMessagesAnonymousChats(nullAsFalse(toggles.getVoiceMessagesAnonymousChats()))
        .voiceMessagesOneOnOneChats(nullAsFalse(toggles.getVoiceMessagesOneOnOneChats()))
        .voiceMessagesGroupChats(nullAsFalse(toggles.getVoiceMessagesGroupChats()))
        .voiceMessagesSupervisionChats(nullAsFalse(toggles.getVoiceMessagesSupervisionChats()))
        .build();
  }
}
