package multicalc.client;

import java.awt.GraphicsEnvironment;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public final class CalculatorApp {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 5050;

    private CalculatorApp() {}

    public static void main(String[] args) {
        TcpCalculatorClient client;
        try {
            client = createClient(args);
        } catch (IllegalArgumentException error) {
            System.err.println("Usage: CalculatorApp [host [port]]");
            System.err.println(error.getMessage());
            System.exit(2);
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("MultiCalc needs a graphical display.");
            System.exit(1);
        }

        SwingUtilities.invokeLater(() -> showWindow(client));
    }

    private static TcpCalculatorClient createClient(String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Too many arguments.");
        }
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        if (host.isBlank()) {
            throw new IllegalArgumentException("Enter a server hostname or IP address.");
        }
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535.");
        }
        return new TcpCalculatorClient(host, port);
    }

    private static void showWindow(TcpCalculatorClient client) {
        JFrame frame = new JFrame("MultiCalc");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(new CalculatorPanel(client));
        frame.pack();
        frame.setMinimumSize(frame.getSize());
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
