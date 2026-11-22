import math
import re
import socket
from typing import BinaryIO


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


def calculate(connection: socket.socket, responses: BinaryIO, expression: str) -> float:
    """Send one calculation and read its reply on an open connection."""
    connection.sendall(_encode_request(expression))
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
            return value
    raise ProtocolError("Invalid calculator response")
