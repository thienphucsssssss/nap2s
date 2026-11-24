-- Tạo table: dữ liệu tích lũy của người chơi

CREATE TABLE `nap2s_player_data`
(
    `uuid`         VARCHAR(36)  NOT NULL,
    `name`         VARCHAR(32)  NOT NULL,
    `key`          VARCHAR(100) NOT NULL,
    `value`        INT DEFAULT 0 NOT NULL,
    `last_updated` BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT `NAP2S_PLAYER_DATA_PK`
        UNIQUE (`uuid`, `key`)
);