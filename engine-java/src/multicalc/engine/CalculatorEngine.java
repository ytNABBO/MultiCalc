package multicalc.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class CalculatorEngine {
    private static final int TIMEOUT_SECONDS = 5;

    private final Path executable;

    public CalculatorEngine(Path executable) {
        this.executable = executable.toAbsolutePath();
    }

    public String calculate(String expression) throws IOException, InterruptedException {
        Process process;
        try {
            process = new ProcessBuilder(executable.toString(), expression).start();
        } catch (IOException error) {
            throw new IOException("Could not start the calculator at " + executable, error);
        }

        try (var stdout = process.getInputStream(); var stderr = process.getErrorStream()) {
            process.getOutputStream().close();

            // The calculator writes one short result or error, so neither pipe fills.
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IOException("Calculator timed out after " + TIMEOUT_SECONDS + " seconds");
            }

            if (process.exitValue() != 0) {
                String error = new String(stderr.readAllBytes(), StandardCharsets.UTF_8).strip();
                throw new IOException(error.isEmpty()
                        ? "Calculator exited with status " + process.exitValue()
                        : error);
            }

            String output = new String(stdout.readAllBytes(), StandardCharsets.UTF_8);
            String result = output.strip();
            if (output.lines().count() != 1 || !isFiniteNumber(result)) {
                throw new IOException("Calculator did not return a numeric result");
            }
            return result;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static boolean isFiniteNumber(String result) {
        try {
            return Double.isFinite(Double.parseDouble(result));
        } catch (NumberFormatException error) {
            return false;
        }
    }
}
