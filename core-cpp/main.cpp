#include "calculator.h"

#include <charconv>
#include <cstddef>
#include <iostream>
#include <stdexcept>
#include <string_view>
#include <system_error>

namespace {

constexpr std::size_t maxExpressionBytes = 4091;

void printResult(double value) {
    char output[64];
    const auto [end, error] = std::to_chars(output, output + sizeof output, value);
    if (error != std::errc{}) {
        throw std::runtime_error("Could not format the result");
    }
    std::cout.write(output, end - output) << '\n';
}

}  // namespace

int main(int argc, char* argv[]) {
    if (argc != 2) {
        std::cerr << "Usage: calc \"<expression>\"\n";
        return 2;
    }

    const std::string_view expression(argv[1]);
    if (expression.size() > maxExpressionBytes) {
        std::cerr << "Error: Expression exceeds " << maxExpressionBytes << " bytes\n";
        return 1;
    }

    try {
        printResult(multicalc::calculate(expression));
    } catch (const std::runtime_error& error) {
        std::cerr << "Error: " << error.what() << '\n';
        return 1;
    }
}
