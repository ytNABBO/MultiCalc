using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;

namespace MultiCalc.Client;

public partial class CalculatorWindow : Window
{
    private readonly TcpCalculatorClient client;

    internal CalculatorWindow(TcpCalculatorClient client)
    {
        this.client = client;
        InitializeComponent();
        ExpressionInput.TextChanged += (_, _) => ClearOutcome();
        Loaded += (_, _) => ExpressionInput.Focus();
    }

    private void Key_Click(object sender, RoutedEventArgs e)
    {
        ExpressionInput.Focus();
        string key = (string)((Button)sender).Content;
        switch (key)
        {
            case "Clear":
                ExpressionInput.Clear();
                ClearOutcome();
                break;
            case "Del":
                EditingCommands.Backspace.Execute(null, ExpressionInput);
                break;
            default:
                int insertion = ExpressionInput.SelectionStart;
                ExpressionInput.SelectedText = key;
                ExpressionInput.CaretIndex = insertion + key.Length;
                break;
        }
    }

    private async void Evaluate_Click(object sender, RoutedEventArgs e)
    {
        if (ExpressionInput.IsReadOnly)
        {
            return;
        }

        string expression = ExpressionInput.Text;
        ClearOutcome();
        if (string.IsNullOrWhiteSpace(expression))
        {
            StatusText.Text = "Enter an expression.";
            ExpressionInput.Focus();
            return;
        }

        ExpressionInput.IsReadOnly = true;
        Keypad.IsEnabled = false;
        StatusText.Text = "Calculating…";
        try
        {
            ResultOutput.Text = await client.CalculateAsync(expression);
            StatusText.Text = string.Empty;
        }
        catch (IOException error)
        {
            StatusText.Text = error.Message;
        }
        finally
        {
            ExpressionInput.IsReadOnly = false;
            Keypad.IsEnabled = true;
            ExpressionInput.Focus();
        }
    }

    private void ClearOutcome()
    {
        ResultOutput.Clear();
        StatusText.Text = string.Empty;
    }
}
