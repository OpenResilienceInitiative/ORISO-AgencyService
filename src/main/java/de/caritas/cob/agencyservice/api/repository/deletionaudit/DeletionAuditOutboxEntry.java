package de.caritas.cob.agencyservice.api.repository.deletionaudit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One finished agency deletion waiting to be pulled into the UserService deletion log (OBP 2.1
 * #130). Ids and the outcome only; no names, addresses or free text.
 */
@Entity
@Table(name = "deletion_audit_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeletionAuditOutboxEntry {

  public static final String EXECUTED = "EXECUTED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", updatable = false, nullable = false)
  private Long id;

  @Column(name = "agency_id", updatable = false, nullable = false)
  private Long agencyId;

  @Column(name = "tenant_id", updatable = false)
  private Long tenantId;

  @Column(name = "outcome", updatable = false, nullable = false, length = 16)
  private String outcome;

  @Column(name = "created_at", updatable = false, nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "delivered_at")
  private LocalDateTime deliveredAt;

  public static DeletionAuditOutboxEntry executed(Long agencyId, Long tenantId) {
    var entry = new DeletionAuditOutboxEntry();
    entry.agencyId = agencyId;
    entry.tenantId = tenantId;
    entry.outcome = EXECUTED;
    entry.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    return entry;
  }
}
