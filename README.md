# MultiCalc

I want to build a calculator that I can use from a desktop app, a terminal, or a browser. All three should use the same calculation engine, so an expression like `2 + 3 * (4 - 1)` gives me `11` wherever I enter it.

I'm using this project to bring together the languages and tools I study at school. I want to see how a parser, a network service, a database, and different user interfaces can work together in one application.

## How I want it to work

I'll write the calculation engine in C++ and give it a command-line interface. I want it to understand operator precedence, parentheses, and unary minus, and to give clear errors when an expression is invalid or tries to divide by zero. My first desktop client will use Java Swing and run the C++ program directly.

Once the engine works, I'll build a Java TCP server that runs it for several users at the same time. A thread pool will handle concurrent connections. I'll connect the Swing app to the server and build a second desktop client in C# with WPF. A Python command-line client will use the same protocol, sending an expression and receiving either a result or an error.

For the web version, I'll build a calculator in HTML, CSS, and JavaScript. It will send expressions to a PHP API, which will pass them to the Java server and return the result or error as JSON.

## Calculation history

I want the Java server to save calculations to MySQL through JDBC while continuing to answer requests if the database is temporarily unavailable. The browser calculator will show a local history. A PHP dashboard will show the history across clients and let me see how often the calculator is used, which expressions are common, and how many requests return errors.

## What I want to test

I'll write unit tests for the C++ parser and use Python to check that the engine gives the expected answers as the project grows. I also want to simulate clients using the server at the same time and measure response times and errors. I'll use GitHub Actions to run the automated checks and keep notes on what I learn for my final exam.

## Project status

MultiCalc is in the planning stage.
