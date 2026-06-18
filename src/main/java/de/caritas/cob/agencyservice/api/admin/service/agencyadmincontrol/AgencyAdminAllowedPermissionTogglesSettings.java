package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgencyAdminAllowedPermissionTogglesSettings {

  private Boolean appearance;
  private Boolean anonymousChat;
  private Boolean calls;
  private Boolean groupChat;
  private Boolean supervision;
  private Boolean supervisionAnonymousChats;
  private Boolean supervisionOneOnOneChats;
  private Boolean audioCalls;
  private Boolean audioCallsAnonymousChats;
  private Boolean audioCallsOneOnOneChats;
  private Boolean audioCallsGroupChats;
  private Boolean audioCallsSupervisionChats;
  private Boolean videoCalls;
  private Boolean videoCallsAnonymousChats;
  private Boolean videoCallsOneOnOneChats;
  private Boolean videoCallsGroupChats;
  private Boolean videoCallsSupervisionChats;
  private Boolean threads;
  private Boolean threadsAnonymousChats;
  private Boolean threadsOneOnOneChats;
  private Boolean threadsGroupChats;
  private Boolean threadsSupervisionChats;
  private Boolean voiceMessages;
  private Boolean voiceMessagesAnonymousChats;
  private Boolean voiceMessagesOneOnOneChats;
  private Boolean voiceMessagesGroupChats;
  private Boolean voiceMessagesSupervisionChats;
}
