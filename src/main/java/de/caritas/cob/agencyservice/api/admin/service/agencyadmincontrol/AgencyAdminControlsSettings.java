package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgencyAdminControlsSettings {

  private Boolean permissionsPageEnabled;
  private AgencyAdminAllowedPermissionTogglesSettings allowedPermissionToggles;

  /**
   * Per-feature flags an upper role locks on for lower roles (Träger -> Beratungsstelle). {@code
   * true} = enforced-on; absent/{@code false} = not enforced. Same shape as {@link
   * #allowedPermissionToggles}. See ADR-013. Null on legacy rows (backward compatible).
   */
  private AgencyAdminAllowedPermissionTogglesSettings enforcedPermissionToggles;
}
