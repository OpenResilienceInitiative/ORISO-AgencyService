-- ADR-014: departments reference their legal texts instead of owning inline copies. The inline
-- content_dpp / content_imprint columns stay for one release as a read-fallback and rollback
-- anchor; the read path ignores them once a reference is set.
ALTER TABLE `agencyservice`.`agency_topic`
ADD COLUMN IF NOT EXISTS `dpp_id` bigint(21) NULL;

ALTER TABLE `agencyservice`.`agency_topic`
ADD COLUMN IF NOT EXISTS `imprint_id` bigint(21) NULL;

ALTER TABLE `agencyservice`.`agency_topic`
ADD CONSTRAINT `fk_agency_topic_dpp` FOREIGN KEY (`dpp_id`)
REFERENCES `agencyservice`.`legal_text` (`id`) ON UPDATE CASCADE;

ALTER TABLE `agencyservice`.`agency_topic`
ADD CONSTRAINT `fk_agency_topic_imprint` FOREIGN KEY (`imprint_id`)
REFERENCES `agencyservice`.`legal_text` (`id`) ON UPDATE CASCADE;
