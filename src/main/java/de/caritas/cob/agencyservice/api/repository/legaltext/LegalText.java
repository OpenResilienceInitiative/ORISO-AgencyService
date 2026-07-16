package de.caritas.cob.agencyservice.api.repository.legaltext;

import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * A reusable legal text (data privacy policy or Impressum) owned by a Träger (tenant), per
 * ADR-014: departments (Fachbereich = agency × topic) reference one text per kind, and several
 * departments may share the same text — one maintained document instead of N inline copies.
 *
 * <p>{@code content} is a JSON language→HTML map, the same shape the inline
 * {@code agency_topic.content_dpp}/{@code content_imprint} columns use, so the admin editor and the
 * public read side keep working unchanged.
 */
@Entity
@Table(name = "legal_text")
@AllArgsConstructor
@RequiredArgsConstructor
@Getter
@Setter
@Builder
public class LegalText {

  @Id
  @SequenceGenerator(name = "id_seq", allocationSize = 1, sequenceName = "sequence_legal_text")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "id_seq")
  @Column(name = "id", updatable = false, nullable = false)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false)
  private LegalTextKind kind;

  /** Admin-facing name, e.g. "Standard-DSE Träger" — shown in the legal-text library. */
  @Column(name = "label", nullable = false)
  private String label;

  /** JSON language→HTML map, same shape as the former inline department columns. */
  @Column(name = "content")
  private String content;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "publication_status", nullable = false)
  private PublicationStatus publicationStatus = PublicationStatus.DRAFT;

  @Column(name = "create_date")
  private LocalDateTime createDate;

  @Column(name = "update_date")
  private LocalDateTime updateDate;

  /** Mirrors {@code AgencyTopic#applyDefaults}: NOT NULL status survives non-builder paths. */
  @PrePersist
  void applyDefaults() {
    if (publicationStatus == null) {
      publicationStatus = PublicationStatus.DRAFT;
    }
  }
}
