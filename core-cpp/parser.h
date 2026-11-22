#ifndef MULTICALC_PARSER_H
#define MULTICALC_PARSER_H

#include "expression.h"

#include <string_view>

namespace multicalc {

ExpressionTree parseExpression(std::string_view expression);

}  // namespace multicalc

#endif
