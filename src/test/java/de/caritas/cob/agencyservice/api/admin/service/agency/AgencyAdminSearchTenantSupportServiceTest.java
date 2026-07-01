package de.caritas.cob.agencyservice.api.admin.service.agency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.model.AgencyAdminSearchResultDTO;
import de.caritas.cob.agencyservice.api.model.Sort;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgencyAdminSearchTenantSupportServiceTest {

  private static final String USER_ID = "userId";

  @Mock
  private EntityManagerFactory entityManagerFactory;

  @Mock
  private AuthenticatedUser authenticatedUser;

  @Mock
  private UserAdminService userAdminService;

  @Mock
  private CriteriaBuilder criteriaBuilder;

  @Mock
  private Root<Agency> root;

  @Mock
  private Path<Object> tenantIdPath;

  @Mock
  private Predicate predicate;

  private AgencyAdminSearchTenantSupportService service;

  @BeforeEach
  void setUp() {
    TenantContext.clear();
    service = new AgencyAdminSearchTenantSupportService(
        entityManagerFactory, authenticatedUser, userAdminService);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void tenantPredicate_shouldFilterByTenantId_whenTenantContextIsOne() {
    stubTenantIdPath();
    TenantContext.setCurrentTenant(1L);
    when(criteriaBuilder.equal(tenantIdPath, 1L)).thenReturn(predicate);
    when(criteriaBuilder.and(predicate)).thenReturn(predicate);

    Predicate result = service.tenantPredicate(criteriaBuilder, root);

    assertThat(result).isSameAs(predicate);
    verify(criteriaBuilder).equal(tenantIdPath, 1L);
    verify(criteriaBuilder).and(predicate);
  }

  @Test
  void tenantPredicate_shouldFilterByTenantId_whenTenantContextIsTwo() {
    stubTenantIdPath();
    TenantContext.setCurrentTenant(2L);
    when(criteriaBuilder.equal(tenantIdPath, 2L)).thenReturn(predicate);
    when(criteriaBuilder.and(predicate)).thenReturn(predicate);

    Predicate result = service.tenantPredicate(criteriaBuilder, root);

    assertThat(result).isSameAs(predicate);
    verify(criteriaBuilder).equal(tenantIdPath, 2L);
    verify(criteriaBuilder).and(predicate);
  }

  @Test
  void tenantPredicate_shouldRequireNonNullTenantId_whenTenantContextIsZero() {
    stubTenantIdPath();
    TenantContext.setCurrentTenant(0L);
    when(criteriaBuilder.isNotNull(tenantIdPath)).thenReturn(predicate);

    Predicate result = service.tenantPredicate(criteriaBuilder, root);

    assertThat(result).isSameAs(predicate);
    verify(criteriaBuilder).isNotNull(tenantIdPath);
    verify(criteriaBuilder, never()).equal(any(), any());
  }

  @Test
  void agenciesWithoutKeywordFilterPredicates_shouldReturnAdminAndTenantPredicates() {
    stubTenantIdPath();
    TenantContext.setCurrentTenant(1L);
    stubUnrestrictedTenantScope();
    when(criteriaBuilder.equal(tenantIdPath, 1L)).thenReturn(predicate);
    when(criteriaBuilder.and(predicate)).thenReturn(predicate);

    Predicate[] predicates = service.agenciesWithoutKeywordFilterPredicates(criteriaBuilder, root);

    assertThat(predicates).hasSize(2);
    assertThat(predicates[0]).isNotNull();
    assertThat(predicates[1]).isSameAs(predicate);
  }

  @Test
  void createSearchAgenciesWithKeywordFilterPredicate_shouldReturnThreePredicates() {
    stubTenantIdPath();
    TenantContext.setCurrentTenant(1L);
    stubUnrestrictedTenantScope();
    stubKeywordSearchCriteriaBuilder();
    when(criteriaBuilder.equal(tenantIdPath, 1L)).thenReturn(predicate);
    when(criteriaBuilder.and(predicate)).thenReturn(predicate);

    AgencyAdminSearch agencyAdminSearch = AgencyAdminSearch.builder().keyword("berlin").build();

    Predicate[] predicates =
        service.createSearchAgenciesWithKeywordFilterPredicate(
            agencyAdminSearch, criteriaBuilder, root);

    assertThat(predicates).hasSize(3);
    verify(criteriaBuilder).or(any(Predicate.class), any(Predicate.class), any(Predicate.class));
    verify(criteriaBuilder, times(3)).like(any(Expression.class), eq("%berlin%"));
    verify(criteriaBuilder, times(3)).lower(any(Expression.class));
  }

  @Test
  void agencyAdminFilterPredicate_shouldCombineTenantScopeAndManagedAgencies_whenRestrictedAdmin() {
    stubTenantIdPath();
    TenantContext.setCurrentTenant(1L);
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);
    when(authenticatedUser.getUserId()).thenReturn(USER_ID);
    when(authenticatedUser.getTenantId()).thenReturn(null);
    when(userAdminService.getAdminUserAgencyIds(USER_ID)).thenReturn(List.of(100L));

    Predicate tenantScopePredicate = mock(Predicate.class);
    Predicate adminAgencyPredicate = mock(Predicate.class);
    Predicate combinedPredicate = mock(Predicate.class);
    Path<Object> idPath = mock(Path.class);
    Predicate inPredicate = mock(Predicate.class);

    when(criteriaBuilder.equal(tenantIdPath, 1L)).thenReturn(tenantScopePredicate);
    when(root.get("id")).thenReturn(idPath);
    when(idPath.in(List.of(100L))).thenReturn(inPredicate);
    when(criteriaBuilder.and(inPredicate)).thenReturn(adminAgencyPredicate);
    when(criteriaBuilder.and(tenantScopePredicate, adminAgencyPredicate))
        .thenReturn(combinedPredicate);

    Predicate result = service.agencyAdminFilterPredicate(criteriaBuilder, root);

    assertThat(result).isSameAs(combinedPredicate);
    verify(criteriaBuilder).and(tenantScopePredicate, adminAgencyPredicate);
  }

  @Test
  void agencyAdminFilterPredicate_shouldReturnAlwaysFalsePredicate_whenRestrictedAdminManagesNoAgencies() {
    stubTenantIdPath();
    TenantContext.setCurrentTenant(1L);
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);
    when(authenticatedUser.getUserId()).thenReturn(USER_ID);
    when(authenticatedUser.getTenantId()).thenReturn(1L);
    when(userAdminService.getAdminUserAgencyIds(USER_ID)).thenReturn(Collections.emptyList());

    Expression<Integer> literalOne = mock(Expression.class);
    Expression<Integer> literalTwo = mock(Expression.class);
    Predicate tenantScopePredicate = mock(Predicate.class);
    Predicate alwaysFalsePredicate = mock(Predicate.class);

    when(criteriaBuilder.equal(tenantIdPath, 1L)).thenReturn(tenantScopePredicate);
    when(criteriaBuilder.literal(1)).thenReturn(literalOne);
    when(criteriaBuilder.literal(2)).thenReturn(literalTwo);
    when(criteriaBuilder.equal(literalOne, literalTwo)).thenReturn(alwaysFalsePredicate);

    Predicate result = service.agencyAdminFilterPredicate(criteriaBuilder, root);

    assertThat(result).isSameAs(alwaysFalsePredicate);
    verify(criteriaBuilder).equal(literalOne, literalTwo);
  }

  @Test
  void agencyAdminFilterPredicate_shouldReturnConjunction_whenTenantContextIsZero() {
    TenantContext.setCurrentTenant(0L);
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    when(authenticatedUser.getTenantId()).thenReturn(0L);

    Predicate alwaysTruePredicate = mock(Predicate.class);
    when(criteriaBuilder.conjunction()).thenReturn(alwaysTruePredicate);

    Predicate result = service.agencyAdminFilterPredicate(criteriaBuilder, root);

    assertThat(result).isSameAs(alwaysTruePredicate);
    verify(criteriaBuilder).conjunction();
  }

  @Test
  void searchAgencies_shouldDelegateToSearchWithoutKeywordFilter_whenKeywordIsBlank() {
    EntityManager entityManager = mock(EntityManager.class);
    when(entityManagerFactory.createEntityManager()).thenReturn(entityManager);

    AgencyAdminSearchTenantSupportService spyService = spy(service);
    SearchResult<Agency> searchResult = new SearchResult<>(List.of(new Agency()), 42L);
    doReturn(searchResult)
        .when(spyService)
        .searchAgenciesWithoutKeywordFilter(eq(entityManager), any(AgencyAdminSearch.class));
    setField(spyService, "topicsFeatureEnabled", false);

    AgencyAdminSearchResultDTO result = spyService.searchAgencies("", 1, 10, new Sort());

    assertThat(result.getTotal()).isEqualTo(42);
    verify(spyService)
        .searchAgenciesWithoutKeywordFilter(eq(entityManager), any(AgencyAdminSearch.class));
    verify(entityManager).close();
  }

  private void stubTenantIdPath() {
    when(root.get("tenantId")).thenReturn(tenantIdPath);
  }

  private void stubUnrestrictedTenantScope() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    when(authenticatedUser.getTenantId()).thenReturn(1L);
    when(criteriaBuilder.equal(tenantIdPath, 1L)).thenReturn(predicate);
  }

  @SuppressWarnings("unchecked")
  private void stubKeywordSearchCriteriaBuilder() {
    Path<Object> namePath = mock(Path.class);
    Path<Object> postCodePath = mock(Path.class);
    Path<Object> cityPath = mock(Path.class);
    Expression<String> lowerExpression = mock(Expression.class);
    Predicate likePredicate = mock(Predicate.class);
    Predicate keywordPredicate = mock(Predicate.class);

    when(root.get("name")).thenReturn(namePath);
    when(root.get("postCode")).thenReturn(postCodePath);
    when(root.get("city")).thenReturn(cityPath);
    when(criteriaBuilder.lower(any(Expression.class))).thenReturn(lowerExpression);
    when(criteriaBuilder.like(any(Expression.class), anyString())).thenReturn(likePredicate);
    when(criteriaBuilder.or(likePredicate, likePredicate, likePredicate))
        .thenReturn(keywordPredicate);
  }
}
