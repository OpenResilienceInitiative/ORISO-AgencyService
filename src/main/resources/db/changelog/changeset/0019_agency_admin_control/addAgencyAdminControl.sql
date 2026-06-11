CREATE TABLE `agencyservice`.`agency_admin_control` (
  `id` bigint(21) NOT NULL,
  `controls` text COLLATE utf8_unicode_ci NOT NULL,
  `update_date` datetime NOT NULL DEFAULT (UTC_TIMESTAMP),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE SEQUENCE agencyservice.sequence_agency_admin_control
INCREMENT BY 1
MINVALUE = 0
NOMAXVALUE
START WITH 0
CACHE 0;
