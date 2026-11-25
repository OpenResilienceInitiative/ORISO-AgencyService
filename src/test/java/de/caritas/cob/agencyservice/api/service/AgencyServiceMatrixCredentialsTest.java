package de.caritas.cob.agencyservice.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.admin.service.agency.AgencySettingsService;
import de.caritas.cob.agencyservice.api.admin.service.agency.DemographicsConverter;
import de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol.AgencyAdminControlsService;
import de.caritas.cob.agencyservice.api.manager.consultingtype.ConsultingTypeManager;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.service.matrix.AgencyMatrixPasswordCipher;
import de.caritas.cob.agencyservice.api.service.matrix.MatrixProvisioningService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class AgencyServiceMatrixCredentialsTest {

  private static final String APP_KEY = "test-agency-matrix-encryption-key";

  @InjectMocks private AgencyService agencyService;

  @Mock private ConsultingTypeManager consultingTypeManager;
  @Mock private AgencyRepository agencyRepository;
  @Mock private MatrixProvisioningService matrixProvisioningService;
  @Mock private TenantService tenantService;
  @Mock private DemographicsConverter demographicsConverter;
  @Mock private CentralDataProtectionTemplateService centralDataProtectionTemplateService;
  @Mock private ApplicationSettingsService applicationSettingsService;
  @Mock private AgencySettingsService agencySettingsService;
  @Mock private AgencyAdminControlsService agencyAdminControlsService;
  @Mock private de.caritas.cob.agencyservice.api.service.legal.LegalTextInheritanceResolver legalTextInheritanceResolver;

  @org.mockito.Spy
  private final de.caritas.cob.agencyservice.api.converter.AgencyEffectivePermissionSettingsApplier
      effectivePermissionSettingsApplier =
          new de.caritas.cob.agencyservice.api.converter.AgencyEffectivePermissionSettingsApplier();
  @Spy
  private AgencyMatrixPasswordCipher matrixPasswordCipher =
      new AgencyMatrixPasswordCipher(APP_KEY);

  @Test
  void getMatrixCredentialsShouldNotDecryptStoredPassword() {
    var agency =
        Agency.builder()
            .id(7L)
            .name("Test agency")
            .consultingTypeId(1)
            .matrixUserId("@agency:matrix")
            .build();
    agency.setMatrixPassword(matrixPasswordCipher.encrypt("plain-secret"));
    when(agencyRepository.findById(7L)).thenReturn(Optional.of(agency));

    var credentials = agencyService.getMatrixCredentials(7L).orElseThrow();

    assertThat(credentials.getMatrixUserId()).isEqualTo("@agency:matrix");
    verify(matrixPasswordCipher, never()).decrypt(anyString());
  }

  @Test
  void provisionMatrixCredentialsShouldPersistEncryptedPassword() {
    var agency = Agency.builder().id(9L).name("Test agency").consultingTypeId(1).build();
    when(agencyRepository.findById(9L)).thenReturn(Optional.of(agency));
    when(matrixProvisioningService.ensureAgencyAccount(eq("agency-9"), eq("Test agency")))
        .thenReturn(
            Optional.of(
                new MatrixProvisioningService.MatrixCredentials("@agency:matrix", "new-secret")));
    when(agencyRepository.updateMatrixCredentials(eq(9L), eq("@agency:matrix"), anyString()))
        .thenReturn(1);

    var credentials = agencyService.provisionMatrixCredentials(9L).orElseThrow();

    assertThat(credentials.getMatrixUserId()).isEqualTo("@agency:matrix");
    assertThat(agency.isMatrixAccountProvisioned()).isTrue();
    assertThat(agencyService.provisionMatrixCredentials(9L)).contains(credentials);
    verify(matrixProvisioningService).ensureAgencyAccount("agency-9", "Test agency");
    verify(matrixPasswordCipher, never()).decrypt(anyString());

    var passwordCaptor = ArgumentCaptor.forClass(String.class);
    verify(agencyRepository)
        .updateMatrixCredentials(eq(9L), eq("@agency:matrix"), passwordCaptor.capture());
    assertThat(passwordCaptor.getValue()).startsWith("enc:");
    assertThat(matrixPasswordCipher.decrypt(passwordCaptor.getValue())).isEqualTo("new-secret");
  }

  @Test
  void existingAccountProvisionResponseDoesNotDecryptOrRotatePassword() {
    var agency = Agency.builder().id(7L).name("Synthetic centre").consultingTypeId(1).matrixUserId("@agency:matrix")
        .matrixPassword("enc:unreadable-with-current-key").build();
    when(agencyRepository.findById(7L)).thenReturn(Optional.of(agency));

    assertThat(agencyService.provisionMatrixCredentials(7L).orElseThrow().getMatrixUserId())
        .isEqualTo("@agency:matrix");
    verify(matrixPasswordCipher, never()).decrypt(anyString());
    org.mockito.Mockito.verifyNoInteractions(matrixProvisioningService);
    verify(agencyRepository, never()).updateMatrixCredentials(
        org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString());
  }

  @Test
  void identityReadDoesNotDependOnStoredPasswordOrEncryptionKey() {
    var agency = Agency.builder().id(7L).name("Synthetic centre").consultingTypeId(1).matrixUserId("@agency:matrix")
        .matrixPassword("enc:unreadable-with-current-key").build();
    when(agencyRepository.findById(7L)).thenReturn(Optional.of(agency));
    assertThat(agencyService.getMatrixCredentials(7L).orElseThrow().getMatrixUserId())
        .isEqualTo("@agency:matrix");
    verify(matrixPasswordCipher, never()).decrypt(anyString());
  }

  @Test
  void recordedCompletionWorksAfterReloadWithoutAStoredPassword() {
    var agency = Agency.builder().id(7L).name("Synthetic centre").consultingTypeId(1)
        .matrixUserId("@agency:matrix").matrixAccountProvisioned(true).build();
    when(agencyRepository.findById(7L)).thenReturn(Optional.of(agency));

    assertThat(agencyService.provisionMatrixCredentials(7L).orElseThrow().getMatrixUserId())
        .isEqualTo("@agency:matrix");

    verifyNoInteractions(matrixProvisioningService, matrixPasswordCipher);
  }

  @ParameterizedTest
  @CsvSource({"@agency:matrix,", ", enc:synthetic-retained-password"})
  void pendingLegacyStateDoesNotProvisionOrResetAnAccount(
      String matrixUserId, String matrixPassword) {
    var agency = Agency.builder().id(7L).name("Synthetic centre").consultingTypeId(1)
        .matrixUserId(matrixUserId).matrixPassword(matrixPassword).build();
    when(agencyRepository.findById(7L)).thenReturn(Optional.of(agency));

    assertThat(agencyService.provisionMatrixCredentials(7L)).isEmpty();

    verifyNoInteractions(matrixProvisioningService, matrixPasswordCipher);
  }

  @Test
  void failedLocalWriteDoesNotMarkTheLoadedAgencyAsProvisioned() {
    var agency = Agency.builder().id(9L).name("Synthetic centre").consultingTypeId(1).build();
    when(agencyRepository.findById(9L)).thenReturn(Optional.of(agency));
    when(matrixProvisioningService.ensureAgencyAccount("agency-9", "Synthetic centre"))
        .thenReturn(Optional.of(
            new MatrixProvisioningService.MatrixCredentials("@agency:matrix", "synthetic-password")));
    doThrow(new DataAccessResourceFailureException("Synthetic persistence error"))
        .when(agencyRepository).updateMatrixCredentials(eq(9L), eq("@agency:matrix"), anyString());

    assertThat(agencyService.provisionMatrixCredentials(9L)).isEmpty();

    assertThat(agency.getMatrixUserId()).isNull();
    assertThat(agency.getMatrixPassword()).isNull();
    assertThat(agency.isMatrixAccountProvisioned()).isFalse();
  }

  @Test
  void missingRowWriteDoesNotReportAProvisionedIdentity() {
    var agency = Agency.builder().id(9L).name("Synthetic centre").consultingTypeId(1).build();
    when(agencyRepository.findById(9L)).thenReturn(Optional.of(agency));
    when(matrixProvisioningService.ensureAgencyAccount("agency-9", "Synthetic centre"))
        .thenReturn(Optional.of(
            new MatrixProvisioningService.MatrixCredentials("@agency:matrix", "synthetic-password")));
    when(agencyRepository.updateMatrixCredentials(eq(9L), eq("@agency:matrix"), anyString()))
        .thenReturn(0);

    assertThat(agencyService.provisionMatrixCredentials(9L)).isEmpty();

    assertThat(agency.getMatrixUserId()).isNull();
    assertThat(agency.getMatrixPassword()).isNull();
    assertThat(agency.isMatrixAccountProvisioned()).isFalse();
  }

  @Test
  void missingAgencyHasNoIdentity() {
    when(agencyRepository.findById(7L)).thenReturn(Optional.empty());
    assertThat(agencyService.getMatrixCredentials(7L)).isEmpty();
    org.mockito.Mockito.verifyNoInteractions(matrixPasswordCipher);
  }
}
