import argparse
import math
import random
import socket
import statistics
import sys
import threading
import time
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field

from tcp_client import (
    DEFAULT_HOST,
    DEFAULT_PORT,
    SOCKET_TIMEOUT_SECONDS,
    CalculationError,
    ProtocolError,
    calculate,
)


@dataclass
class ClientReport:
    attempted: int = 0
    latencies_ms: list[float] = field(default_factory=list)
    issues: Counter[str] = field(default_factory=Counter)
    disconnected: bool = False


def random_calculation(generator):
    left, middle, right = (generator.randint(1, 100) for _ in range(3))
    return generator.choice((
        (f"{left}+{middle}*{right}", left + middle * right),
        (f"({left}+{middle})*{right}", (left + middle) * right),
        (f"{left}-{middle}-{right}", left - middle - right),
        (f"-({left}+{middle})/{right}", -(left + middle) / right),
        (f"{left}/{middle}/{right}", left / middle / right),
    ))


def run_client(options, client_number, start, cancelled):
    report = ClientReport()
    generator = random.Random(options.seed + client_number)
    start.wait()
    if cancelled.is_set():
        return report

    try:
        with socket.create_connection((options.host, options.port), SOCKET_TIMEOUT_SECONDS) as connection:
            with connection.makefile("rb") as responses:
                for _ in range(options.requests_per_client):
                    if cancelled.is_set():
                        break
                    expression, expected = random_calculation(generator)
                    report.attempted += 1
                    sent_at = time.perf_counter()
                    try:
                        actual = calculate(connection, responses, expression)
                    except CalculationError as error:
                        report.issues[f"Server error: {error}"] += 1
                        continue
                    latency_ms = (time.perf_counter() - sent_at) * 1000
                    if math.isclose(actual, expected, rel_tol=1e-12):
                        report.latencies_ms.append(latency_ms)
                    else:
                        report.issues[f"{expression} expected {expected}, got {actual}"] += 1
    except (OSError, ProtocolError) as error:
        report.disconnected = True
        report.issues[f"Connection or protocol error: {error}"] += 1
    return report


def run_load(options):
    start = threading.Event()
    cancelled = threading.Event()
    with ThreadPoolExecutor(max_workers=options.clients) as executor:
        try:
            futures = [
                executor.submit(run_client, options, number, start, cancelled)
                for number in range(options.clients)
            ]
            started_at = time.perf_counter()
            start.set()
            reports = [future.result() for future in futures]
        except BaseException:
            cancelled.set()
            start.set()
            raise
    return reports, time.perf_counter() - started_at


def print_summary(options, reports, elapsed):
    latencies = sorted(latency for report in reports for latency in report.latencies_ms)
    succeeded = len(latencies)
    attempted = sum(report.attempted for report in reports)
    failed = attempted - succeeded
    skipped = options.clients * options.requests_per_client - attempted
    disconnected = sum(report.disconnected for report in reports)

    print(f"Load test with {options.clients} clients, {options.requests_per_client} requests each, seed {options.seed}")
    print(f"Requests attempted={attempted} succeeded={succeeded} failed={failed} skipped={skipped}")
    print(f"Client sessions interrupted={disconnected}")
    print(f"Elapsed {elapsed:.3f} s, successful requests/s {succeeded / elapsed:.1f}")
    if latencies:
        p95 = latencies[math.ceil(0.95 * succeeded) - 1]
        print(f"Successful request latency (ms) min={latencies[0]:.2f} mean={statistics.fmean(latencies):.2f} "
              f"p95={p95:.2f} max={latencies[-1]:.2f}")

    issues = Counter()
    for report in reports:
        issues.update(report.issues)
    for message, count in issues.most_common(5):
        print(f"{count} × {message!r}", file=sys.stderr)

    return int(failed > 0 or skipped > 0 or disconnected > 0)


def positive_int(value):
    number = int(value)
    if number < 1:
        raise argparse.ArgumentTypeError("must be a positive integer")
    return number


def main():
    parser = argparse.ArgumentParser(description="Measure the calculator server under concurrent TCP requests.")
    parser.add_argument("--host", default=DEFAULT_HOST, help="server hostname or IP address (default: %(default)s)")
    parser.add_argument("--port", type=positive_int, default=DEFAULT_PORT, help="server port (default: %(default)s)")
    parser.add_argument("--clients", type=positive_int, default=10, help="concurrent clients (default: %(default)s)")
    parser.add_argument("--requests-per-client", type=positive_int, default=10,
                        help="calculations per client (default: %(default)s)")
    parser.add_argument("--seed", type=int, default=0, help="seed for the expression generator (default: %(default)s)")
    options = parser.parse_args()
    if options.port > 65535:
        parser.error("--port must be between 1 and 65535")
    if not options.host.strip():
        parser.error("--host must be a hostname or IP address")

    try:
        reports, elapsed = run_load(options)
    except KeyboardInterrupt:
        print("Load test interrupted.", file=sys.stderr)
        return 130
    except RuntimeError as error:
        print(f"Could not run the load test: {error}", file=sys.stderr)
        return 2
    return print_summary(options, reports, elapsed)


if __name__ == "__main__":
    sys.exit(main())
