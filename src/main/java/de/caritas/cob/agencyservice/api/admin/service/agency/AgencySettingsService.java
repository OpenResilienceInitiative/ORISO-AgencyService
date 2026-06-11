package de.caritas.cob.agencyservice.api.admin.service.agency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.RuntimeJsonMappingException;
import de.caritas.cob.agencyservice.api.model.Settings;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class AgencySettingsService {

  private final ObjectMapper objectMapper = new ObjectMapper();

  public Settings toSettings(String settingsJson) {
    if (StringUtils.isBlank(settingsJson)) {
      return new Settings();
    }
    try {
      Settings settings = objectMapper.readValue(settingsJson, Settings.class);
      return settings != null ? settings : new Settings();
    } catch (JsonProcessingException exception) {
      throw new RuntimeJsonMappingException(exception.getMessage());
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
