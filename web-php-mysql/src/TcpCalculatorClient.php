<?php

declare(strict_types=1);

namespace MultiCalc;

use InvalidArgumentException;
use LengthException;
use RuntimeException;

final class CalculationException extends RuntimeException
{
}

final class CalculatorRateLimitException extends RuntimeException
{
}

final class CalculatorTimeoutException extends RuntimeException
{
}

final class TcpCalculatorClient
{
    private const REQUEST_PREFIX = 'CALC ';
    private const MAX_LINE_BYTES = 4096;
    private const CONNECT_TIMEOUT_SECONDS = 5;
    private const STREAM_TIMEOUT_SECONDS = 10;
    private const DECIMAL = '/\A[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?\z/';

    public function __construct(private string $host, private int $port)
    {
    }

    public function calculate(string $expression): string
    {
        $request = $this->encodeRequest($expression);
        $host = $this->host;
        if (str_contains($host, ':') && !str_starts_with($host, '[')) {
            $host = "[$host]";
        }

        $socket = @stream_socket_client(
            "tcp://$host:{$this->port}", $errorCode, $errorMessage, self::CONNECT_TIMEOUT_SECONDS
        );
        if ($socket === false) {
            throw new RuntimeException('Could not connect to the calculator server.');
        }

        try {
            if (!stream_set_timeout($socket, self::STREAM_TIMEOUT_SECONDS)) {
                throw new RuntimeException('Could not configure the calculator server connection.');
            }
            $this->writeRequest($socket, $request);
            $line = @fgets($socket, self::MAX_LINE_BYTES + 2);
            if ($line === false || !str_ends_with($line, "\n")) {
                $this->checkTimeout($socket);
                throw new RuntimeException('The calculator server returned an incomplete or oversized response.');
            }
            return $this->parseResponse(substr($line, 0, -1));
        } finally {
            fclose($socket);
        }
    }

    private function encodeRequest(string $expression): string
    {
        if (strlen($expression) > self::MAX_LINE_BYTES - strlen(self::REQUEST_PREFIX)) {
            throw new LengthException('The expression exceeds the calculator server request limit.');
        }
        if (preg_match('/[\r\n\x00]/', $expression) === 1 || preg_match('/(*UCP)\S/u', $expression) !== 1) {
            throw new InvalidArgumentException('Enter a nonblank expression on a single line.');
        }
        return self::REQUEST_PREFIX . $expression . "\n";
    }

    /** @param resource $socket */
    private function writeRequest($socket, string $request): void
    {
        $length = strlen($request);
        for ($offset = 0; $offset < $length; $offset += $written) {
            $written = @fwrite($socket, substr($request, $offset));
            if ($written === false || $written === 0) {
                $this->checkTimeout($socket);
                throw new RuntimeException('Could not send the expression to the calculator server.');
            }
        }
    }

    /** @param resource $socket */
    private function checkTimeout($socket): void
    {
        if (stream_get_meta_data($socket)['timed_out']) {
            throw new CalculatorTimeoutException('The calculator server timed out.');
        }
    }

    private function parseResponse(string $response): string
    {
        if (preg_match('//u', $response) === 1 && preg_match('/[\r\x00]/', $response) === 0) {
            if (str_starts_with($response, 'ERR ') && strlen($response) > 4) {
                $message = substr($response, 4);
                if ($message === 'Rate limit exceeded') {
                    throw new CalculatorRateLimitException($message);
                }
                throw new CalculationException($message);
            }
            if (str_starts_with($response, 'OK ')) {
                $value = substr($response, 3);
                if (preg_match(self::DECIMAL, $value) === 1 && is_finite((float) $value)) {
                    return $value;
                }
            }
        }
        throw new RuntimeException('The calculator server returned an invalid response.');
    }
}
