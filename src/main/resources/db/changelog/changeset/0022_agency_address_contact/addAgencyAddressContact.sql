ALTER TABLE `agencyservice`.`agency` ADD COLUMN IF NOT EXISTS street varchar(255) NULL;
ALTER TABLE `agencyservice`.`agency` ADD COLUMN IF NOT EXISTS house_number varchar(20) NULL;
ALTER TABLE `agencyservice`.`agency` ADD COLUMN IF NOT EXISTS floor_building varchar(100) NULL;
ALTER TABLE `agencyservice`.`agency` ADD COLUMN IF NOT EXISTS country varchar(100) NULL;
ALTER TABLE `agencyservice`.`agency` ADD COLUMN IF NOT EXISTS phone varchar(30) NULL;
ALTER TABLE `agencyservice`.`agency` ADD COLUMN IF NOT EXISTS phone_secondary varchar(30) NULL;
ALTER TABLE `agencyservice`.`agency` ADD COLUMN IF NOT EXISTS email varchar(255) NULL;
