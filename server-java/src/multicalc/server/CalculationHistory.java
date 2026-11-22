package multicalc.server;

import java.net.InetAddress;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class CalculationHistory implements AutoCloseable {
    private static final int QUEUE_CAPACITY = 128;
    private static final int SHUTDOWN_GRACE_SECONDS = 5;
    private static final String INSERT = """
            INSERT INTO calculations (created_at, expression, result, status, client_ip)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final String url;
    private final Properties connectionProperties;
    private final ThreadPoolExecutor writer;
    private final AtomicLong failedWrites = new AtomicLong();
    private final AtomicLong skippedWrites = new AtomicLong();

    private CalculationHistory(String url, Properties connectionProperties) {
        this.url = url;
        this.connectionProperties = connectionProperties;
        writer = url == null ? null : new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY), task -> {
                    var thread = new Thread(task, "calculation-history-writer");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    static CalculationHistory fromEnvironment() {
        String url = System.getenv("MULTICALC_DB_URL");
        if (url == null || url.isBlank()) {
            return new CalculationHistory(null, null);
        }

        try {
            DriverManager.getDriver(url);
        } catch (SQLException error) {
            System.err.println("Calculation history disabled. Check the JDBC URL and add MySQL Connector/J to the classpath.");
            return new CalculationHistory(null, null);
        }

        var properties = new Properties();
        properties.setProperty("connectTimeout", "1500");
        properties.setProperty("socketTimeout", "1500");
        String user = System.getenv("MULTICALC_DB_USER");
        String password = System.getenv("MULTICALC_DB_PASSWORD");
        if (user != null) {
            properties.setProperty("user", user);
        }
        if (password != null) {
            properties.setProperty("password", password);
        }
        return new CalculationHistory(url, properties);
    }

    void record(InetAddress client, String expression, String result) {
        if (writer == null) {
            return;
        }

        LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
        byte[] address = client.getAddress();
        try {
            writer.execute(() -> insert(createdAt, expression, result, address));
        } catch (RejectedExecutionException error) {
            if (skippedWrites.incrementAndGet() == 1) {
                System.err.println("Calculation history skipped a row because its writer queue is full or closed.");
            }
        }
    }

    private void insert(LocalDateTime createdAt, String expression, String result, byte[] address) {
        try (var connection = DriverManager.getConnection(url, connectionProperties);
             var statement = connection.prepareStatement(INSERT)) {
            statement.setObject(1, createdAt);
            statement.setString(2, expression);
            statement.setString(3, result);
            statement.setString(4, result == null ? "ERR" : "OK");
            statement.setBytes(5, address);
            statement.executeUpdate();
        } catch (SQLException error) {
            if (failedWrites.incrementAndGet() == 1) {
                System.err.println("Calculation history write failed (SQL state " + error.getSQLState() + ").");
            }
        }
    }

    @Override
    public void close() {
        if (writer == null) {
            return;
        }

        writer.shutdown();
        try {
            if (!writer.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
                stopWriter();
            }
        } catch (InterruptedException error) {
            stopWriter();
            Thread.currentThread().interrupt();
        }

        long failed = failedWrites.get();
        long skipped = skippedWrites.get();
        if (failed > 0 || skipped > 0) {
            System.err.println("Calculation history failed=" + failed + " skipped=" + skipped);
        }
    }

    private void stopWriter() {
        skippedWrites.addAndGet(writer.shutdownNow().size());
        System.err.println("Calculation history stopped waiting for pending writes during shutdown.");
    }
}
