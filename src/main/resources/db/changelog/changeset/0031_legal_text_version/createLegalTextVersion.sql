-- ADR-021 decision 3: a generic, immutable publication history for legal texts, on every level
-- and for every kind. Blueprint: ORISO-TenantService `tenant_dpa_version` (changeset 0018),
-- generalised by (kind, owner_level, owner_id) so the DPP, the imprint and any future kind share
-- one table, one endpoint and one editor wiring.
--
-- Identity is a SURROGATE id, never the publication timestamp. The AVV work had to truncate its
-- version key to seconds because MariaDB `datetime` (DATETIME(0)) drops sub-second precision on
-- the round trip, and an equality match on that key silently failed. A snapshot here is addressed
-- by its own id, so no caller ever has to reconstruct a timestamp.
CREATE TABLE IF NOT EXISTS `agencyservice`.`legal_text_version` (
    `id` bigint(21) NOT NULL,
    `tenant_id` bigint(21) NULL,
    `kind` varchar(20) NOT NULL,
    -- DEPARTMENT (agency_topic.id) | AGENCY (agency.id) | SHARED (legal_text.id)
    `owner_level` varchar(20) NOT NULL,
    `owner_id` bigint(21) NOT NULL,
    `content` longtext NULL,
    `published_at` datetime NOT NULL,
    -- Keycloak user id of the publisher; NULL where the publish had no authenticated user
    -- (technical/migration path). Never guessed.
    `published_by` varchar(255) NULL,
    -- NULL = this is the version currently in force for its owner. Set when a newer one is
    -- published, so "which wording was in force on date X" is answerable without a self-join.
    `superseded_at` datetime NULL,
    PRIMARY KEY (`id`),
    KEY `idx_legal_text_version_owner` (`owner_level`, `owner_id`, `kind`, `published_at`),
    KEY `idx_legal_text_version_current` (`owner_level`, `owner_id`, `kind`, `superseded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE SEQUENCE IF NOT EXISTS `agencyservice`.`sequence_legal_text_version`
INCREMENT BY 1
MINVALUE = 0
NOMAXVALUE
START WITH 0
CACHE 10;
