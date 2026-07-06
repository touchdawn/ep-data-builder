package com.ep.databuilder.engine.channel;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExecResult {

    private int affectedRows;
    /** 猎户通道回传的审计 id，DIRECT 通道为 null */
    private Long auditId;
}
