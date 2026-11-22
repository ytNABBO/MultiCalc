<?php

declare(strict_types=1);

namespace MultiCalc;

use DateTimeImmutable;
use DateTimeZone;
use PDO;
use PDOException;
use Throwable;

final class CalculationHistory
{
    private const PAGE_SIZE = 25;

    private function __construct(private PDO $connection)
    {
    }

    public static function fromEnvironment(): ?self
    {
        $dsn = getenv('MULTICALC_DASHBOARD_DSN');
        if ($dsn === false || trim($dsn) === '') {
            return null;
        }
        if (!str_starts_with($dsn, 'mysql:')) {
            throw new PDOException('The calculation history requires a MySQL PDO DSN.');
        }

        $user = getenv('MULTICALC_DASHBOARD_USER');
        $password = getenv('MULTICALC_DASHBOARD_PASSWORD');
        return new self(new PDO($dsn, $user === false ? null : $user, $password === false ? null : $password, [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
            PDO::ATTR_TIMEOUT => 2,
        ]));
    }

    public function snapshot(int $requestedPage): array
    {
        $this->connection->exec('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY');
        $this->connection->beginTransaction();

        try {
            $totals = $this->connection->prepare(
                "SELECT COUNT(*) AS total, COALESCE(SUM(status = 'ERR'), 0) AS failed FROM calculations"
            );
            $totals->execute();
            $counts = $totals->fetch();
            $total = (int) $counts['total'];
            $pageCount = intdiv(max(0, $total - 1), self::PAGE_SIZE) + 1;
            $page = min(max($requestedPage, 1), $pageCount);

            $snapshot = [
                'total' => $total,
                'failed' => (int) $counts['failed'],
                'daily' => $this->dailyCounts(),
                'top' => $this->topExpressions(),
                'rows' => $this->historyPage($page),
                'page' => $page,
                'page_count' => $pageCount,
            ];
            $this->connection->commit();
            return $snapshot;
        } catch (Throwable $error) {
            if ($this->connection->inTransaction()) {
                $this->connection->rollBack();
            }
            throw $error;
        }
    }

    private function dailyCounts(): array
    {
        $today = new DateTimeImmutable('today', new DateTimeZone('UTC'));
        $firstDay = $today->modify('-6 days');
        $tomorrow = $today->modify('+1 day');
        $query = $this->connection->prepare(<<<'SQL'
            SELECT DATE(created_at) AS date_key, COUNT(*) AS total
            FROM calculations
            WHERE created_at >= :first_day AND created_at < :tomorrow
            GROUP BY DATE(created_at)
            SQL);
        $query->execute([
            'first_day' => $firstDay->format('Y-m-d'),
            'tomorrow' => $tomorrow->format('Y-m-d'),
        ]);
        $countsByDay = array_column($query->fetchAll(), 'total', 'date_key');

        $days = [];
        for ($day = $firstDay; $day < $tomorrow; $day = $day->modify('+1 day')) {
            $key = $day->format('Y-m-d');
            $days[] = ['day' => $key, 'total' => (int) ($countsByDay[$key] ?? 0)];
        }
        return $days;
    }

    private function topExpressions(): array
    {
        // The digest keeps long expressions with the same sorting prefix separate.
        $query = $this->connection->prepare(<<<'SQL'
            SELECT expression, COUNT(*) AS total
            FROM calculations
            GROUP BY expression, SHA2(expression, 256)
            ORDER BY total DESC, MIN(id)
            LIMIT 5
            SQL);
        $query->execute();
        return array_map(
            static fn (array $row): array => ['expression' => $row['expression'], 'total' => (int) $row['total']],
            $query->fetchAll()
        );
    }

    private function historyPage(int $page): array
    {
        $query = $this->connection->prepare(<<<'SQL'
            SELECT created_at, expression, result, status, INET6_NTOA(client_ip) AS client_ip
            FROM calculations
            ORDER BY created_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            SQL);
        $query->bindValue(':limit', self::PAGE_SIZE, PDO::PARAM_INT);
        $query->bindValue(':offset', ($page - 1) * self::PAGE_SIZE, PDO::PARAM_INT);
        $query->execute();
        return $query->fetchAll();
    }
}
