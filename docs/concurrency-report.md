# MultiCalc concurrency report

The Java TCP server completed all 9,660 measured calculations. Median throughput reached 722.1 requests per second at 50 clients. At 100 clients, the median p95 was 26.00 ms and the slowest response took 2.86 seconds.

## Setup

- Source commit `3c451b9`, built from a clean copy.
- One Debian 13 x86-64 machine with an Intel Core i5-5350U and four available logical CPUs. The Java server, C++ engine, and Python clients ran on the same machine, with TCP traffic on loopback.
- GCC 14.2.0 with `-O2`, OpenJDK 21.0.13.1 with Java 17 compile targeting, and Python 3.13.5.
- The server had eight workers and room for 128 queued connections. Each worker serves one connection until that session ends.
- One warm-up of eight clients with ten requests each, followed by three trials at each client count. Each measured client sent 20 expressions over one connection, waited for each reply before sending the next request, and used a workload generated from seed `123`.

## Results

Request counts sum the three trials for each client count. Elapsed time, throughput, mean latency, and p95 are medians of the per-trial values. Max latency is the largest observed successful request time across the three trials.

| Clients | Successful | Failed | Skipped | Elapsed (s) | Requests/s | Mean (ms) | p95 (ms) | Max (ms) |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 60 | 0 | 0 | 0.059 | 339.1 | 2.80 | 3.98 | 5.45 |
| 10 | 600 | 0 | 0 | 0.311 | 642.1 | 12.41 | 18.94 | 307.68 |
| 50 | 3000 | 0 | 0 | 1.385 | 722.1 | 39.64 | 22.02 | 1325.96 |
| 100 | 6000 | 0 | 0 | 3.050 | 655.8 | 80.79 | 26.00 | 2857.66 |

Throughput divides correct replies by the full trial time, including connection setup. Latency measures each correct request from send to reply and includes any worker queue wait after connecting, the C++ process launch, and the calculation itself. The load tester calculates p95 using the nearest-rank method.

The throughput ranges at 50 clients (661.3–725.6 requests/s) and 100 clients (642.9–723.6 requests/s) overlapped. The clearest change was in latency. With eight workers at 100 clients, up to 92 sessions can wait for a worker. Their first requests are at most 4.6% of the 2,000 requests in a trial, so p95 can stay low while the longest waits grow. This is consistent with the rise in mean and maximum latency.

All measured replies matched the expected values, and every session completed. The server independently reported 9,740 successful calculations after shutdown, including 80 warm-up requests. These figures describe finite loopback bursts on one shared machine.

## Reproduce

Build the C++ executable with `-O2` and start a fresh Java server on loopback. [Build and run instructions](../README.md#run-the-java-tcp-server)

On versions with request rate limiting, start the benchmark server with `MULTICALC_REQUESTS_PER_SECOND=2000` so the test measures concurrency without hitting the default shared rate limit.

Run the warm-up and measured trials sequentially from the repository root. If the server uses a different port, pass `--port` to each invocation.

```sh
python3 tools-python/load_test.py --clients 8 --requests-per-client 10 --seed 123
for trial in 1 2 3; do
    for clients in 1 10 50 100; do
        python3 tools-python/load_test.py --clients "$clients" --requests-per-client 20 --seed 123
    done
done
```
