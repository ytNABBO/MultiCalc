CREATE DATABASE IF NOT EXISTS multicalc CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE multicalc;

CREATE TABLE IF NOT EXISTS calculations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    expression TEXT NOT NULL,
    result VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    status ENUM('OK', 'ERR') NOT NULL,
    client_ip VARBINARY(16) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_calculations_created_at (created_at),
    CONSTRAINT chk_calculations_outcome CHECK (
        (status = 'OK' AND result IS NOT NULL AND result <> '')
        OR (status = 'ERR' AND result IS NULL)
    ),
    CONSTRAINT chk_calculations_client_ip CHECK (OCTET_LENGTH(client_ip) IN (4, 16))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin;
