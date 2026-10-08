package de.caritas.cob.agencyservice.api.repository.legaltext;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An unpublished agency-owned legal document, isolated from all public legal-text state. */
@Entity
@Table(
    name = "agency_legal_draft",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_agency_legal_draft_owner_kind",
            columnNames = {"agency_id", "kind"}))
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class AgencyLegalDraft {

  /** Stable identity for one create lifetime. A discard/recreate always receives a new id. */
  @Id
  @Column(name = "row_id", length = 36, nullable = false, updatable = false)
  private String rowId;

  @Column(name = "agency_id", nullable = false, updatable = false)
  private Long agencyId;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", length = 16, nullable = false, updatable = false)
  private LegalTextKind kind;

  @Column(name = "content", columnDefinition = "longtext", nullable = false)
  private String content;

  @Column(name = "consent_text", columnDefinition = "longtext")
  private String consentText;

  /** Monotonic half of the opaque row-id/version compare-and-swap token. */
  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "saved_at", nullable = false)
  private LocalDateTime savedAt;

  /** The forwarded template this draft was adopted from; a copy, it no longer follows the source. */
  @Column(name = "origin_proposal_id")
  private Long originProposalId;
}
