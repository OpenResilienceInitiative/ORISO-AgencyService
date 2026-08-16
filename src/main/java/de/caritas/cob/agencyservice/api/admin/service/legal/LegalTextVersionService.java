package de.caritas.cob.agencyservice.api.admin.service.legal;

import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextLevel;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextVersion;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextVersionRepository;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write side of the ADR-021 decision 3 publication history: turns "this wording is now in force"
 * into an immutable snapshot.
 *
 * <p>Callers are the publish paths themselves, so this service performs <b>no</b> authorisation of
 * its own — every caller has already run its IDOR and cross-tenant guards, and adding a second,
 * differently-shaped check here would only create a way for the two to disagree.
 *
 * <h2>What counts as a publish</h2>
 *
 * <p>On the department level (level 4) and for the shared ADR-014 objects, a publish is explicit:
 * the admin ticks publish, the row becomes {@code PUBLISHED}, a snapshot is written. A draft-save
 * writes nothing — that is exactly the distinction #212 asks for, and it is why {@code update_date}
 * could never serve as a publication date.
 *
 * <p><b>The agency level (level 3) has no publication status at all</b> — CONTEXT-legal-documents
 * states it as "what is stored, applies". A stored agency-wide text is therefore in force the
 * moment it is saved, and <em>a save is a publish at that level</em>: {@code AgencyAdminService}
 * calls this service on every agency update whose legal content changed. Waiting for a publish flag
 * that the level does not have would leave level 3 permanently historyless.
 *
 * <p>That makes deduplication load-bearing rather than cosmetic: an agency update about opening
 * hours resends the unchanged legal texts, and without {@link #recordPublication} skipping an
 * unchanged wording every such edit would forge a new "version" of a document nobody touched.
 */
@Service
@RequiredArgsConstructor
public class LegalTextVersionService {

  private final @NonNull LegalTextVersionRepository legalTextVersionRepository;
  private final @NonNull AuthenticatedUser authenticatedUser;

  /**
   * Records a published wording, unless it is byte-identical to the version currently in force
   * (see the class comment). Supersedes the previous current version with the same timestamp the
   * new one carries, so the history has no gap and no overlap.
   *
   * <p>ADR-021 decision 4: the consent sentence travels with the policy. Changing only the consent
   * wording is still a new version — its body may be byte-identical, and that is exactly what makes
   * "which consent belonged to which policy" answerable by "the same version" instead of by
   * correlating timestamps.
   *
   * @return the newly written snapshot, or {@link Optional#empty()} when policy and consent wording
   *     were both unchanged, or the policy is empty (an empty document is not a version of
   *     anything)
   */
  @Transactional
  public Optional<LegalTextVersion> recordPublication(
      LegalTextLevel ownerLevel,
      Long ownerId,
      LegalTextKind kind,
      Long tenantId,
      String content,
      String consentText) {
    if (ownerId == null || isBlank(content)) {
      return Optional.empty();
    }
    var open =
        legalTextVersionRepository.findByOwnerLevelAndOwnerIdAndKindAndSupersededAtIsNull(
            ownerLevel, ownerId, kind);
    if (open.stream()
        .anyMatch(
            version ->
                Objects.equals(version.getContent(), content)
                    && Objects.equals(version.getConsentText(), consentText))) {
      return Optional.empty();
    }

    var now = LocalDateTime.now();
    open.forEach(version -> version.setSupersededAt(now));
    legalTextVersionRepository.saveAll(open);

    return Optional.of(
        legalTextVersionRepository.save(
            LegalTextVersion.builder()
                .tenantId(tenantId)
                .kind(kind)
                .ownerLevel(ownerLevel)
                .ownerId(ownerId)
                .content(content)
                .consentText(consentText)
                .publishedAt(now)
                .publishedBy(authenticatedUser.getUserId())
                .build()));
  }

  /** The full history of one document, newest first. */
  @Transactional(readOnly = true)
  public List<LegalTextVersion> listVersions(
      LegalTextLevel ownerLevel, Long ownerId, LegalTextKind kind) {
    return legalTextVersionRepository
        .findByOwnerLevelAndOwnerIdAndKindOrderByPublishedAtDescIdDesc(ownerLevel, ownerId, kind);
  }

  /**
   * An absent document. {@code LegalContentSanitizer} serialises "no translations" to the empty
   * JSON object, so {@code "{}"} is as empty as {@code null} here — snapshotting it would put a
   * blank "version" into a history whose whole purpose is to reproduce real wordings.
   */
  private boolean isBlank(String content) {
    return content == null || content.isBlank() || "{}".equals(content.trim());
  }
}
