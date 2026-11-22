package multicalc.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

final class Protocol {
    private static final int MAX_REQUEST_BYTES = 4096;
    private static final String COMMAND = "CALC ";

    private Protocol() {}

    record Request(String expression, String error, boolean closesConnection) {
        private static Request calculation(String expression) {
            return new Request(expression, null, false);
        }

        private static Request invalid(String message) {
            return new Request(null, message, false);
        }
    }

    static Request readRequest(InputStream input) throws IOException {
        byte[] bytes = new byte[MAX_REQUEST_BYTES + 1];
        int length = 0;
        int next;
        while ((next = input.read()) != -1) {
            if (next == '\n') {
                if (length > 0 && bytes[length - 1] == '\r') {
                    --length;
                }
                return parseRequest(bytes, length);
            }

            // The extra byte can only be the CR of a CRLF terminator.
            if (length == bytes.length || (length == MAX_REQUEST_BYTES && next != '\r')) {
                return new Request(null, "Request too long", true);
            }
            bytes[length++] = (byte) next;
        }
        return null;
    }

    private static Request parseRequest(byte[] bytes, int length) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes, 0, length)).toString();
        } catch (CharacterCodingException invalidEncoding) {
            return Request.invalid("Invalid UTF-8");
        }

        if (!text.startsWith(COMMAND)) {
            return Request.invalid("Invalid request");
        }
        String expression = text.substring(COMMAND.length());
        if (expression.indexOf('\r') >= 0 || expression.indexOf('\0') >= 0) {
            return Request.invalid("Invalid expression character");
        }
        if (expression.isBlank()) {
            return Request.invalid("Empty expression");
        }
        return Request.calculation(expression);
    }

    static void writeResult(OutputStream output, String result) throws IOException {
        writeLine(output, "OK " + result);
    }

    static void writeError(OutputStream output, String message) throws IOException {
        String text = message.replace('\r', ' ').replace('\n', ' ').replace('\0', ' ').strip();
        writeLine(output, "ERR " + (text.isEmpty() ? "Calculation failed" : text));
    }

    private static void writeLine(OutputStream output, String line) throws IOException {
        output.write((line + '\n').getBytes(StandardCharsets.UTF_8));
        output.flush();
    }
}
