package com.ep.databuilder.engine.template;

import com.ep.databuilder.common.BizException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 步骤条件表达式：仅支持 `变量 op 字面量` 与 and/or（or 优先级低于 and）。
 * op: == != > < >= <=
 */
public final class ConditionEvaluator {

    private static final Pattern COMPARISON =
            Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*(==|!=|>=|<=|>|<)\\s*(.+)");

    private ConditionEvaluator() {
    }

    public static boolean eval(String expr, Map<String, Object> vars) {
        if (expr == null || expr.trim().isEmpty()) {
            return true;
        }
        for (String orPart : expr.split("(?i)\\s+or\\s+")) {
            boolean all = true;
            for (String andPart : orPart.split("(?i)\\s+and\\s+")) {
                if (!evalComparison(andPart.trim(), vars)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    private static boolean evalComparison(String comparison, Map<String, Object> vars) {
        Matcher m = COMPARISON.matcher(comparison);
        if (!m.matches()) {
            throw new BizException("条件表达式不合法：" + comparison + "（仅支持 变量 op 字面量）");
        }
        String varName = m.group(1);
        String op = m.group(2);
        String literal = stripQuotes(m.group(3).trim());
        if (!vars.containsKey(varName)) {
            throw new BizException("条件表达式引用的变量不存在：" + varName);
        }
        Object value = vars.get(varName);

        Double leftNum = toNumber(value);
        Double rightNum = toNumber(literal);
        if (leftNum != null && rightNum != null) {
            int cmp = leftNum.compareTo(rightNum);
            switch (op) {
                case "==": return cmp == 0;
                case "!=": return cmp != 0;
                case ">":  return cmp > 0;
                case "<":  return cmp < 0;
                case ">=": return cmp >= 0;
                case "<=": return cmp <= 0;
                default:   throw new BizException("未知操作符：" + op);
            }
        }
        String left = value == null ? "" : String.valueOf(value);
        if ("==".equals(op)) {
            return left.equals(literal);
        }
        if ("!=".equals(op)) {
            return !left.equals(literal);
        }
        throw new BizException("非数字值不支持 " + op + " 比较：" + comparison);
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && ((s.startsWith("'") && s.endsWith("'"))
                || (s.startsWith("\"") && s.endsWith("\"")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static Double toNumber(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
