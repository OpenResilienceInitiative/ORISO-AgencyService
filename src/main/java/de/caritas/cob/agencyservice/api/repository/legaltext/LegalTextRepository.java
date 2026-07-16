package de.caritas.cob.agencyservice.api.repository.legaltext;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface LegalTextRepository extends CrudRepository<LegalText, Long> {

  List<LegalText> findByTenantIdAndKindOrderByLabelAsc(Long tenantId, LegalTextKind kind);
}
