ALTER TABLE `agencyservice`.`agency_topic`
ADD COLUMN IF NOT EXISTS content_dpp longtext NULL;

ALTER TABLE `agencyservice`.`agency_topic`
ADD COLUMN IF NOT EXISTS publication_status varchar(20) NOT NULL DEFAULT 'DRAFT';
