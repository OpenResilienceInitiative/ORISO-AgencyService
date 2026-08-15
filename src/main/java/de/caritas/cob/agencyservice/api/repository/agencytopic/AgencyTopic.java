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
import jakarta.validation.constraints.Size;
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

  /**
   * ORISO-Admin#197: override of the agency-level opening hours for this department (Fachbereich).
   * {@code null} = inherit the Beratungsstelle's opening hours (resolution Fachbereich ?? agency).
   */
  @Size(max = 1000)
  @Column(name = "opening_hours")
  private String openingHours;

  /** ORISO-Admin#197: the department's phone extension (Durchwahl). {@code null} = none. */
  @Size(max = 50)
  @Column(name = "phone_extension")
  private String phoneExtension;

  /**
   * ORISO-Admin#197: floor/location detail (Etage/Bereich) of this department inside the building.
   * {@code null} = inherit the agency-level floor/building detail.
   */
  @Size(max = 100)
  @Column(name = "floor_location")
  private String floorLocation;

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
   * reference another Träger's document. Mirrors the service rule: a tenant-scoped agency (tenant
   * not null/0) may only reference a text of its own tenant — a null-tenant (global/orphan) or
   * different-tenant text is rejected; single-tenant / technical agencies (tenant null or 0) stay
   * unrestricted.
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
    if (reference == null) {
      return;
    }
    Long agencyTenant = agency == null ? null : agency.getTenantId();
    // Single-tenant / technical agencies (tenant null or 0) carry no cross-tenant risk.
    if (agencyTenant == null || agencyTenant.equals(0L)) {
      return;
    }
    Long referenceTenant = reference.getTenantId();
    if (!agencyTenant.equals(referenceTenant)) {
      throw new IllegalStateException(
          String.format(
              "agency_topic.%s_id references a legal text of tenant %s but the agency belongs to tenant %d",
              slot, referenceTenant, agencyTenant));
    }
  }
}
