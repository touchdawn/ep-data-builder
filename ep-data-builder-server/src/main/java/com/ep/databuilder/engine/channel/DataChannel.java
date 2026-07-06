package com.ep.databuilder.engine.channel;

import com.ep.databuilder.env.DatasourceEntity;

import java.util.List;

/**
 * 目标库数据通道抽象。主通道 HUNTER（测试猎户数据执行开放接口），兜底通道 DIRECT（自持凭证直连）。
 * 切换通道对工厂定义零影响：两个实现共享同一份 SQL 渲染产物（带 ? 的语句 + 绑定参数）。
 */
public interface DataChannel {

    QueryResult query(DatasourceEntity ds, String sql, List<Object> params, int maxRows, String traceId);

    ExecResult execute(DatasourceEntity ds, String sql, List<Object> params, String traceId);
}
