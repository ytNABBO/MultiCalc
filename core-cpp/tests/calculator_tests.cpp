#include "calculator.h"

#include <cstddef>
#include <exception>
#include <iostream>
#include <iterator>
#include <stdexcept>
#include <string>
#include <string_view>

namespace {

struct ResultCase {
    std::string_view expression;
    double expected;
};

struct ErrorCase {
    std::string_view expression;
    std::string_view expectedMessage;
};

bool checkResult(const ResultCase& test) {
    try {
        const double actual = multicalc::calculate(test.expression);
        if (actual == test.expected) {
            return true;
        }
        std::cerr << "Expression \"" << test.expression << "\" expected " << test.expected
                  << " but returned " << actual << '\n';
    } catch (const std::exception& error) {
        std::cerr << "Expression \"" << test.expression << "\" unexpectedly failed with "
                  << error.what() << '\n';
    }
    return false;
}

bool checkError(const ErrorCase& test) {
    try {
        const double actual = multicalc::calculate(test.expression);
        std::cerr << "Expression \"" << test.expression << "\" unexpectedly returned "
                  << actual << '\n';
    } catch (const std::runtime_error& error) {
        if (std::string_view{error.what()}.find(test.expectedMessage) != std::string_view::npos) {
            return true;
        }
        std::cerr << "Expression \"" << test.expression << "\" expected an error containing \""
                  << test.expectedMessage << "\" but received \"" << error.what() << "\"\n";
    } catch (const std::exception& error) {
        std::cerr << "Expression \"" << test.expression << "\" threw an unexpected error: "
                  << error.what() << '\n';
    }
    return false;
}

}  // namespace

int main() {
    const std::string deepestExpression = std::string(128, '(') + "1" + std::string(128, ')');
    const std::string tooDeepExpression = "(" + deepestExpression + ")";

    const ResultCase resultCases[] = {
        {"42", 42},
        {"2+3*4", 14},
        {"(2+3)*4", 20},
        {"2+3*(4-(2-1))", 11},
        {"10-3-2", 5},
        {"8/4/2", 1},
        {"2*-(-3)", 6},
        {"10--3", 13},
        {"\t (2.5 + .75) / 2 \n", 1.625},
        {"1.25e2 - 2.5e1", 100},
        {deepestExpression, 1},
    };
    const ErrorCase errorCases[] = {
        {std::string_view{}, "Expected a number"},
        {" \t", "Expected a number"},
        {"2*", "Expected a number"},
        {"(2+3", "Expected ')'"},
        {"2+3)", "Unexpected character"},
        {"2 3", "Unexpected character"},
        {"2(3)", "Unexpected character"},
        {"2+abc", "Expected a number"},
        {"1/(2-2)", "Division by zero"},
        {"1/-0", "Division by zero"},
        {"1e9999", "Number is out of range"},
        {"1e308 * 10", "Result is out of range"},
        {"nan", "Number is out of range"},
        {tooDeepExpression, "Expression is nested too deeply"},
    };

    std::size_t failed = 0;
    for (const auto& test : resultCases) {
        if (!checkResult(test)) {
            ++failed;
        }
    }
    for (const auto& test : errorCases) {
        if (!checkError(test)) {
            ++failed;
        }
    }

    const auto total = std::size(resultCases) + std::size(errorCases);
    if (failed != 0) {
        std::cerr << failed << " of " << total << " calculator tests failed\n";
        return 1;
    }

    std::cout << total << " calculator tests passed\n";
}
