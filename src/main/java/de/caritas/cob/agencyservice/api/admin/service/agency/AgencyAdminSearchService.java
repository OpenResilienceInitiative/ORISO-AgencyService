package de.caritas.cob.agencyservice.api.admin.service.agency;

import static io.micrometer.common.util.StringUtils.isBlank;

import com.google.common.collect.Lists;
import de.caritas.cob.agencyservice.api.admin.hallink.SearchResultLinkBuilder;
import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.service.TenantService;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

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

  protected static final Pattern ONLY_SPECIAL_CHARS = Pattern.compile("[^a-zA-Z0-9]+");
  protected static final String NAME_SEARCH_FIELD = "name";
  protected static final String POST_CODE_SEARCH_FIELD = "postCode";
  protected static final String CITY_SEARCH_FIELD = "city";
  protected static final String TENANT_ID_SEARCH_FIELD = "tenantId";
  protected static final String DELETE_DATE_FIELD = "deleteDate";
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

  /**
   * Same as {@link #searchAgencies(String, Integer, Integer, Sort)}, with the invite-bar type-ahead
   * additions (ORISO-Admin#1026): the keyword also matches the name of one of the agency's topics
   * (Fachbereich), and {@code excludeDeleted} leaves soft-deleted agencies out. Every result carries
   * its tenant's name (best effort) and its topic names resolved in the agency's own tenant.
   */
  public AgencyAdminSearchResultDTO searchAgencies(final String keyword, final Integer page,
      final Integer perPage, Sort sort, boolean excludeDeleted) {

    SearchResult<Agency> queryResult = new SearchResult<>(Lists.newArrayList(), 0L);
    boolean withKeyword = !(isBlank(keyword) || hasOnlySpecialCharacters(keyword));

    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      var agencyAdminSearch = AgencyAdminSearch.builder()
          .keyword(keyword)
          .pageNumber(page)
          .pageSize(perPage)
          .sortField(sort != null && sort.getField() != null ? sort.getField().getValue() : null)
          .ascending(
              sort != null && sort.getOrder() != null ? sort.getOrder().equals(OrderEnum.ASC)
                  : true)
          .excludeDeleted(excludeDeleted)
          .topicMatchedAgencyIds(
              withKeyword ? agencyIdsWithTopicNameMatching(entityManager, keyword) : Set.of())
          .build();
      queryResult = withKeyword
          ? searchAgenciesByKeyword(entityManager, agencyAdminSearch)
          : searchAgenciesWithoutKeywordFilter(entityManager, agencyAdminSearch);
      // Load the topics of the page while the entity manager is open. The paged queries no longer
      // fetch-join them: a collection fetch plus setMaxResults made Hibernate page IN MEMORY, i.e.
      // load every matching agency for each page. @BatchSize loads them 50 agencies at a time.
      queryResult.getResult().forEach(agency -> Hibernate.initialize(agency.getAgencyTopics()));
    }

    var agencies = queryResult.getResult();
    if (topicsFeatureEnabled) {
      agencyTopicEnrichmentService.enrichAgenciesWithTopics(agencies);
    }

    Map<Long, Optional<String>> tenantNames = new HashMap<>();
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

  /**
   * The agencies offering a topic whose name contains the keyword. Topics live in
   * ConsultingTypeService per tenant, so the names are looked up in every tenant the caller may
   * see: a tenant-bound admin's own tenant, for the platform admin every tenant that has agencies.
   * The result is only a widening of the keyword match — the tenant and agency-admin scope
   * predicates still apply to it.
   */
  private Set<Long> agencyIdsWithTopicNameMatching(EntityManager entityManager, String keyword) {
    if (!topicsFeatureEnabled || agencyTopicEnrichmentService == null) {
      return Set.of();
    }
    Long callerTenantId = callerTenantId();
    Collection<Long> tenantIds;
    if (callerTenantId == null || callerTenantId.equals(0L)) {
      tenantIds = entityManager
          .createQuery("select distinct a.tenantId from Agency a", Long.class)
          .getResultList();
    } else {
      tenantIds = Collections.singletonList(callerTenantId);
    }
    Set<Long> topicIds = new HashSet<>();
    String needle = keyword.trim().toLowerCase(Locale.ROOT);
    for (Long tenantId : tenantIds) {
      agencyTopicEnrichmentService.topicsOfTenant(tenantId).stream()
          .filter(topic -> topic.getId() != null && topic.getName() != null)
          .filter(topic -> topic.getName().toLowerCase(Locale.ROOT).contains(needle))
          .forEach(topic -> topicIds.add(topic.getId()));
    }
    if (topicIds.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(entityManager
        .createQuery(
            "select distinct t.agency.id from AgencyTopic t where t.topicId in :topicIds",
            Long.class)
        .setParameter("topicIds", topicIds)
        .getResultList());
  }

  private Long callerTenantId() {
    Long tenantId = authenticatedUser.getTenantId();
    return tenantId != null ? tenantId : TenantContext.getCurrentTenant();
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
        deletedFilterPredicate(agencyAdminSearch, criteriaBuilder, root),
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
        deletedFilterPredicate(agencyAdminSearch, criteriaBuilder, root),
        agencyAdminFilterPredicate(criteriaBuilder, root)};
  }

  protected Predicate deletedFilterPredicate(AgencyAdminSearch agencyAdminSearch,
      CriteriaBuilder criteriaBuilder, Root<Agency> root) {
    return agencyAdminSearch.isExcludeDeleted()
        ? criteriaBuilder.isNull(root.get(DELETE_DATE_FIELD))
        : criteriaBuilder.conjunction();
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
    Long tenantId = authenticatedUser.getTenantId();
    if (tenantId == null) {
      tenantId = TenantContext.getCurrentTenant();
    }
    // technical/super context is represented by tenant 0 and may see all tenants
    if (tenantId == null || tenantId.equals(0L)) {
      return alwaysTruePredicate(criteriaBuilder);
    }
    return criteriaBuilder.equal(root.get(TENANT_ID_SEARCH_FIELD), tenantId);
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
    String keyword = agencyAdminSearch.getKeyword();
    Predicate textMatch = keywordTextPredicate(keyword, criteriaBuilder, root);
    Set<Long> topicMatches = agencyAdminSearch.getTopicMatchedAgencyIds();
    return topicMatches == null || topicMatches.isEmpty()
        ? textMatch
        : criteriaBuilder.or(textMatch, root.get("id").in(topicMatches));
  }

  private Predicate keywordTextPredicate(String keyword, CriteriaBuilder criteriaBuilder,
      Root<Agency> root) {
    return criteriaBuilder.or(
        criteriaBuilder.like(criteriaBuilder.lower(root.get(NAME_SEARCH_FIELD)),
            "%" + keyword.toLowerCase() + "%"),
        criteriaBuilder.like(
            criteriaBuilder.lower(root.get(POST_CODE_SEARCH_FIELD)),
            "%" + keyword.toLowerCase() + "%"),
        criteriaBuilder.like(criteriaBuilder.lower(root.get(CITY_SEARCH_FIELD)),
            "%" + keyword.toLowerCase() + "%")
    );
  }

  private boolean hasOnlySpecialCharacters(String str) {
    return ONLY_SPECIAL_CHARS.matcher(str).matches();
  }

}
