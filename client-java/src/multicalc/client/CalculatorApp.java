package multicalc.client;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public final class CalculatorApp {
    private CalculatorApp() {}

    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("Usage: CalculatorApp [path-to-calc]");
            System.exit(2);
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("MultiCalc needs a graphical display.");
            System.exit(1);
        }

        Path executable = Path.of(args.length == 1 ? args[0] : "./calc");
        SwingUtilities.invokeLater(() -> showWindow(new CalculatorEngine(executable)));
    }

    private static void showWindow(CalculatorEngine engine) {
        JFrame frame = new JFrame("MultiCalc");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(new CalculatorPanel(engine));
        frame.pack();
        frame.setMinimumSize(frame.getSize());
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
