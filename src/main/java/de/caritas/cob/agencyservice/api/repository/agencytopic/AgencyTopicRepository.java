package de.caritas.cob.agencyservice.api.repository.agencytopic;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for the department-level ({@code Fachbereich} = agency × topic) rows, used by the
 * per-department data privacy policy (DPP) flow to load and update a single {@code agency_topic}
 * without going through the parent {@code Agency} aggregate.
 */
public interface AgencyTopicRepository extends JpaRepository<AgencyTopic, Long> {

  /** Finds the department row for a given agency and topic (the natural key of a Fachbereich). */
  Optional<AgencyTopic> findByAgency_IdAndTopicId(Long agencyId, Long topicId);

  /** Finds all department rows for a given agency. */
  @Query(value = "select * from agency_topic where agency_id = :agencyId", nativeQuery = true)
  List<AgencyTopic> findAllByAgencyId(@Param("agencyId") Long agencyId);

  /** How many departments reference this shared DPP text ("used by N", ADR-014). */
  long countByDpp_Id(Long legalTextId);

  /** How many departments reference this shared Impressum text ("used by N", ADR-014). */
  long countByImprint_Id(Long legalTextId);
}
