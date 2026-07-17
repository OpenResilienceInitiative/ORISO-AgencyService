package de.caritas.cob.agencyservice.api.repository.agencytopic;

import de.caritas.cob.agencyservice.api.model.TopicDTO;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalText;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "agency_topic",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_agency_topic",
            columnNames = {"agency_id", "topic_id"}))
@AllArgsConstructor
@RequiredArgsConstructor
@Getter
@Setter
@Builder
public class AgencyTopic {

  @Id
  @SequenceGenerator(name = "id_seq", allocationSize = 1, sequenceName = "sequence_agency_topic")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "id_seq")
  @Column(name = "id", updatable = false, nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "agency_id", nullable = false)
  private Agency agency;

  @Column(name = "topic_id")
  private Long topicId;

  @Column(name = "create_date")
  private LocalDateTime createDate;

  @Column(name = "update_date")
  private LocalDateTime updateDate;

  /** This department's (Fachbereich) own data privacy policy (Datenschutzerklärung) text. */
  @Column(name = "content_dpp")
  private String contentDpp;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "publication_status", nullable = false)
  private PublicationStatus publicationStatus = PublicationStatus.DRAFT;

  /** This department's (Fachbereich) own imprint (Impressum) text. */
  @Column(name = "content_imprint")
  private String contentImprint;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "publication_status_imprint", nullable = false)
  private PublicationStatus publicationStatusImprint = PublicationStatus.DRAFT;

  /**
   * ADR-014: reference to the shared data privacy policy object. When set it wins over the inline
   * {@link #contentDpp}; several departments may point at the same text. {@code null} = fall back
   * to the inline column (pre-backfill rows) or, if that is empty too, to the tenant-level text.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dpp_id")
  private LegalText dpp;

  /** ADR-014: reference to the shared Impressum object, same semantics as {@link #dpp}. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "imprint_id")
  private LegalText imprint;

  @Transient TopicDTO topicData = new TopicDTO();

  /**
   * Guarantees the NOT NULL publication statuses are never null on the no-arg / all-args
   * construction paths (Lombok's @Builder.Default only applies to the builder).
   */
  @PrePersist
  void applyDefaults() {
    if (publicationStatus == null) {
      publicationStatus = PublicationStatus.DRAFT;
    }
    if (publicationStatusImprint == null) {
      publicationStatusImprint = PublicationStatus.DRAFT;
    }
    validateLegalTextReferences();
  }

  @PreUpdate
  void validateOnUpdate() {
    validateLegalTextReferences();
  }

  /**
   * ADR-014 invariants, enforced at the persistence boundary so no future write path can slip a
   * bad assignment past the service-level guards: the DPP slot only accepts {@code
   * LegalTextKind.DPP} objects, the imprint slot only {@code IMPRINT}, and a department must never
   * reference another Träger's document (checked only when both tenant ids are known —
   * single-tenant mode carries nulls).
   */
  private void validateLegalTextReferences() {
    assertReferenceKind(dpp, LegalTextKind.DPP, "dpp");
    assertReferenceKind(imprint, LegalTextKind.IMPRINT, "imprint");
    assertReferenceTenant(dpp, "dpp");
    assertReferenceTenant(imprint, "imprint");
  }

  private void assertReferenceKind(
      LegalText reference, LegalTextKind expectedKind, String slot) {
    if (reference != null && reference.getKind() != expectedKind) {
      throw new IllegalStateException(
          String.format(
              "agency_topic.%s_id must reference a legal text of kind %s but got %s",
              slot, expectedKind, reference.getKind()));
    }
  }

  private void assertReferenceTenant(LegalText reference, String slot) {
    Long referenceTenant = reference == null ? null : reference.getTenantId();
    Long agencyTenant = agency == null ? null : agency.getTenantId();
    if (referenceTenant != null && agencyTenant != null && !referenceTenant.equals(agencyTenant)) {
      throw new IllegalStateException(
          String.format(
              "agency_topic.%s_id references a legal text of tenant %d but the agency belongs to tenant %d",
              slot, referenceTenant, agencyTenant));
    }
  }
}
