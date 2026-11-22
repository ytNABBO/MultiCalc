#ifndef MULTICALC_EXPRESSION_H
#define MULTICALC_EXPRESSION_H

#include <cstddef>
#include <variant>
#include <vector>

namespace multicalc {

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

// Children precede their parents, and the last node is the root.
using ExpressionTree = std::vector<Node>;

}  // namespace multicalc

#endif
