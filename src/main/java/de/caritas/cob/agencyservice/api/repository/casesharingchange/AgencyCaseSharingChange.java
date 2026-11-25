package de.caritas.cob.agencyservice.api.repository.casesharingchange;

import de.caritas.cob.agencyservice.api.model.AgencyCaseSharingMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One change of a counselling centre's effective case sharing model (ORISO-Admin#2494). Append
 * only: every column is insert-only and the repository offers no update or delete.
 *
 * <p>Models are stored as the {@link AgencyCaseSharingMode} wire value; {@code previousModel} is
 * null only if the previous model was unknown.
 */
@Entity
@Table(name = "agency_case_sharing_change")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgencyCaseSharingChange {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", updatable = false, nullable = false)
  private Long id;

  @Column(name = "agency_id", updatable = false, nullable = false)
  private Long agencyId;

  @Column(name = "tenant_id", updatable = false)
  private Long tenantId;

  /** UTC. */
  @Column(name = "changed_at", updatable = false, nullable = false)
  private LocalDateTime changedAt;

  @Column(name = "changed_by_user_id", updatable = false, length = 36)
  private String changedByUserId;

  @Column(name = "changed_by_name", updatable = false, length = 255)
  private String changedByName;

  @Column(name = "previous_model", updatable = false, length = 32)
  private String previousModel;

  @Column(name = "new_model", updatable = false, nullable = false, length = 32)
  private String newModel;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", updatable = false, nullable = false, length = 32)
  private AgencyCaseSharingChangeSource source;

  @Builder
  private AgencyCaseSharingChange(Long agencyId, Long tenantId, LocalDateTime changedAt,
      String changedByUserId, String changedByName, AgencyCaseSharingMode previousModel,
      AgencyCaseSharingMode newModel, AgencyCaseSharingChangeSource source) {
    this.agencyId = agencyId;
    this.tenantId = tenantId;
    this.changedAt = changedAt;
    this.changedByUserId = changedByUserId;
    this.changedByName = changedByName;
    this.previousModel = previousModel == null ? null : previousModel.getValue();
    this.newModel = newModel.getValue();
    this.source = source;
  }
}
