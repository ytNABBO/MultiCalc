# MultiCalc TCP protocol

Clients connect to the configured server host and port. A connection can carry multiple calculations. Send one request, read its full response, then send the next request on the same connection.

## Line format

- Encode each message as UTF-8 without a byte order mark. The server accepts requests ending in LF (`\n`) or CRLF (`\r\n`). It sends responses ending in LF and flushes each response.
- Read through the line ending to get a complete message. A single TCP read can contain part of a line or more than one line.
- The command and response prefixes are case-sensitive. One ASCII space separates the prefix from its payload. Everything after that first space belongs to the payload.

## Requests and responses

Send `CALC <expression>`. The expression follows the C++ calculator syntax and can include spaces or tabs. It must contain a non-whitespace character. CR, LF, and NUL cannot appear inside the expression.

The server returns one of these responses for each complete request within the size limit.

| Response | Meaning |
| --- | --- |
| `OK <result>` | The result is a finite decimal number with `.` as the decimal separator. Scientific notation and signed zero are allowed. |
| `ERR <message>` | The request or calculation failed. The message is nonempty text for display and contains no CR, LF, or NUL. Clients use the `ERR` prefix to detect failure. |

An unknown command, a missing separator, an empty expression, a forbidden character, invalid UTF-8, or a calculation error returns `ERR`. The connection remains open for another request.

## Connection limits

A request can contain up to 4096 bytes, including the `CALC ` prefix and excluding the line ending. The server checks the size before decoding. When a request exceeds the limit, the server sends `ERR Request too long` if it can still write to the socket, then closes the connection.

On EOF or a read timeout, the server discards any unfinished request and closes the connection. If the client reaches EOF or times out before receiving a full response, it treats the exchange as a connection failure and reconnects before sending another request.

## Examples

| Request | Response |
| --- | --- |
| `CALC 2 + 3 * (4 - 1)\n` | `OK 11\n` |
| `CALC 7 / 2\r\n` | `OK 3.5\n` |
| `CALC 1 / 0\n` | `ERR Division by zero\n` |
| `CALC \n` | `ERR Empty expression\n` |
| `calc 2 + 3\n` | `ERR Invalid request\n` |

The examples show `\n` and `\r\n` as visible escapes for line endings. Error text may vary.
