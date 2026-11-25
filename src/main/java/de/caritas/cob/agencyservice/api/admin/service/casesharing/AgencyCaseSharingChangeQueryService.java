package de.caritas.cob.agencyservice.api.admin.service.casesharing;

import de.caritas.cob.agencyservice.api.admin.service.agency.AgencyAdminSearchService;
import de.caritas.cob.agencyservice.api.model.AgencyCaseSharingChangeDTO;
import de.caritas.cob.agencyservice.api.model.AgencyCaseSharingChangeDTO.SourceEnum;
import de.caritas.cob.agencyservice.api.model.AgencyCaseSharingChangeListDTO;
import de.caritas.cob.agencyservice.api.model.AgencyCaseSharingMode;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.casesharingchange.AgencyCaseSharingChange;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Paged read of the case sharing change log for the Admin "Logs" area (ORISO-Frontend#2494).
 *
 * <p>Scope: every row is joined to its agency and filtered with {@link
 * AgencyAdminSearchService#callerScopePredicate}, the same role scope as the agency search {@code
 * GET /agencyadmin/agencies}. Like that search it runs on its own entity manager, outside the
 * Hibernate tenant filter, so the predicate alone decides.
 */
@Service
@RequiredArgsConstructor
public class AgencyCaseSharingChangeQueryService {

  public static final int DEFAULT_PER_PAGE = 20;
  public static final int MAX_PER_PAGE = 100;

  /** Fixed wire shape: seconds, UTC, trailing Z. */
  private static final DateTimeFormatter CHANGED_AT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

  private final @NonNull EntityManagerFactory entityManagerFactory;
  private final @NonNull AgencyAdminSearchService agencyAdminSearchService;

  /**
   * One page of changes, newest first.
   *
   * @param agencyId optional agency filter, narrows the caller's scope only
   * @param page 0-based page, null = 0
   * @param perPage entries per page, null = 20, capped at 100
   */
  public AgencyCaseSharingChangeListDTO findChanges(Long agencyId, Integer page, Integer perPage) {
    int pageNumber = page == null ? 0 : Math.max(0, page);
    int pageSize = perPage == null ? DEFAULT_PER_PAGE : Math.clamp(perPage, 1, MAX_PER_PAGE);

    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      var cb = entityManager.getCriteriaBuilder();

      var countQuery = cb.createQuery(Long.class);
      var countChange = countQuery.from(AgencyCaseSharingChange.class);
      var countAgency = countQuery.from(Agency.class);
      countQuery.select(cb.count(countChange))
          .where(scope(cb, countChange, countAgency, agencyId));
      long total = entityManager.createQuery(countQuery).getSingleResult();

      List<AgencyCaseSharingChangeDTO> entries = new ArrayList<>();
      if (total > (long) pageNumber * pageSize) {
        var query = cb.createTupleQuery();
        var change = query.from(AgencyCaseSharingChange.class);
        var agency = query.from(Agency.class);
        query.multiselect(change.alias("change"), agency.get("name").alias("agencyName"))
            .where(scope(cb, change, agency, agencyId))
            .orderBy(cb.desc(change.get("changedAt")), cb.desc(change.get("id")));
        entityManager.createQuery(query)
            .setFirstResult(pageNumber * pageSize)
            .setMaxResults(pageSize)
            .getResultList()
            .forEach(row -> entries.add(toDto(row)));
      }
      return new AgencyCaseSharingChangeListDTO().total(total).entries(entries);
    }
  }

  private Predicate[] scope(CriteriaBuilder cb, Root<AgencyCaseSharingChange> change,
      Root<Agency> agency, Long agencyId) {
    var join = cb.equal(change.get("agencyId"), agency.get("id"));
    var callerScope = agencyAdminSearchService.callerScopePredicate(cb, agency);
    var filter = agencyId == null ? cb.conjunction() : cb.equal(change.get("agencyId"), agencyId);
    return new Predicate[] {join, callerScope, filter};
  }

  private static AgencyCaseSharingChangeDTO toDto(Tuple row) {
    var change = row.get("change", AgencyCaseSharingChange.class);
    return new AgencyCaseSharingChangeDTO()
        .id(change.getId())
        .agencyId(change.getAgencyId())
        .agencyName(row.get("agencyName", String.class))
        .tenantId(change.getTenantId())
        .changedAt(formatUtc(change.getChangedAt()))
        .changedBy(change.getChangedByName())
        .previousModel(modelOrNull(change.getPreviousModel()))
        .newModel(modelOrNull(change.getNewModel()))
        .source(SourceEnum.fromValue(change.getSource().name()));
  }

  static String formatUtc(LocalDateTime utc) {
    return utc.atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(CHANGED_AT);
  }

  private static AgencyCaseSharingMode modelOrNull(String value) {
    return value == null ? null : AgencyCaseSharingMode.fromValue(value);
  }
}
