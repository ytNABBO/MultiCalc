import argparse
import math
from pathlib import Path
import subprocess
import sys


CASE_TIMEOUT = 5


def finite_number(text):
    value = float(text)
    if not math.isfinite(value):
        raise ValueError("number must be finite")
    return value


def load_cases(path):
    cases = []
    with path.open(encoding="utf-8") as source:
        for number, line in enumerate(source, start=1):
            row = line.strip()
            if not row:
                continue

            fields = row.split(";")
            if len(fields) != 2:
                raise ValueError(f"{path}:{number}: expected expression;expected")
            expression, expected = (field.strip() for field in fields)
            if not expression or "\0" in expression:
                raise ValueError(f"{path}:{number}: invalid expression field")
            try:
                value = finite_number(expected)
            except ValueError:
                raise ValueError(f"{path}:{number}: expected a finite numeric result") from None
            cases.append((number, expression, value))

    if not cases:
        raise ValueError(f"{path}: no test cases found")
    return cases


def check_case(calculator, expression, expected):
    try:
        result = subprocess.run(
            [calculator, expression],
            stdin=subprocess.DEVNULL,
            capture_output=True,
            encoding="utf-8",
            errors="replace",
            timeout=CASE_TIMEOUT,
        )
    except subprocess.TimeoutExpired:
        return f"calculator timed out after {CASE_TIMEOUT} seconds"

    if result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip()
        return f"calculator exited with status {result.returncode}: {detail!r}"
    if result.stderr:
        return f"calculator wrote to stderr: {result.stderr!r}"

    lines = result.stdout.splitlines()
    if len(lines) != 1:
        return f"expected one numeric output line, got {result.stdout!r}"
    try:
        actual = finite_number(lines[0])
    except ValueError:
        return f"expected a finite numeric result, got {lines[0]!r}"

    if not math.isclose(actual, expected, rel_tol=1e-12):
        return f"expected {expected}, got {actual}"
    return None


def main():
    parser = argparse.ArgumentParser(description="Run regression cases against the calculator CLI.")
    parser.add_argument("calculator", type=Path, help="path to the compiled calculator")
    parser.add_argument("cases", type=Path, help="path to an expression;expected test file")
    args = parser.parse_args()

    try:
        cases = load_cases(args.cases)
        calculator = args.calculator.absolute()
    except (OSError, ValueError) as error:
        parser.exit(2, f"Error: {error}\n")

    failed = 0
    for line, expression, expected in cases:
        try:
            failure = check_case(calculator, expression, expected)
        except OSError as error:
            parser.exit(2, f"Error running {calculator}: {error.strerror}\n")
        if failure is not None:
            print(f"{args.cases}:{line}: {expression!r}: {failure}", file=sys.stderr)
            failed += 1

    print(f"{len(cases) - failed}/{len(cases)} cases passed")
    return int(failed != 0)


if __name__ == "__main__":
    sys.exit(main())
