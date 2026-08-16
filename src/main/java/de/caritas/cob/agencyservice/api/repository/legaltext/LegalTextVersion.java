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

/**
 * One immutable snapshot of a legal text as it was published (ADR-021 decision 3). Blueprint:
 * ORISO-TenantService's {@code tenant_dpa_version}, generalised over {@link LegalTextKind} and
 * {@link LegalTextLevel} so DPP, imprint and future kinds share one mechanism instead of one
 * special case per document.
 *
 * <p><b>Identity is the surrogate {@link #id}, never {@link #publishedAt}.</b> The AVV work keyed
 * its versions by the activation timestamp and had to truncate it to seconds to survive the
 * MariaDB {@code DATETIME(0)} round trip; every consumer then had to reproduce that truncation
 * exactly or silently miss. Nothing here depends on timestamp equality.
 *
 * <p>Rows are append-only. Republishing sets {@link #supersededAt} on the previous current row and
 * inserts a new one; nothing ever edits a stored wording, because the point of the table is to be
 * able to produce the wording that was in force on a given date.
 */
@Entity
@Table(name = "legal_text_version")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class LegalTextVersion {

  @Id
  @SequenceGenerator(
      name = "legal_text_version_id_seq",
      allocationSize = 1,
      sequenceName = "sequence_legal_text_version")
  @GeneratedValue(
      strategy = GenerationType.SEQUENCE,
      generator = "legal_text_version_id_seq")
  @Column(name = "id", updatable = false, nullable = false)
  private Long id;

  /** Owning Träger, copied from the owner at publish time. {@code null} in single-tenant mode. */
  @Column(name = "tenant_id")
  private Long tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false)
  private LegalTextKind kind;

  @Enumerated(EnumType.STRING)
  @Column(name = "owner_level", nullable = false)
  private LegalTextLevel ownerLevel;

  /**
   * The id of the row this snapshot belongs to, interpreted per {@link #ownerLevel}:
   * {@code agency_topic.id}, {@code agency.id} or {@code legal_text.id}.
   */
  @Column(name = "owner_id", nullable = false)
  private Long ownerId;

  /** The wording as published: the JSON language→HTML map string, stored verbatim. */
  @Column(name = "content", columnDefinition = "longtext")
  private String content;

  @Column(name = "published_at", nullable = false)
  private LocalDateTime publishedAt;

  /**
   * Keycloak user id of the publisher, or {@code null} when the publish had no authenticated user.
   * Never guessed — an unknown publisher is recorded as unknown (#212).
   */
  @Column(name = "published_by")
  private String publishedBy;

  /** {@code null} = the version currently in force for this owner and kind. */
  @Column(name = "superseded_at")
  private LocalDateTime supersededAt;
}
