USE multicalc;

INSERT INTO calculations (created_at, expression, result, status, client_ip) VALUES
    (UTC_TIMESTAMP(6) - INTERVAL 2 DAY, '2 + 3 * (4 - 1)', '11', 'OK', INET6_ATON('127.0.0.1')),
    (UTC_TIMESTAMP(6) - INTERVAL 1 DAY, '7 / 2', '3.5', 'OK', INET6_ATON('::1')),
    (UTC_TIMESTAMP(6) - INTERVAL 1 DAY, '1 / 0', NULL, 'ERR', INET6_ATON('127.0.0.1')),
    (UTC_TIMESTAMP(6), '2 + 3 * (4 - 1)', '11', 'OK', INET6_ATON('::1')),
    (UTC_TIMESTAMP(6), '-0', '-0', 'OK', INET6_ATON('::1')),
    (UTC_TIMESTAMP(6), '2 +', NULL, 'ERR', INET6_ATON('127.0.0.1'));
