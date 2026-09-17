package de.caritas.cob.agencyservice.api.repository.legaltext;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence boundary for unpublished agency legal drafts. */
public interface AgencyLegalDraftRepository extends JpaRepository<AgencyLegalDraft, String> {

  Optional<AgencyLegalDraft> findByAgencyIdAndKind(Long agencyId, LegalTextKind kind);

  /** Atomic update: both the create-lifetime row id and its version must still match. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update AgencyLegalDraft draft
         set draft.content = :content,
             draft.consentText = :consentText,
             draft.savedAt = :savedAt,
             draft.version = draft.version + 1
       where draft.agencyId = :agencyId
         and draft.kind = :kind
         and draft.rowId = :rowId
         and draft.version = :version
      """)
  int compareAndSwap(
      @Param("agencyId") Long agencyId,
      @Param("kind") LegalTextKind kind,
      @Param("rowId") String rowId,
      @Param("version") Long version,
      @Param("content") String content,
      @Param("consentText") String consentText,
      @Param("savedAt") LocalDateTime savedAt);

  /** Atomic discard with the same full revision check as an update. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      delete from AgencyLegalDraft draft
       where draft.agencyId = :agencyId
         and draft.kind = :kind
         and draft.rowId = :rowId
         and draft.version = :version
      """)
  int compareAndDelete(
      @Param("agencyId") Long agencyId,
      @Param("kind") LegalTextKind kind,
      @Param("rowId") String rowId,
      @Param("version") Long version);
}
