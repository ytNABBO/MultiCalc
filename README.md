# MultiCalc

I want to build a calculator that I can use from a desktop app, a terminal, or a browser. All three should use the same calculation engine, so an expression like `2 + 3 * (4 - 1)` gives me `11` wherever I enter it.

I'm using this project to bring together the languages and tools I study at school. I want to see how a parser, a network service, a database, and different user interfaces can work together in one application.

## v0.3.0

I can calculate in the browser using the same engine as the desktop clients. The Java server can save results to MySQL, and the PHP dashboard shows shared history, daily activity, and the error rate. The browser keeps its own recent calculations locally.

## How I want it to work

The calculation engine is written in C++ and runs from the command line. It understands operator precedence, parentheses, and unary minus, and gives clear errors when an expression is invalid or tries to divide by zero.

My Java TCP server runs the same engine for several users at a time with a thread pool. My Java Swing and C# WPF desktop apps send expressions to the server and display the results or errors they receive. The Python load tester uses the same protocol. [TCP protocol specification](docs/protocol.md)

My browser calculator uses HTML, CSS, and JavaScript. It sends expressions to the PHP API, which forwards them to the Java server and returns results or errors as JSON.

## Calculation history

My Java server can save calculation history to MySQL through JDBC. It keeps answering requests during a database outage. My PHP dashboard shows history across clients, daily activity, the most frequent expressions, and the error rate. The browser calculator also saves recent results locally.

## What I want to test

I use C++ unit tests to check parsing and arithmetic, and a Python regression runner to check the compiled calculator from the command line. I use the Python load tester to measure response times and errors when several clients use the server at once. I'll keep notes on what I learn for my final exam.

GitHub Actions builds the C++ calculator, Java server and Swing client, and C# WPF client on every push and pull request. It also runs the C++ unit tests and Python regression cases. [CI workflow](.github/workflows/ci.yml)

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

Build `calc` first, then compile and start the server from the repository root with JDK 17 or later.

```sh
javac --release 17 -d server-java/out \
    engine-java/src/multicalc/engine/*.java server-java/src/multicalc/server/*.java
java -cp server-java/out multicalc.server.CalculatorServer
```

The server listens on `127.0.0.1:5050` and runs `./calc` by default. The optional arguments are the port, path to `calc`, and bind address, in that order. Use port `0` to let the OS choose a free port. The server prints its listening address when it starts.

Send `CALC 2+3*(4-1)` followed by a newline to receive `OK 11`. Send another request after reading the response. The server handles up to eight sessions at once and queues up to 128 waiting connections. An active session closes after five seconds of read inactivity. Press Ctrl+C to stop accepting connections and let calculations already underway finish.

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

Type an expression or use the keypad, then press Enter or `=`. The client opens a connection for each calculation and uses `127.0.0.1:5050` by default. Pass the host and port as arguments to connect to another server.

## Run the C# WPF client

With the TCP server running, use the .NET 10 SDK on Windows to launch the client from the repository root.

```powershell
dotnet run --project client-csharp/MultiCalc.Client.csproj
```

Type an expression or use the keypad, then press Enter or `=`. The client connects to `127.0.0.1:5050` by default. To use a different server, append `--` followed by the host and port to the command.

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

### Enable history in the Java server

Apply the schema and build the Java server as above. Use a MySQL account with `INSERT` access to `multicalc.calculations` and add the JDBC driver to the runtime classpath. The example uses Connector/J 9.7.0. [Connector/J download](https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar)

```bash
export MULTICALC_DB_URL='jdbc:mysql://127.0.0.1:3306/multicalc'
export MULTICALC_DB_USER='your_mysql_user'
read -rsp 'MySQL password: ' MULTICALC_DB_PASSWORD
printf '\n'
export MULTICALC_DB_PASSWORD
java -cp 'server-java/out:/path/to/mysql-connector-j-9.7.0.jar' multicalc.server.CalculatorServer
```

Set `MULTICALC_DB_URL` to enable persistence. A background writer opens one connection per insert and closes it afterward. Each row records the UTC time when the calculation finished. During an outage, the server keeps replying and skips failed writes. When MySQL recovers, new calculations can be saved again. The queue holds up to 128 waiting rows, and the server skips new rows if the queue fills. It gives queued writes up to five seconds to finish during shutdown.

## Run the PHP calculator API

Start the Java TCP server. From the repository root, start a local PHP 8 server with `web-php-mysql` as its document root. With XAMPP on Linux, use `/opt/lampp/bin/php` for the PHP command.

```sh
php -S 127.0.0.1:8080 -t web-php-mysql
```

In another terminal, send a calculation to `POST /api/calculate.php`.

```sh
curl -H 'Content-Type: application/json' \
    -d '{"expression":"2+3*(4-1)"}' \
    http://127.0.0.1:8080/api/calculate.php
```

A successful request returns `{"result":"11"}` with status `200`. The result stays a string to preserve the engine's formatting. Errors return an `error` string. Use `400` for invalid input, `405` for another method, `413` for a JSON body over 32768 bytes or an expression over 4091 bytes, `415` for an invalid content type, and `422` for a calculation the Java server rejects. A rate-limited calculation returns `429` with `Retry-After: 1`. An unavailable or invalid upstream response returns `502`. A TCP read or write timeout returns `504`.

The PHP API connects to `127.0.0.1:5050` by default. Set `MULTICALC_TCP_HOST` and `MULTICALC_TCP_PORT` before starting PHP to use another calculator server.

## View the PHP dashboard

Apply `database/schema.sql` and use PHP with the `pdo_mysql` extension. Start PHP with the dashboard's own database account, which needs `SELECT` access on `multicalc.calculations`.

```bash
export MULTICALC_DASHBOARD_DSN='mysql:host=127.0.0.1;port=3306;dbname=multicalc;charset=utf8mb4'
export MULTICALC_DASHBOARD_USER='your_read_only_mysql_user'
read -rsp 'MySQL password: ' MULTICALC_DASHBOARD_PASSWORD
printf '\n'
export MULTICALC_DASHBOARD_PASSWORD
php -S 127.0.0.1:8080 -t web-php-mysql
```

Open `http://127.0.0.1:8080/dashboard/`. The dashboard charts the last seven UTC days, ranks the five most frequent expressions across all history, and shows 25 calculations per history page. Load `database/sample-data.sql` to explore it with example rows.

## Run the browser calculator

Start the Java TCP server and PHP server as above, then open `http://127.0.0.1:8080/`. Type an expression or use the keypad. Press Enter in the expression field or select `=` to calculate.

The browser saves the last 20 successful calculations. Select a recent calculation to reuse its expression, or select **Clear history** to remove the saved calculations from this browser.
