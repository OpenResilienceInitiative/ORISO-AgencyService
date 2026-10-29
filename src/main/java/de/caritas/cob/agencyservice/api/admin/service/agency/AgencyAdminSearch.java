package de.caritas.cob.agencyservice.api.admin.service.agency;

import java.util.List;
import java.util.Set;
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

  /** Resolved once before paging; reused by the result and count predicates. */
  @Builder.Default
  private List<TokenMatches> tokenMatches = List.of();

  public record TokenMatches(String token, Set<Long> tenantIds,
      Set<Long> agencyIds) {
    public TokenMatches {
      tenantIds = Set.copyOf(tenantIds);
      agencyIds = Set.copyOf(agencyIds);
    }
  }
}
