package de.caritas.cob.agencyservice.api.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.access.AccessDeniedException;

@RunWith(MockitoJUnitRunner.class)
public class AuthenticatedUserTest {

  @Test(expected = NullPointerException.class)
  public void AuthenticatedUser_Should_ThrowNullPointerExceptionWhenArgumentsAreNull() {
    new AuthenticatedUser(null, null, null, null, null);
  }

  @Test
  public void AuthenticatedUser_Should_AllowUserIdToBeNull() {
    AuthenticatedUser authenticatedUser = new AuthenticatedUser();
    authenticatedUser.setUserId(null);

    assertThat(authenticatedUser.getUserId()).isNull();
  }

  @Test(expected = NullPointerException.class)
  public void AuthenticatedUser_Should_ThrowNullPointerExceptionWhenUsernameIsNull() {
    AuthenticatedUser authenticatedUser = new AuthenticatedUser();
    authenticatedUser.setUsername(null);
  }

  @Test
  public void requireUserId_Should_ReturnDomainUserId_WhenPresent() {
    AuthenticatedUser authenticatedUser = new AuthenticatedUser();
    authenticatedUser.setUserId("domain-user-id");

    assertThat(authenticatedUser.requireUserId()).isEqualTo("domain-user-id");
  }

  @Test
  public void requireUserId_Should_ThrowAccessDeniedException_WhenMissing() {
    AuthenticatedUser authenticatedUser = new AuthenticatedUser();

    assertThatExceptionOfType(AccessDeniedException.class)
        .isThrownBy(authenticatedUser::requireUserId)
        .withMessage("Domain user id is required for this operation");
  }

  @Test
  public void isPlatformAdmin_Should_BeTrue_ForTenantZeroWithAgencyAndTenantAdminRoles() {
    assertThat(user(0L, "agency-admin", "tenant-admin").isPlatformAdmin()).isTrue();
  }

  @Test
  public void isPlatformAdmin_Should_BeFalse_ForTenantZeroWithoutBothAdminRoles() {
    assertThat(user(0L, "tenant-admin").isPlatformAdmin()).isFalse();
    assertThat(user(0L, "agency-admin").isPlatformAdmin()).isFalse();
    assertThat(user(0L, "restricted-agency-admin").isPlatformAdmin()).isFalse();
  }

  @Test
  public void isPlatformAdmin_Should_BeFalse_ForAdminOfARealTenant() {
    assertThat(user(1L, "agency-admin", "tenant-admin").isPlatformAdmin()).isFalse();
    assertThat(user(null, "agency-admin", "tenant-admin").isPlatformAdmin()).isFalse();
  }

  @Test
  public void isTechnicalUser_Should_FollowTheTechnicalRealmRole() {
    assertThat(user(null, "technical").isTechnicalUser()).isTrue();
    assertThat(user(0L, "tenant-admin").isTechnicalUser()).isFalse();
    assertThat(new AuthenticatedUser().isTechnicalUser()).isFalse();
  }

  private static AuthenticatedUser user(Long tenantId, String... roles) {
    AuthenticatedUser authenticatedUser = new AuthenticatedUser();
    authenticatedUser.setTenantId(tenantId);
    authenticatedUser.setRoles(Set.of(roles));
    return authenticatedUser;
  }
}
