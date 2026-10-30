package de.caritas.cob.agencyservice.api.controller;

import de.caritas.cob.agencyservice.api.repository.deletionaudit.DeletionAuditOutboxEntry;
import de.caritas.cob.agencyservice.api.repository.deletionaudit.DeletionAuditOutboxRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pull side of the agency deletion-log outbox (OBP 2.1 #130). UserService reads pending rows,
 * records them in its deletion log and acknowledges them. Authorization is in {@code
 * SecurityConfig}: the RUNTIME task actor with {@code deletion-audit-read} only.
 */
@RestController
@RequiredArgsConstructor
public class DeletionAuditOutboxController {

  static final int MAX_BATCH = 100;

  private final @NonNull DeletionAuditOutboxRepository repository;

  @GetMapping("/internal/deletion-audit-outbox")
  public List<PendingEntry> pending(@RequestParam(defaultValue = "50") int limit) {
    int size = Math.max(1, Math.min(limit, MAX_BATCH));
    return repository.findPending(PageRequest.of(0, size)).stream()
        .map(PendingEntry::of)
        .toList();
  }

  @PostMapping("/internal/deletion-audit-outbox/acknowledgements")
  public ResponseEntity<Acknowledged> acknowledge(@RequestBody Acknowledgement request) {
    if (request == null
        || request.ids() == null
        || request.ids().isEmpty()
        || request.ids().size() > MAX_BATCH
        || request.ids().stream().anyMatch(id -> id == null || id <= 0)) {
      return ResponseEntity.badRequest().build();
    }
    int count = repository.markDelivered(request.ids(), LocalDateTime.now(ZoneOffset.UTC));
    return ResponseEntity.ok(new Acknowledged(count));
  }

  public record PendingEntry(
      Long id, Long agencyId, Long tenantId, String outcome, String createdAt) {
    static PendingEntry of(DeletionAuditOutboxEntry entry) {
      return new PendingEntry(
          entry.getId(),
          entry.getAgencyId(),
          entry.getTenantId(),
          entry.getOutcome(),
          entry.getCreatedAt().toString());
    }
  }

  public record Acknowledgement(List<Long> ids) {}

  public record Acknowledged(int acknowledged) {}
}
