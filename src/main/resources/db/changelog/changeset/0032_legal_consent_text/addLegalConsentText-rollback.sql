ALTER TABLE `agencyservice`.`agency_topic` DROP COLUMN IF EXISTS `consent_text`;
ALTER TABLE `agencyservice`.`agency` DROP COLUMN IF EXISTS `consent_text`;
ALTER TABLE `agencyservice`.`legal_text` DROP COLUMN IF EXISTS `consent_text`;
ALTER TABLE `agencyservice`.`legal_text_version` DROP COLUMN IF EXISTS `consent_text`;
