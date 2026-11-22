# MultiCalc

I want to build a calculator that I can use from a desktop app, a terminal, or a browser. All three should use the same calculation engine, so an expression like `2 + 3 * (4 - 1)` gives me `11` wherever I enter it.

I'm using this project to bring together the languages and tools I study at school. I want to see how a parser, a network service, a database, and different user interfaces can work together in one application.

## v0.1.0

My first release includes the C++ command-line calculator and the Java Swing desktop client. The C++ unit tests check the parser and arithmetic, and the Python regression cases check the result printed by the compiled calculator. The build, run, and test commands are below.

## How I want it to work

The calculation engine is written in C++ and runs from the command line. It understands operator precedence, parentheses, and unary minus, and gives clear errors when an expression is invalid or tries to divide by zero.

My Java TCP server runs the same engine for several users at a time with a thread pool. My Java Swing and C# WPF desktop apps send expressions to the server and display the results or errors they receive. I'll add a Python command-line client that uses the same protocol. [TCP protocol specification](docs/protocol.md)

For the web version, I'll build a calculator in HTML, CSS, and JavaScript. It will send expressions to a PHP API, which will pass them to the Java server and return the result or error as JSON.

## Calculation history

I want the Java server to save calculations to MySQL through JDBC while continuing to answer requests if the database is temporarily unavailable. The browser calculator will show a local history. A PHP dashboard will show the history across clients and let me see how often the calculator is used, which expressions are common, and how many requests return errors.

## What I want to test

I use C++ unit tests to check parsing and arithmetic, and a Python regression runner to check the compiled calculator from the command line. I also want to simulate clients using the server at the same time and measure response times and errors. I'll use GitHub Actions to run the automated checks and keep notes on what I learn for my final exam.

## Run the C++ calculator

Build and run it from the repository root with a C++17 compiler.

```sh
g++ -std=c++17 core-cpp/main.cpp core-cpp/parser.cpp core-cpp/calculator.cpp -o calc
./calc "2 + 3 * (4 - 1)"
```

This prints `11`. The CLI takes one quoted expression and supports `+`, `-`, `*`, `/`, parentheses, unary minus, decimals, and scientific notation. It uses double-precision floating-point arithmetic. Errors go to stderr with exit status `1` for invalid calculations or `2` for incorrect usage.

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
