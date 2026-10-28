package de.caritas.cob.agencyservice.api.repository.legaltext;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The complete agency draft an adoption replaced, kept readable afterwards. */
@Entity
@Table(name = "agency_legal_draft_archive")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class AgencyLegalDraftArchive {

  @Id
  @SequenceGenerator(
      name = "agency_legal_draft_archive_id_seq",
      allocationSize = 1,
      sequenceName = "sequence_agency_legal_draft_archive")
  @GeneratedValue(
      strategy = GenerationType.SEQUENCE,
      generator = "agency_legal_draft_archive_id_seq")
  @Column(name = "id", updatable = false, nullable = false)
  private Long id;

  @Column(name = "agency_id", nullable = false, updatable = false)
  private Long agencyId;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", length = 16, nullable = false, updatable = false)
  private LegalTextKind kind;

  @Column(name = "draft_row_id", length = 36, nullable = false, updatable = false)
  private String draftRowId;

  @Column(name = "draft_revision", length = 64, nullable = false, updatable = false)
  private String draftRevision;

  @Column(name = "content", columnDefinition = "longtext", nullable = false, updatable = false)
  private String content;

  @Column(name = "consent_text", columnDefinition = "longtext", updatable = false)
  private String consentText;

  @Column(name = "draft_saved_at", nullable = false, updatable = false)
  private LocalDateTime draftSavedAt;

  @Column(name = "origin_proposal_id", updatable = false)
  private Long originProposalId;

  @Column(name = "replaced_by_proposal_id", nullable = false, updatable = false)
  private Long replacedByProposalId;

  @Column(name = "archived_by", updatable = false)
  private String archivedBy;

  @Column(name = "archived_at", nullable = false, updatable = false)
  private LocalDateTime archivedAt;
}
