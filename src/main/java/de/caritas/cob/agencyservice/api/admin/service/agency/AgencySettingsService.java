package de.caritas.cob.agencyservice.api.admin.service.agency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.RuntimeJsonMappingException;
import de.caritas.cob.agencyservice.api.model.Settings;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class AgencySettingsService {

  /** Retired key, superseded by the featureMediaUpload* family (ADR-015). */
  private static final String LEGACY_ATTACHMENT_UPLOAD_DISABLED = "featureAttachmentUploadDisabled";

  // Lenient on unknown properties: stored agency settings may still carry keys removed from
  // the schema (e.g. the retired attachment flag); a read must never fail because of them.
  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public Settings toSettings(String settingsJson) {
    if (StringUtils.isBlank(settingsJson)) {
      return new Settings();
    }
    try {
      JsonNode root = objectMapper.readTree(settingsJson);
      Settings settings = objectMapper.treeToValue(root, Settings.class);
      if (settings == null) {
        return new Settings();
      }
      translateLegacyAttachmentUploadDisabled(root, settings);
      return settings;
    } catch (JsonProcessingException exception) {
      throw new RuntimeJsonMappingException(exception.getMessage());
    }
  }

  /**
   * One-time translation of the retired {@code featureAttachmentUploadDisabled} key (ADR-015): a
   * stored {@code true} means the agency had switched uploads off, so absent upload-family keys
   * become {@code false}. Explicitly stored media keys always win; the legacy key itself is never
   * written back (the schema no longer knows it), so it disappears with the next save.
   */
  private void translateLegacyAttachmentUploadDisabled(JsonNode root, Settings settings) {
    if (!root.path(LEGACY_ATTACHMENT_UPLOAD_DISABLED).asBoolean(false)) {
      return;
    }
    if (settings.getFeatureMediaUploadEnabled() == null) {
      settings.setFeatureMediaUploadEnabled(false);
    }
    if (settings.getFeatureMediaUploadAnonymousChatsEnabled() == null) {
      settings.setFeatureMediaUploadAnonymousChatsEnabled(false);
    }
    if (settings.getFeatureMediaUploadOneOnOneChatsEnabled() == null) {
      settings.setFeatureMediaUploadOneOnOneChatsEnabled(false);
    }
    if (settings.getFeatureMediaUploadGroupChatsEnabled() == null) {
      settings.setFeatureMediaUploadGroupChatsEnabled(false);
    }
    if (settings.getFeatureMediaUploadSupervisionChatsEnabled() == null) {
      settings.setFeatureMediaUploadSupervisionChatsEnabled(false);
    }
  }

  public String toSettingsJson(Settings settings) {
    if (settings == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(stripAgencyAdminControls(settings));
    } catch (JsonProcessingException exception) {
      throw new RuntimeJsonMappingException(exception.getMessage());
    }
  }

  private Settings stripAgencyAdminControls(Settings settings) {
    Settings settingsWithoutControls = objectMapper.convertValue(settings, Settings.class);
    settingsWithoutControls.setAgencyAdminControls(null);
    return settingsWithoutControls;
  }
}
