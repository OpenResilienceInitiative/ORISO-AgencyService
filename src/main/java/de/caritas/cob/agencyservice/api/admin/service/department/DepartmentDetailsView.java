package de.caritas.cob.agencyservice.api.admin.service.department;

/**
 * Read view of a department's (Fachbereich = agency × topic) stored contact detail overrides
 * (ORISO-Admin#197). {@code null} {@code openingHours}/{@code floorLocation} mean "no override,
 * inherits the Beratungsstelle value"; {@code null} {@code phoneExtension} means the department
 * has no extension (there is no agency-level counterpart to inherit). The admin editor needs the
 * raw overrides (not resolved values) so it can render the inheritance affordance; resolution
 * happens on the public API only.
 */
public record DepartmentDetailsView(
    String openingHours, String phoneExtension, String floorLocation) {}
