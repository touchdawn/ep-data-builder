package com.ep.databuilder.engine.channel;

import com.ep.databuilder.common.BizException;
import com.ep.databuilder.common.SqlGuard;
import com.ep.databuilder.engine.ds.DynamicDataSourceManager;
import com.ep.databuilder.env.DatasourceEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;

/** DIRECT 兜底通道：自持凭证直连目标库（仅项目自建库/猎户未就绪过渡期/应急场景） */
@Component
@RequiredArgsConstructor
public class DirectJdbcDataChannel implements DataChannel {

    private final DynamicDataSourceManager dataSourceManager;

    @Override
    public QueryResult query(DatasourceEntity ds, String sql, List<Object> params, int maxRows, String traceId) {
        SqlGuard.checkQuery(sql);
        JdbcTemplate jdbc = dataSourceManager.jdbcTemplate(ds);
        int cap = maxRows <= 0 ? 100 : Math.min(maxRows, 1000);
        try {
            return jdbc.query(sql, params.toArray(),
                    (ResultSetExtractor<QueryResult>) rs -> extract(rs, cap));
        } catch (DataAccessException e) {
            throw new BizException("数据库查询失败[" + ds.getSchemaCode() + "]：" + rootMessage(e));
        }
    }

    @Override
    public ExecResult execute(DatasourceEntity ds, String sql, List<Object> params, String traceId) {
        SqlGuard.checkExecute(sql);
        JdbcTemplate jdbc = dataSourceManager.jdbcTemplate(ds);
        try {
            int affected = jdbc.update(sql, params.toArray());
            return new ExecResult(affected, null);
        } catch (DataAccessException e) {
            throw new BizException("数据库执行失败[" + ds.getSchemaCode() + "]：" + rootMessage(e));
        }
    }

    private static QueryResult extract(ResultSet rs, int cap) {
        try {
            ResultSetMetaData meta = rs.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                columns.add(meta.getColumnLabel(i));
            }
            List<List<Object>> rows = new ArrayList<>();
            boolean truncated = false;
            while (rs.next()) {
                if (rows.size() >= cap) {
                    truncated = true;
                    break;
                }
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= columns.size(); i++) {
                    row.add(rs.getObject(i));
                }
                rows.add(row);
            }
            QueryResult result = new QueryResult();
            result.setColumns(columns);
            result.setRows(rows);
            result.setTruncated(truncated);
            return result;
        } catch (Exception e) {
            throw new BizException("读取查询结果失败：" + e.getMessage());
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage();
    }
}
