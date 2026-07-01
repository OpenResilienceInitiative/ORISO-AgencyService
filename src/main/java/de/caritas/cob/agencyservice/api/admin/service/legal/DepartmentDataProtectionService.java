package de.caritas.cob.agencyservice.api.admin.service.legal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.exception.httpresponses.AgencyAccessDeniedException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.InternalServerErrorException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.api.validation.InputSanitizer;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes a department's ({@code Fachbereich} = agency × topic) own data privacy policy (DPP).
 *
 * <p>This is the AgencyService counterpart to the TenantService DPA publish flow: the admin authors
 * multilingual HTML in the panel's editor; here it is (a) authorised against the caller's agencies
 * so a restricted agency admin cannot write another agency's Fachbereich (IDOR guard), (b) OWASP
 * sanitised per translation, and (c) stored as a JSON language→HTML map in {@code
 * agency_topic.content_dpp} — mirroring how the tenant privacy/DPA content is persisted so the admin
 * UI can reuse the same language-aware viewer.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DepartmentDataProtectionService {

  private final @NonNull AgencyTopicRepository agencyTopicRepository;
  private final @NonNull InputSanitizer inputSanitizer;
  private final @NonNull AuthenticatedUser authenticatedUser;
  private final @NonNull UserAdminService userAdminService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Sanitises and stores the department's DPP for the given agency × topic. When {@code publish} is
   * true the department is marked {@link PublicationStatus#PUBLISHED}, otherwise it is kept as a
   * {@link PublicationStatus#DRAFT} (draft-save).
   *
   * @return the resulting publication status
   */
  @Transactional
  public PublicationStatus publishDepartmentDataPrivacy(
      Long agencyId, Long topicId, Map<String, String> content, boolean publish) {
    assertUserMayEditAgency(agencyId);

    AgencyTopic department =
        agencyTopicRepository
            .findByAgency_IdAndTopicId(agencyId, topicId)
            .orElseThrow(NotFoundException::new);

    department.setContentDpp(toJson(sanitizeTranslations(content)));
    department.setPublicationStatus(
        publish ? PublicationStatus.PUBLISHED : PublicationStatus.DRAFT);
    department.setUpdateDate(LocalDateTime.now());
    agencyTopicRepository.save(department);

    return department.getPublicationStatus();
  }

  private void assertUserMayEditAgency(Long agencyId) {
    if (authenticatedUser.hasRestrictedAgencyPriviliges()) {
      var adminAgencyIds = userAdminService.getAdminUserAgencyIds(authenticatedUser.getUserId());
      if (adminAgencyIds == null || !adminAgencyIds.contains(agencyId)) {
        log.warn(
            "Admin user {} may not edit the data privacy policy of agency {}",
            authenticatedUser.getUserId(),
            agencyId);
        throw new AgencyAccessDeniedException();
      }
    }
  }

  private Map<String, String> sanitizeTranslations(Map<String, String> content) {
    if (content == null) {
      return Map.of();
    }
    return content.entrySet().stream()
        .filter(entry -> entry.getKey() != null)
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry ->
                    inputSanitizer.sanitizeAllowingFormattingAndLinks(
                        entry.getValue() == null ? "" : entry.getValue())));
  }

  private String toJson(Map<String, String> sanitized) {
    try {
      return objectMapper.writeValueAsString(sanitized);
    } catch (JsonProcessingException e) {
      throw new InternalServerErrorException(
          "Could not serialize department data privacy policy content", e);
    }
  }
}
