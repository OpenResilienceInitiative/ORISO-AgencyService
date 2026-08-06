package de.caritas.cob.agencyservice.api.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.caritas.cob.agencyservice.api.authorization.Authority;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;

import static java.util.Objects.nonNull;

/**
 * Representation of the via Keyclcoak authentificated user
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AuthenticatedUser {

  private String userId;

  @NonNull
  private String username;

  @NonNull
  private String accessToken;

  private Set<String> roles;

  private Long tenantId;

  @JsonIgnore
  public boolean isRestrictedAgencyAdmin() {
    return nonNull(roles) && roles.contains(Authority.RESTRICTED_AGENCY_ADMIN.getRoleName());
  }

  @JsonIgnore
  public boolean isAgencyAdmin() {
    return nonNull(roles) && roles.contains(Authority.AGENCY_ADMIN.getRoleName());
  }

  @JsonIgnore
  public boolean isTenantSuperAdmin() {
    return nonNull(roles) && roles.contains(Authority.TENANT_ADMIN.getRoleName());
  }

  @JsonIgnore
  public boolean hasRestrictedAgencyPriviliges() {
    return isRestrictedAgencyAdmin() && !isAgencyAdmin();
  }

  /**
   * Returns the domain user id for operations scoped to a concrete agency administrator.
   * Platform and tenant administrators are not guaranteed to have this custom Keycloak claim.
   */
  @JsonIgnore
  public String requireUserId() {
    if (userId == null || userId.isBlank()) {
      throw new AccessDeniedException("Domain user id is required for this operation");
    }
    return userId;
  }


}
