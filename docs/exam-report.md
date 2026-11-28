# MultiCalc exam report

MultiCalc evaluates the same expression from a terminal, a desktop app, or a browser. A single C++ engine owns the arithmetic. Each interface handles input and presents the result or error. This report covers the source and behavior in the `v1.0.0` tag. [Release tag](https://github.com/ytNABBO/MultiCalc/releases/tag/v1.0.0)

## From expression to result

The expression `2+3*(4-1)` returns `11`. The C++ recursive-descent parser recognizes sums, products, and factors. It builds an abstract syntax tree with addition at the root, multiplication as the right operand, and `4-1` inside the product. The parser stores children before their parents, so the evaluator can walk the nodes once in order. An invalid expression or division by zero produces an error. [C++ parser](../core-cpp/parser.cpp) · [Evaluator](../core-cpp/calculator.cpp)

The terminal invokes the C++ executable directly. Java Swing and C# WPF send a `CALC` line over TCP. The browser sends JSON to PHP, which translates the request into the same TCP protocol. The Java server launches `calc` with the expression as one argument and replies with `OK 11` or an `ERR` message. The PHP API keeps results as strings so the browser receives the engine's original formatting, including `-0`. [Architecture diagram](architecture.md) · [TCP protocol](protocol.md)

## Project milestones

| Tag | Commit | Milestone |
| --- | --- | --- |
| `v0.1.0` | `c91f80c` | C++ parser and CLI, unit tests, Python CLI regression, and a Swing interface that ran the engine locally |
| `v0.2.0` | `a777f96` | Java TCP server, networked Swing and WPF clients, and Python load testing |
| `v0.3.0` | `1a4395f` | MySQL history through JDBC, PHP API and dashboard, and a browser calculator with local history |
| `v1.0.0` | `ccc8485` | GitHub Actions, input and rate limits, a threat model, and updated architecture and setup documentation |

The tags and commit history record when each milestone shipped. The roadmap links completed tasks to GitHub issues that cite their implementation commits. [GitHub tags](https://github.com/ytNABBO/MultiCalc/tags) · [Commit history](https://github.com/ytNABBO/MultiCalc/commits/main/) · [Roadmap](../ROADMAP.md#computational-core-and-basic-desktop)

## Engineering decisions and tradeoffs

- **TCP framing and concurrency.** TCP delivers a byte stream. A newline marks the end of each UTF-8 request or response, even if it takes several reads to receive the whole line. Each of the eight Java workers handles one session at a time, and the server can queue 128 waiting connections. This bounds concurrent work, while a client holding a session delays clients waiting for a worker. Global counts use `AtomicLong`, and `ConcurrentHashMap.merge` updates each peer's counts atomically. The server reports counts after workers drain on a graceful shutdown. [TCP protocol](protocol.md) · [Server statistics](../README.md#server-statistics)
- **Persistence and availability.** A bounded background queue lets the TCP worker reply independently of MySQL. The JDBC writer uses parameterized inserts and an `INSERT` account. The dashboard uses PDO and a separate `SELECT` account. A full queue or a failed database write can leave gaps in shared history. Browser local storage records recent successful calculations in that browser profile. [History setup](../README.md#set-up-mysql-history)
- **Trust boundaries.** The TCP server caps a request at 4,096 bytes and checks a shared rate budget before launching the engine. By default, the budget refills at 100 calculations per second and holds up to 100. PHP reports throttling as HTTP `429`. The dashboard escapes stored text and exposes shared history, so access controls matter on a shared deployment. The default plaintext TCP endpoint is on loopback. [Threat model](threat-model.md)

## What the benchmark showed

The concurrency report measured three seeded trials at each client count. Each client sent 20 requests over one connection on loopback. All 9,660 measured requests matched the expected results, with zero failed or skipped requests. [Full benchmark and reproduction steps](concurrency-report.md)

| Clients | Median requests/s | Median p95 latency | Slowest request |
| ---: | ---: | ---: | ---: |
| 1 | 339.1 | 3.98 ms | 5.45 ms |
| 10 | 642.1 | 18.94 ms | 307.68 ms |
| 50 | 722.1 | 22.02 ms | 1,325.96 ms |
| 100 | 655.8 | 26.00 ms | 2,857.66 ms |

The throughput ranges at 50 and 100 clients overlapped. At 100 clients, the median p95 was 26 ms while the slowest request took nearly 2.86 seconds. If 92 sessions are waiting for workers, their first requests account for at most 4.6% of the 2,000 requests in one trial. That helps explain why the slowest requests grew much more than p95. These measurements describe short bursts on one machine at `3c451b9`, before the rate limit was added.

## Release verification

For `v1.0.0`, the release check passed 25 C++ unit tests and 16 Python regression cases. A 100-client run completed all 2,000 requests with `MULTICALC_REQUESTS_PER_SECOND=2000`. The Swing client displayed a result and an arithmetic error. Live PHP and database checks covered result and error history, plus rate-limit rejection. Windows CI built the WPF client. [Release CI](https://github.com/ytNABBO/MultiCalc/actions/runs/45966660926) · [Benchmark configuration](concurrency-report.md#reproduce)

## Live demo

Build `calc` and the Java TCP server from the repository root. Start the Java server on `127.0.0.1:5050` and, for the web part, PHP on `127.0.0.1:8080`. Leave each server running in its own terminal. The calculator demo below uses `nc` and `curl`. [Local setup](../README.md#local-setup)

1. Run the engine directly and show the regression cases.

   ```sh
   ./calc '2+3*(4-1)'
   python3 tools-python/regression.py ./calc tools-python/cases.txt
   ```

   Expect `11` and `16/16 cases passed`.

2. Use the TCP protocol to repeat the calculation and show an arithmetic error.

   ```sh
   printf 'CALC 2+3*(4-1)\n' | nc -w 2 127.0.0.1 5050
   printf 'CALC 1/0\n' | nc -w 2 127.0.0.1 5050
   ```

   Expect `OK 11` and `ERR Division by zero`.

3. Send the same expression to PHP, then enter it at `http://127.0.0.1:8080/` to show the browser result and recent history.

   ```sh
   curl -fsS -H 'Content-Type: application/json' \
       -d '{"expression":"2+3*(4-1)"}' \
       http://127.0.0.1:8080/api/calculate.php
   ```

   Expect `{"result":"11"}`. With MySQL history configured, open `/dashboard/` to show the saved calculations and usage statistics. [Dashboard setup](../README.md#view-the-php-dashboard)

4. Run four clients for five requests each. Expect 20 successful requests and use the report to explain throughput and tail latency.

   ```sh
   python3 tools-python/load_test.py --clients 4 --requests-per-client 5 --seed 123
   ```
