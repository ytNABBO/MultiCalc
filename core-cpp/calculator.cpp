#include "calculator.h"
#include "parser.h"

#include <cmath>
#include <stdexcept>
#include <vector>

namespace multicalc {
namespace {

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

double evaluate(const ExpressionTree& nodes) {
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

}  // namespace

double calculate(std::string_view expression) {
    return evaluate(parseExpression(expression));
}

}  // namespace multicalc
