package de.caritas.cob.agencyservice.api.repository.agencyadmincontrol;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface AgencyAdminControlRepository extends CrudRepository<AgencyAdminControlEntity, Long> {

  Optional<AgencyAdminControlEntity> findTopByOrderByIdAsc();
}
