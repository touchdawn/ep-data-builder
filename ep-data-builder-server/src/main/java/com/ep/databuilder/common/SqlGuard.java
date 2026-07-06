package com.ep.databuilder.common;

import java.util.regex.Pattern;

/**
 * SQL 底线守护（平台侧，两个通道统一执行；猎户侧另有一层，纵深防御）：
 * 单语句、类型白名单、UPDATE/DELETE 强制 WHERE、禁 DDL/DCL/TRUNCATE。
 * 判断前剥离注释，防注释绕过。
 */
public final class SqlGuard {

    private static final Pattern LINE_COMMENT = Pattern.compile("--[^\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    private SqlGuard() {
    }

    /** 查询侧：仅 SELECT */
    public static void checkQuery(String sql) {
        String s = strip(sql);
        checkSingleStatement(s);
        if (!startsWith(s, "SELECT")) {
            throw new BizException("查询通道仅允许 SELECT 语句");
        }
    }

    /** 执行侧：仅 INSERT/UPDATE/DELETE，UPDATE/DELETE 必须带 WHERE */
    public static void checkExecute(String sql) {
        String s = strip(sql);
        checkSingleStatement(s);
        String upper = s.toUpperCase();
        if (startsWith(s, "INSERT")) {
            return;
        }
        if (startsWith(s, "UPDATE") || startsWith(s, "DELETE")) {
            if (!upper.contains(" WHERE ")) {
                throw new BizException("UPDATE/DELETE 必须带 WHERE 条件");
            }
            return;
        }
        throw new BizException("执行通道仅允许 INSERT/UPDATE/DELETE 语句（禁 DDL/DCL/TRUNCATE）");
    }

    private static void checkSingleStatement(String s) {
        if (s.isEmpty()) {
            throw new BizException("SQL 不能为空");
        }
        // 去掉合法的末尾分号后，不允许再出现分号（多语句）
        String body = s.endsWith(";") ? s.substring(0, s.length() - 1) : s;
        if (body.contains(";")) {
            throw new BizException("仅允许单条 SQL 语句");
        }
    }

    private static String strip(String sql) {
        if (sql == null) {
            return "";
        }
        String s = BLOCK_COMMENT.matcher(sql).replaceAll(" ");
        s = LINE_COMMENT.matcher(s).replaceAll(" ");
        return s.trim();
    }

    private static boolean startsWith(String s, String keyword) {
        return s.regionMatches(true, 0, keyword, 0, keyword.length());
    }
}
