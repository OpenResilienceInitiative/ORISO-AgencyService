package de.caritas.cob.agencyservice.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TaskServiceIdentityTest {
  @Test
  void differentActorsCannotShareClientOrSubjectEvenForOptionalBindings() {
    assertThatThrownBy(
            () ->
                new TaskServiceIdentity(
                    groupedBindings("CONFIG_WIZARD")
                        .withProperty("IDENTITY_MAIL_CLIENT_ID", "backend-setup")
                        .withProperty("IDENTITY_MAIL_SERVICE_SUBJECT", "mail-subject")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                new TaskServiceIdentity(
                    groupedBindings("CONFIG_WIZARD")
                        .withProperty("IDENTITY_ACCOUNT_CREATE_CLIENT_ID", "backend-account-create")
                        .withProperty("IDENTITY_ACCOUNT_CREATE_SERVICE_SUBJECT", "setup-subject")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void groupedModeRequiresActorBindingsAndCannotFallBackToOldTaskKeys() {
    assertThatThrownBy(
            () ->
                new TaskServiceIdentity(
                    bindings()
                        .withProperty("TASK_IDENTITY_MODEL", "FIVE_ACTORS")
                        .withProperty("TASK_IDENTITY_REQUIRED_TASKS", "CONFIG_WIZARD")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                new TaskServiceIdentity(
                    groupedBindings("CONFIG_WIZARD")
                        .withProperty("IDENTITY_RUNTIME_CLIENT_ID", "backend-runtime")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new TaskServiceIdentity(groupedBindings("UNKNOWN_PURPOSE")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                new TaskServiceIdentity(
                    groupedBindings("CONFIG_WIZARD").withProperty("TASK_IDENTITY_AUDIENCE", "")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void completeGroupedSetupCannotAddHumanDefaultOrNativeAdministratorGrants() {
    var identity = new TaskServiceIdentity(groupedBindings("CONFIG_WIZARD"));
    for (String grant :
        List.of(
            "technical",
            "tenant-admin",
            "default-roles-online-beratung",
            "manage-users",
            "manage-realm",
            "view-users",
            "query-users",
            "realm-admin")) {
      assertThat(
              identity.allows(
                  groupedCaller(
                      List.of(
                          "config-wizard", "invitation-reservations", "consultant-import", grant)),
                  "CONFIG_WIZARD"))
          .isFalse();
    }
  }

  @Test
  void configuredAccountActorWithHumanGrantsIsStillClassifiedAsMachineToken() {
    var identity =
        new TaskServiceIdentity(
            groupedBindings("CONFIG_WIZARD")
                .withProperty("IDENTITY_ACCOUNT_ADMIN_CLIENT_ID", "backend-account-admin")
                .withProperty("IDENTITY_ACCOUNT_ADMIN_SERVICE_SUBJECT", "account-admin-subject"));
    var jwt =
        Jwt.withTokenValue("fixture")
            .header("alg", "none")
            .subject("account-admin-subject")
            .claim("azp", "backend-account-admin")
            .audience(List.of("receiver"))
            .expiresAt(Instant.now().plusSeconds(60))
            .claim("realm_access", Map.of("roles", List.of("tenant-admin")))
            .build();

    assertThat(identity.isTaskToken(jwt)).isTrue();
    assertThat(identity.allows(new JwtAuthenticationToken(jwt, List.of()), "CONFIG_WIZARD"))
        .isFalse();
  }

  @Test
  void groupedActorCannotInheritDefaultClientRoles() {
    var identity = new TaskServiceIdentity(groupedBindings("CONFIG_WIZARD"));
    var actor =
        groupedCaller(List.of("config-wizard", "invitation-reservations", "consultant-import"));
    var jwt =
        Jwt.withTokenValue("fixture")
            .headers(headers -> headers.putAll(actor.getToken().getHeaders()))
            .claims(claims -> claims.putAll(actor.getToken().getClaims()))
            .claim("resource_access", Map.of("account", Map.of("roles", List.of("manage-account"))))
            .build();

    assertThat(identity.allows(new JwtAuthenticationToken(jwt, List.of()), "CONFIG_WIZARD"))
        .isFalse();
  }

  @Test
  void groupedSetupRequiresItsCompleteRoleUnion() {
    var identity = new TaskServiceIdentity(groupedBindings("CONFIG_WIZARD"));
    var incomplete = groupedCaller(List.of("config-wizard", "invitation-reservations"));

    assertThat(identity.allows(incomplete, "CONFIG_WIZARD")).isFalse();
  }

  @Test
  void misspelledOrBlankIdentityModelCannotFallBackToLegacyBindings() {
    for (String model : List.of("", " FIVE_ACTORS", "five_actors", "FOUR_ACTORS")) {
      assertThatThrownBy(
              () -> new TaskServiceIdentity(bindings().withProperty("TASK_IDENTITY_MODEL", model)))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void groupedActorCannotActivateAnUndeclaredReceiverPurpose() {
    var identity = new TaskServiceIdentity(groupedBindings("CONFIG_WIZARD"));
    var caller =
        groupedCaller(List.of("config-wizard", "invitation-reservations", "consultant-import"));

    assertThat(identity.allows(caller, "CONFIG_WIZARD")).isTrue();
    assertThat(identity.allows(caller, "INVITE_RESERVATIONS")).isFalse();
  }

  private static final List<String> RUNTIME_ROLES =
      List.of(
          "runtime-policy",
          "matrix-agency",
          "matrix-agency-provision",
          "appointment-sync",
          "appointment-participant-cleanup",
          "deletion-audit-read");

  @Test
  void groupedRuntimeReadsTheDeletionAuditOutboxOnlyWithItsCompleteRoleUnion() {
    var identity = new TaskServiceIdentity(runtimeBindings("DELETION_AUDIT_READ"));

    assertThat(identity.allows(runtimeCaller(RUNTIME_ROLES, "receiver"), "DELETION_AUDIT_READ"))
        .isTrue();
    assertThat(
            identity.allows(
                runtimeCaller(RUNTIME_ROLES.subList(0, 5), "receiver"), "DELETION_AUDIT_READ"))
        .isFalse();
    assertThat(identity.allows(runtimeCaller(RUNTIME_ROLES, "other"), "DELETION_AUDIT_READ"))
        .isFalse();
    assertThat(
            identity.allows(
                runtimeCaller(List.of("deletion-audit-read"), "receiver"), "DELETION_AUDIT_READ"))
        .isFalse();
  }

  @Test
  void deletionAuditReadMustBeDeclaredAtTheReceiver() {
    var identity = new TaskServiceIdentity(runtimeBindings("MATRIX_AGENCY"));

    assertThat(identity.allows(runtimeCaller(RUNTIME_ROLES, "receiver"), "DELETION_AUDIT_READ"))
        .isFalse();
    assertThat(identity.allows(runtimeCaller(RUNTIME_ROLES, "receiver"), "MATRIX_AGENCY"))
        .isTrue();
  }

  @Test
  void legacyModeNeverAuthorizesTheDeletionAuditOutbox() {
    var environment =
        new MockEnvironment()
            .withProperty("TASK_IDENTITY_AUDIENCE", "receiver")
            .withProperty("IDENTITY_DELETION_AUDIT_READ_CLIENT_ID", "backend-runtime")
            .withProperty("IDENTITY_DELETION_AUDIT_READ_SERVICE_SUBJECT", "runtime-subject");
    var identity = new TaskServiceIdentity(environment);

    assertThat(
            identity.allows(
                runtimeCaller(List.of("deletion-audit-read"), "receiver"), "DELETION_AUDIT_READ"))
        .isFalse();
    assertThat(identity.allows(runtimeCaller(RUNTIME_ROLES, "receiver"), "DELETION_AUDIT_READ"))
        .isFalse();
    assertThatThrownBy(
            () ->
                new TaskServiceIdentity(
                    environment.withProperty(
                        "TASK_IDENTITY_REQUIRED_TASKS", "DELETION_AUDIT_READ")))
        .isInstanceOf(IllegalStateException.class);
  }

  private MockEnvironment runtimeBindings(String purposes) {
    return new MockEnvironment()
        .withProperty("TASK_IDENTITY_MODEL", "FIVE_ACTORS")
        .withProperty("TASK_IDENTITY_REQUIRED_TASKS", purposes)
        .withProperty("TASK_IDENTITY_AUDIENCE", "receiver")
        .withProperty("IDENTITY_RUNTIME_CLIENT_ID", "backend-runtime")
        .withProperty("IDENTITY_RUNTIME_SERVICE_SUBJECT", "runtime-subject");
  }

  private JwtAuthenticationToken runtimeCaller(List<String> roles, String audience) {
    return new JwtAuthenticationToken(
        Jwt.withTokenValue("fixture")
            .header("alg", "none")
            .subject("runtime-subject")
            .claim("azp", "backend-runtime")
            .audience(List.of(audience))
            .expiresAt(Instant.now().plusSeconds(60))
            .claim("realm_access", Map.of("roles", roles))
            .build(),
        List.of());
  }

  private MockEnvironment groupedBindings(String purposes) {
    return new MockEnvironment()
        .withProperty("TASK_IDENTITY_MODEL", "FIVE_ACTORS")
        .withProperty("TASK_IDENTITY_REQUIRED_TASKS", purposes)
        .withProperty("TASK_IDENTITY_AUDIENCE", "receiver")
        .withProperty("IDENTITY_SETUP_CLIENT_ID", "backend-setup")
        .withProperty("IDENTITY_SETUP_SERVICE_SUBJECT", "setup-subject");
  }

  private JwtAuthenticationToken groupedCaller(List<String> roles) {
    return new JwtAuthenticationToken(
        Jwt.withTokenValue("fixture")
            .header("alg", "none")
            .subject("setup-subject")
            .claim("azp", "backend-setup")
            .audience(List.of("receiver"))
            .expiresAt(Instant.now().plusSeconds(60))
            .claim("realm_access", Map.of("roles", roles))
            .build(),
        List.of());
  }

  @Test
  void groupedSetupUsesOneBindingForDeclaredWizardAndReservationPurposes() {
    var identity =
        new TaskServiceIdentity(
            new MockEnvironment()
                .withProperty("TASK_IDENTITY_MODEL", "FIVE_ACTORS")
                .withProperty("TASK_IDENTITY_REQUIRED_TASKS", "CONFIG_WIZARD,INVITE_RESERVATIONS")
                .withProperty("TASK_IDENTITY_AUDIENCE", "receiver")
                .withProperty("IDENTITY_SETUP_CLIENT_ID", "backend-setup")
                .withProperty("IDENTITY_SETUP_SERVICE_SUBJECT", "setup-subject"));
    var actor =
        Jwt.withTokenValue("fixture")
            .header("alg", "none")
            .subject("setup-subject")
            .claim("azp", "backend-setup")
            .audience(List.of("receiver"))
            .expiresAt(Instant.now().plusSeconds(60))
            .claim(
                "realm_access",
                Map.of(
                    "roles",
                    List.of("config-wizard", "invitation-reservations", "consultant-import")))
            .build();
    var caller = new JwtAuthenticationToken(actor, List.of());

    assertThat(identity.allows(caller, "CONFIG_WIZARD")).isTrue();
    assertThat(identity.allows(caller, "INVITE_RESERVATIONS")).isTrue();
    assertThat(identity.allows(caller, "RUNTIME_POLICY")).isFalse();
  }

  private MockEnvironment bindings() {
    return new MockEnvironment()
        .withProperty("TASK_IDENTITY_AUDIENCE", "receiver")
        .withProperty("IDENTITY_CONFIG_WIZARD_CLIENT_ID", "backend-config-wizard")
        .withProperty("IDENTITY_CONFIG_WIZARD_SERVICE_SUBJECT", "wizard-subject");
  }

  @Test
  void requiredMissingAndDuplicatedBindingsFailStartup() {
    assertThatThrownBy(
            () ->
                new TaskServiceIdentity(
                    new MockEnvironment()
                        .withProperty("TASK_IDENTITY_REQUIRED_TASKS", "CONFIG_WIZARD")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                new TaskServiceIdentity(
                    bindings()
                        .withProperty(
                            "IDENTITY_NOTIFICATION_DISPATCH_CLIENT_ID", "backend-config-wizard")
                        .withProperty(
                            "IDENTITY_NOTIFICATION_DISPATCH_SERVICE_SUBJECT", "dispatch-subject")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                new TaskServiceIdentity(
                    bindings()
                        .withProperty(
                            "IDENTITY_NOTIFICATION_DISPATCH_CLIENT_ID",
                            "backend-notification-dispatch")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void boundTaskCannotInheritHumanOrNativeAdministration() {
    var identity = new TaskServiceIdentity(bindings());
    for (String forbidden :
        List.of(
            "technical",
            "tenant-admin",
            "agency-admin",
            "realm-admin",
            "manage-users",
            "impersonation",
            "*")) {
      var jwt = token(List.of("config-wizard", forbidden));
      assertThat(identity.allows(new JwtAuthenticationToken(jwt, List.of()), "CONFIG_WIZARD"))
          .isFalse();
      assertThat(identity.isTaskToken(jwt)).isTrue();
    }
    var jwt = token(List.of("config-wizard"));
    assertThat(identity.allows(new JwtAuthenticationToken(jwt, List.of()), "CONFIG_WIZARD"))
        .isTrue();
    assertThat(identity.allows(new JwtAuthenticationToken(jwt, List.of()), "INVITE_RESERVATIONS"))
        .isFalse();
  }

  private Jwt token(List<String> roles) {
    return Jwt.withTokenValue("fixture")
        .header("alg", "none")
        .subject("wizard-subject")
        .claim("azp", "backend-config-wizard")
        .audience(List.of("receiver"))
        .expiresAt(Instant.now().plusSeconds(60))
        .claim("realm_access", Map.of("roles", roles))
        .build();
  }
}
