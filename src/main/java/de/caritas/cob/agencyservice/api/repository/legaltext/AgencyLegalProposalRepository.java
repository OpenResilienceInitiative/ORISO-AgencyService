package de.caritas.cob.agencyservice.api.repository.legaltext;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgencyLegalProposalRepository extends JpaRepository<AgencyLegalProposal, Long> {

  /** Locking, so a concurrent identical forward is seen after its commit instead of colliding. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select p from AgencyLegalProposal p where p.source = :source and p.sourceRevision = :revision"
          + " and p.recipientAgencyId in :agencyIds order by p.recipientAgencyId")
  List<AgencyLegalProposal> findLockedBySourceRevisionAndRecipients(
      @Param("source") AgencyLegalProposalSource source,
      @Param("revision") String revision,
      @Param("agencyIds") Collection<Long> agencyIds);

  List<AgencyLegalProposal> findBySourceAndSourceRevisionAndRecipientAgencyIdIn(
      AgencyLegalProposalSource source, String sourceRevision, Collection<Long> agencyIds);

  List<AgencyLegalProposal> findByRecipientAgencyIdOrderByCreatedAtDescIdDesc(Long agencyId);

  List<AgencyLegalProposal> findByRecipientAgencyIdAndKindOrderByCreatedAtDescIdDesc(
      Long agencyId, LegalTextKind kind);

  Optional<AgencyLegalProposal> findByIdAndRecipientAgencyId(Long id, Long agencyId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from AgencyLegalProposal p where p.id = :id and p.recipientAgencyId = :agencyId")
  Optional<AgencyLegalProposal> findLockedByIdAndRecipientAgencyId(
      @Param("id") Long id, @Param("agencyId") Long agencyId);

  /** Locked in a fixed order so an adopt or dismiss at a recipient waits for the supersede. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select p from AgencyLegalProposal p where p.recipientAgencyId in :agencyIds and p.kind = :kind"
          + " and p.status in :statuses order by p.recipientAgencyId, p.id")
  List<AgencyLegalProposal> findLockedOpenByRecipients(
      @Param("agencyIds") Collection<Long> agencyIds,
      @Param("kind") LegalTextKind kind,
      @Param("statuses") Collection<AgencyLegalProposalStatus> statuses);
}
