package de.caritas.cob.agencyservice.api.admin.service.agency;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class AgencyAdminSearch {

  private String keyword;
  private int pageNumber;
  private int pageSize;
  private String sortField;
  private boolean ascending;

  private boolean excludeDeleted;

  /** Requested tenant (Träger); null = every tenant in the caller's scope. */
  private Long tenantId;

  /** Agencies offering a topic whose name matches the keyword; empty, never null. */
  @Builder.Default
  private java.util.Set<Long> topicMatchedAgencyIds = java.util.Set.of();


}
