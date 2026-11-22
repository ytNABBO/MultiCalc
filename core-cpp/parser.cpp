#include "parser.h"

#include <cctype>
#include <charconv>
#include <cmath>
#include <stdexcept>
#include <string>
#include <system_error>
#include <utility>

namespace multicalc {
namespace {

class Parser {
public:
    explicit Parser(std::string_view expression) : expression_(expression) {}

    ExpressionTree parse() {
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
        if (position_ == expression_.size()) {
            fail("Expected a number or '('");
        }

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
    ExpressionTree nodes_;
};

}  // namespace

ExpressionTree parseExpression(std::string_view expression) {
    return Parser{expression}.parse();
}

}  // namespace multicalc
