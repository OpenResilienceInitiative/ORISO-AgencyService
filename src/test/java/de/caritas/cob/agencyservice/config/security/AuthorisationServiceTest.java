package de.caritas.cob.agencyservice.config.security;

import static de.caritas.cob.agencyservice.api.authorization.Authority.AGENCY_ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AuthorisationServiceTest {

  private final AuthorisationService authorisationService = new AuthorisationService();

  @Mock
  private SecurityContext mockSecurityContext;

  @Mock
  private Authentication mockAuthentication;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void extractRealmRoles_Should_returnRolesList_When_realmAccessAndRolesPresent() {
    List<String> roles = Lists.newArrayList("agency-admin", "other");
    Jwt jwt = buildJwtWithRealmRoles(roles);

    Collection<String> result = authorisationService.extractRealmRoles(jwt);

    assertThat(result).containsExactlyElementsOf(roles);
  }

  @Test
  void extractRealmRoles_Should_returnEmptyList_When_realmAccessPresentButRolesNull() {
    Jwt jwt = buildJwtWithNullRoles();

    Collection<String> result = authorisationService.extractRealmRoles(jwt);

    assertThat(result).isEmpty();
  }

  @Test
  void extractRealmRoles_Should_returnEmptyList_When_realmAccessIsNull() {
    Jwt jwt = buildJwtWithoutRealmAccess();

    Collection<String> result = authorisationService.extractRealmRoles(jwt);

    assertThat(result).isEmpty();
  }

  @Test
  void extractRealmRoles_Should_throwClassCastException_When_rolesIsNotAList() {
    Jwt jwt = buildJwtWithNonListRoles();

    // Documents current (possibly unintended) behavior — unchecked cast at line 43
    assertThatThrownBy(() -> authorisationService.extractRealmRoles(jwt))
        .isInstanceOf(ClassCastException.class);
  }

  @Test
  void extractRealmAuthorities_Should_returnMappedAuthorities_When_singleValidRole() {
    Jwt jwt = buildJwtWithRealmRoles(Lists.newArrayList("agency-admin"));

    Collection<GrantedAuthority> result = authorisationService.extractRealmAuthorities(jwt);

    assertThat(result).hasSize(2);
    List<String> authorities =
        result.stream().map(GrantedAuthority::getAuthority).toList();
    assertThat(authorities).containsAll(AGENCY_ADMIN.getAuthorities());
  }

  @Test
  void extractRealmAuthorities_Should_returnEmptyCollection_When_rolesIsEmptyList() {
    Jwt jwt = buildJwtWithRealmRoles(Lists.newArrayList());

    Collection<GrantedAuthority> result = authorisationService.extractRealmAuthorities(jwt);

    assertThat(result).isEmpty();
  }

  @Test
  void extractRealmAuthorities_Should_returnEmptyCollection_When_realmAccessIsNull() {
    Jwt jwt = buildJwtWithoutRealmAccess();

    Collection<GrantedAuthority> result = authorisationService.extractRealmAuthorities(jwt);

    assertThat(result).isEmpty();
  }

  @Test
  void extractRealmAuthorities_Should_returnEmptyCollection_When_roleIsUnknown() {
    Jwt jwt = buildJwtWithRealmRoles(Lists.newArrayList("unknown-role"));

    Collection<GrantedAuthority> result = authorisationService.extractRealmAuthorities(jwt);

    assertThat(result).isEmpty();
  }

  @Test
  void extractRealmAuthorities_Should_returnEmptyCollection_When_roleHasDifferentCasing() {
    Jwt jwt = buildJwtWithRealmRoles(Lists.newArrayList("Agency-Admin"));

    Collection<GrantedAuthority> result = authorisationService.extractRealmAuthorities(jwt);

    // Authority.fromRoleName uses case-sensitive String.equals — "Agency-Admin" does not match
    // "agency-admin". Unlike the GrantedAuthoritiesMapper overload, extractRealmAuthorities does
    // not lowercase role names before lookup.
    assertThat(result).isEmpty();
  }

  @Test
  void extractRealmAuthorities_Should_returnValidAuthorities_When_mixedWithUnknownRoles() {
    Jwt jwt =
        buildJwtWithRealmRoles(
            Lists.newArrayList("unknown-role-1", "agency-admin", "unknown-role-2"));

    Collection<GrantedAuthority> result = authorisationService.extractRealmAuthorities(jwt);

    assertThat(result).hasSize(2);
    List<String> authorities =
        result.stream().map(GrantedAuthority::getAuthority).toList();
    assertThat(authorities).containsAll(AGENCY_ADMIN.getAuthorities());
    assertThat(authorities).doesNotContain("unknown-role-1", "unknown-role-2");
  }

  @Test
  void getUsername_Should_returnUsernameClaim_When_claimPresent() {
    givenAuthenticatedWithJwt(buildJwtWithUsername("test-user"));

    Object result = authorisationService.getUsername();

    assertThat(result).isEqualTo("test-user");
  }

  @Test
  void getUsername_Should_returnNull_When_usernameClaimAbsent() {
    givenAuthenticatedWithJwt(buildJwtWithoutUsernameClaim());

    Object result = authorisationService.getUsername();

    assertThat(result).isNull();
  }

  private void givenAuthenticatedWithJwt(Jwt jwt) {
    SecurityContextHolder.setContext(mockSecurityContext);
    when(mockSecurityContext.getAuthentication()).thenReturn(mockAuthentication);
    when(mockAuthentication.getPrincipal()).thenReturn(jwt);
  }

  private Jwt buildJwtWithRealmRoles(List<String> roles) {
    Map<String, Object> headers = new HashMap<>();
    headers.put("alg", "HS256");
    headers.put("typ", "JWT");
    HashMap<String, Object> claimMap = Maps.newHashMap();
    var realmAccess = Maps.newHashMap();
    realmAccess.put("roles", roles);
    claimMap.put("realm_access", realmAccess);
    return new Jwt(
        "token", Instant.now(), Instant.now().plusSeconds(1), headers, claimMap);
  }

  private Jwt buildJwtWithNullRoles() {
    Map<String, Object> headers = new HashMap<>();
    headers.put("alg", "HS256");
    headers.put("typ", "JWT");
    HashMap<String, Object> claimMap = Maps.newHashMap();
    var realmAccess = Maps.newHashMap();
    realmAccess.put("roles", null);
    claimMap.put("realm_access", realmAccess);
    return new Jwt(
        "token", Instant.now(), Instant.now().plusSeconds(1), headers, claimMap);
  }

  private Jwt buildJwtWithoutRealmAccess() {
    Map<String, Object> headers = new HashMap<>();
    headers.put("alg", "HS256");
    headers.put("typ", "JWT");
    HashMap<String, Object> claimMap = Maps.newHashMap();
    claimMap.put("sub", "test-subject");
    return new Jwt(
        "token", Instant.now(), Instant.now().plusSeconds(1), headers, claimMap);
  }

  private Jwt buildJwtWithoutUsernameClaim() {
    Map<String, Object> headers = new HashMap<>();
    headers.put("alg", "HS256");
    headers.put("typ", "JWT");
    HashMap<String, Object> claimMap = Maps.newHashMap();
    claimMap.put("sub", "test-subject");
    return new Jwt(
        "token", Instant.now(), Instant.now().plusSeconds(1), headers, claimMap);
  }

  private Jwt buildJwtWithNonListRoles() {
    Map<String, Object> headers = new HashMap<>();
    headers.put("alg", "HS256");
    headers.put("typ", "JWT");
    HashMap<String, Object> claimMap = Maps.newHashMap();
    var realmAccess = Maps.newHashMap();
    realmAccess.put("roles", "agency-admin");
    claimMap.put("realm_access", realmAccess);
    return new Jwt(
        "token", Instant.now(), Instant.now().plusSeconds(1), headers, claimMap);
  }

  private Jwt buildJwtWithUsername(String username) {
    Map<String, Object> headers = new HashMap<>();
    headers.put("alg", "HS256");
    headers.put("typ", "JWT");
    HashMap<String, Object> claimMap = Maps.newHashMap();
    claimMap.put("username", username);
    return new Jwt(
        "token", Instant.now(), Instant.now().plusSeconds(1), headers, claimMap);
  }
}
