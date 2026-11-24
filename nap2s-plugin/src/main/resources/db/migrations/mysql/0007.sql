CREATE TABLE `nap2s_banking_order`
(
    `id`               INT AUTO_INCREMENT PRIMARY KEY,
    `order_id`         VARCHAR(64)  NOT NULL,
    `uuid`             VARCHAR(100) NOT NULL,
    `player_name`      VARCHAR(64)  NOT NULL,
    `amount`           INT          NOT NULL,
    `point_amount`     INT          NOT NULL,
    `status`           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    `checkout_url`     VARCHAR(512) NOT NULL DEFAULT '',
    `qr_image_url`     VARCHAR(512) NOT NULL DEFAULT '',
    `transfer_content` VARCHAR(255) NOT NULL DEFAULT '',
    `transaction_id`   VARCHAR(128) NOT NULL DEFAULT '',
    `gateway`          VARCHAR(64)  NOT NULL DEFAULT 'sepay',
    `raw_response`     LONGTEXT     NOT NULL,
    `server`           VARCHAR(32)  NOT NULL DEFAULT 'web',
    `expires_at`       BIGINT       NOT NULL DEFAULT 0,
    `paid_at`          BIGINT       NOT NULL DEFAULT 0,
    `rewarded`         TINYINT      NOT NULL DEFAULT 0,
    `created_at`       BIGINT       NOT NULL,
    `updated_at`       BIGINT       NOT NULL,
    UNIQUE KEY `uk_nap2s_banking_order_order_id` (`order_id`),
    KEY `idx_nap2s_banking_order_pending` (`server`, `status`, `created_at`)
);
