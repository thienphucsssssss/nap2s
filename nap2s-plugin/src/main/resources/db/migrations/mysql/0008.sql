-- Migration: thêm 3 cột snapshot thông tin ngân hàng vào bảng banking_order
ALTER TABLE `nap2s_banking_order` ADD COLUMN `bank_name` VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE `nap2s_banking_order` ADD COLUMN `bank_account_number` VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE `nap2s_banking_order` ADD COLUMN `bank_account_holder` VARCHAR(128) NOT NULL DEFAULT '';
