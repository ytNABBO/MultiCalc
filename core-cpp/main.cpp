#include "calculator.h"

#include <charconv>
#include <iostream>
#include <stdexcept>
#include <system_error>

namespace {

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

    try {
        printResult(multicalc::calculate(argv[1]));
    } catch (const std::runtime_error& error) {
        std::cerr << "Error: " << error.what() << '\n';
        return 1;
    }
}
