package com.ep.databuilder.engine.ds;

import com.ep.databuilder.common.AesUtil;
import com.ep.databuilder.common.BizException;
import com.ep.databuilder.env.DatasourceEntity;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DIRECT 通道的动态数据源管理：每个数据源一个小 Hikari 池（max 2），
 * 以 jdbcUrl|username|passwordEnc 做指纹，配置变更自动失效重建。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicDataSourceManager implements DisposableBean {

    private final AesUtil aesUtil;
    private final Map<Long, CachedDataSource> cache = new ConcurrentHashMap<>();

    public JdbcTemplate jdbcTemplate(DatasourceEntity ds) {
        return new JdbcTemplate(dataSource(ds));
    }

    public synchronized HikariDataSource dataSource(DatasourceEntity ds) {
        if (ds.getJdbcUrl() == null || ds.getJdbcUrl().trim().isEmpty()) {
            throw new BizException("数据源 " + ds.getSchemaCode() + " 未配置 JDBC URL（DIRECT 通道必填）");
        }
        String fingerprint = ds.getJdbcUrl() + "|" + ds.getUsername() + "|" + ds.getPasswordEnc();
        CachedDataSource cached = cache.get(ds.getId());
        if (cached != null && cached.fingerprint.equals(fingerprint)) {
            return cached.dataSource;
        }
        if (cached != null) {
            closeQuietly(cached.dataSource);
        }
        HikariDataSource hikari = new HikariDataSource();
        hikari.setPoolName("target-ds-" + ds.getId());
        hikari.setJdbcUrl(ds.getJdbcUrl());
        if (ds.getUsername() != null && !ds.getUsername().isEmpty()) {
            hikari.setUsername(ds.getUsername());
        }
        if (ds.getPasswordEnc() != null && !ds.getPasswordEnc().isEmpty()) {
            hikari.setPassword(aesUtil.decrypt(ds.getPasswordEnc()));
        }
        String driver = inferDriver(ds.getJdbcUrl());
        if (driver != null) {
            hikari.setDriverClassName(driver);
        }
        hikari.setMaximumPoolSize(2);
        hikari.setMinimumIdle(0);
        hikari.setIdleTimeout(60_000);
        hikari.setConnectionTimeout(5_000);
        cache.put(ds.getId(), new CachedDataSource(hikari, fingerprint));
        return hikari;
    }

    public synchronized void evict(Long dsId) {
        CachedDataSource cached = cache.remove(dsId);
        if (cached != null) {
            closeQuietly(cached.dataSource);
        }
    }

    private static String inferDriver(String jdbcUrl) {
        if (jdbcUrl.startsWith("jdbc:sqlite:")) {
            return "org.sqlite.JDBC";
        }
        if (jdbcUrl.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (jdbcUrl.startsWith("jdbc:dm:")) {
            return "dm.jdbc.driver.DmDriver";
        }
        if (jdbcUrl.startsWith("jdbc:oracle:")) {
            return "oracle.jdbc.OracleDriver";
        }
        return null; // 交给 DriverManager 自动识别
    }

    private static void closeQuietly(HikariDataSource ds) {
        try {
            ds.close();
        } catch (Exception e) {
            log.warn("关闭数据源连接池失败", e);
        }
    }

    @Override
    public void destroy() {
        cache.values().forEach(c -> closeQuietly(c.dataSource));
        cache.clear();
    }

    private static class CachedDataSource {
        final HikariDataSource dataSource;
        final String fingerprint;

        CachedDataSource(HikariDataSource dataSource, String fingerprint) {
            this.dataSource = dataSource;
            this.fingerprint = fingerprint;
        }
    }
}
