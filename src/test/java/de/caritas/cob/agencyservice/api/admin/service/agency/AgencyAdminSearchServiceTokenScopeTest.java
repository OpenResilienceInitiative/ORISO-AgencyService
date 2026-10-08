package de.caritas.cob.agencyservice.api.admin.service.agency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.testHelper.JwtAuthenticatedUserHelper;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/**
 * {@link AgencyAdminSearchService#agencyAdminFilterPredicate} with an {@code AuthenticatedUser}
 * built from a real JWT (see {@link JwtAuthenticatedUserHelper}) instead of a mock, so the test
 * proves which token value ends up as the admin id handed to the UserService.
 */
@ExtendWith(MockitoExtension.class)
class AgencyAdminSearchServiceTokenScopeTest {

  private static final String ADMIN_SUBJECT = "8ed43c2c-1f51-4169-a7d9-c75de7eaf830";

  @Mock private EntityManagerFactory entityManagerFactory;
  @Mock private UserAdminService userAdminService;
  @Mock private CriteriaBuilder criteriaBuilder;
  @Mock private Root<Agency> root;

  @BeforeEach
  void clearTenant() {
    TenantContext.clear();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @SuppressWarnings("unchecked")
  void agencyAdminFilterPredicate_Should_scopeBySubject_When_restrictedAdminTokenLacksUserIdClaim() {
    var admin = JwtAuthenticatedUserHelper.userWithoutUserIdClaim(
        ADMIN_SUBJECT, JwtAuthenticatedUserHelper.RESTRICTED_AGENCY_ADMIN_ROLE);
    var service = new AgencyAdminSearchService(entityManagerFactory, admin, userAdminService);
    when(userAdminService.getAdminUserAgencyIds(ADMIN_SUBJECT)).thenReturn(List.of(100L, 101L));

    Predicate allTenants = mock(Predicate.class);
    Predicate inPredicate = mock(Predicate.class);
    Predicate adminAgencies = mock(Predicate.class);
    Predicate combined = mock(Predicate.class);
    Path<Object> idPath = mock(Path.class);
    when(criteriaBuilder.conjunction()).thenReturn(allTenants);
    when(root.get("id")).thenReturn(idPath);
    when(idPath.in(List.of(100L, 101L))).thenReturn(inPredicate);
    when(criteriaBuilder.and(inPredicate)).thenReturn(adminAgencies);
    when(criteriaBuilder.and(allTenants, adminAgencies)).thenReturn(combined);

    Predicate result = service.agencyAdminFilterPredicate(criteriaBuilder, root);

    assertThat(result).isSameAs(combined);
    verify(userAdminService).getAdminUserAgencyIds(ADMIN_SUBJECT);
  }

  @Test
  void agencyAdminFilterPredicate_Should_notScope_When_platformAdminTokenLacksUserIdClaim() {
    var platformAdmin = JwtAuthenticatedUserHelper.userWithoutUserIdClaim(
        ADMIN_SUBJECT, JwtAuthenticatedUserHelper.AGENCY_ADMIN_ROLE);
    var service =
        new AgencyAdminSearchService(entityManagerFactory, platformAdmin, userAdminService);
    Predicate allTenants = mock(Predicate.class);
    when(criteriaBuilder.conjunction()).thenReturn(allTenants);

    Predicate result = service.agencyAdminFilterPredicate(criteriaBuilder, root);

    assertThat(result).isSameAs(allTenants);
    verifyNoInteractions(userAdminService);
  }

  @Test
  void agencyAdminFilterPredicate_Should_deny_When_restrictedAdminTokenHasNoIdentity() {
    var admin = JwtAuthenticatedUserHelper.userWithoutAnyIdentity(
        JwtAuthenticatedUserHelper.RESTRICTED_AGENCY_ADMIN_ROLE);
    var service = new AgencyAdminSearchService(entityManagerFactory, admin, userAdminService);
    when(criteriaBuilder.conjunction()).thenReturn(mock(Predicate.class));

    assertThatExceptionOfType(AccessDeniedException.class)
        .isThrownBy(() -> service.agencyAdminFilterPredicate(criteriaBuilder, root));

    verifyNoInteractions(userAdminService);
  }
}
