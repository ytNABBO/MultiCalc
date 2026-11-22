package multicalc.server;

import java.io.PrintStream;
import java.net.InetAddress;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class ServerStats {
    private static final Counts SUCCESS = new Counts(1, 0);
    private static final Counts FAILURE = new Counts(0, 1);

    private final AtomicLong succeeded = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final ConcurrentHashMap<InetAddress, Counts> byClient = new ConcurrentHashMap<>();

    void recordCalculation(InetAddress client, boolean succeeded) {
        // Atomic increments protect the totals, and merge serializes updates for each address.
        if (succeeded) {
            this.succeeded.incrementAndGet();
        } else {
            failed.incrementAndGet();
        }
        byClient.merge(client, succeeded ? SUCCESS : FAILURE, Counts::plus);
    }

    void printSummary(PrintStream output) {
        // Call after worker shutdown so the global and per-client counts describe the same work.
        printCounts(output, "Calculations", new Counts(succeeded.get(), failed.get()));
        byClient.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(InetAddress::getHostAddress)))
                .forEachOrdered(entry ->
                        printCounts(output, "Client " + entry.getKey().getHostAddress(), entry.getValue()));
    }

    private static void printCounts(PrintStream output, String label, Counts counts) {
        output.println(label + " total=" + (counts.succeeded() + counts.failed())
                + " succeeded=" + counts.succeeded() + " failed=" + counts.failed());
    }

    private record Counts(long succeeded, long failed) {
        private Counts plus(Counts other) {
            return new Counts(succeeded + other.succeeded, failed + other.failed);
        }
    }
}
