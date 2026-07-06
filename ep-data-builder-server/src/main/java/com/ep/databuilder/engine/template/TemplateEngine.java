package com.ep.databuilder.engine.template;

import com.ep.databuilder.common.BizException;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自研 ${} 模板引擎（刻意保持弱表达力，复杂逻辑应拆步骤或拆工厂）：
 * - ${varName}            变量引用（缺失即报错）
 * - ${fn(args)}           内置函数：now/daysAgo/hoursAgo/nowMillis/daysAgoMillis/randomStr/randomInt/uuid/seq
 *   参数可以是数字字面量、'字符串'、或变量名
 * - 文本模式：值内联进字符串（URL/body/header/参数默认值）
 * - SQL 模式：每个 ${} 变成一个 ? 绑定参数，杜绝拼接注入
 */
public final class TemplateEngine {

    private static final Pattern TOKEN = Pattern.compile("\\$\\{([^}]*)}");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern FUNC_CALL = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\((.*)\\)");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+");

    private TemplateEngine() {
    }

    /** 文本模式渲染 */
    public static String renderText(String template, Map<String, Object> vars) {
        if (template == null) {
            return null;
        }
        Matcher m = TOKEN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            Object value = eval(m.group(1), vars);
            m.appendReplacement(sb, Matcher.quoteReplacement(asText(value)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** SQL 模式渲染：${} → ? + 绑定参数 */
    public static RenderedSql renderSql(String template, Map<String, Object> vars) {
        if (template == null || template.trim().isEmpty()) {
            throw new BizException("SQL 模板不能为空");
        }
        List<Object> params = new ArrayList<>();
        Matcher m = TOKEN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            params.add(eval(m.group(1), vars));
            m.appendReplacement(sb, "?");
        }
        m.appendTail(sb);
        return new RenderedSql(sb.toString(), params);
    }

    /** 求值单个表达式（变量或函数调用） */
    public static Object eval(String rawExpr, Map<String, Object> vars) {
        String expr = rawExpr == null ? "" : rawExpr.trim();
        if (expr.isEmpty()) {
            throw new BizException("模板表达式 ${} 内容为空");
        }
        if (IDENTIFIER.matcher(expr).matches()) {
            if (!vars.containsKey(expr)) {
                throw new BizException("模板变量不存在：" + expr + "（检查参数定义与前序步骤输出）");
            }
            return vars.get(expr);
        }
        Matcher fn = FUNC_CALL.matcher(expr);
        if (fn.matches()) {
            return invoke(fn.group(1), splitArgs(fn.group(2)), vars);
        }
        throw new BizException("不支持的模板表达式：${" + expr + "}（仅支持变量名或内置函数调用）");
    }

    private static List<String> splitArgs(String raw) {
        List<String> args = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return args;
        }
        for (String part : raw.split(",")) {
            args.add(part.trim());
        }
        return args;
    }

    private static Object resolveArg(String arg, Map<String, Object> vars) {
        if (NUMBER.matcher(arg).matches()) {
            return Long.parseLong(arg);
        }
        if (arg.length() >= 2 && ((arg.startsWith("'") && arg.endsWith("'"))
                || (arg.startsWith("\"") && arg.endsWith("\"")))) {
            return arg.substring(1, arg.length() - 1);
        }
        if (IDENTIFIER.matcher(arg).matches()) {
            if (!vars.containsKey(arg)) {
                throw new BizException("函数参数引用的变量不存在：" + arg);
            }
            return vars.get(arg);
        }
        throw new BizException("不支持的函数参数：" + arg);
    }

    private static long asLong(Object v, String fnName) {
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new BizException("函数 " + fnName + " 需要数字参数，实际为：" + v);
        }
    }

    private static Object invoke(String name, List<String> rawArgs, Map<String, Object> vars) {
        List<Object> args = new ArrayList<>();
        for (String raw : rawArgs) {
            args.add(resolveArg(raw, vars));
        }
        long now = System.currentTimeMillis();
        switch (name) {
            case "now":
                requireArgs(name, args, 0);
                return new Timestamp(now);
            case "daysAgo":
                requireArgs(name, args, 1);
                return new Timestamp(now - asLong(args.get(0), name) * 86_400_000L);
            case "hoursAgo":
                requireArgs(name, args, 1);
                return new Timestamp(now - asLong(args.get(0), name) * 3_600_000L);
            case "nowMillis":
                requireArgs(name, args, 0);
                return now;
            case "daysAgoMillis":
                requireArgs(name, args, 1);
                return now - asLong(args.get(0), name) * 86_400_000L;
            case "randomStr": {
                requireArgs(name, args, 1);
                int len = (int) asLong(args.get(0), name);
                String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < len; i++) {
                    sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
                }
                return sb.toString();
            }
            case "randomInt": {
                requireArgs(name, args, 2);
                long a = asLong(args.get(0), name);
                long b = asLong(args.get(1), name);
                if (a > b) {
                    throw new BizException("randomInt(a,b) 需要 a <= b");
                }
                return ThreadLocalRandom.current().nextLong(a, b + 1);
            }
            case "uuid":
                requireArgs(name, args, 0);
                return UUID.randomUUID().toString();
            case "seq":
                requireArgs(name, args, 0);
                return now; // 简单可用的递增值：epoch 毫秒
            default:
                throw new BizException("未知的模板函数：" + name);
        }
    }

    private static void requireArgs(String name, List<Object> args, int count) {
        if (args.size() != count) {
            throw new BizException("函数 " + name + " 需要 " + count + " 个参数，实际 " + args.size() + " 个");
        }
    }

    /** 文本化：Timestamp 格式化为 yyyy-MM-dd HH:mm:ss，null 渲染为空串 */
    public static String asText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Timestamp) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((Timestamp) value);
        }
        return String.valueOf(value);
    }
}
