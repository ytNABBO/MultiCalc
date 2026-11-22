using System.Globalization;
using System.Windows;

namespace MultiCalc.Client;

public partial class App : Application
{
    private const string DefaultHost = "127.0.0.1";
    private const int DefaultPort = 5050;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        if (!TryReadEndpoint(e.Args, out string host, out int port))
        {
            MessageBox.Show(
                "Pass a server hostname and an optional port between 1 and 65535.",
                "MultiCalc", MessageBoxButton.OK, MessageBoxImage.Warning);
            Shutdown(2);
            return;
        }

        var window = new CalculatorWindow(new TcpCalculatorClient(host, port));
        MainWindow = window;
        window.Show();
    }

    private static bool TryReadEndpoint(string[] args, out string host, out int port)
    {
        host = args.Length > 0 ? args[0] : DefaultHost;
        port = DefaultPort;
        if (args.Length > 2 || string.IsNullOrWhiteSpace(host))
        {
            return false;
        }

        return args.Length < 2 ||
            (int.TryParse(args[1], NumberStyles.None, CultureInfo.InvariantCulture, out port) &&
             port is >= 1 and <= 65535);
    }
}
