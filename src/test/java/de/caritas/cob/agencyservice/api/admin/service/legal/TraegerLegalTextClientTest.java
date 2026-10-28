package de.caritas.cob.agencyservice.api.admin.service.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.exception.httpresponses.AgencyAccessDeniedException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.service.TenantHeaderSupplier;
import de.caritas.cob.agencyservice.api.service.securityheader.SecurityHeaderSupplier;
import de.caritas.cob.agencyservice.config.apiclient.TenantServiceApiControllerFactory;
import de.caritas.cob.agencyservice.tenantservice.generated.ApiClient;
import de.caritas.cob.agencyservice.tenantservice.generated.web.TenantControllerApi;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.TenantLegalDraftDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.TenantLegalTextVersionDTO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@ExtendWith(MockitoExtension.class)
class TraegerLegalTextClientTest {

  @Mock private TenantServiceApiControllerFactory factory;
  @Mock private SecurityHeaderSupplier securityHeaderSupplier;
  @Mock private TenantHeaderSupplier tenantHeaderSupplier;
  @Mock private TenantControllerApi api;

  private TraegerLegalTextClient client;

  @BeforeEach
  void setUp() {
    client = new TraegerLegalTextClient(factory, securityHeaderSupplier, tenantHeaderSupplier);
    when(factory.createControllerApi()).thenReturn(api);
    when(api.getApiClient()).thenReturn(mock(ApiClient.class));
    var headers = new HttpHeaders();
    headers.add("Authorization", "Bearer caller");
    when(securityHeaderSupplier.getKeycloakAndCsrfHttpHeaders()).thenReturn(headers);
  }

  @Test
  void draftUsesTheTenantServiceSpellingAndKeepsTheConsentOnlyForThePrivacyPolicy() {
    when(api.getTenantLegalDraft(4L, "PRIVACY"))
        .thenReturn(
            new TenantLegalDraftDTO()
                .revision("12:3")
                .content(Map.of("de", "<p>x</p>"))
                .privacyConsent(Map.of("de", "ok")));

    var text = client.draft(4L, LegalTextKind.DPP);

    assertThat(text.revision()).isEqualTo("12:3");
    assertThat(text.consentText()).containsEntry("de", "ok");
  }

  @Test
  void publishedIsTheVersionInForce() {
    when(api.getTenantLegalTextVersions(4L, "IMPRINT"))
        .thenReturn(
            List.of(
                new TenantLegalTextVersionDTO().id(9L).content("{\"de\":\"<p>live</p>\"}"),
                new TenantLegalTextVersionDTO()
                    .id(8L)
                    .content("{\"de\":\"<p>old</p>\"}")
                    .supersededAt("2026-09-01T10:00:00")));

    var text = client.published(4L, LegalTextKind.IMPRINT);

    assertThat(text.revision()).isEqualTo("version:9");
    assertThat(text.content()).containsEntry("de", "<p>live</p>");
    assertThat(text.consentText()).isNull();
  }

  @Test
  void tenantServiceRefusalsKeepTheirMeaning() {
    when(api.getTenantLegalDraft(4L, "IMPRINT"))
        .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));
    assertThatThrownBy(() -> client.draft(4L, LegalTextKind.IMPRINT))
        .isInstanceOf(NotFoundException.class);

    when(api.getTenantLegalDraft(5L, "IMPRINT"))
        .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> client.draft(5L, LegalTextKind.IMPRINT))
        .isInstanceOf(AgencyAccessDeniedException.class);

    when(api.getTenantLegalTextVersions(4L, "DPP")).thenReturn(List.of());
    assertThatThrownBy(() -> client.published(4L, LegalTextKind.DPP))
        .isInstanceOf(NotFoundException.class);
  }
}
