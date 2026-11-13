package vn.edu.course.calculator;

import java.math.BigDecimal;
import java.math.MathContext;

public final class CalculatorEngine {
    private static final MathContext MC = MathContext.DECIMAL64;
    private final String input;
    private int position;

    private CalculatorEngine(String expression) {
        input = expression.replace('×', '*')
                .replace('÷', '/')
                .replace('−', '-');
    }

    public static String evaluate(String expression) {
        if (expression == null || expression.isEmpty()) {
            throw new IllegalArgumentException();
        }
        CalculatorEngine parser = new CalculatorEngine(expression);
        BigDecimal value = parser.parseExpression();
        // Ví dụ 2.3.4: không được bỏ qua phần .4 còn dư.
        if (parser.position != parser.input.length()) {
            throw new IllegalArgumentException();
        }
        return value.stripTrailingZeros().toPlainString();
    }

    // expression = term (('+' | '-') term)*
    private BigDecimal parseExpression() {
        BigDecimal value = parseTerm();
        while (position < input.length()) {
            char operator = input.charAt(position);
            if (operator != '+' && operator != '-') break;
            position++;
            BigDecimal right = parseTerm();
            value = operator == '+'
                    ? value.add(right, MC)
                    : value.subtract(right, MC);
        }
        return value;
    }

    // term = number (('*' | '/') number)*
    private BigDecimal parseTerm() {
        BigDecimal value = parseNumber();
        while (position < input.length()) {
            char operator = input.charAt(position);
            if (operator != '*' && operator != '/') break;
            position++;
            BigDecimal right = parseNumber();
            if (operator == '*') {
                value = value.multiply(right, MC);
            } else {
                if (right.signum() == 0) throw new ArithmeticException();
                value = value.divide(right, MC);
            }
        }
        return value;
    }

    // Một số có thể bắt đầu bằng dấu âm, ví dụ -3 hoặc -.5.
    private BigDecimal parseNumber() {
        int start = position;
        if (position < input.length() && input.charAt(position) == '-') {
            position++;
        }
        boolean hasDigit = false;
        boolean hasPoint = false;
        while (position < input.length()) {
            char c = input.charAt(position);
            if (c >= '0' && c <= '9') {
                hasDigit = true;
                position++;
            } else if (c == '.' && !hasPoint) {
                hasPoint = true;
                position++;
            } else {
                break;
            }
        }
        if (!hasDigit) throw new IllegalArgumentException();
        return new BigDecimal(input.substring(start, position));
    }
}