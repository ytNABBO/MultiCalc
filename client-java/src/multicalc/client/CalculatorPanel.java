package multicalc.client;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import multicalc.engine.CalculatorEngine;

final class CalculatorPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private static final String[] KEYS = {
        "Clear", "Del", "(", ")",
        "7", "8", "9", "/",
        "4", "5", "6", "*",
        "1", "2", "3", "-",
        "0", ".", "=", "+"
    };

    private final transient CalculatorEngine engine;
    private final JTextField expression = new JTextField(24);
    private final JTextField result = new JTextField(24);
    private final JTextArea status = new JTextArea(2, 24);
    private final JPanel keypad = new JPanel(new GridLayout(0, 4, 8, 8));

    CalculatorPanel(CalculatorEngine engine) {
        super(new BorderLayout(0, 12));
        this.engine = engine;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        expression.setFont(expression.getFont().deriveFont(20f));
        expression.addActionListener(event -> evaluate());
        expression.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                clearOutcome();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                clearOutcome();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                clearOutcome();
            }
        });

        result.setFont(result.getFont().deriveFont(20f));
        result.setEditable(false);
        result.setHorizontalAlignment(SwingConstants.RIGHT);

        JPanel display = new JPanel(new GridLayout(0, 1, 0, 8));
        display.add(labeledField("Expression", expression));
        display.add(labeledField("Result", result));
        add(display, BorderLayout.NORTH);

        for (String key : KEYS) {
            JButton button = new JButton(key);
            button.setMargin(new Insets(8, 8, 8, 8));
            button.addActionListener(event -> pressKey(key));
            keypad.add(button);
        }
        add(keypad, BorderLayout.CENTER);

        status.setEditable(false);
        status.setFocusable(false);
        status.setOpaque(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setFont(new JLabel().getFont());
        status.getAccessibleContext().setAccessibleName("Calculation status");
        add(status, BorderLayout.SOUTH);
    }

    private static JPanel labeledField(String title, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(0, 3));
        JLabel label = new JLabel(title);
        label.setLabelFor(field);
        row.add(label, BorderLayout.NORTH);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private void pressKey(String key) {
        switch (key) {
            case "Clear" -> {
                expression.setText("");
                clearOutcome();
            }
            case "Del" -> deletePreviousCharacter();
            case "=" -> evaluate();
            default -> expression.replaceSelection(key);
        }
        expression.requestFocusInWindow();
    }

    private void deletePreviousCharacter() {
        int caret = expression.getCaretPosition();
        if (caret > 0 && expression.getSelectionStart() == expression.getSelectionEnd()) {
            int previous = expression.getText().offsetByCodePoints(caret, -1);
            expression.select(previous, caret);
        }
        expression.replaceSelection("");
    }

    private void clearOutcome() {
        result.setText("");
        status.setText("");
    }

    private void evaluate() {
        if (!expression.isEditable()) {
            return;
        }
        String input = expression.getText();
        clearOutcome();
        if (input.isBlank()) {
            status.setText("Enter an expression.");
            return;
        }

        setBusy(true);
        status.setText("Calculating…");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws IOException, InterruptedException {
                return engine.calculate(input);
            }

            @Override
            protected void done() {
                try {
                    result.setText(get());
                    status.setText("");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    status.setText("Calculation interrupted.");
                } catch (ExecutionException error) {
                    String message = error.getCause().getMessage();
                    status.setText(message == null || message.isBlank() ? "Calculation failed." : message);
                } finally {
                    setBusy(false);
                    expression.requestFocusInWindow();
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        expression.setEditable(!busy);
        for (Component button : keypad.getComponents()) {
            button.setEnabled(!busy);
        }
    }
}
