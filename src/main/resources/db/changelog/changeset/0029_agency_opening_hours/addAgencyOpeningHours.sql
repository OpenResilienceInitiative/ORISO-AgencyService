ALTER TABLE `agencyservice`.`agency`
  ADD COLUMN IF NOT EXISTS `opening_hours` VARCHAR(1000) NULL;
