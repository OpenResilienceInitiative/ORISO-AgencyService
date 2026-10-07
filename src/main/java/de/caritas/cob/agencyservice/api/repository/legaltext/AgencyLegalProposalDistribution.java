package de.caritas.cob.agencyservice.api.repository.legaltext;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One forwarding request of a Träger, with its own snapshot of the text, so the sent history
 * survives deleted recipients. The request key makes a retried request return this row.
 */
@Entity
@Table(name = "agency_legal_proposal_distribution")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class AgencyLegalProposalDistribution {

  @Id
  @Column(name = "id", length = 36, nullable = false, updatable = false)
  private String id;

  @Column(name = "request_key", length = 128, nullable = false, updatable = false, unique = true)
  private String requestKey;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private Long tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", length = 16, nullable = false, updatable = false)
  private LegalTextKind kind;

  @Enumerated(EnumType.STRING)
  @Column(name = "audience", length = 16, nullable = false, updatable = false)
  private AgencyLegalProposalAudience audience;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", length = 16, nullable = false, updatable = false)
  private AgencyLegalProposalSource source;

  @Column(name = "source_revision", length = 64, nullable = false, updatable = false)
  private String sourceRevision;

  @Column(name = "request_fingerprint", length = 64, nullable = false, updatable = false)
  private String requestFingerprint;

  /** Comma-separated agency ids, fixed at send time (an ALL send does not grow later). */
  @Column(name = "recipient_ids", columnDefinition = "longtext", nullable = false, updatable = false)
  private String recipientIds;

  @Column(name = "content", columnDefinition = "longtext", nullable = false, updatable = false)
  private String content;

  @Column(name = "consent_text", columnDefinition = "longtext", updatable = false)
  private String consentText;

  @Column(name = "created_by", updatable = false)
  private String createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
