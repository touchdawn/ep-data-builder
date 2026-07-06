package com.ep.databuilder.engine;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** 单步执行产物：轨迹文本 + 提取的输出变量 */
@Data
public class StepOutcome {

    /** 渲染后的请求（报文/SQL，已脱敏），入轨迹 */
    private String requestText;
    /** 响应摘要（状态+报文/影响行数），入轨迹 */
    private String responseText;
    private Map<String, Object> outputs = new LinkedHashMap<>();
}
