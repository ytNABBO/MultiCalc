package multicalc.server;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import multicalc.engine.CalculatorEngine;

public final class CalculatorServer implements AutoCloseable {
    private static final int DEFAULT_PORT = 5050;
    private static final int WORKERS = 8;
    private static final int QUEUE_CAPACITY = 128;
    private static final int SHUTDOWN_GRACE_SECONDS = 6;

    private final ServerSocket listener;
    private final CalculatorEngine engine;
    private final ServerStats stats = new ServerStats();
    private final ExecutorService workers = new ThreadPoolExecutor(
            WORKERS, WORKERS, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY));
    private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean stopping = new AtomicBoolean();

    private CalculatorServer(ServerSocket listener, CalculatorEngine engine) {
        this.listener = listener;
        this.engine = engine;
    }

    public static void main(String[] args) {
        try {
            if (args.length > 3) {
                throw new IllegalArgumentException("Too many arguments.");
            }
            int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 0 and 65535.");
            }
            Path executable = Path.of(args.length > 1 ? args[1] : "./calc");
            String bindAddress = args.length > 2 ? args[2] : "127.0.0.1";
            start(port, executable, bindAddress);
        } catch (IllegalArgumentException error) {
            System.err.println("Usage: CalculatorServer [port [path-to-calc [bind-address]]]");
            System.err.println(error.getMessage());
            System.exit(2);
        } catch (IOException error) {
            System.err.println("Server error: " + error.getMessage());
            System.exit(1);
        }
    }

    private static void start(int port, Path executable, String bindAddress) throws IOException {
        if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
            throw new IOException("Calculator executable is unavailable at " + executable.toAbsolutePath());
        }

        var engine = new CalculatorEngine(executable);
        var listener = new ServerSocket(port, QUEUE_CAPACITY, InetAddress.getByName(bindAddress));
        try (var server = new CalculatorServer(listener, engine)) {
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "calculator-shutdown"));
            String host = listener.getInetAddress().getHostAddress();
            if (host.contains(":")) {
                host = "[" + host + "]";
            }
            System.out.println("Listening on " + host + ":" + listener.getLocalPort());
            server.serve();
        }
    }

    private void serve() throws IOException {
        while (!stopping.get()) {
            Socket connection;
            try {
                connection = listener.accept();
            } catch (SocketException error) {
                if (stopping.get()) {
                    return;
                }
                throw error;
            }

            connections.add(connection);
            try {
                workers.execute(() -> handle(connection));
            } catch (RejectedExecutionException error) {
                closeQuietly(connection);
                connections.remove(connection);
            }
        }
    }

    private void handle(Socket connection) {
        try {
            new ClientSession(connection, engine, stats, stopping::get).run();
        } finally {
            connections.remove(connection);
        }
    }

    @Override
    public void close() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }

        closeQuietly(listener);
        workers.shutdown();
        // Unblock idle reads while allowing in-progress calculations to write their replies.
        connections.forEach(CalculatorServer::stopReading);

        try {
            if (workers.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
                stats.printSummary(System.out);
                return;
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }

        workers.shutdownNow();
        connections.forEach(CalculatorServer::closeQuietly);
    }

    private static void stopReading(Socket connection) {
        try {
            connection.shutdownInput();
        } catch (IOException ignored) {
            // The session may have closed the socket concurrently.
        }
    }

    private static void closeQuietly(Closeable resource) {
        try {
            resource.close();
        } catch (IOException ignored) {
            // Shutdown continues if a socket reports a close error.
        }
    }
}
