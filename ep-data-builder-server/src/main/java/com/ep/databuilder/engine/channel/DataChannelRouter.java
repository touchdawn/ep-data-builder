package com.ep.databuilder.engine.channel;

import com.ep.databuilder.env.DatasourceEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 按数据源配置路由通道：HUNTER（主）/DIRECT（兜底） */
@Component
@RequiredArgsConstructor
public class DataChannelRouter {

    private final HunterDataChannel hunterDataChannel;
    private final DirectJdbcDataChannel directJdbcDataChannel;

    public DataChannel route(DatasourceEntity ds) {
        return "DIRECT".equalsIgnoreCase(ds.getChannel()) ? directJdbcDataChannel : hunterDataChannel;
    }
}
