package de.caritas.cob.agencyservice.api.service.legal;

/**
 * The outcome of one walk up the ADR-021 ladder: the wording that is actually in force, the level it
 * came from, and — where AgencyService owns a publication history for that level — the id of the
 * version it corresponds to.
 *
 * @param content the stored JSON language→HTML map string, or {@code null} when nothing is authored
 *     anywhere on the chain
 * @param consentText the consent sentence stored with the policy (ADR-021 decision 4); always
 *     {@code null} for imprints, which are never consent-bearing (decision 7)
 * @param sourceLevel which rung answered — never omitted, because "the privacy policy" without a
 *     level is not a valid statement (decision 1)
 * @param versionId the {@code legal_text_version} row this wording corresponds to, or {@code null}
 *     for the Träger and platform levels, whose history lives in ORISO-TenantService rather than
 *     here. This is the identifier a consuming service pins a recorded consent to (ORISO-UserService
 *     {@code session.consented_legal_version_id}, ADR-022 decision 2) — without it, "is this room
 *     cleared for the current version" cannot be answered at all.
 */
public record ResolvedLegalText(
    String content, String consentText, LegalTextSourceLevel sourceLevel, Long versionId) {

  /** Nothing authored on any level. */
  public static ResolvedLegalText none() {
    return new ResolvedLegalText(null, null, LegalTextSourceLevel.NONE, null);
  }

  public boolean isPresent() {
    return content != null && !content.isBlank();
  }
}
