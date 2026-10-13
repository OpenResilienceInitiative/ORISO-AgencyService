package de.caritas.cob.agencyservice.api.admin.service;

import de.caritas.cob.agencyservice.api.model.AgencyAdminResponseDTO;
import de.caritas.cob.agencyservice.api.model.AgencyStatusResponseDTO;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.service.TenantService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** One availability composition for status commands and management lists/details. */
@Service
@RequiredArgsConstructor
public class AgencyAvailabilityService {

  private final @NonNull TenantService tenantService;

  public AgencyStatusResponseDTO getAvailability(Agency agency) {
    return snapshot(agency, tenantActive(agency.getTenantId()));
  }

  /** Unknown results are also deduplicated, without an operational cache across requests. */
  public Map<Long, AgencyStatusResponseDTO> getAvailabilities(List<Agency> agencies) {
    Map<Long, Optional<Boolean>> tenantAvailability = new HashMap<>();
    Map<Long, AgencyStatusResponseDTO> result = new HashMap<>();
    for (Agency agency : agencies) {
      var parent = tenantAvailability.computeIfAbsent(agency.getTenantId(),
          id -> Optional.ofNullable(tenantActive(id)));
      result.put(agency.getId(), snapshot(agency, parent.orElse(null)));
    }
    return result;
  }

  private Boolean tenantActive(Long tenantId) {
    try {
      return !tenantService.isTenantInactive(tenantId);
    } catch (RuntimeException unavailableAuthority) {
      return null;
    }
  }

  public static AgencyStatusResponseDTO snapshot(Agency agency, Boolean tenantActive) {
    var restrictions = new ArrayList<String>();
    boolean ownActive = !agency.isOffline();
    if (agency.getDeleteDate() != null) {
      restrictions.add("deleted");
    }
    if (!ownActive) {
      restrictions.add("centre_paused");
    }
    if (Boolean.FALSE.equals(tenantActive)) {
      restrictions.add("tenant_inactive");
    }
    if (tenantActive == null) {
      restrictions.add("tenant_status_unknown");
    }
    Boolean active = agency.getDeleteDate() != null || !ownActive
        ? Boolean.FALSE : tenantActive;
    return new AgencyStatusResponseDTO().agencyId(agency.getId()).active(active)
        .ownActive(ownActive).tenantActive(tenantActive).restrictions(restrictions);
  }

  public static void enrich(AgencyAdminResponseDTO dto, AgencyStatusResponseDTO status) {
    dto.setActive(status.getActive());
    dto.setOwnActive(status.getOwnActive());
    dto.setTenantActive(status.getTenantActive());
    dto.setRestrictions(status.getRestrictions());
  }
}
