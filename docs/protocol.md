# MultiCalc TCP protocol

Clients connect over plaintext TCP to the configured server host and port. The default endpoint is `127.0.0.1:5050`. A connection can carry multiple calculations. Send one request, read its full response, then send the next request on the same connection. [Deployment assumptions](threat-model.md)

## Line format

- Encode each message as UTF-8 without a byte order mark. The server accepts requests ending in LF (`\n`) or CRLF (`\r\n`). It sends responses ending in LF and flushes each response.
- Read through the line ending to get a complete message. A single TCP read can contain part of a line or more than one line.
- The command and response prefixes are case-sensitive. One ASCII space separates the prefix from its payload. Everything after that first space belongs to the payload.

## Requests and responses

Send `CALC <expression>`. The expression follows the C++ calculator syntax and can include spaces or tabs. It must contain a non-whitespace character. CR, LF, and NUL cannot appear inside the expression.

The server replies to each complete request it processes with one of these lines.

| Response | Meaning |
| --- | --- |
| `OK <result>` | The result is a finite decimal number with `.` as the decimal separator. Scientific notation and signed zero are allowed. |
| `ERR <message>` | The request or calculation failed. The prefix identifies the failure and the payload gives a nonempty message for display without CR, LF, or NUL. |

The desktop clients and PHP API preserve the original result text, including signed zero. The Python load tester converts results to floating-point values for comparison.

An unknown command, a missing separator, an empty expression, a forbidden character, invalid UTF-8, or a calculation error returns `ERR`. The connection remains open for another request.

## Rate limit

The Java server uses one token bucket for all connections. By default it refills at 100 tokens per second and holds up to 100 tokens. Set `MULTICALC_REQUESTS_PER_SECOND` to a positive 32-bit integer to change both the refill rate and burst capacity. Each admitted `CALC` request consumes one token, including calculations that later return an error.

When the budget is exhausted, the server replies `ERR Rate limit exceeded` before it runs the engine. The connection remains open and the client can retry after a delay. The PHP API maps this exact reply to HTTP `429` with `Retry-After: 1`.

## Connection limits

A request can contain up to 4,096 bytes, including the `CALC ` prefix and excluding the line ending. That leaves up to 4,091 UTF-8 bytes for the expression. The server checks the size before decoding. When a request exceeds the limit, the server sends `ERR Request too long` if it can still write to the socket, then closes the connection. The bundled TCP clients also limit replies to 4,096 bytes before the final LF.

On EOF or after five seconds of read inactivity in an active session, the server discards any unfinished request and closes the connection. If the client reaches EOF or times out before receiving a full response, it treats the exchange as a connection failure and reconnects before sending another request. A calculation may have finished and entered history before its response was lost. Retrying can create another history entry.

## Examples

| Request | Response |
| --- | --- |
| `CALC 2 + 3 * (4 - 1)\n` | `OK 11\n` |
| `CALC 7 / 2\r\n` | `OK 3.5\n` |
| `CALC -0\n` | `OK -0\n` |
| `CALC 1 / 0\n` | `ERR Division by zero\n` |
| `CALC \n` | `ERR Empty expression\n` |
| `calc 2 + 3\n` | `ERR Invalid request\n` |

The examples show `\n` and `\r\n` as visible escapes for line endings. Other error payloads may vary. Client code can rely on the `ERR` prefix for failures and the exact rate-limit and overlength messages described above.
