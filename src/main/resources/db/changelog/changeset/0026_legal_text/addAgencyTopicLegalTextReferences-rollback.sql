ALTER TABLE `agencyservice`.`agency_topic` DROP FOREIGN KEY IF EXISTS `fk_agency_topic_dpp`;
ALTER TABLE `agencyservice`.`agency_topic` DROP FOREIGN KEY IF EXISTS `fk_agency_topic_imprint`;
ALTER TABLE `agencyservice`.`agency_topic` DROP INDEX IF EXISTS `idx_agency_topic_dpp_id`;
ALTER TABLE `agencyservice`.`agency_topic` DROP INDEX IF EXISTS `idx_agency_topic_imprint_id`;
ALTER TABLE `agencyservice`.`agency_topic` DROP COLUMN IF EXISTS `dpp_id`;
ALTER TABLE `agencyservice`.`agency_topic` DROP COLUMN IF EXISTS `imprint_id`;
