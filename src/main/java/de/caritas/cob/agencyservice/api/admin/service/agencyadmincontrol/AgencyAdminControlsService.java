package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.RuntimeJsonMappingException;
import de.caritas.cob.agencyservice.api.model.AgencyAdminControls;
import de.caritas.cob.agencyservice.api.model.Settings;
import de.caritas.cob.agencyservice.api.repository.agencyadmincontrol.AgencyAdminControlEntity;
import de.caritas.cob.agencyservice.api.repository.agencyadmincontrol.AgencyAdminControlRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgencyAdminControlsService {

  private final @NonNull AgencyAdminControlRepository agencyAdminControlRepository;
  private final @NonNull AgencyAdminControlsConverter agencyAdminControlsConverter;

  public AgencyAdminControls getControls() {
    return agencyAdminControlsConverter.toAgencyAdminControls(getControlsSettings());
  }

  public AgencyAdminControls updateControls(AgencyAdminControls agencyAdminControls) {
    AgencyAdminControlsSettings controlsSettings =
        agencyAdminControlsConverter.toAgencyAdminControlsSettings(agencyAdminControls);
    saveControlsSettings(controlsSettings);
    return agencyAdminControlsConverter.toAgencyAdminControls(controlsSettings);
  }

  public Settings enrichSettingsWithAgencyAdminControls(Settings settings) {
    Settings enrichedSettings = settings != null ? settings : new Settings();
    enrichedSettings.setAgencyAdminControls(getControls());
    return enrichedSettings;
  }

  private AgencyAdminControlsSettings getControlsSettings() {
    return findExistingControls()
        .map(entity -> parseControlsSettings(entity.getControls()))
        .orElseGet(agencyAdminControlsConverter::createDefaultControlsSettings);
  }

  private void saveControlsSettings(AgencyAdminControlsSettings controlsSettings) {
    AgencyAdminControlEntity entity =
        findExistingControls().orElseGet(AgencyAdminControlEntity::new);
    entity.setControls(serializeControlsSettings(controlsSettings));
    entity.setUpdateDate(LocalDateTime.now(ZoneOffset.UTC));
    agencyAdminControlRepository.save(entity);
  }

  private Optional<AgencyAdminControlEntity> findExistingControls() {
    return agencyAdminControlRepository.findTopByOrderByIdAsc();
  }

  private AgencyAdminControlsSettings parseControlsSettings(String controlsJson) {
    if (StringUtils.isBlank(controlsJson) || "{}".equals(controlsJson.trim())) {
      return agencyAdminControlsConverter.createDefaultControlsSettings();
    }
    try {
      AgencyAdminControlsSettings settings =
          new ObjectMapper().readValue(controlsJson, AgencyAdminControlsSettings.class);
      return settings != null ? settings : agencyAdminControlsConverter.createDefaultControlsSettings();
    } catch (JsonProcessingException exception) {
      throw new RuntimeJsonMappingException(exception.getMessage());
    }
  }

  private String serializeControlsSettings(AgencyAdminControlsSettings controlsSettings) {
    try {
      return new ObjectMapper().writeValueAsString(controlsSettings);
    } catch (JsonProcessingException exception) {
      throw new RuntimeJsonMappingException(exception.getMessage());
    }
  }
}
