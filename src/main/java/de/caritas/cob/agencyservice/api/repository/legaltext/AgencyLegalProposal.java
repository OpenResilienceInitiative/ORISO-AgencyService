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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One Träger template as it arrived at one Beratungsstelle. The text is an immutable copy; only the
 * decision fields change.
 */
@Entity
@Table(
    name = "agency_legal_proposal",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_agency_legal_proposal_source_recipient",
            columnNames = {"source", "source_revision", "recipient_agency_id"}))
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class AgencyLegalProposal {

  @Id
  @SequenceGenerator(
      name = "agency_legal_proposal_id_seq",
      allocationSize = 1,
      sequenceName = "sequence_agency_legal_proposal")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "agency_legal_proposal_id_seq")
  @Column(name = "id", updatable = false, nullable = false)
  private Long id;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "recipient_agency_id", nullable = false, updatable = false)
  private Long recipientAgencyId;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private Long tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", length = 16, nullable = false, updatable = false)
  private LegalTextKind kind;

  @Column(name = "distribution_id", length = 36, nullable = false, updatable = false)
  private String distributionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "audience", length = 16, nullable = false, updatable = false)
  private AgencyLegalProposalAudience audience;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", length = 16, nullable = false, updatable = false)
  private AgencyLegalProposalSource source;

  @Column(name = "source_revision", length = 64, nullable = false, updatable = false)
  private String sourceRevision;

  @Column(name = "content", columnDefinition = "longtext", nullable = false, updatable = false)
  private String content;

  @Column(name = "consent_text", columnDefinition = "longtext", updatable = false)
  private String consentText;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 16, nullable = false)
  private AgencyLegalProposalStatus status;

  @Column(name = "created_by", updatable = false)
  private String createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "decided_by")
  private String decidedBy;

  @Column(name = "decided_at")
  private LocalDateTime decidedAt;

  @Column(name = "superseded_by_proposal_id")
  private Long supersededByProposalId;

  @Column(name = "superseded_at")
  private LocalDateTime supersededAt;
}
