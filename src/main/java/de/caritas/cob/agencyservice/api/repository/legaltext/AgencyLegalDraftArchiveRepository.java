package de.caritas.cob.agencyservice.api.repository.legaltext;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgencyLegalDraftArchiveRepository
    extends JpaRepository<AgencyLegalDraftArchive, Long> {

  List<AgencyLegalDraftArchive> findByAgencyIdOrderByArchivedAtDescIdDesc(Long agencyId);

  List<AgencyLegalDraftArchive> findByAgencyIdAndKindOrderByArchivedAtDescIdDesc(
      Long agencyId, LegalTextKind kind);

  Optional<AgencyLegalDraftArchive> findByIdAndAgencyId(Long id, Long agencyId);
}
