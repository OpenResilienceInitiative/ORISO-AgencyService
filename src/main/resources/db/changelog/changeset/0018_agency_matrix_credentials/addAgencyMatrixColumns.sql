-- Adds the Matrix service-account credential columns on agency.
-- IF NOT EXISTS: several environments already carry these columns from
-- manual ALTERs (Liquibase was disabled in-cluster), so this changeset
-- must be safe to run on both fresh and already-patched databases.
ALTER TABLE `agency` ADD COLUMN IF NOT EXISTS `matrix_user_id` varchar(255) DEFAULT NULL;
ALTER TABLE `agency` ADD COLUMN IF NOT EXISTS `matrix_password` varchar(255) DEFAULT NULL;
