package de.caritas.cob.agencyservice.api.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
}
