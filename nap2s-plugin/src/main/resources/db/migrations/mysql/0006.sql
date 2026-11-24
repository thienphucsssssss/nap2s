ALTER TABLE `nap2s_napthe_log`
    ADD COLUMN `provider` VARCHAR(64) NOT NULL DEFAULT '' AFTER `server`;
