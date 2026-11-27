import argparse
import math
import re
import socket
import sys
from typing import BinaryIO


DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 5050
SOCKET_TIMEOUT_SECONDS = 10
MAX_LINE_BYTES = 4096
REQUEST_PREFIX = "CALC "
DECIMAL = re.compile(r"[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?")


class CalculationError(Exception):
    """The server replied with an error for this calculation."""


class ProtocolError(Exception):
    """A request or response violated the wire format."""


def _encode_request(expression: str) -> bytes:
    if not expression.strip() or any(char in expression for char in "\r\n\0"):
        raise ProtocolError("Invalid expression for a TCP request")
    if len(expression) > MAX_LINE_BYTES - len(REQUEST_PREFIX):
        raise ProtocolError("Calculator request exceeds 4096 bytes")

    try:
        request = (REQUEST_PREFIX + expression).encode("utf-8")
    except UnicodeEncodeError as error:
        raise ProtocolError("Expression cannot be encoded as UTF-8") from error
    if len(request) > MAX_LINE_BYTES:
        raise ProtocolError("Calculator request exceeds 4096 bytes")
    return request + b"\n"


def _exchange(connection: socket.socket, responses: BinaryIO, request: bytes) -> tuple[str, float]:
    connection.sendall(request)
    line = responses.readline(MAX_LINE_BYTES + 2)
    payload = line.rstrip(b"\n")
    if len(payload) > MAX_LINE_BYTES:
        raise ProtocolError("Calculator response exceeds 4096 bytes")
    if not line.endswith(b"\n"):
        raise ProtocolError("Connection closed before a complete calculator response")

    try:
        response = payload.decode("utf-8")
    except UnicodeDecodeError as error:
        raise ProtocolError("Calculator response is not valid UTF-8") from error
    if any(char in response for char in "\r\0"):
        raise ProtocolError("Invalid character in calculator response")

    prefix, separator, result = response.partition(" ")
    if separator and prefix == "ERR" and result:
        raise CalculationError(result)
    if separator and prefix == "OK" and DECIMAL.fullmatch(result):
        value = float(result)
        if math.isfinite(value):
            return result, value
    raise ProtocolError("Invalid calculator response")


def calculate(connection: socket.socket, responses: BinaryIO, expression: str) -> float:
    """Send one calculation and read its numeric result on an open connection."""
    _, value = _exchange(connection, responses, _encode_request(expression))
    return value


def main() -> int:
    parser = argparse.ArgumentParser(description="Evaluate one expression over the calculator TCP protocol.")
    parser.add_argument("--host", default=DEFAULT_HOST, help="server hostname or IP address (default: %(default)s)")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help="server port (default: %(default)s)")
    parser.add_argument("expression", help="arithmetic expression to evaluate")
    options = parser.parse_args()
    if not 1 <= options.port <= 65535:
        parser.error("--port must be between 1 and 65535")
    if not options.host.strip():
        parser.error("--host must be a hostname or IP address")

    try:
        request = _encode_request(options.expression)
        with socket.create_connection((options.host, options.port), SOCKET_TIMEOUT_SECONDS) as connection:
            with connection.makefile("rb") as responses:
                result, _ = _exchange(connection, responses, request)
    except CalculationError as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1
    except (OSError, ProtocolError) as error:
        print(f"Connection or protocol error: {error}", file=sys.stderr)
        return 2

    print(result)
    return 0


if __name__ == "__main__":
    sys.exit(main())
