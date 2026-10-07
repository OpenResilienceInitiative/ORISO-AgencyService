package de.caritas.cob.agencyservice.api.repository.legaltext;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgencyLegalProposalDistributionRepository
    extends JpaRepository<AgencyLegalProposalDistribution, String> {

  Optional<AgencyLegalProposalDistribution> findByRequestKey(String requestKey);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from AgencyLegalProposalDistribution d where d.requestKey = :requestKey")
  Optional<AgencyLegalProposalDistribution> findLockedByRequestKey(
      @Param("requestKey") String requestKey);

  List<AgencyLegalProposalDistribution> findByTenantIdAndKindOrderByCreatedAtDescIdDesc(
      Long tenantId, LegalTextKind kind);
}
