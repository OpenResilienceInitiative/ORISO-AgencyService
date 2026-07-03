package de.caritas.cob.agencyservice.api.repository.agencytopic;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for the department-level ({@code Fachbereich} = agency × topic) rows, used by the
 * per-department data privacy policy (DPP) flow to load and update a single {@code agency_topic}
 * without going through the parent {@code Agency} aggregate.
 */
public interface AgencyTopicRepository extends JpaRepository<AgencyTopic, Long> {

  /** Finds the department row for a given agency and topic (the natural key of a Fachbereich). */
  Optional<AgencyTopic> findByAgency_IdAndTopicId(Long agencyId, Long topicId);
}
