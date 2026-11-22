<?php

declare(strict_types=1);

use MultiCalc\CalculationHistory;

require_once __DIR__ . '/../src/CalculationHistory.php';

function escapeHtml(string $value): string
{
    return htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

$page = filter_input(INPUT_GET, 'page', FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]);
$dashboard = null;
$noticeTitle = 'Connect the history database';
$notice = 'Set MULTICALC_DASHBOARD_DSN to view calculation history.';
try {
    $history = CalculationHistory::fromEnvironment();
    if ($history !== null) {
        $dashboard = $history->snapshot(is_int($page) ? $page : 1);
    }
} catch (PDOException) {
    http_response_code(503);
    error_log('MultiCalc dashboard could not load calculation history.');
    $noticeTitle = 'History is unavailable';
    $notice = 'Check the database connection and try again.';
}

header('Content-Type: text/html; charset=utf-8');
header('Cache-Control: no-store');
?>
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Calculation history | MultiCalc</title>
    <link rel="stylesheet" href="dashboard.css">
</head>
<body>
    <header class="topbar">
        <div class="container"><a class="wordmark" href="../">MultiCalc</a></div>
    </header>
    <main class="container content">
        <h1>Calculation history</h1>
        <?php if ($dashboard === null): ?>
            <section class="panel notice">
                <h2><?= escapeHtml($noticeTitle) ?></h2>
                <p><?= escapeHtml($notice) ?></p>
            </section>
        <?php else:
            $rate = $dashboard['total'] > 0
                ? number_format(100 * $dashboard['failed'] / $dashboard['total'], 1) . '%'
                : '—';
            $scale = max(1, ...array_column($dashboard['daily'], 'total'));
            $utc = new DateTimeZone('UTC');
        ?>
            <dl class="summary" aria-label="History totals">
                <div class="panel metric">
                    <dt>Calculations</dt>
                    <dd><?= escapeHtml(number_format($dashboard['total'])) ?></dd>
                </div>
                <div class="panel metric">
                    <dt>Errors</dt>
                    <dd><?= escapeHtml(number_format($dashboard['failed'])) ?></dd>
                </div>
                <div class="panel metric">
                    <dt>Error rate</dt>
                    <dd><?= escapeHtml($rate) ?></dd>
                </div>
            </dl>

            <div class="insights">
                <section class="panel insight" aria-labelledby="daily-heading">
                    <h2 id="daily-heading">Calculations per day</h2>
                    <p class="context">Last 7 days · UTC</p>
                    <ul class="daily-chart">
                        <?php foreach ($dashboard['daily'] as $day):
                            $label = (new DateTimeImmutable($day['day'], $utc))->format('j M');
                            $width = round(100 * $day['total'] / $scale, 2) . '%';
                        ?>
                            <li>
                                <time datetime="<?= escapeHtml($day['day']) ?>"><?= escapeHtml($label) ?></time>
                                <div class="bar-track" aria-hidden="true">
                                    <span style="width: <?= escapeHtml($width) ?>"></span>
                                </div>
                                <span class="count"><?= escapeHtml(number_format($day['total'])) ?></span>
                            </li>
                        <?php endforeach; ?>
                    </ul>
                </section>
                <section class="panel insight" aria-labelledby="frequent-heading">
                    <h2 id="frequent-heading">Most frequent expressions</h2>
                    <p class="context">Across all calculation history</p>
                    <?php if ($dashboard['top'] === []): ?>
                        <p class="empty">No expressions yet.</p>
                    <?php else: ?>
                        <ul class="frequent-list">
                            <?php foreach ($dashboard['top'] as $entry): ?>
                                <li>
                                    <code title="<?= escapeHtml($entry['expression']) ?>"><?= escapeHtml($entry['expression']) ?></code>
                                    <span class="count"><?= escapeHtml(number_format($entry['total'])) ?></span>
                                </li>
                            <?php endforeach; ?>
                        </ul>
                    <?php endif; ?>
                </section>
            </div>

            <section class="panel records" aria-labelledby="records-heading">
                <h2 id="records-heading">History</h2>
                <?php if ($dashboard['rows'] === []): ?>
                    <p class="empty">No calculations yet.</p>
                <?php else: ?>
                    <div class="table-scroll" tabindex="0" role="region" aria-labelledby="records-heading">
                        <table>
                            <thead>
                                <tr>
                                    <th scope="col">Time (UTC)</th>
                                    <th scope="col">Expression</th>
                                    <th scope="col">Result</th>
                                    <th scope="col">Status</th>
                                    <th scope="col">Client IP</th>
                                </tr>
                            </thead>
                            <tbody>
                                <?php foreach ($dashboard['rows'] as $row):
                                    $timestamp = substr($row['created_at'], 0, 19);
                                    $timestampIso = str_replace(' ', 'T', $timestamp) . 'Z';
                                    $statusClass = $row['status'] === 'OK' ? 'status-ok' : 'status-error';
                                ?>
                                    <tr>
                                        <td><time datetime="<?= escapeHtml($timestampIso) ?>"><?= escapeHtml($timestamp) ?></time></td>
                                        <td><code class="expression" title="<?= escapeHtml($row['expression']) ?>"><?= escapeHtml($row['expression']) ?></code></td>
                                        <td><code><?= escapeHtml($row['result'] ?? '—') ?></code></td>
                                        <td><span class="status <?= escapeHtml($statusClass) ?>"><?= escapeHtml($row['status']) ?></span></td>
                                        <td class="client-ip"><?= escapeHtml($row['client_ip']) ?></td>
                                    </tr>
                                <?php endforeach; ?>
                            </tbody>
                        </table>
                    </div>
                    <?php if ($dashboard['page_count'] > 1): ?>
                        <nav class="pagination" aria-label="History pages">
                            <span>Page <?= escapeHtml(number_format($dashboard['page'])) ?> of <?= escapeHtml(number_format($dashboard['page_count'])) ?></span>
                            <div>
                                <?php if ($dashboard['page'] > 1): ?>
                                    <a href="?page=<?= escapeHtml((string) ($dashboard['page'] - 1)) ?>">Previous</a>
                                <?php endif; ?>
                                <?php if ($dashboard['page'] < $dashboard['page_count']): ?>
                                    <a href="?page=<?= escapeHtml((string) ($dashboard['page'] + 1)) ?>">Next</a>
                                <?php endif; ?>
                            </div>
                        </nav>
                    <?php endif; ?>
                <?php endif; ?>
            </section>
        <?php endif; ?>
    </main>
</body>
</html>
