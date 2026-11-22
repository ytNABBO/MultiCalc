using System;
using System.Globalization;
using System.IO;
using System.Net.Sockets;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace MultiCalc.Client;

internal sealed class TcpCalculatorClient(string host, int port)
{
    private const string RequestPrefix = "CALC ";
    private const int MaxLineBytes = 4096;
    private static readonly UTF8Encoding Utf8 = new(false, true);
    private static readonly Regex Decimal = new(
        @"\A[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?\z", RegexOptions.NonBacktracking);

    internal async Task<string> CalculateAsync(string expression)
    {
        byte[] request = EncodeRequest(expression);
        using var client = new TcpClient();
        await ConnectAsync(client).ConfigureAwait(false);

        using NetworkStream stream = client.GetStream();
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(10));
        string response;
        try
        {
            await stream.WriteAsync(request.AsMemory(), timeout.Token).ConfigureAwait(false);
            response = await ReadResponseAsync(stream, timeout.Token).ConfigureAwait(false);
        }
        catch (OperationCanceledException error)
        {
            throw new IOException("Timed out waiting for the calculator server.", error);
        }
        catch (IOException error) when (error.InnerException is SocketException)
        {
            throw new IOException("The connection to the calculator server was interrupted.", error);
        }

        return ParseResponse(response);
    }

    private async Task ConnectAsync(TcpClient client)
    {
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        try
        {
            await client.ConnectAsync(host, port, timeout.Token).ConfigureAwait(false);
        }
        catch (OperationCanceledException error)
        {
            throw new IOException($"Timed out connecting to {host} on port {port}.", error);
        }
        catch (SocketException error)
        {
            throw new IOException($"Could not connect to {host} on port {port}.", error);
        }
    }

    private static byte[] EncodeRequest(string expression)
    {
        if (string.IsNullOrWhiteSpace(expression))
        {
            throw new IOException("Enter an expression.");
        }
        if (expression.AsSpan().IndexOfAny('\r', '\n', '\0') >= 0)
        {
            throw new IOException("The expression contains an invalid character.");
        }
        if (expression.Length > MaxLineBytes - RequestPrefix.Length)
        {
            throw new IOException("The expression is too long.");
        }

        try
        {
            if (Utf8.GetByteCount(expression) > MaxLineBytes - RequestPrefix.Length)
            {
                throw new IOException("The expression is too long.");
            }
            return Utf8.GetBytes(RequestPrefix + expression + "\n");
        }
        catch (EncoderFallbackException error)
        {
            throw new IOException("The expression contains an invalid character.", error);
        }
    }

    private static async Task<string> ReadResponseAsync(NetworkStream stream, CancellationToken cancellationToken)
    {
        var bytes = new byte[MaxLineBytes + 1];
        int length = 0;
        while (length < bytes.Length)
        {
            int read = await stream.ReadAsync(bytes.AsMemory(length), cancellationToken).ConfigureAwait(false);
            if (read == 0)
            {
                throw new IOException("The calculator server closed the connection before sending a complete response.");
            }

            int lineEnd = Array.IndexOf(bytes, (byte)'\n', length, read);
            if (lineEnd >= 0)
            {
                try
                {
                    return Utf8.GetString(bytes, 0, lineEnd);
                }
                catch (DecoderFallbackException error)
                {
                    throw new IOException("The calculator server returned invalid UTF-8.", error);
                }
            }
            length += read;
        }

        throw new IOException("The calculator server's response is too long.");
    }

    private static string ParseResponse(string response)
    {
        if (response.AsSpan().IndexOfAny('\r', '\0') < 0)
        {
            if (response.StartsWith("ERR ", StringComparison.Ordinal) && response.Length > 4)
            {
                throw new IOException(response[4..]);
            }
            if (response.StartsWith("OK ", StringComparison.Ordinal))
            {
                string result = response[3..];
                if (Decimal.IsMatch(result)
                    && double.TryParse(result, NumberStyles.Float, CultureInfo.InvariantCulture, out double value)
                    && double.IsFinite(value))
                {
                    return result;
                }
            }
        }
        throw new IOException("The calculator server returned an invalid response.");
    }
}
