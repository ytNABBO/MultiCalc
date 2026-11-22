package multicalc.server;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.function.BooleanSupplier;
import multicalc.engine.CalculatorEngine;

final class ClientSession implements Runnable {
    private static final int READ_TIMEOUT_MILLIS = 5000;
    private static final String ENGINE_ERROR_PREFIX = "Error: ";

    private final Socket socket;
    private final CalculatorEngine engine;
    private final BooleanSupplier stopping;

    ClientSession(Socket socket, CalculatorEngine engine, BooleanSupplier stopping) {
        this.socket = socket;
        this.engine = engine;
        this.stopping = stopping;
    }

    @Override
    public void run() {
        try (socket) {
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            var input = new BufferedInputStream(socket.getInputStream());
            var output = socket.getOutputStream();

            while (!stopping.getAsBoolean()) {
                var request = Protocol.readRequest(input);
                if (request == null || stopping.getAsBoolean()) {
                    return;
                }
                if (request.error() != null) {
                    Protocol.writeError(output, request.error());
                    if (request.closesConnection()) {
                        return;
                    }
                } else {
                    calculate(output, request.expression());
                }
            }
        } catch (IOException disconnected) {
            // Socket errors and read timeouts end this session only.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void calculate(OutputStream output, String expression) throws IOException, InterruptedException {
        String result;
        try {
            result = engine.calculate(expression);
        } catch (IOException failure) {
            String message = failure.getMessage();
            Protocol.writeError(output, message != null && message.startsWith(ENGINE_ERROR_PREFIX)
                    ? message.substring(ENGINE_ERROR_PREFIX.length())
                    : "Calculation failed");
            return;
        }
        Protocol.writeResult(output, result);
    }
}
