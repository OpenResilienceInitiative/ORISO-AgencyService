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
}
