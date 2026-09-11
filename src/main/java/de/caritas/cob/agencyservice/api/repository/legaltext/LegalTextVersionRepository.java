package de.caritas.cob.agencyservice.api.repository.legaltext;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read/append access to the ADR-021 legal-text publication history. */
public interface LegalTextVersionRepository extends JpaRepository<LegalTextVersion, Long> {

  /** The full history of one document, newest first (the order the version picker expects). */
  List<LegalTextVersion> findByOwnerLevelAndOwnerIdAndKindOrderByPublishedAtDescIdDesc(
      LegalTextLevel ownerLevel, Long ownerId, LegalTextKind kind);

  /**
   * The version currently in force for one document, i.e. the one not yet superseded. Ordered by
   * id so a same-second republish (MariaDB {@code datetime} has no sub-second precision) still has
   * a deterministic winner.
   */
  Optional<LegalTextVersion> findFirstByOwnerLevelAndOwnerIdAndKindAndSupersededAtIsNullOrderByIdDesc(
      LegalTextLevel ownerLevel, Long ownerId, LegalTextKind kind);

  /**
   * Every not-yet-superseded row of one document. Normally at most one; the list form exists so
   * superseding is idempotent if a historical bug ever left two rows open.
   */
  List<LegalTextVersion> findByOwnerLevelAndOwnerIdAndKindAndSupersededAtIsNull(
      LegalTextLevel ownerLevel, Long ownerId, LegalTextKind kind);
}
