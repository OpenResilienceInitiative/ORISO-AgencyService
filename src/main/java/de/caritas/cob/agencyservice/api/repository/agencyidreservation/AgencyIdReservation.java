package de.caritas.cob.agencyservice.api.repository.agencyidreservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * A binding reservation of one agency ID by an open invite (TEN-INV-U2).
 *
 * <p>The reserved agency ID is the primary key, so the database itself guarantees that the same
 * ID can never be reserved twice — application-level checks are only advisory.
 *
 * <p>Implements {@link Persistable} so that saving a new reservation always issues an
 * {@code INSERT} (and therefore trips the primary key constraint on a conflict) instead of being
 * silently merged into an existing row.
 */
@Entity
@Table(name = "agency_id_reservation")
@Getter
@NoArgsConstructor
public class AgencyIdReservation implements Persistable<Long> {

  @Id
  @Column(name = "agency_id", updatable = false, nullable = false)
  private Long agencyId;

  /**
   * The tenant the pending agency/invite belongs to. Validated against TenantService on
   * reservation; tenant IDs themselves are never reserved here (that is TenantService's job,
   * TEN-INV-U1).
   */
  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "create_date", nullable = false)
  private LocalDateTime createDate;

  @Transient
  private boolean isNew = false;

  public static AgencyIdReservation newReservation(Long agencyId, Long tenantId) {
    var reservation = new AgencyIdReservation();
    reservation.agencyId = agencyId;
    reservation.tenantId = tenantId;
    reservation.createDate = LocalDateTime.now(ZoneOffset.UTC);
    reservation.isNew = true;
    return reservation;
  }

  @Override
  public Long getId() {
    return agencyId;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostLoad
  @PostPersist
  private void markNotNew() {
    this.isNew = false;
  }
}
