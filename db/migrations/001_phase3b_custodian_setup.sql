-- Phase 3B additive migration for existing databases. Apply ONCE before deployment.
-- No tables are added. Fresh schema scripts already include these columns.
-- Nullable legacy audit fields deliberately do not invent actors or timestamps.
-- MySQL DDL commits implicitly. Back up first; apply using a deployment account.
USE custodian_db;
SET time_zone = '+00:00';

ALTER TABLE organization
  ADD COLUMN registered_by_custodian_id BIGINT NULL,
  ADD COLUMN created_by_user_id BIGINT NULL,
  ADD COLUMN updated_at DATETIME(6) NULL,
  ADD COLUMN updated_by_user_id BIGINT NULL;

ALTER TABLE lender_custodian_mandate
  ADD COLUMN created_at DATETIME(6) NULL,
  ADD COLUMN created_by_user_id BIGINT NULL,
  ADD COLUMN updated_at DATETIME(6) NULL,
  ADD COLUMN updated_by_user_id BIGINT NULL;

ALTER TABLE msla_agreement
  ADD COLUMN created_by_user_id BIGINT NULL,
  ADD COLUMN updated_at DATETIME(6) NULL,
  ADD COLUMN updated_by_user_id BIGINT NULL;

