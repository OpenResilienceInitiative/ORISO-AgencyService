package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.RuntimeJsonMappingException;
import de.caritas.cob.agencyservice.api.model.AgencyAdminControls;
import de.caritas.cob.agencyservice.api.model.Settings;
import de.caritas.cob.agencyservice.api.repository.agencyadmincontrol.AgencyAdminControlEntity;
import de.caritas.cob.agencyservice.api.repository.agencyadmincontrol.AgencyAdminControlRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgencyAdminControlsServiceTest {

  @InjectMocks
  private AgencyAdminControlsService agencyAdminControlsService;

  @Mock
  private AgencyAdminControlRepository agencyAdminControlRepository;

  @Mock
  private AgencyAdminControlsConverter agencyAdminControlsConverter;

  private AgencyAdminControls defaultControls;
  private AgencyAdminControlsSettings defaultSettings;

  @BeforeEach
  void setUp() {
    defaultSettings = AgencyAdminControlsSettings.builder().permissionsPageEnabled(true).build();
    defaultControls = new AgencyAdminControls().permissionsPageEnabled(true);
  }

  @Test
  void getControls_Should_ReturnParsedControls_WhenDbHasValidJson() {
    when(agencyAdminControlRepository.findTopByOrderByIdAsc())
        .thenReturn(Optional.of(entityWithControls("{\"permissionsPageEnabled\":false}", 1L)));
    AgencyAdminControls expected =
        new AgencyAdminControls().permissionsPageEnabled(false);
    when(agencyAdminControlsConverter.toAgencyAdminControls(
            argThat(settings -> Boolean.FALSE.equals(settings.getPermissionsPageEnabled()))))
        .thenReturn(expected);

    AgencyAdminControls result = agencyAdminControlsService.getControls();

    assertThat(result).isSameAs(expected);
    verify(agencyAdminControlsConverter, never()).createDefaultControlsSettings();
  }

  @Test
  void getControls_Should_ReturnDefaultControls_WhenDbHasNoRecord() {
    when(agencyAdminControlRepository.findTopByOrderByIdAsc()).thenReturn(Optional.empty());
    stubConverterDefaults();

    AgencyAdminControls result = agencyAdminControlsService.getControls();

    assertThat(result).isSameAs(defaultControls);
    verify(agencyAdminControlsConverter).createDefaultControlsSettings();
  }

  @Test
  void getControls_Should_ReturnDefaultControls_WhenDbHasEmptyStringJson() {
    when(agencyAdminControlRepository.findTopByOrderByIdAsc())
        .thenReturn(Optional.of(entityWithControls("", 1L)));
    stubConverterDefaults();

    AgencyAdminControls result = agencyAdminControlsService.getControls();

    assertThat(result).isSameAs(defaultControls);
    verify(agencyAdminControlsConverter).createDefaultControlsSettings();
  }

  @Test
  void getControls_Should_ReturnDefaultControls_WhenDbHasSpaceJson() {
    when(agencyAdminControlRepository.findTopByOrderByIdAsc())
        .thenReturn(Optional.of(entityWithControls(" ", 1L)));
    stubConverterDefaults();

    AgencyAdminControls result = agencyAdminControlsService.getControls();

    assertThat(result).isSameAs(defaultControls);
    verify(agencyAdminControlsConverter).createDefaultControlsSettings();
  }

  @Test
  void getControls_Should_ReturnDefaultControls_WhenDbHasTabJson() {
    when(agencyAdminControlRepository.findTopByOrderByIdAsc())
        .thenReturn(Optional.of(entityWithControls("\t", 1L)));
    stubConverterDefaults();

    AgencyAdminControls result = agencyAdminControlsService.getControls();

    assertThat(result).isSameAs(defaultControls);
    verify(agencyAdminControlsConverter).createDefaultControlsSettings();
  }

  @Test
  void getControls_Should_ReturnDefaultControls_WhenDbHasEmptyObjectJson() {
    when(agencyAdminControlRepository.findTopByOrderByIdAsc())
        .thenReturn(Optional.of(entityWithControls("{}", 1L)));
    stubConverterDefaults();

    AgencyAdminControls result = agencyAdminControlsService.getControls();

    assertThat(result).isSameAs(defaultControls);
    verify(agencyAdminControlsConverter).createDefaultControlsSettings();
  }

  @Test
  void getControls_Should_ReturnDefaultControls_WhenDbHasWhitespaceWrappedEmptyObjectJson() {
    when(agencyAdminControlRepository.findTopByOrderByIdAsc())
        .thenReturn(Optional.of(entityWithControls(" {} ", 1L)));
    stubConverterDefaults();

    AgencyAdminControls result = agencyAdminControlsService.getControls();

    assertThat(result).isSameAs(defaultControls);
    verify(agencyAdminControlsConverter).createDefaultControlsSettings();
  }

  @Test
  void getControls_Should_ReturnDefaultControls_WhenDbHasNullLiteralJson() {
    when(agencyAdminControlRepository.findTopByOrderByIdAsc())
        .thenReturn(Optional.of(entityWithControls("null", 1L)));
    stubConverterDefaults();

    AgencyAdminControls result = agencyAdminControlsService.getControls();

    assertThat(result).isSameAs(defaultControls);
    verify(agencyAdminControlsConverter).createDefaultControlsSettings();
  }

  @Test
  void getControls_Should_ThrowRuntimeJsonMappingException_WhenDbHasInvalidJson() {
    when(agencyAdminControlRepository.findTopByOrderByIdAsc())
        .thenReturn(Optional.of(entityWithControls("{not-valid-json", 1L)));

    assertThrows(RuntimeJsonMappingException.class, () -> agencyAdminControlsService.getControls());
  }

  @Test
  void updateControls_Should_CreateNewEntity_WhenDbHasNoRecord() {
    AgencyAdminControls input = new AgencyAdminControls().permissionsPageEnabled(false);
    AgencyAdminControlsSettings settings =
        AgencyAdminControlsSettings.builder().permissionsPageEnabled(false).build();
    stubUpdateRoundTrip(input, settings);
    when(agencyAdminControlRepository.findTopByOrderByIdAsc()).thenReturn(Optional.empty());
    when(agencyAdminControlRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    AgencyAdminControls result = agencyAdminControlsService.updateControls(input);

    assertThat(result).isSameAs(input);
    ArgumentCaptor<AgencyAdminControlEntity> captor =
        ArgumentCaptor.forClass(AgencyAdminControlEntity.class);
    verify(agencyAdminControlRepository).save(captor.capture());
    verify(agencyAdminControlRepository, times(1)).findTopByOrderByIdAsc();
    assertThat(captor.getValue().getId()).isNull();
    assertThat(captor.getValue().getControls()).contains("\"permissionsPageEnabled\":false");
  }

  @Test
  void updateControls_Should_UpdateExistingEntity_WhenDbHasRecord() {
    AgencyAdminControls input = new AgencyAdminControls().permissionsPageEnabled(false);
    AgencyAdminControlsSettings settings =
        AgencyAdminControlsSettings.builder().permissionsPageEnabled(false).build();
    stubUpdateRoundTrip(input, settings);
    AgencyAdminControlEntity existing =
        entityWithControls("{\"permissionsPageEnabled\":true}", 1L);
    when(agencyAdminControlRepository.findTopByOrderByIdAsc())
        .thenReturn(Optional.of(existing));
    when(agencyAdminControlRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    AgencyAdminControls result = agencyAdminControlsService.updateControls(input);

    assertThat(result).isSameAs(input);
    ArgumentCaptor<AgencyAdminControlEntity> captor =
        ArgumentCaptor.forClass(AgencyAdminControlEntity.class);
    verify(agencyAdminControlRepository).save(captor.capture());
    verify(agencyAdminControlRepository, times(1)).findTopByOrderByIdAsc();
    assertThat(captor.getValue().getId()).isEqualTo(1L);
    assertThat(captor.getValue().getControls()).contains("\"permissionsPageEnabled\":false");
  }

  @Test
  void updateControls_Should_SetUpdateDateOnSave() {
    AgencyAdminControls input = new AgencyAdminControls().permissionsPageEnabled(false);
    AgencyAdminControlsSettings settings =
        AgencyAdminControlsSettings.builder().permissionsPageEnabled(false).build();
    stubUpdateRoundTrip(input, settings);
    when(agencyAdminControlRepository.findTopByOrderByIdAsc()).thenReturn(Optional.empty());
    when(agencyAdminControlRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
    agencyAdminControlsService.updateControls(input);
    LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);

    ArgumentCaptor<AgencyAdminControlEntity> captor =
        ArgumentCaptor.forClass(AgencyAdminControlEntity.class);
    verify(agencyAdminControlRepository).save(captor.capture());
    assertThat(captor.getValue().getUpdateDate())
        .isAfterOrEqualTo(before)
        .isBeforeOrEqualTo(after);
  }

  @Test
  void enrichSettingsWithAgencyAdminControls_Should_CreateSettingsAndEnrich_WhenSettingsIsNull() {
    when(agencyAdminControlRepository.findTopByOrderByIdAsc()).thenReturn(Optional.empty());
    stubConverterDefaults();

    Settings result = agencyAdminControlsService.enrichSettingsWithAgencyAdminControls(null);

    assertThat(result).isNotNull();
    assertThat(result.getAgencyAdminControls()).isSameAs(defaultControls);
  }

  @Test
  void enrichSettingsWithAgencyAdminControls_Should_EnrichExistingSettings_WhenSettingsIsNotNull() {
    Settings settings = new Settings().featureStatisticsEnabled(true);
    when(agencyAdminControlRepository.findTopByOrderByIdAsc()).thenReturn(Optional.empty());
    stubConverterDefaults();

    Settings result = agencyAdminControlsService.enrichSettingsWithAgencyAdminControls(settings);

    assertSame(settings, result);
    assertThat(result.getFeatureStatisticsEnabled()).isTrue();
    assertThat(result.getAgencyAdminControls()).isSameAs(defaultControls);
  }

  private void stubConverterDefaults() {
    when(agencyAdminControlsConverter.createDefaultControlsSettings()).thenReturn(defaultSettings);
    when(agencyAdminControlsConverter.toAgencyAdminControls(defaultSettings))
        .thenReturn(defaultControls);
  }

  private void stubUpdateRoundTrip(
      AgencyAdminControls input, AgencyAdminControlsSettings settings) {
    when(agencyAdminControlsConverter.toAgencyAdminControlsSettings(input)).thenReturn(settings);
    when(agencyAdminControlsConverter.toAgencyAdminControls(settings)).thenReturn(input);
  }

  private AgencyAdminControlEntity entityWithControls(String controlsJson, Long id) {
    return AgencyAdminControlEntity.builder()
        .id(id)
        .controls(controlsJson)
        .updateDate(LocalDateTime.now(ZoneOffset.UTC))
        .build();
  }
}
