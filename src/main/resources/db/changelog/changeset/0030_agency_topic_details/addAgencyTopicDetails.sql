ALTER TABLE `agencyservice`.`agency_topic`
  ADD COLUMN IF NOT EXISTS `opening_hours` VARCHAR(1000) NULL;
ALTER TABLE `agencyservice`.`agency_topic`
  ADD COLUMN IF NOT EXISTS `phone_extension` VARCHAR(50) NULL;
ALTER TABLE `agencyservice`.`agency_topic`
  ADD COLUMN IF NOT EXISTS `floor_location` VARCHAR(100) NULL;
