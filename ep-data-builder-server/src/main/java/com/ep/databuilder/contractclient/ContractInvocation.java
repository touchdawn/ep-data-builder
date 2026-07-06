package com.ep.databuilder.contractclient;

import lombok.Data;

/** 从契约平台拉取的接口调用方式（契约 JSON 的 invocation 段） */
@Data
public class ContractInvocation {

    private String method;
    private String path;
    private String contentType;
}
