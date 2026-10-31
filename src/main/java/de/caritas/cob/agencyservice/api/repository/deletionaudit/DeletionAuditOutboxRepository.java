package de.caritas.cob.agencyservice.api.repository.deletionaudit;

import de.caritas.cob.agencyservice.api.repository.TenantUnaware;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Cross-tenant queue: the reader is a technical actor, never a tenant-scoped human. */
@TenantUnaware
public interface DeletionAuditOutboxRepository
    extends JpaRepository<DeletionAuditOutboxEntry, Long> {

  @Query("select e from DeletionAuditOutboxEntry e where e.deliveredAt is null order by e.id asc")
  List<DeletionAuditOutboxEntry> findPending(Pageable page);

  /** Stamps only rows not yet acknowledged, so a repeated acknowledgement changes nothing. */
  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update DeletionAuditOutboxEntry e set e.deliveredAt = :at"
          + " where e.id in :ids and e.deliveredAt is null")
  int markDelivered(@Param("ids") Collection<Long> ids, @Param("at") LocalDateTime at);

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from DeletionAuditOutboxEntry e where e.deliveredAt is not null")
  int deleteDelivered();
}
