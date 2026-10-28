package de.caritas.cob.agencyservice.api.admin.service.legal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.caritas.cob.agencyservice.api.exception.httpresponses.AgencyAccessDeniedException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.InternalServerErrorException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.service.TenantHeaderSupplier;
import de.caritas.cob.agencyservice.api.service.securityheader.SecurityHeaderSupplier;
import de.caritas.cob.agencyservice.config.apiclient.TenantServiceApiControllerFactory;
import de.caritas.cob.agencyservice.tenantservice.generated.web.TenantControllerApi;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.TenantLegalTextVersionDTO;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * Reads the Träger's own imprint or privacy text from TenantService with the caller's token, so
 * TenantService's own tenant check decides whether this Träger admin may read it.
 */
@Service
@RequiredArgsConstructor
public class TraegerLegalTextClient {

  private static final TypeReference<LinkedHashMap<String, String>> LANGUAGE_MAP =
      new TypeReference<>() {};
  public static final String PUBLISHED_REVISION_PREFIX = "version:";

  private final @NonNull TenantServiceApiControllerFactory tenantServiceApiControllerFactory;
  private final @NonNull SecurityHeaderSupplier securityHeaderSupplier;
  private final @NonNull TenantHeaderSupplier tenantHeaderSupplier;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** One exact Träger revision: the draft's id:version, or "version:&lt;id&gt;" when published. */
  public record TraegerLegalText(
      String revision, Map<String, String> content, Map<String, String> consentText) {}

  public TraegerLegalText draft(Long tenantId, LegalTextKind kind) {
    var draft =
        call(() -> api().getTenantLegalDraft(tenantId, kind == LegalTextKind.DPP ? "PRIVACY" : "IMPRINT"));
    return new TraegerLegalText(
        draft.getRevision(),
        draft.getContent(),
        kind == LegalTextKind.DPP ? draft.getPrivacyConsent() : null);
  }

  /** The wording in force; TenantService keeps no published consent sentence for a Träger. */
  public TraegerLegalText published(Long tenantId, LegalTextKind kind) {
    TenantLegalTextVersionDTO current =
        call(() -> api().getTenantLegalTextVersions(tenantId, kind.name())).stream()
            .filter(version -> version.getSupersededAt() == null)
            .findFirst()
            .orElseThrow(NotFoundException::new);
    return new TraegerLegalText(
        PUBLISHED_REVISION_PREFIX + current.getId(), readMap(current.getContent()), null);
  }

  private TenantControllerApi api() {
    var api = tenantServiceApiControllerFactory.createControllerApi();
    var headers = securityHeaderSupplier.getKeycloakAndCsrfHttpHeaders();
    tenantHeaderSupplier.addTenantHeader(headers);
    headers.forEach((key, value) -> api.getApiClient().addDefaultHeader(key, value.iterator().next()));
    return api;
  }

  private <T> T call(Supplier<T> request) {
    try {
      return request.get();
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
        throw new NotFoundException();
      }
      if (e.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)
          || e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
        throw new AgencyAccessDeniedException();
      }
      throw new InternalServerErrorException("TenantService rejected the legal text read", e);
    } catch (RestClientException e) {
      throw new InternalServerErrorException("Could not read the Träger legal text", e);
    }
  }

  private Map<String, String> readMap(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, LANGUAGE_MAP);
    } catch (JsonProcessingException e) {
      throw new InternalServerErrorException("Could not read the published Träger legal text", e);
    }
  }
}
