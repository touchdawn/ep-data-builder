package com.ep.databuilder.engine.template;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** SQL 模板渲染产物：带 ? 占位的语句 + 绑定参数（与 JDBC / 猎户开放接口同构） */
@Getter
@AllArgsConstructor
public class RenderedSql {

    private final String sql;
    private final List<Object> params;
}
