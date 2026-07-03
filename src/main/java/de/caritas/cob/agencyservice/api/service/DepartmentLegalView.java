package de.caritas.cob.agencyservice.api.service;

/**
 * Public read view of a department's (Fachbereich = agency × topic) legal texts. Both fields carry
 * the stored JSON language→HTML map string when the respective text is PUBLISHED, and {@code null}
 * when it is a draft or was never authored — drafts are never exposed to users.
 */
public record DepartmentLegalView(String dppContent, String imprintContent) {}
