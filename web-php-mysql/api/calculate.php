<?php

declare(strict_types=1);

use MultiCalc\CalculationException;
use MultiCalc\CalculatorTimeoutException;
use MultiCalc\TcpCalculatorClient;

require_once __DIR__ . '/../src/TcpCalculatorClient.php';

const MAX_JSON_BODY_BYTES = 32768;

function sendJson(int $status, array $payload): void
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
    exit;
}

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    header('Allow: POST');
    sendJson(405, ['error' => 'Use POST to submit a calculation.']);
}

$mediaType = strtolower(trim(explode(';', $_SERVER['CONTENT_TYPE'] ?? '', 2)[0]));
if ($mediaType !== 'application/json') {
    sendJson(415, ['error' => 'Send the expression as application/json.']);
}

$body = file_get_contents('php://input', false, null, 0, MAX_JSON_BODY_BYTES + 1);
if ($body === false) {
    sendJson(400, ['error' => 'Could not read the request body.']);
}
if (strlen($body) > MAX_JSON_BODY_BYTES) {
    sendJson(413, ['error' => 'The request body is too large.']);
}

try {
    $request = json_decode($body, false, 16, JSON_THROW_ON_ERROR);
} catch (JsonException) {
    sendJson(400, ['error' => 'The request body must be valid JSON.']);
}
$expression = is_object($request) ? ($request->expression ?? null) : null;
if (!is_string($expression)) {
    sendJson(400, ['error' => 'Send an expression string.']);
}

$host = getenv('MULTICALC_TCP_HOST');
$portSetting = getenv('MULTICALC_TCP_PORT');
$port = $portSetting === false ? 5050 : filter_var($portSetting, FILTER_VALIDATE_INT, [
    'options' => ['min_range' => 1, 'max_range' => 65535],
]);
if (($host !== false && trim($host) === '') || $port === false) {
    sendJson(500, ['error' => 'The calculator server address is misconfigured.']);
}

try {
    $client = new TcpCalculatorClient($host === false ? '127.0.0.1' : $host, $port);
    sendJson(200, ['result' => $client->calculate($expression)]);
} catch (LengthException $error) {
    sendJson(413, ['error' => $error->getMessage()]);
} catch (InvalidArgumentException $error) {
    sendJson(400, ['error' => $error->getMessage()]);
} catch (CalculationException $error) {
    sendJson(422, ['error' => $error->getMessage()]);
} catch (CalculatorTimeoutException $error) {
    sendJson(504, ['error' => $error->getMessage()]);
} catch (RuntimeException $error) {
    sendJson(502, ['error' => $error->getMessage()]);
}
