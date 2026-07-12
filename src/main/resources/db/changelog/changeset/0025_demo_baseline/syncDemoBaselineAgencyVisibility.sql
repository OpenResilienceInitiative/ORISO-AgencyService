-- ORISO demo/initial-delivery AgencyService baseline.
-- Opt-in via Liquibase context "demo-baseline"; safe to run repeatedly.

SET @demo_agency_id := 246;
SET @demo_tenant_id := 21;
SET @demo_postcode := '88885';

INSERT INTO `agencyservice`.`agency` (
    `id`,
    `tenant_id`,
    `name`,
    `description`,
    `postcode`,
    `city`,
    `is_team_agency`,
    `consulting_type`,
    `is_offline`,
    `is_external`,
    `create_date`,
    `update_date`,
    `delete_date`,
    `counselling_relations`
)
SELECT
    @demo_agency_id,
    @demo_tenant_id,
    'Caritasverband Wismar',
    'Demo/initial-delivery counselling center for public registration baseline checks.',
    '23966',
    'Wismar',
    0,
    1,
    0,
    0,
    UTC_TIMESTAMP(),
    UTC_TIMESTAMP(),
    NULL,
    'RELATIVE_COUNSELLING,SELF_COUNSELLING,PARENTAL_COUNSELLING'
WHERE NOT EXISTS (
    SELECT 1 FROM `agencyservice`.`agency` WHERE `id` = @demo_agency_id
);

UPDATE `agencyservice`.`agency`
SET
    `tenant_id` = @demo_tenant_id,
    `name` = 'Caritasverband Wismar',
    `consulting_type` = 1,
    `is_offline` = 0,
    `is_external` = 0,
    `delete_date` = NULL,
    `update_date` = UTC_TIMESTAMP()
WHERE `id` = @demo_agency_id;

INSERT INTO `agencyservice`.`agency_postcode_range` (
    `id`,
    `tenant_id`,
    `agency_id`,
    `postcode_from`,
    `postcode_to`,
    `create_date`,
    `update_date`
)
SELECT
    900000001,
    @demo_tenant_id,
    @demo_agency_id,
    '00000',
    '99999',
    UTC_TIMESTAMP(),
    UTC_TIMESTAMP()
WHERE EXISTS (
    SELECT 1 FROM `agencyservice`.`agency` WHERE `id` = @demo_agency_id
)
AND NOT EXISTS (
    SELECT 1
    FROM `agencyservice`.`agency_postcode_range`
    WHERE `agency_id` = @demo_agency_id
      AND `postcode_from` <= @demo_postcode
      AND `postcode_to` >= @demo_postcode
);

INSERT INTO `agencyservice`.`agency_topic` (
    `id`,
    `agency_id`,
    `topic_id`,
    `create_date`,
    `update_date`,
    `publication_status`,
    `publication_status_imprint`
)
SELECT
    900000002,
    @demo_agency_id,
    2,
    UTC_TIMESTAMP(),
    UTC_TIMESTAMP(),
    'DRAFT',
    'DRAFT'
WHERE EXISTS (
    SELECT 1 FROM `agencyservice`.`agency` WHERE `id` = @demo_agency_id
)
ON DUPLICATE KEY UPDATE
    `agency_id` = VALUES(`agency_id`),
    `topic_id` = VALUES(`topic_id`),
    `update_date` = UTC_TIMESTAMP(),
    `publication_status` = COALESCE(`publication_status`, 'DRAFT'),
    `publication_status_imprint` = COALESCE(`publication_status_imprint`, 'DRAFT');

INSERT INTO `agencyservice`.`agency_topic` (
    `id`,
    `agency_id`,
    `topic_id`,
    `create_date`,
    `update_date`,
    `publication_status`,
    `publication_status_imprint`
)
SELECT
    900000010,
    @demo_agency_id,
    10,
    UTC_TIMESTAMP(),
    UTC_TIMESTAMP(),
    'DRAFT',
    'DRAFT'
WHERE EXISTS (
    SELECT 1 FROM `agencyservice`.`agency` WHERE `id` = @demo_agency_id
)
ON DUPLICATE KEY UPDATE
    `agency_id` = VALUES(`agency_id`),
    `topic_id` = VALUES(`topic_id`),
    `update_date` = UTC_TIMESTAMP(),
    `publication_status` = COALESCE(`publication_status`, 'DRAFT'),
    `publication_status_imprint` = COALESCE(`publication_status_imprint`, 'DRAFT');

-- MariaDB requires SETVAL's next_value argument to be an integer literal.
-- SETVAL never lowers a sequence, so these reserved baseline IDs are safe even
-- when an environment has already advanced beyond them.
DO SETVAL(`agencyservice`.`sequence_agency_postcode_range`, 900000001, 0);
DO SETVAL(`agencyservice`.`sequence_agency_topic`, 900000010, 0);
