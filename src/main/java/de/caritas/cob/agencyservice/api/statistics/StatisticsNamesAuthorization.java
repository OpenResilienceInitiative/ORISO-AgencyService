package de.caritas.cob.agencyservice.api.statistics;

import de.caritas.cob.agencyservice.api.authorization.Authority.AuthorityValue;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Who may read Beratungsstelle names for the platform statistics panel (ORISO-UserService#1666):
 * the platform administrator, or the Statistik-Leser whose token names the platform tenant 0.
 * Träger, agency and consultant admins, a reader token for any other tenant and the technical
 * identity are refused.
 */
@Component("statisticsNamesAuthorization")
@RequiredArgsConstructor
public class StatisticsNamesAuthorization {

  private final @NonNull AuthenticatedUser authenticatedUser;

  public boolean mayReadNames() {
    if (authenticatedUser.isTechnicalUser()
        || !Long.valueOf(0L).equals(authenticatedUser.getTenantId())) {
      return false;
    }
    return authenticatedUser.isPlatformAdmin() || hasStatisticsNamesAuthority();
  }

  private boolean hasStatisticsNamesAuthority() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(AuthorityValue.READ_STATISTICS_NAMES::equals);
  }
}
