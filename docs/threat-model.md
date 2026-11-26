# MultiCalc threat model

MultiCalc is designed for a local machine or a trusted private network. The deployment operator controls the server process, calculator executable, PHP configuration, and database credentials. Treat expressions and network input as untrusted, including expressions already stored in history.

## Trust boundaries

- Browsers send JSON to PHP. PHP and the desktop and Python clients send calculation lines to the Java TCP server. The TCP protocol uses plaintext, and PHP appears to Java as the TCP peer for browser requests.
- Java passes the expression to the configured C++ executable as one process argument. `ProcessBuilder` invokes the executable directly, and the parser accepts arithmetic syntax with a nesting limit.
- Java can write history to MySQL using parameterized inserts and an account with `INSERT` privileges. PHP reads through a separate `SELECT` account and uses prepared queries. The dashboard escapes stored text for HTML, and the browser displays results and local history as text.

## Input and load limits

| Entry point | Limit |
| --- | --- |
| C++ command line | 4,091 bytes per expression |
| Java TCP server | 4,096 UTF-8 bytes per request, including `CALC ` and excluding the line ending |
| PHP calculation API | 32,768 bytes per JSON body and 4,091 UTF-8 bytes per expression |

The TCP parser checks request length before UTF-8 decoding. It rejects malformed UTF-8, blank expressions, and embedded CR or NUL. LF ends the request. PHP also rejects line breaks and NUL inside a JSON expression before forwarding it.

One token bucket per Java process controls how many valid `CALC` requests can start engine work. It allows 100 requests per second and a burst of 100 by default. Set `MULTICALC_REQUESTS_PER_SECOND` to a positive integer to change both values. Each admitted request spends one token before the server launches the engine, even if the arithmetic later fails. When the bucket is empty, Java replies with `ERR Rate limit exceeded` and keeps the session open. PHP returns HTTP `429` with a `Retry-After` value of `1`. Rejected requests leave server calculation counts and persistent history untouched. [TCP protocol](protocol.md)

Eight workers and a bounded connection queue limit the number of sessions the server handles at once. A five-second read inactivity timeout and a five-second engine timeout bound idle reads and engine runs. The database writer has its own bounded queue and skips writes during failures or saturation so the calculator can keep replying.

## Deployment and data exposure

- Clients share the calculation budget, so a busy client can consume capacity needed by others. The read timeout applies to each wait for input, and an active connection can occupy a worker for the duration of its session. On shared deployments, use upstream connection and HTTP limits alongside the application limit. Dashboard queries also need their own access and traffic controls.
- The TCP server accepts unauthenticated connections. The PHP routes rely on access controls supplied by the web server. Keep TCP and MySQL on loopback or a restricted network. For remote access, the deployment supplies encrypted transport and controls who can reach the services. Restrict `/dashboard/` to authorized viewers because it exposes shared history.
- History contains UTC timestamps, full expressions, results, status, and TCP peer IP addresses when enabled. Shutdown statistics include counts by TCP peer IP. Browser history is stored locally in the browser profile. Treat the database, any backups, server logs, and browser profiles as access-controlled data, and choose a retention policy for a shared deployment.
