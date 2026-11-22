#include <cctype>
#include <charconv>
#include <cmath>
#include <cstddef>
#include <iostream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <system_error>
#include <utility>
#include <variant>
#include <vector>

namespace {

using NodeIndex = std::size_t;

struct UnaryMinus {
    NodeIndex operand;
};

struct BinaryExpression {
    char operation;
    NodeIndex left;
    NodeIndex right;
};

using Node = std::variant<double, UnaryMinus, BinaryExpression>;

class Parser {
public:
    explicit Parser(std::string_view expression) : expression_(expression) {}

    std::vector<Node> parse() {
        parseSum(0);
        skipWhitespace();
        if (position_ != expression_.size()) {
            fail("Unexpected character");
        }
        return std::move(nodes_);
    }

private:
    static constexpr std::size_t maxNesting = 128;

    NodeIndex parseSum(std::size_t depth) {
        NodeIndex left = parseProduct(depth);
        while (const char operation = takeToken("+-")) {
            const NodeIndex right = parseProduct(depth);
            left = append(BinaryExpression{operation, left, right});
        }
        return left;
    }

    NodeIndex parseProduct(std::size_t depth) {
        NodeIndex left = parseFactor(depth);
        while (const char operation = takeToken("*/")) {
            const NodeIndex right = parseFactor(depth);
            left = append(BinaryExpression{operation, left, right});
        }
        return left;
    }

    NodeIndex parseFactor(std::size_t depth) {
        if (depth > maxNesting) {
            skipWhitespace();
            fail("Expression is nested too deeply");
        }

        switch (takeToken("-(")) {
        case '-':
            return append(UnaryMinus{parseFactor(depth + 1)});
        case '(': {
            const NodeIndex inner = parseSum(depth + 1);
            if (!takeToken(")")) {
                fail("Expected ')'");
            }
            return inner;
        }
        default:
            return parseNumber();
        }
    }

    NodeIndex parseNumber() {
        const char* first = expression_.data() + position_;
        const char* last = expression_.data() + expression_.size();
        double value = 0;
        const auto [end, error] = std::from_chars(first, last, value);

        if (error == std::errc::invalid_argument) {
            fail("Expected a number or '('");
        }
        if (error == std::errc::result_out_of_range || !std::isfinite(value)) {
            fail("Number is out of range");
        }

        position_ += static_cast<std::size_t>(end - first);
        return append(value);
    }

    char takeToken(std::string_view tokens) {
        skipWhitespace();
        if (position_ == expression_.size() ||
            tokens.find(expression_[position_]) == std::string_view::npos) {
            return '\0';
        }
        return expression_[position_++];
    }

    void skipWhitespace() {
        while (position_ < expression_.size() &&
               std::isspace(static_cast<unsigned char>(expression_[position_]))) {
            ++position_;
        }
    }

    NodeIndex append(Node node) {
        nodes_.push_back(node);
        return nodes_.size() - 1;
    }

    [[noreturn]] void fail(std::string_view message) const {
        throw std::runtime_error(std::string(message) + " at position " +
                                 std::to_string(position_ + 1));
    }

    std::string_view expression_;
    std::size_t position_ = 0;
    std::vector<Node> nodes_;
};

double evaluateBinary(char operation, double left, double right) {
    switch (operation) {
    case '+':
        return left + right;
    case '-':
        return left - right;
    case '*':
        return left * right;
    case '/':
        if (right == 0) {
            throw std::runtime_error("Division by zero");
        }
        return left / right;
    default:
        throw std::logic_error("Unknown binary operator");
    }
}

double evaluate(const std::vector<Node>& nodes) {
    // The parser appends children before their parent, so evaluation can use a loop.
    std::vector<double> results;
    results.reserve(nodes.size());
    for (const Node& node : nodes) {
        if (const auto* number = std::get_if<double>(&node)) {
            results.push_back(*number);
        } else if (const auto* unary = std::get_if<UnaryMinus>(&node)) {
            results.push_back(-results[unary->operand]);
        } else {
            const auto& binary = std::get<BinaryExpression>(node);
            const double value = evaluateBinary(binary.operation, results[binary.left],
                                                results[binary.right]);
            if (!std::isfinite(value)) {
                throw std::runtime_error("Result is out of range");
            }
            results.push_back(value);
        }
    }
    return results.back();
}

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
        printResult(evaluate(Parser{argv[1]}.parse()));
    } catch (const std::runtime_error& error) {
        std::cerr << "Error: " << error.what() << '\n';
        return 1;
    }
}
