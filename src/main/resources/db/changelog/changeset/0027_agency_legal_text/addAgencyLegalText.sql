-- ADR-014 middle level of the chain tenant -> agency -> department: the Beratungsstelle's own
-- data privacy policy and imprint, stored as a JSON language->HTML map like the department
-- columns. No publication_status counterpart: what is stored is what is in force, and every
-- Fachbereich inherits it until it publishes one of its own.
ALTER TABLE `agencyservice`.`agency`
ADD COLUMN IF NOT EXISTS content_dpp longtext NULL;

ALTER TABLE `agencyservice`.`agency`
ADD COLUMN IF NOT EXISTS content_imprint longtext NULL;
