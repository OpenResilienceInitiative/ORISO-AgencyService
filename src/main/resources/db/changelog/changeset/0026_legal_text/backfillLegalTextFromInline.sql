-- ADR-014 backfill: lift the inline per-department legal texts into shared legal_text objects.
-- Byte-identical texts (same tenant, same content, same publication status) collapse into ONE
-- shared row, so departments that legally share a document reference a single maintained object.
-- Only reference-less rows with real content are lifted; re-running is a no-op because linked
-- rows are excluded by the dpp_id/imprint_id IS NULL guards.

-- 1) one legal_text per distinct (tenant, content, status) DPP
INSERT INTO `agencyservice`.`legal_text`
    (`id`, `tenant_id`, `kind`, `label`, `content`, `publication_status`, `create_date`, `update_date`)
SELECT NEXT VALUE FOR `agencyservice`.`sequence_legal_text`,
       src.`tenant_id`,
       'DPP',
       CONCAT('Migrated data privacy policy ', ROW_NUMBER() OVER (ORDER BY src.`tenant_id`)),
       src.`content`,
       src.`status`,
       UTC_TIMESTAMP(),
       UTC_TIMESTAMP()
FROM (
    SELECT DISTINCT a.`tenant_id` AS `tenant_id`,
                    at.`content_dpp` AS `content`,
                    at.`publication_status` AS `status`
    FROM `agencyservice`.`agency_topic` at
    JOIN `agencyservice`.`agency` a ON a.`id` = at.`agency_id`
    WHERE at.`content_dpp` IS NOT NULL AND at.`content_dpp` <> '' AND at.`dpp_id` IS NULL
) src;

-- 2) link each department to its (possibly shared) DPP object
UPDATE `agencyservice`.`agency_topic` at
JOIN `agencyservice`.`agency` a ON a.`id` = at.`agency_id`
JOIN `agencyservice`.`legal_text` lt
    ON lt.`kind` = 'DPP'
    AND (lt.`tenant_id` <=> a.`tenant_id`)
    AND lt.`content` = at.`content_dpp`
    AND lt.`publication_status` = at.`publication_status`
SET at.`dpp_id` = lt.`id`
WHERE at.`content_dpp` IS NOT NULL AND at.`content_dpp` <> '' AND at.`dpp_id` IS NULL;

-- 3) one legal_text per distinct (tenant, content, status) imprint
INSERT INTO `agencyservice`.`legal_text`
    (`id`, `tenant_id`, `kind`, `label`, `content`, `publication_status`, `create_date`, `update_date`)
SELECT NEXT VALUE FOR `agencyservice`.`sequence_legal_text`,
       src.`tenant_id`,
       'IMPRINT',
       CONCAT('Migrated imprint ', ROW_NUMBER() OVER (ORDER BY src.`tenant_id`)),
       src.`content`,
       src.`status`,
       UTC_TIMESTAMP(),
       UTC_TIMESTAMP()
FROM (
    SELECT DISTINCT a.`tenant_id` AS `tenant_id`,
                    at.`content_imprint` AS `content`,
                    at.`publication_status_imprint` AS `status`
    FROM `agencyservice`.`agency_topic` at
    JOIN `agencyservice`.`agency` a ON a.`id` = at.`agency_id`
    WHERE at.`content_imprint` IS NOT NULL AND at.`content_imprint` <> '' AND at.`imprint_id` IS NULL
) src;

-- 4) link each department to its (possibly shared) imprint object
UPDATE `agencyservice`.`agency_topic` at
JOIN `agencyservice`.`agency` a ON a.`id` = at.`agency_id`
JOIN `agencyservice`.`legal_text` lt
    ON lt.`kind` = 'IMPRINT'
    AND (lt.`tenant_id` <=> a.`tenant_id`)
    AND lt.`content` = at.`content_imprint`
    AND lt.`publication_status` = at.`publication_status_imprint`
SET at.`imprint_id` = lt.`id`
WHERE at.`content_imprint` IS NOT NULL AND at.`content_imprint` <> '' AND at.`imprint_id` IS NULL;
