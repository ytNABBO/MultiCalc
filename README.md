# MultiCalc

I want to build a calculator that I can use from a desktop app, a terminal, or a browser. All three should use the same calculation engine, so an expression like `2 + 3 * (4 - 1)` gives me `11` wherever I enter it.

I'm using this project to bring together the languages and tools I study at school. I want to see how a parser, a network service, a database, and different user interfaces can work together in one application.

## v0.3.0

I can calculate in the browser using the same engine as the desktop clients. The Java server can save results to MySQL, and the PHP dashboard shows shared history, daily activity, and the error rate. The browser keeps its own recent calculations locally.

## How I want it to work

The calculation engine is written in C++ and runs from the command line. It understands operator precedence, parentheses, and unary minus, and gives clear errors when an expression is invalid or tries to divide by zero.

My Java TCP server runs the same engine for several users at a time with a thread pool. My Java Swing and C# WPF desktop apps send expressions to the server and display the results or errors they receive. The Python load tester uses the same protocol.

My browser calculator uses HTML, CSS, and JavaScript. It sends expressions to the PHP API, which forwards them to the Java server and returns results or errors as JSON. [Architecture diagram](docs/architecture.md) · [TCP protocol specification](docs/protocol.md)

## Calculation history

My Java server can save calculation history to MySQL through JDBC. It keeps answering requests during a database outage. My PHP dashboard shows history across clients, daily activity, the most frequent expressions, and the error rate. The browser calculator also saves recent results locally.

## What I want to test

I use C++ unit tests to check parsing and arithmetic, and a Python regression runner to check the compiled calculator from the command line. I use the Python load tester to measure response times and errors when several clients use the server at once. I'll keep notes on what I learn for my final exam.

GitHub Actions builds the C++ calculator, Java server and Swing client, and C# WPF client on every push and pull request. It also runs the C++ unit tests and Python regression cases. [CI workflow](.github/workflows/ci.yml)

## Local setup

Run the commands from the root of your checkout. The server, database, and PHP examples use a Bash shell. Leave the Java TCP server and the PHP web server running in separate terminals while you use their clients.

