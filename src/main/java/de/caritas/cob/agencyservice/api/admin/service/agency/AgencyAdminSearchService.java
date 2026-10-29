package de.caritas.cob.agencyservice.api.admin.service.agency;

import com.google.common.collect.Lists;
import de.caritas.cob.agencyservice.api.admin.hallink.SearchResultLinkBuilder;
import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.topicservice.generated.web.model.TopicDTO;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.api.model.AgencyAdminSearchResultDTO;
import de.caritas.cob.agencyservice.api.model.SearchResultLinks;
import de.caritas.cob.agencyservice.api.model.Sort;
import de.caritas.cob.agencyservice.api.model.Sort.OrderEnum;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;

import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgencyAdminSearchService {

  protected static final String NAME_SEARCH_FIELD = "name";
  protected static final String POST_CODE_SEARCH_FIELD = "postCode";
  protected static final String CITY_SEARCH_FIELD = "city";
  protected static final String TENANT_ID_SEARCH_FIELD = "tenantId";
  protected static final String DELETE_DATE_FIELD = "deleteDate";
  protected static final char LIKE_ESCAPE = '!';
  protected final @NonNull EntityManagerFactory entityManagerFactory;

  protected final @NonNull AuthenticatedUser authenticatedUser;

  protected final @NonNull UserAdminService userAdminService;

  @Autowired(required = false)
  private AgencyTopicEnrichmentService agencyTopicEnrichmentService;

  @Autowired(required = false)
  private TenantService tenantService;

  private AgencyRepository agencyRepository;

  @Value("${feature.topics.enabled}")
  private boolean topicsFeatureEnabled;

  @Value("${multitenancy.enabled:false}")
  private boolean multitenancyEnabled;

  /**
   * Searches for agencies by a given keyword, limits the result by perPage and generates a
   * {@link AgencyAdminSearchResultDTO} containing hal links.
   *
   * @param keyword the keyword to search for
   * @param page    the current requested page
   * @param perPage the amount of items in one page
   * @return the result list
   */
  public AgencyAdminSearchResultDTO searchAgencies(final String keyword, final Integer page,
      final Integer perPage, Sort sort) {
    return searchAgencies(keyword, page, perPage, sort, false);
  }

  public AgencyAdminSearchResultDTO searchAgencies(final String keyword, final Integer page,
      final Integer perPage, Sort sort, boolean excludeDeleted) {
    return searchAgencies(keyword, page, perPage, sort,
        new AgencySearchFilter(excludeDeleted, null));
  }

  /** The keyword also matches the agency's topic names (Fachbereich). */
  public AgencyAdminSearchResultDTO searchAgencies(final String keyword, final Integer page,
      final Integer perPage, Sort sort, AgencySearchFilter filter) {
    SearchResult<Agency> queryResult = new SearchResult<>(Lists.newArrayList(), 0L);
    var tokens = AdminSearchText.tokens(keyword);
    boolean withKeyword = !tokens.isEmpty();
    Map<Long, Optional<String>> tenantNames = new HashMap<>();
    Map<Long, List<TopicDTO>> topicsByTenant = new HashMap<>();

    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      var agencyAdminSearch = AgencyAdminSearch.builder()
          .keyword(keyword)
          .pageNumber(page)
          .pageSize(perPage)
          .sortField(sort != null && sort.getField() != null ? sort.getField().getValue() : null)
          .ascending(
              sort != null && sort.getOrder() != null ? sort.getOrder().equals(OrderEnum.ASC)
                  : true)
          .excludeDeleted(filter.excludeDeleted())
          .tenantId(filter.tenantId())
          .build();
      if (withKeyword) {
        resolveTokenMatches(entityManager, agencyAdminSearch, tokens, tenantNames, topicsByTenant);
      }
      queryResult = withKeyword
          ? searchAgenciesByKeyword(entityManager, agencyAdminSearch)
          : searchAgenciesWithoutKeywordFilter(entityManager, agencyAdminSearch);
      // No fetch-join in the paged queries: collection fetch + setMaxResults pages in memory.
      // Load the topics here while the entity manager is open; @BatchSize batches them.
      queryResult.getResult().forEach(agency -> Hibernate.initialize(agency.getAgencyTopics()));
    }

    var agencies = queryResult.getResult();
    if (topicsFeatureEnabled) {
      if (withKeyword) {
        agencyTopicEnrichmentService.enrichAgenciesWithResolvedTopics(agencies, topicsByTenant);
      } else {
        agencyTopicEnrichmentService.enrichAgenciesWithTopics(agencies);
      }
    }

    var resultList = agencies.stream()
        .map(agency -> {
          var dto = new AgencyAdminFullResponseDTOBuilder(agency).fromAgency();
          if (dto.getEmbedded() != null && agency.getTenantId() != null) {
            dto.getEmbedded().setTenantName(tenantNames
                .computeIfAbsent(agency.getTenantId(), this::tenantName).orElse(null));
          }
          return dto;
        })
        .toList();

    SearchResultLinks searchResultLinks = SearchResultLinkBuilder.getInstance()
        .withPage(page)
        .withPerPage(perPage)
        .withTotalResults(queryResult.getTotalSize().intValue())
        .withKeyword(keyword)
        .buildSearchResultLinks();

    return new AgencyAdminSearchResultDTO()
        .embedded(resultList)
        .links(searchResultLinks)
        .total(queryResult.getTotalSize().intValue());
  }

  /** Best effort: a tenant name that cannot be resolved leaves the field empty, never fails. */
  private Optional<String> tenantName(Long tenantId) {
    if (tenantService == null) {
      return Optional.empty();
    }
    try {
      var tenant = tenantService.getRestrictedTenantDataByTenantId(tenantId);
      return Optional.ofNullable(tenant == null ? null : tenant.getName());
    } catch (RuntimeException exception) {
      log.warn("Could not resolve the name of tenant {} for the agency search", tenantId);
      return Optional.empty();
    }
  }

  /** Resolve owner metadata only for agencies already inside all caller/listing predicates. */
  private void resolveTokenMatches(EntityManager em, AgencyAdminSearch search, List<String> tokens,
      Map<Long, Optional<String>> tenantNames,
      Map<Long, List<TopicDTO>> topics) {
    var cb = em.getCriteriaBuilder();
    var query = cb.createTupleQuery();
    var root = query.from(Agency.class);
    query.multiselect(root.get("id"), root.get("tenantId"))
        .where(agenciesWithoutKeywordFilterPredicates(search, cb, root));
    var candidates = em.createQuery(query).getResultList();
    Map<Long, Long> tenantByAgency = new HashMap<>();
    candidates.forEach(row -> tenantByAgency.put(row.get(0, Long.class), row.get(1, Long.class)));
    Set<Long> tenantIds = new HashSet<>(tenantByAgency.values());
    var knownTenantIds = tenantIds.stream().filter(java.util.Objects::nonNull).toList();
    if (!knownTenantIds.isEmpty()) {
      if (tenantService == null) {
        throw new IllegalStateException("Tenant metadata is required for agency search");
      }
      tenantIds.forEach(id -> tenantNames.put(id, Optional.empty()));
      tenantService.getRestrictedTenantDataByTenantIds(knownTenantIds).forEach(tenant -> {
        if (tenantIds.contains(tenant.getId())) {
          tenantNames.put(tenant.getId(), Optional.ofNullable(tenant.getName()));
        }
      });
    }
    if (topicsFeatureEnabled) {
      if (agencyTopicEnrichmentService == null) {
        throw new IllegalStateException("Topic metadata is required for agency search");
      }
      tenantIds.forEach(id -> topics.put(id,
          agencyTopicEnrichmentService.topicsOfTenantForSearch(id)));
    }
    // Only scalar links, no collection fetch-join and no page-first filtering.
    List<Object[]> links = topicsFeatureEnabled && !tenantByAgency.isEmpty()
        ? em.createQuery("select t.agency.id, t.topicId from AgencyTopic t "
            + "where t.agency.id in :agencyIds", Object[].class)
            .setParameter("agencyIds", tenantByAgency.keySet()).getResultList() : List.of();
    search.setTokenMatches(tokens.stream().map(token -> {
      Set<Long> matchingTenants = new HashSet<>();
      tenantNames.forEach((id, name) -> {
        if (name.map(AdminSearchText::fold).orElse("").contains(token)) {
          matchingTenants.add(id);
        }
      });
      Map<Long, Set<Long>> topicIds = new HashMap<>();
      topics.forEach((tenantId, catalog) -> {
        Set<Long> matches = new HashSet<>();
        catalog.stream().filter(topic -> topic.getId() != null && topic.getName() != null)
            .filter(topic -> AdminSearchText.fold(topic.getName()).contains(token))
            .forEach(topic -> matches.add(topic.getId()));
        topicIds.put(tenantId, matches);
      });
      Set<Long> matchingAgencies = new HashSet<>();
      links.forEach(link -> {
        Long agencyId = (Long) link[0];
        if (topicIds.getOrDefault(tenantByAgency.get(agencyId), Set.of()).contains(link[1])) {
          matchingAgencies.add(agencyId);
        }
      });
      return new AgencyAdminSearch.TokenMatches(token, matchingTenants, matchingAgencies);
    }).toList());
  }

  /**
   * Empty = every tenant. With multitenancy, tenant 0 widens the scope only for a platform admin or
   * the technical user; any other tenant-0 caller stays in tenant 0 and sees no other tenant.
   */
  private Optional<Long> scopedTenantId() {
    Long tenantId = authenticatedUser.getTenantId();
    if (tenantId == null) {
      tenantId = TenantContext.getCurrentTenant();
    }
    if (tenantId == null) {
      // Only without multitenancy; with it, the tenant-support subclass filters by TenantContext.
      return Optional.empty();
    }
    if (tenantId.equals(0L) && (!multitenancyEnabled
        || authenticatedUser.isPlatformAdmin() || authenticatedUser.isTechnicalUser())) {
      return Optional.empty();
    }
    return Optional.of(tenantId);
  }

  public SearchResult<Agency> searchAgenciesByKeyword(EntityManager entityManager,
      AgencyAdminSearch agencyAdminSearch) {
    CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();

    CriteriaQuery<Agency> criteriaQuery = criteriaBuilder.createQuery(Agency.class);
    Root<Agency> root = criteriaQuery.from(Agency.class);
    root.alias("agency");

    Predicate[] searchAgenciesWithKeywordFilterPredicate = createSearchAgenciesWithKeywordFilterPredicate(
        agencyAdminSearch, criteriaBuilder, root);

    criteriaQuery.where(searchAgenciesWithKeywordFilterPredicate);

    var agencies = applySortingAndPagination(entityManager, agencyAdminSearch,
        criteriaBuilder, criteriaQuery, root);

    CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
    Root<Agency> countRoot = countQuery.from(Agency.class);
    countQuery.select(criteriaBuilder.count(countRoot)).where(
        createSearchAgenciesWithKeywordFilterPredicate(
            agencyAdminSearch, criteriaBuilder, countRoot));
    Long totalResultSize = entityManager.createQuery(countQuery).getSingleResult();
    return new SearchResult<>(agencies, totalResultSize);
  }

  protected Predicate[] createSearchAgenciesWithKeywordFilterPredicate(
      AgencyAdminSearch agencyAdminSearch, CriteriaBuilder criteriaBuilder,
      Root<Agency> root) {
    return new Predicate[]{
        keywordSearchPredicate(agencyAdminSearch, criteriaBuilder, root),
        searchFilterPredicate(agencyAdminSearch, criteriaBuilder, root),
        agencyAdminFilterPredicate(criteriaBuilder, root)};
  }

  public SearchResult<Agency> searchAgenciesWithoutKeywordFilter(EntityManager entityManager,
      AgencyAdminSearch agencyAdminSearch) {
    CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
    CriteriaQuery<Agency> criteriaQuery = criteriaBuilder.createQuery(Agency.class);
    Root<Agency> root = criteriaQuery.from(Agency.class);
    root.alias("agency");

    criteriaQuery.where(
        agenciesWithoutKeywordFilterPredicates(agencyAdminSearch, criteriaBuilder, root));

    var agencies = applySortingAndPagination(entityManager, agencyAdminSearch,
        criteriaBuilder, criteriaQuery, root);

    CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
    Root<Agency> countRoot = countQuery.from(Agency.class);
    countQuery.select(criteriaBuilder.count(countRoot)).where(
        agenciesWithoutKeywordFilterPredicates(agencyAdminSearch, criteriaBuilder, countRoot));
    Long totalResultSize = entityManager.createQuery(countQuery).getSingleResult();
    return new SearchResult<>(agencies, totalResultSize);
  }

  protected Predicate[] agenciesWithoutKeywordFilterPredicates(
      AgencyAdminSearch agencyAdminSearch, CriteriaBuilder criteriaBuilder, Root<Agency> root) {
    return new Predicate[]{
        searchFilterPredicate(agencyAdminSearch, criteriaBuilder, root),
        agencyAdminFilterPredicate(criteriaBuilder, root)};
  }

  /** The optional filters; they are ANDed with the role scope, so they can only narrow it. */
  protected Predicate searchFilterPredicate(AgencyAdminSearch agencyAdminSearch,
      CriteriaBuilder criteriaBuilder, Root<Agency> root) {
    Predicate notDeleted = agencyAdminSearch.isExcludeDeleted()
        ? criteriaBuilder.isNull(root.get(DELETE_DATE_FIELD))
        : criteriaBuilder.conjunction();
    Predicate inTenant = agencyAdminSearch.getTenantId() != null
        ? criteriaBuilder.equal(root.get(TENANT_ID_SEARCH_FIELD), agencyAdminSearch.getTenantId())
        : criteriaBuilder.conjunction();
    return criteriaBuilder.and(notDeleted, inTenant);
  }

  Predicate agencyAdminFilterPredicate(CriteriaBuilder criteriaBuilder, Root<Agency> root) {
    Predicate tenantScopePredicate = tenantScopePredicate(criteriaBuilder, root);
    if (authenticatedUser.hasRestrictedAgencyPriviliges()) {
      var adminAgencyIds = userAdminService.getAdminUserAgencyIds(authenticatedUser.requireUserId());
      if (!adminAgencyIds.isEmpty()) {
        return criteriaBuilder.and(
            tenantScopePredicate, createPredicateForAgencyAdmin(criteriaBuilder, root, adminAgencyIds));
      } else {
        return alwaysFalsePredicate(criteriaBuilder);
      }
    } else {
      return tenantScopePredicate;
    }
  }

  private Predicate tenantScopePredicate(CriteriaBuilder criteriaBuilder, Root<Agency> root) {
    Optional<Long> tenantId = scopedTenantId();
    return tenantId.isPresent()
        ? criteriaBuilder.equal(root.get(TENANT_ID_SEARCH_FIELD), tenantId.get())
        : alwaysTruePredicate(criteriaBuilder);
  }

  private Predicate alwaysFalsePredicate(CriteriaBuilder criteriaBuilder) {
    return criteriaBuilder.equal(criteriaBuilder.literal(1), criteriaBuilder.literal(2));
  }

  private Predicate alwaysTruePredicate(CriteriaBuilder criteriaBuilder) {
    return criteriaBuilder.conjunction();
  }


  protected Predicate createPredicateForAgencyAdmin(CriteriaBuilder criteriaBuilder,
      Root<Agency> root, Collection<Long> adminAgencyIds) {
    return criteriaBuilder.and(root.get("id").in(adminAgencyIds));
  }

  private List<Agency> applySortingAndPagination(EntityManager entityManager,
      AgencyAdminSearch agencyAdminSearch,
      CriteriaBuilder criteriaBuilder, CriteriaQuery<Agency> criteriaQuery, Root<Agency> root) {
    // Sorting
    if (agencyAdminSearch.getSortField() != null && !agencyAdminSearch.getSortField().isEmpty()) {
      Path<String> expression = root.get(agencyAdminSearch.getSortField());
      Class<?> javaType = expression.getJavaType();
      addOrderBy(agencyAdminSearch, criteriaBuilder, criteriaQuery, expression, javaType);
    }

    // Pagination. Page size and page number arrive straight from query parameters, so both can
    // be negative. Only the zero case was handled, and a negative size reached
    // setMaxResults() and surfaced as a 500 ("Max results cannot be negative"). Non-positive
    // size now means no results, and a non-positive page number means the first page — the same
    // contract the zero case already had (#206).
    int firstResult = agencyAdminSearch.getPageNumber() <= 1 ? 0
        : (agencyAdminSearch.getPageNumber() - 1) * agencyAdminSearch.getPageSize();
    return agencyAdminSearch.getPageSize() <= 0 ? Lists.newArrayList()
        : entityManager.createQuery(criteriaQuery)
            .setFirstResult(firstResult)
            .setMaxResults(agencyAdminSearch.getPageSize())
            .getResultList();
  }

  private void addOrderBy(AgencyAdminSearch agencyAdminSearch,
      CriteriaBuilder criteriaBuilder, CriteriaQuery<Agency> criteriaQuery, Path<String> expression,
      Class<?> javaType) {
    if (String.class.equals(javaType)) {
      Expression<String> toLower = criteriaBuilder.lower(
          expression);
      Order order =
          agencyAdminSearch.isAscending() ? criteriaBuilder.asc(toLower) : criteriaBuilder.desc(
              toLower);
      criteriaQuery.orderBy(order);
    } else {
      Order order =
          agencyAdminSearch.isAscending() ? criteriaBuilder.asc(expression) : criteriaBuilder.desc(
              expression);
      criteriaQuery.orderBy(order);
    }
  }

  protected Predicate keywordSearchPredicate(AgencyAdminSearch agencyAdminSearch,
      CriteriaBuilder criteriaBuilder, Root<Agency> root) {
    var matches = agencyAdminSearch.getTokenMatches();
    if (matches.isEmpty()) {
      matches = AdminSearchText.tokens(agencyAdminSearch.getKeyword()).stream()
          .map(token -> new AgencyAdminSearch.TokenMatches(token, Set.of(), Set.of())).toList();
    }
    return criteriaBuilder.and(matches.stream().map(match -> {
      Predicate text = keywordTextPredicate(match.token(), criteriaBuilder, root);
      Predicate tenant = match.tenantIds().isEmpty() ? criteriaBuilder.disjunction()
          : root.get("tenantId").in(match.tenantIds());
      Predicate topic = match.agencyIds().isEmpty() ? criteriaBuilder.disjunction()
          : root.get("id").in(match.agencyIds());
      return criteriaBuilder.or(text, tenant, topic);
    }).toArray(Predicate[]::new));
  }

  private Predicate keywordTextPredicate(String keyword, CriteriaBuilder cb, Root<Agency> root) {
    String pattern = "%" + escapeLikeWildcards(keyword) + "%";
    var fields = new ArrayList<Expression<String>>();
    fields.add(root.get("id").as(String.class));
    for (String field : List.of(NAME_SEARCH_FIELD, "street", POST_CODE_SEARCH_FIELD, CITY_SEARCH_FIELD)) {
      fields.add(root.get(field));
    }
    return cb.or(fields.stream().map(field -> cb.like(
        cb.function("admin_search_text", String.class, field), pattern, LIKE_ESCAPE))
        .toArray(Predicate[]::new));
  }

  /** A typed "%" or "_" is text, not a wildcard; "!" because MariaDB mangles a backslash. */
  static String escapeLikeWildcards(String text) {
    return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }


}
