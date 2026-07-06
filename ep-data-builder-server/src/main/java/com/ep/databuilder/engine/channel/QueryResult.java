package com.ep.databuilder.engine.channel;

import lombok.Data;

import java.util.List;

@Data
public class QueryResult {

    private List<String> columns;
    private List<List<Object>> rows;
    /** 猎户通道回传的审计 id，DIRECT 通道为 null */
    private Long auditId;
    private boolean truncated;
}