| To run | What you need |
| --- | --- |
| [C++ calculator](#run-the-c-calculator) and [unit tests](#run-the-c-unit-tests) | A C++17 compiler and standard library with floating-point `std::to_chars` |
| [Java TCP server](#run-the-java-tcp-server) | The compiled C++ calculator and JDK 17 or later |
| [Java Swing client](#run-the-java-swing-client) | A running TCP server, JDK 17 or later, and a graphical desktop |
| [C# WPF client](#run-the-c-wpf-client) | A running TCP server, Windows, and the .NET 10 SDK |
| [Python regression runner](#run-the-python-regression-cases) and [load tester](#run-the-python-load-tester) | Python 3.9 or later and either the compiled C++ calculator for regression or a running TCP server for load testing |
| [Browser calculator](#run-the-browser-calculator) and [HTTP API](#run-the-php-calculator-api) | A running TCP server, PHP 8, and `curl` for the API example |
| [Shared history](#set-up-mysql-history) and [dashboard](#view-the-php-dashboard) | MySQL 8.0.16 or later, Connector/J for Java writes, and PHP with `pdo_mysql` for dashboard reads |

## Run the C++ calculator

Build and run it from the repository root with a C++17 compiler.

```sh
g++ -std=c++17 core-cpp/main.cpp core-cpp/parser.cpp core-cpp/calculator.cpp -o calc
./calc "2 + 3 * (4 - 1)"
```

This prints `11`. The CLI takes one quoted expression of up to 4091 bytes and supports `+`, `-`, `*`, `/`, parentheses, unary minus, decimals, and scientific notation. It uses double-precision floating-point arithmetic. Errors go to stderr with exit status `1` for invalid calculations or `2` for incorrect usage.

Inside `core-cpp/`, `main.cpp` handles command-line arguments and output, `parser.cpp` builds the expression tree defined in `expression.h`, and `calculator.cpp` evaluates it.

## Run the C++ unit tests

Build and run the test executable from the repository root.

```sh
g++ -std=c++17 -Icore-cpp core-cpp/tests/calculator_tests.cpp \
    core-cpp/parser.cpp core-cpp/calculator.cpp -o calc-tests
./calc-tests
```

## Run the Python regression cases

After building `calc`, run this command from the repository root.

```sh
python3 tools-python/regression.py ./calc tools-python/cases.txt
```

Each nonblank line in the case file contains an expression and its expected numeric result, such as `2+3*(4-1);11`. The runner compares results with a relative tolerance of `1e-12`, reports any failed case by line number, and exits with status `1` if a case fails or `2` if it cannot read the cases or launch the calculator.

## Run the Java TCP server

Build `calc` first, then compile and start the server from the repository root with JDK 17 or later. [C++ build command](#run-the-c-calculator)

```sh
javac --release 17 -d server-java/out \
    engine-java/src/multicalc/engine/*.java server-java/src/multicalc/server/*.java
java -cp server-java/out multicalc.server.CalculatorServer
```

The server listens on `127.0.0.1:5050` and runs `./calc` by default. The optional arguments are the port, path to `calc`, and bind address, in that order. Use port `0` to let the OS choose a free port. The server prints its listening address when it starts.

To check the server from another terminal with netcat, send one request and look for `OK 11`.

```sh
printf 'CALC 2+3*(4-1)\n' | nc -w 2 127.0.0.1 5050
```

The server handles up to eight sessions at once and queues up to 128 waiting connections. An active session closes after five seconds of read inactivity. Press Ctrl+C to stop accepting connections and let calculations already underway finish.

The server admits 100 calculations per second across all connections by default, with a burst capacity of 100. Set `MULTICALC_REQUESTS_PER_SECOND` to a positive integer to change both values. When the budget is exhausted, the server replies `ERR Rate limit exceeded` and keeps the connection open. The rejection happens before engine execution, history writes, or statistics updates. [Threat model](docs/threat-model.md)

### Server statistics

After a graceful shutdown, the server prints successful and failed calculation counts for the whole run and for each client IP address. A calculation counts once the engine produces a result or reports an error. The counters start at zero each time the server starts.

Worker threads increment the global `AtomicLong` counters concurrently. `ConcurrentHashMap.merge` updates each client's counts atomically, even when several connections come from the same address. The server prints the summary after the worker pool drains so both sets of counts reflect the same completed calculations.

## Run the Java Swing client

With the TCP server running, compile and launch the client from the repository root with JDK 17 or later.

```sh
javac --release 17 -d client-java/out client-java/src/multicalc/client/*.java
java -cp client-java/out multicalc.client.CalculatorApp
```

Type an expression or use the keypad, then press Enter or `=`. The client opens a connection for each calculation and uses `127.0.0.1:5050` by default. To use a different endpoint, append its host and port to the `java` command.

## Run the C# WPF client

With the TCP server running, use the .NET 10 SDK on Windows to launch the client from the repository root.

```powershell
dotnet run --project client-csharp/MultiCalc.Client.csproj
```

Type an expression or use the keypad, then press Enter or `=`. The client connects to `127.0.0.1:5050` by default. To use a different endpoint, append `--` followed by the host and port to `dotnet run`. For a client on a separate Windows machine, start the Java server with its reachable private address as the bind address. [Network access and trust assumptions](docs/threat-model.md#deployment-and-data-exposure)

## Run the Python load tester

With the Java TCP server running, use Python 3.9 or later from the repository root.

```sh
python3 tools-python/load_test.py --clients 10 --requests-per-client 10 --seed 123
```

Each client keeps its own connection and sends one expression at a time. Repeat with the same client count, request count, and seed to replay the workload. Use `--host` and `--port` to change the default endpoint `127.0.0.1:5050`.

The report shows successful, failed, and skipped requests, throughput, and response times for successful calculations. A connection or protocol failure ends that client's session and skips any remaining requests. A failed or skipped request gives the command exit status `1`.

[Concurrency results for 1, 10, 50, and 100 clients](docs/concurrency-report.md)

## Set up MySQL history

Use MySQL 8.0.16 or later. From the repository root, run the schema with a database account that can create the `multicalc` database and its `calculations` table.

```sh
mysql -u root -p < database/schema.sql
```

To explore the schema locally, load the sample history. Each run appends six example rows.

```sh
mysql -u root -p < database/sample-data.sql
```

The table defaults `created_at` to UTC and keeps successful results as the engine printed them, including signed zero and scientific notation. Failed calculations store `NULL` for `result`. Use `INET6_NTOA(client_ip)` to display the stored IPv4 or IPv6 address.

### Create the history accounts

For a local MySQL server, open a MySQL administrative session and run the statements below with your own passwords. Create the writer account for Java history and the reader account for the dashboard.

```sql
CREATE USER 'multicalc_writer'@'127.0.0.1' IDENTIFIED BY 'replace_with_writer_password';
GRANT INSERT ON multicalc.calculations TO 'multicalc_writer'@'127.0.0.1';
CREATE USER 'multicalc_reader'@'127.0.0.1' IDENTIFIED BY 'replace_with_reader_password';
GRANT SELECT ON multicalc.calculations TO 'multicalc_reader'@'127.0.0.1';
```

### Enable history in the Java server

Apply the schema, create the writer account, and compile the Java TCP server as above. Stop the Java process if it is already running. Start it with the JDBC driver on the runtime classpath, replacing the JAR path in the command with your download location. The example uses Connector/J 9.7.0. [Connector/J download](https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar)

```bash
export MULTICALC_DB_URL='jdbc:mysql://127.0.0.1:3306/multicalc'
export MULTICALC_DB_USER='multicalc_writer'
read -rsp 'MySQL password: ' MULTICALC_DB_PASSWORD
printf '\n'
export MULTICALC_DB_PASSWORD
java -cp 'server-java/out:/path/to/mysql-connector-j-9.7.0.jar' multicalc.server.CalculatorServer
```

Set `MULTICALC_DB_URL` to enable persistence. A background writer opens one connection per insert and closes it afterward. Each row records the UTC time when the calculation finished. During an outage, the server keeps replying and skips failed writes. When MySQL recovers, new calculations can be saved again. The queue holds up to 128 waiting rows, and the server skips new rows if the queue fills. It gives queued writes up to five seconds to finish during shutdown.

## Start the PHP web server

Use PHP 8 with `web-php-mysql` as the document root. A single PHP process serves the browser calculator, API, and dashboard. To enable dashboard reads, set its database environment variables in the same terminal before starting PHP. With XAMPP on Linux, use `/opt/lampp/bin/php` for the PHP command. [Dashboard configuration](#view-the-php-dashboard)

```sh
php -S 127.0.0.1:8080 -t web-php-mysql
```

## Run the PHP calculator API

Start the Java TCP and PHP web servers. MySQL is optional for calculations. If Java listens at a different address or port than `127.0.0.1:5050`, set `MULTICALC_TCP_HOST` and `MULTICALC_TCP_PORT` before starting PHP. [Java TCP server setup](#run-the-java-tcp-server) · [PHP web server setup](#start-the-php-web-server)

In another terminal, send a calculation to `POST /api/calculate.php`.

```sh
curl -H 'Content-Type: application/json' \
    -d '{"expression":"2+3*(4-1)"}' \
    http://127.0.0.1:8080/api/calculate.php
```

A successful request returns `{"result":"11"}` with status `200`. The result stays a string to preserve the engine's formatting. Errors return an `error` string. Use `400` for invalid input, `405` for another method, `413` for a JSON body over 32768 bytes or an expression over 4091 bytes, `415` for an invalid content type, and `422` for a calculation the Java server rejects. A rate-limited calculation returns `429` with `Retry-After: 1`. An unavailable or invalid upstream response returns `502`. A TCP read or write timeout returns `504`.

## View the PHP dashboard

Apply the database schema, create the reader account, and use PHP with the `pdo_mysql` extension. If the PHP web server is running, stop it with Ctrl+C. Set these environment variables in the PHP server terminal. [MySQL setup](#set-up-mysql-history)

```bash
export MULTICALC_DASHBOARD_DSN='mysql:host=127.0.0.1;port=3306;dbname=multicalc;charset=utf8mb4'
export MULTICALC_DASHBOARD_USER='multicalc_reader'
read -rsp 'MySQL password: ' MULTICALC_DASHBOARD_PASSWORD
printf '\n'
export MULTICALC_DASHBOARD_PASSWORD
```

Start PHP in that terminal and open `http://127.0.0.1:8080/dashboard/`. The dashboard charts the last seven UTC days, ranks the five most frequent expressions across all history, and shows 25 calculations per history page. It reads MySQL directly, so you can explore it with `database/sample-data.sql` before starting Java. [PHP web server command](#start-the-php-web-server)

## Run the browser calculator

Start the Java TCP and PHP web servers, then open `http://127.0.0.1:8080/`. Type an expression or use the keypad. Press Enter in the expression field or select `=` to calculate. [Java TCP server setup](#run-the-java-tcp-server) · [PHP web server setup](#start-the-php-web-server)

The browser saves the last 20 successful calculations. Select a recent calculation to reuse its expression, or select **Clear history** to remove the saved calculations from this browser.
