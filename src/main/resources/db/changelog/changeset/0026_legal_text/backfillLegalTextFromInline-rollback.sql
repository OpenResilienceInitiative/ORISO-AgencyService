-- Unlink and remove only the migrated objects; the inline columns still hold the original texts.
UPDATE `agencyservice`.`agency_topic` SET `dpp_id` = NULL WHERE `dpp_id` IS NOT NULL;
UPDATE `agencyservice`.`agency_topic` SET `imprint_id` = NULL WHERE `imprint_id` IS NOT NULL;
DELETE FROM `agencyservice`.`legal_text` WHERE `label` LIKE 'Migrated %';
