-- TEN-INV-U2: binding reservations of agency IDs by open invites.
-- The reserved agency ID is the primary key, so the database itself guarantees that of two
-- parallel reservation attempts for the same ID exactly one succeeds.
CREATE TABLE IF NOT EXISTS `agencyservice`.`agency_id_reservation` (
    `agency_id` bigint(21) NOT NULL,
    `tenant_id` bigint(21) NULL,
    `create_date` datetime NOT NULL DEFAULT (UTC_TIMESTAMP),
    PRIMARY KEY (`agency_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
