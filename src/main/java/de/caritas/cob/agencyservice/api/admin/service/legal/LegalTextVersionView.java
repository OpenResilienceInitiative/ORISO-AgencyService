package de.caritas.cob.agencyservice.api.admin.service.legal;

import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextLevel;
import java.time.LocalDateTime;

/**
 * One entry of the ADR-021 publication history as the admin panel reads it: the verbatim wording
 * plus the three facts #212 asks for — which level and kind it belongs to, who published it and
 * when, and until when it was in force ({@code supersededAt == null} = still in force).
 *
 * <p>{@code publishedBy} is {@code null} where no authenticated publisher was recorded. That is an
 * honest unknown, not a placeholder: pre-existing rows carry no history at all rather than a date
 * guessed from {@code update_date}, which may well be a draft-save timestamp.
 */
public record LegalTextVersionView(
    Long id,
    LegalTextKind kind,
    LegalTextLevel ownerLevel,
    Long ownerId,
    String content,
    LocalDateTime publishedAt,
    String publishedBy,
    LocalDateTime supersededAt) {}
