package de.caritas.cob.agencyservice.api.statistics;

import de.caritas.cob.agencyservice.api.repository.agency.AgencyTenantUnawareRepository;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Beratungsstelle names across every Träger. Reads through the tenant-unaware repository on
 * purpose: the caller sits on the platform tenant 0, and the list must never be narrowed by
 * whatever tenant the request context carries.
 */
@Service
@RequiredArgsConstructor
public class AgencyStatisticsNamesService {

  private final @NonNull AgencyTenantUnawareRepository agencyTenantUnawareRepository;

  @Transactional(readOnly = true)
  public List<AgencyStatisticsNameDTO> findNames() {
    return agencyTenantUnawareRepository.findStatisticsNamesOfNonDeletedAgencies();
  }
}
