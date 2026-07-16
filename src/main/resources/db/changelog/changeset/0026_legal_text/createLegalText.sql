-- ADR-014: legal texts (DPP / Impressum) become first-class shared objects owned by a Träger.
CREATE TABLE IF NOT EXISTS `agencyservice`.`legal_text` (
    `id` bigint(21) NOT NULL,
    `tenant_id` bigint(21) NULL,
    `kind` varchar(20) NOT NULL,
    `label` varchar(255) NOT NULL,
    `content` longtext NULL,
    `publication_status` varchar(20) NOT NULL DEFAULT 'DRAFT',
    `create_date` datetime NOT NULL DEFAULT (UTC_TIMESTAMP),
    `update_date` datetime NOT NULL DEFAULT (UTC_TIMESTAMP),
    PRIMARY KEY (`id`),
    KEY `idx_legal_text_tenant_kind` (`tenant_id`, `kind`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE SEQUENCE IF NOT EXISTS `agencyservice`.`sequence_legal_text`
INCREMENT BY 1
MINVALUE = 0
NOMAXVALUE
START WITH 0
CACHE 10;
