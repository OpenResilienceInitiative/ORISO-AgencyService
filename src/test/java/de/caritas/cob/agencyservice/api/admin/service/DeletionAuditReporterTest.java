package de.caritas.cob.agencyservice.api.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.service.TenantHeaderSupplier;
import de.caritas.cob.agencyservice.api.service.securityheader.SecurityHeaderSupplier;
import de.caritas.cob.agencyservice.config.apiclient.UserAdminServiceApiControllerFactory;
import de.caritas.cob.agencyservice.useradminservice.generated.ApiClient;
import de.caritas.cob.agencyservice.useradminservice.generated.web.DeletionAuditLogsControllerApi;
import de.caritas.cob.agencyservice.useradminservice.generated.web.model.DeletionAuditEntryRequestDTO;
import de.caritas.cob.agencyservice.useradminservice.generated.web.model.DeletionAuditEntryRequestDTO.OutcomeEnum;
import de.caritas.cob.agencyservice.useradminservice.generated.web.model.DeletionAuditEntryRequestDTO.TargetTypeEnum;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class DeletionAuditReporterTest {

  @InjectMocks private DeletionAuditReporter reporter;
  @Mock private UserAdminServiceApiControllerFactory factory;
  @Mock private SecurityHeaderSupplier securityHeaderSupplier;
  @Mock private TenantHeaderSupplier tenantHeaderSupplier;
  @Mock private DeletionAuditLogsControllerApi api;
  @Mock private ApiClient apiClient;

  @BeforeEach
  void wire() {
    lenient().when(factory.createDeletionAuditApi()).thenReturn(api);
    lenient().when(api.getApiClient()).thenReturn(apiClient);
    lenient()
        .when(securityHeaderSupplier.getKeycloakAndCsrfHttpHeaders())
        .thenReturn(new HttpHeaders());
  }

  @Test
  void reportAgencyDeletion_Should_SendOnlyTypeIdTenantAndOutcome() {
    reporter.reportAgency(268L, 4L, DeletionAuditReporter.Outcome.SCHEDULED);

    var captor = ArgumentCaptor.forClass(DeletionAuditEntryRequestDTO.class);
    verify(api).recordDeletion(captor.capture());
    var sent = captor.getValue();
    assertThat(sent.getTargetType()).isEqualTo(TargetTypeEnum.AGENCY);
    assertThat(sent.getTargetId()).isEqualTo("268");
    assertThat(sent.getTenantId()).isEqualTo(4L);
    assertThat(sent.getOutcome()).isEqualTo(OutcomeEnum.SCHEDULED);
    // The caller's own token names the actor; nothing about the actor is put on the wire.
    assertThat(sent.getActorId()).isNull();
    assertThat(sent.getActorRole()).isNull();
  }

  @Test
  void reportAgencyDeletion_Should_NeverFailTheDeletion_WhenTheLogServiceIsDown() {
    doThrow(new RestClientException("userservice down")).when(api).recordDeletion(any());

    assertThatCode(() -> reporter.reportAgency(1L, 1L, DeletionAuditReporter.Outcome.SCHEDULED))
        .doesNotThrowAnyException();
  }

  @Test
  void reportAgencyDeletion_Should_NeverFailTheDeletion_WhenNoClientCanBeBuilt() {
    when(factory.createDeletionAuditApi()).thenThrow(new IllegalStateException("no url"));

    assertThatCode(() -> reporter.reportAgency(1L, 1L, DeletionAuditReporter.Outcome.SCHEDULED))
        .doesNotThrowAnyException();
  }

  @Test
  void request_Should_HoldNoPersonalDataFields() {
    var fields =
        Arrays.stream(DeletionAuditEntryRequestDTO.class.getDeclaredFields())
            .filter(f -> !Modifier.isStatic(f.getModifiers()))
            .map(f -> f.getName())
            .toList();
    assertThat(fields)
        .containsExactlyInAnyOrder(
            "targetType", "targetId", "tenantId", "outcome", "actorId", "actorRole");
  }
}
