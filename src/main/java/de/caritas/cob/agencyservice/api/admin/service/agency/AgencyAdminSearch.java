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

  /** ORISO-Admin#1026: leave soft-deleted agencies out (the invite-bar type-ahead). */
  private boolean excludeDeleted;

  /**
   * IDs of the agencies (in the caller's scope) offering a topic whose name matches the keyword.
   * Empty when nothing matches or topics are off; never null.
   */
  @Builder.Default
  private java.util.Set<Long> topicMatchedAgencyIds = java.util.Set.of();


}
