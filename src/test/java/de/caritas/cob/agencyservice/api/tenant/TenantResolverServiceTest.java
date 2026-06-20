package de.caritas.cob.agencyservice.api.tenant;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class TenantResolverServiceTest {

  public static final long TECHNICAL_CONTEXT = 0L;

  @Mock
  SubdomainTenantResolver subdomainTenantResolver;

  @Mock
  AccessTokenTenantResolver accessTokenTenantResolver;

  @Mock
  HttpServletRequest authenticatedRequest;

  @Mock
  HttpServletRequest nonAuthenticatedRequest;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  KeycloakAuthenticationToken token;

  @InjectMocks
  TenantResolverService tenantResolverService;

  @Mock
  private CustomHeaderTenantResolver customHeaderTenantResolver;

  @Mock
  private TechnicalUserTenantResolver technicalUserTenantResolver;

  @Mock
  private SecurityContext mockSecurityContext;

  @Mock
  private Authentication mockAuthentication;

  @Mock
  private MultitenancyWithSingleDomainTenantResolver multitenancyWithSingleDomainTenantResolver;

  @AfterEach
  public void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void resolve_Should_ResolveFromAccessTokenForAuthenticatedUser_And_PassValidation() {
    // given
    givenUserIsAuthenticated();
    when(technicalUserTenantResolver.resolve(authenticatedRequest)).thenReturn(Optional.empty());
    when(accessTokenTenantResolver.resolve(authenticatedRequest)).thenReturn(Optional.of(1L));
    when(customHeaderTenantResolver.resolve(authenticatedRequest)).thenReturn(Optional.empty());
    when(subdomainTenantResolver.resolve(authenticatedRequest)).thenReturn(Optional.of(1L));

    // when
    Long resolvedTenantId = tenantResolverService.resolve(authenticatedRequest);

    // then
    assertThat(resolvedTenantId).isEqualTo(1L);
    verify(accessTokenTenantResolver).resolve(authenticatedRequest);
    verify(subdomainTenantResolver).resolve(authenticatedRequest);
  }

  private void givenUserIsAuthenticated() {
    SecurityContextHolder.setContext(mockSecurityContext);
    when(mockSecurityContext.getAuthentication()).thenReturn(mockAuthentication);
    when(mockAuthentication.isAuthenticated()).thenReturn(true);
  }

  @Test
  void resolve_Should_ThrowAccessDeniedException_ForAuthenticatedUser_When_SubdomainTenantIdDoesNotMatchTenantIdFromToken() {
    // given

    givenUserIsAuthenticated();
    when(technicalUserTenantResolver.resolve(authenticatedRequest)).thenReturn(Optional.empty());
    when(accessTokenTenantResolver.resolve(authenticatedRequest)).thenReturn(Optional.of(1L));
    when(customHeaderTenantResolver.resolve(authenticatedRequest)).thenReturn(Optional.empty());
    when(subdomainTenantResolver.resolve(authenticatedRequest)).thenReturn(Optional.of(2L));

    // when, then
    assertThrows(AccessDeniedException.class,
        () -> tenantResolverService.resolve(authenticatedRequest));
  }

  @Test
  void resolve_Should_ThrowAccessDeniedExceptionForAuthenticatedUser_IfAccessTokenResolverCannotResolveTenant() {
    // given
    givenUserIsAuthenticated();
    when(technicalUserTenantResolver.resolve(authenticatedRequest)).thenReturn(Optional.empty());
    when(accessTokenTenantResolver.resolve(authenticatedRequest)).thenReturn(Optional.empty());
    when(customHeaderTenantResolver.resolve(authenticatedRequest)).thenReturn(Optional.empty());
    when(subdomainTenantResolver.resolve(authenticatedRequest)).thenReturn(Optional.empty());

    // when, then
    assertThrows(AccessDeniedException.class,
        () -> tenantResolverService.resolve(authenticatedRequest));
  }

  @Test
  void resolve_Should_ThrowAccessDeniedExceptionForNotAuthenticatedUser_IfSubdomainCouldNotBeDetermined() {
    // given
    when(multitenancyWithSingleDomainTenantResolver.resolve(nonAuthenticatedRequest))
        .thenReturn(Optional.empty());
    when(customHeaderTenantResolver.resolve(nonAuthenticatedRequest)).thenReturn(Optional.empty());
    when(subdomainTenantResolver.resolve(nonAuthenticatedRequest)).thenReturn(Optional.empty());

    // when, then
    assertThrows(AccessDeniedException.class,
        () -> tenantResolverService.resolve(nonAuthenticatedRequest));
  }

  @Test
  void resolve_Should_ResolveTenantId_IfSubdomainCouldBeDetermined() {
    // given
    when(multitenancyWithSingleDomainTenantResolver.resolve(nonAuthenticatedRequest))
        .thenReturn(Optional.empty());
    when(customHeaderTenantResolver.resolve(nonAuthenticatedRequest)).thenReturn(Optional.empty());
    when(subdomainTenantResolver.resolve(nonAuthenticatedRequest)).thenReturn(Optional.of(1L));

    // when
    Long resolved = tenantResolverService.resolve(nonAuthenticatedRequest);

    // then
    assertThat(resolved).isEqualTo(1L);
    verify(subdomainTenantResolver).resolve(nonAuthenticatedRequest);
  }

  @Test
  void resolve_Should_ResolveTenantId_ForTechnicalUserRole() {
    // given
    givenUserIsAuthenticated();
    when(technicalUserTenantResolver.resolve(authenticatedRequest)).thenReturn(
        Optional.of(TECHNICAL_CONTEXT));

    Long resolved = tenantResolverService.resolve(authenticatedRequest);

    // then
    assertThat(resolved).isEqualTo(TECHNICAL_CONTEXT);
    verify(accessTokenTenantResolver, never()).resolve(authenticatedRequest);
  }

  @Test
  void resolve_Should_ResolveTenantId_FromHeader() {
    // given
    when(multitenancyWithSingleDomainTenantResolver.resolve(authenticatedRequest))
        .thenReturn(Optional.empty());
    when(customHeaderTenantResolver.resolve(authenticatedRequest)).thenReturn(Optional.of(2L));

    // when
    Long resolved = tenantResolverService.resolve(authenticatedRequest);

    // then
    assertThat(resolved).isEqualTo(2L);
    verify(subdomainTenantResolver, never()).resolve(authenticatedRequest);
  }

  @Test
  void resolve_Should_CallSuccessfulResolverOnlyOnce_ForNonAuthenticatedUser() {
    // given
    when(multitenancyWithSingleDomainTenantResolver.resolve(nonAuthenticatedRequest))
        .thenReturn(Optional.empty());
    when(customHeaderTenantResolver.resolve(nonAuthenticatedRequest)).thenReturn(Optional.empty());
    when(subdomainTenantResolver.resolve(nonAuthenticatedRequest)).thenReturn(Optional.of(1L));

    // when
    Long resolved = tenantResolverService.resolve(nonAuthenticatedRequest);

    // then
    assertThat(resolved).isEqualTo(1L);
    verify(subdomainTenantResolver).resolve(nonAuthenticatedRequest);
  }
}
