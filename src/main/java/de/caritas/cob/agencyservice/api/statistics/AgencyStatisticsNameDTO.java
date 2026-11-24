package de.caritas.cob.agencyservice.api.statistics;

/**
 * A Beratungsstelle as the platform statistics panel names it (ORISO-UserService#1666): id, name
 * and owning Träger only, so the Statistik-Leser learns nothing else about the agency.
 */
public record AgencyStatisticsNameDTO(Long id, String name, Long tenantId) {}
