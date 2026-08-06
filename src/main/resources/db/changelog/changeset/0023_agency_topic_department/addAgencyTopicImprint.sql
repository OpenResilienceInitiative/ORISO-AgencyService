-- ADR-003: the department's (Fachbereich = agency x topic) own imprint (Impressum), stored as a
-- JSON language->HTML map like content_dpp, with its own draft/published lifecycle.
ALTER TABLE `agencyservice`.`agency_topic`
ADD COLUMN IF NOT EXISTS content_imprint longtext NULL;

ALTER TABLE `agencyservice`.`agency_topic`
ADD COLUMN IF NOT EXISTS publication_status_imprint varchar(20) NOT NULL DEFAULT 'DRAFT';
