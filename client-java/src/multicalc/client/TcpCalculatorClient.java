package multicalc.client;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

final class TcpCalculatorClient {
    private static final String REQUEST_PREFIX = "CALC ";
    private static final int MAX_REQUEST_BYTES = 4096;
    private static final int MAX_RESPONSE_BYTES = 4096;
    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    private static final int READ_TIMEOUT_MILLIS = 10000;
    private static final Pattern DECIMAL =
            Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    private final String host;
    private final int port;

    TcpCalculatorClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    String calculate(String expression) throws IOException {
        byte[] request = encodeRequest(expression);
        String response;
        try (var socket = new Socket()) {
            connect(socket);
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            var output = socket.getOutputStream();
            output.write(request);
            output.flush();
            response = readResponse(new BufferedInputStream(socket.getInputStream()));
        } catch (SocketTimeoutException error) {
            throw new IOException("Timed out waiting for the calculator server.", error);
        }
        return parseResponse(response);
    }

    private void connect(Socket socket) throws IOException {
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        } catch (SocketTimeoutException error) {
            throw new IOException("Timed out connecting to " + host + " on port " + port + ".", error);
        } catch (IOException error) {
            throw new IOException("Could not connect to " + host + " on port " + port + ".", error);
        }
    }

    private static byte[] encodeRequest(String expression) throws IOException {
        if (expression.isBlank()) {
            throw new IOException("Enter an expression.");
        }
        if (expression.indexOf('\r') >= 0 || expression.indexOf('\n') >= 0 || expression.indexOf('\0') >= 0) {
            throw new IOException("The expression contains an invalid character.");
        }
        if (expression.length() > MAX_REQUEST_BYTES - REQUEST_PREFIX.length()) {
            throw new IOException("The expression is too long.");
        }

        ByteBuffer encoded;
        try {
            encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(REQUEST_PREFIX + expression));
        } catch (CharacterCodingException error) {
            throw new IOException("The expression contains an invalid character.", error);
        }
        int size = encoded.remaining();
        if (size > MAX_REQUEST_BYTES) {
            throw new IOException("The expression is too long.");
        }
        byte[] request = new byte[size + 1];
        encoded.get(request, 0, size);
        request[size] = '\n';
        return request;
    }

    private static String readResponse(InputStream input) throws IOException {
        var bytes = new ByteArrayOutputStream();
        int next;
        while ((next = input.read()) != '\n') {
            if (next == -1) {
                throw new EOFException("The calculator server closed the connection before sending a complete response.");
            }
            if (bytes.size() == MAX_RESPONSE_BYTES) {
                throw new IOException("The calculator server's response is too long.");
            }
            bytes.write(next);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException error) {
            throw new IOException("The calculator server returned invalid UTF-8.", error);
        }
    }

    private static String parseResponse(String response) throws IOException {
        if (response.indexOf('\r') < 0 && response.indexOf('\0') < 0) {
            if (response.startsWith("ERR ") && response.length() > 4) {
                throw new IOException(response.substring(4));
            }
            if (response.startsWith("OK ")) {
                String result = response.substring(3);
                if (DECIMAL.matcher(result).matches() && Double.isFinite(Double.parseDouble(result))) {
                    return result;
                }
            }
        }
        throw new IOException("The calculator server returned an invalid response.");
    }
}
