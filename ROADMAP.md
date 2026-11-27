# MultiCalc roadmap

MultiCalc is a modular, concurrent calculator that brings together the languages and tools studied during the school year. The C++ engine serves the terminal, Java Swing and C# WPF clients, and the browser. The Java TCP server can save history to MySQL for the PHP dashboard. [Architecture](docs/architecture.md) · [Local setup](README.md#local-setup)

## Phases

| Phase | Original study window | Milestone |
| --- | --- | --- |
| Foundations and GitHub setup | November 2026 | Repository structure, setup documentation, and build output ignores |
| Computational core and basic desktop | November–December 2026 | [v0.1.0](https://github.com/ytNABBO/MultiCalc/releases/tag/v0.1.0) |
| Networking and concurrency | January–February 2027 | [v0.2.0](https://github.com/ytNABBO/MultiCalc/releases/tag/v0.2.0) |
| Data persistence and web integration | March–April 2027 | [v0.3.0](https://github.com/ytNABBO/MultiCalc/releases/tag/v0.3.0) |
| Hardening, CI, and exam | May–June 2027 | [v1.0.0](https://github.com/ytNABBO/MultiCalc/releases/tag/v1.0.0) and [exam report](docs/exam-report.md) |

### Foundations and GitHub setup

- [x] Set up the public repository and its component directories.
- [x] Document the project goal, architecture, setup, and roadmap. Ignore generated files from the documented build commands.
- [ ] Set up a GitHub Project board with To Do, In Progress, and Done, and use Issues to track tasks.

The release history uses `main`. The initial plan proposed a `dev` branch and feature branches. [Commit history](https://github.com/ytNABBO/MultiCalc/commits/main/) · [Issues](https://github.com/ytNABBO/MultiCalc/issues)

### Computational core and basic desktop

- [x] Build a C++ command-line calculator with a recursive-descent parser, operator precedence, parentheses, unary minus, and clear errors.
- [x] Cover parsing and arithmetic with C++ unit tests and a Python CLI regression runner.
- [x] Connect the first Swing client directly to the C++ executable through `ProcessBuilder`.
- [x] Tag `v0.1.0` and document the build and run commands.

### Networking and concurrency

- [x] Define a UTF-8 line-based TCP protocol. [Protocol specification](docs/protocol.md)
- [x] Build a concurrent Java TCP server with a thread pool, socket timeouts, graceful shutdown, and thread-safe statistics.
- [x] Connect the Swing and C# WPF clients to the server.
- [x] Use Python to measure errors and latency with 1, 10, 50, and 100 clients. [Concurrency report](docs/concurrency-report.md)
- [x] Tag `v0.2.0`.

### Data persistence and web integration

- [x] Add the MySQL history schema and sample data.
- [x] Save outcomes through JDBC and keep the calculator responsive during database failures.
- [x] Add a PHP JSON API and a dashboard for shared history, daily activity, common expressions, and error rate.
- [x] Build the responsive browser calculator with `fetch` and local history.
- [x] Tag `v0.3.0`.

### Hardening, CI, and exam

- [x] Build C++, Java, and C# in GitHub Actions and run the C++ and Python regression checks. [CI workflow](.github/workflows/ci.yml)
- [x] Enforce input size and calculation rate limits, and describe the trust boundaries. [Threat model](docs/threat-model.md)
- [x] Finish the architecture diagram, protocol specification, and setup guide.
- [x] Tag `v1.0.0`.
- [x] Prepare the oral-exam report from the project docs, release history, measured results, and live demo.

## After v1.0.0

- [x] Add the Python command-line TCP client for sending one expression from a terminal. [Usage](README.md#run-the-python-tcp-client)

## Future school topics

These ideas can become `school-topic` issues as the corresponding topics come up in class.

| Topic | Possible integration |
| --- | --- |
| Cryptography and security | Result hashes, an RSA demo, TLS for TCP, or dashboard sign-in |
| Data structures and algorithms | Variables and functions, an expression cache, or AST optimizations |
| Networking | Run the server and database on separate machines or VMs and document the network setup |
| Android | A mobile client using the existing TCP protocol |
| Virtualization and containers | Docker Compose for the server, MySQL, and PHP |
| Testing and software engineering | More test coverage, pull request reviews, or CI style checks |
