# MultiCalc

I want to build a calculator that I can use from a desktop app, a terminal, or a browser. All three should use the same calculation engine, so an expression like `2 + 3 * (4 - 1)` gives me `11` wherever I enter it.

I'm using this project to bring together the languages and tools I study at school. I want to see how a parser, a network service, a database, and different user interfaces can work together in one application.

## How I want it to work

The calculation engine is written in C++ and runs from the command line. It understands operator precedence, parentheses, and unary minus, and gives clear errors when an expression is invalid or tries to divide by zero. My first desktop client will use Java Swing and run the C++ program directly.

I'll build a Java TCP server that runs the engine for several users at the same time. A thread pool will handle concurrent connections. I'll connect the Swing app to the server and build a second desktop client in C# with WPF. A Python command-line client will use the same protocol, sending an expression and receiving either a result or an error.

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
