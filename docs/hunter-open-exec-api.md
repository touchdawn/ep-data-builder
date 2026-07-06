# 测试猎户「数据执行开放接口」变更方案

> 版本:v0.1(2026-07-06)
> 变更对象:猎户(测试猎户:中控 + 看门狗 + 测试管理端)
> 需求方:ep-data-builder(EP 造数平台)
> 关联文档:[data-builder-design.md](data-builder-design.md) §4

---

## 1. 背景与目标

造数平台需要对测试库执行 SELECT(存在性检查、结果核验、落表探测)与 DML(造数的"SQL 补刀"步骤)。测试猎户当前只有「猎户配置 + 自定义查询」两个模块,没有 DML 能力,且现有能力都是**人的会话**形态(chunk 页面 + 用户权限梯子),没有服务间调用面。

本变更为测试猎户新增一组**面向服务调用方的数据执行开放接口**:参数化 SQL 的查询与执行,token 鉴权,免审批(测试环境改数据的公司惯例本来就是 DataGrip 直连免审批,本接口只是把这一惯例服务化并补上审计),全量留痕。

**定位说明**:这是猎户的平台级能力,不是造数平台专用后门。首个消费方是造数平台,后续其他测试工具(自动化框架、环境巡检等)可按 token 申请接入。生产猎户不部署此能力(见 §6)。

收益(对全局):

- 造数平台**零数据库凭证**:不自持任何测试库账密,数据源覆盖直接继承测试猎户(≈ DBA 管的测试库全集);新 schema 接入猎户即对造数平台自动可用。
- 驱动矩阵(mysql/达梦/oracle/mongo)收敛在看门狗一处,消费方不用管。
- 测试数据操作的审计统一沉淀在猎户,与生产标变审计同一个心智模型。

## 2. 变更范围总览

| 组件 | 变更 | 规模估计 |
|---|---|---|
| 猎户中控 | 新增开放接口 controller(3 个端点)+ ServiceToken 鉴权 Filter + SqlGuard + 开放执行审计表;路由链路零改动(复用 schemaCode → 集群 → 网关 → Nacos → 看门狗) | ~3 人日 |
| 看门狗 | 新增 EXECUTE / EXECUTE_BATCH 指令类型(executeUpdate 语义 + 影响行数返回 + batch 单事务) | ~2 人日 |
| 测试管理端(27.40:32000 架子) | ServiceToken 管理页 + 开放执行审计查询页 | ~1 人日 |
| 联调 | 与造数平台冒烟(见 §8) | ~1 人日 |

生产猎户:**零变更、零部署**。

## 3. 接口规范

前缀建议 `/hunter-open/v1`,挂在测试猎户中控。Header `X-Hunter-Service-Token`。统一响应 `{ "code": 0, "message": "ok", "data": ... }`(错误码命名以猎户仓现行规范为准,下表为语义约定)。

SQL 一律**参数化**:`?` 占位 + `params` 数组(与 JDBC 对齐),不接受调用方拼接值。`traceId` 由调用方透传(造数平台传 `build-{buildId}-step-{n}`),写入审计,两边可互相关联。

### 3.1 查询

```
POST /hunter-open/v1/query
{
  "schemaCode": "mysql.TMy27MAIN-user_center",
  "sql": "SELECT count(*) FROM t_user WHERE user_name = ?",
  "params": ["test_ab3kx9pq"],
  "maxRows": 100,
  "traceId": "build-1024-step-3"
}
→ { "code": 0, "data": {
      "columns": ["count(*)"],
      "rows": [[0]],
      "auditId": 88771
} }
```

- `maxRows` 上限服务端封顶(默认 100,最大 1000),超出截断并在响应打 `truncated: true`。
- 结果统一 `columns[] + rows[][]`(值序列化为字符串/数字/null),类型解释由调用方负责。

### 3.2 执行(单语句 DML)

```
POST /hunter-open/v1/execute
{
  "schemaCode": "mysql.TMy27MAIN-user_center",
  "sql": "UPDATE t_user SET created_time = ? WHERE id = ?",
  "params": [1592822400000, 10001],
  "traceId": "build-1024-step-2"
}
→ { "code": 0, "data": { "affectedRows": 1, "auditId": 88772 } }
```

### 3.3 批量执行(同 schema 多语句单事务)

服务多表关联造数场景(纯 SQL 直插多张表需要原子性):

```
POST /hunter-open/v1/execute-batch
{
  "schemaCode": "mysql.TMy27MAIN-order_center",
  "statements": [
    { "sql": "INSERT INTO t_order (...) VALUES (?, ?, ?)", "params": [...] },
    { "sql": "INSERT INTO t_order_item (...) VALUES (?, ?)", "params": [...] }
  ],
  "traceId": "build-1025-step-1"
}
→ { "code": 0, "data": { "affectedRows": [1, 3], "auditId": 88773 } }
```

- 看门狗在**单连接单事务**内顺序执行,任一语句失败整体回滚。
- 语句数上限 20;跨 schemaCode 不支持(跨库事务不承诺,调用方拆多次调用)。

### 3.4 错误语义

| 错误 | 含义 | 说明 |
|---|---|---|
| TOKEN_INVALID | token 无效/停用 | |
| SCOPE_DENIED | schemaCode 不在该 token 授权范围 | |
| OP_DENIED | 操作类型未授权(如只授了 QUERY 的 token 调 execute) | |
| SQL_REJECTED | SqlGuard 拒绝 | message 带具体规则,如 "UPDATE without WHERE" |
| SCHEMA_NOT_FOUND | schemaCode 未接入测试猎户 | |
| WATCHDOG_UNREACHABLE | 路由成功但看门狗不可达 | 调用方可重试(幂等性自负) |
| EXEC_ERROR | 数据库执行报错 | message 带数据库原始错误摘要 |
| EXEC_TIMEOUT | 超时(默认 30s) | batch 已回滚 |
| RATE_LIMITED | 超 QPS 配额 | |

## 4. ServiceToken 鉴权模型

**与人的权限体系(HUNTER_USER / 项目用户组 / 特殊权限梯子)完全独立**,不复用、不打通——那是人的准入面,这是服务面。实现上是一张独立表 + 一个独立 Filter,不碰现有权限同步逻辑。

```sql
-- 测试猎户中控库新增(生产库不建此表)
CREATE TABLE t_service_token (
  id           BIGINT PRIMARY KEY,
  name         VARCHAR(64)  NOT NULL,   -- 调用方名称,如 ep-data-builder
  token_hash   VARCHAR(128) NOT NULL,   -- SHA-256,明文仅创建时展示一次
  schema_scope TEXT         NOT NULL,   -- 授权 schemaCode 列表,支持后缀通配:mysql.TMy27MAIN-* 或 *
  ops          VARCHAR(32)  NOT NULL,   -- QUERY,EXECUTE,BATCH 组合
  qps_limit    INT          NOT NULL DEFAULT 10,
  enabled      TINYINT      NOT NULL DEFAULT 1,
  last_used_at DATETIME,
  created_at DATETIME, created_by VARCHAR(64)
);
```

- 造数平台的 token 建议 scope 按实际使用的 schemaCode 显式列举起步,稳定后再放宽到通配——授权面从窄到宽,不反向。
- token 管理页放测试管理端;发放/停用记猎户操作日志。

## 5. SqlGuard(中控侧统一执行)

底线规则在中控做(看门狗不重复实现,保持哑执行端定位):

1. 单语句(execute / query;batch 的每条 statement 同样校验);剥离注释后再判断,防注释绕过。
2. 语句类型白名单:query 仅 SELECT;execute/batch 仅 INSERT / UPDATE / DELETE。DDL、DCL、TRUNCATE、存储过程调用一律拒绝。
3. UPDATE / DELETE 必须带 WHERE。
4. 影响行数上限可配(默认单语句 10000 行,超出报错回滚)——防手滑大面积改数。
5. 执行超时 30s(statement timeout 下沉到看门狗)。

调用方(造数平台)另有业务级校验(影响行数期望等),两层防御各管各的。

## 6. 生产隔离(三道闸 + 网络面)

| 闸 | 机制 | 失效假设 |
|---|---|---|
| 闸 0(网络) | 造数平台部署在测试网,与生产中控网络不通 | — |
| 闸 1(配置) | `hunter.open-exec.enabled` 默认 `false`,仅测试猎户配置置 `true`;生产部署基线不含此开关 | 有人在生产误开 → 闸 2 |
| 闸 2(运行时) | 接口实现校验实例环境标,生产环境标直接 403(fail-closed,不看开关) | 环境标被误配 → 闸 3 |
| 闸 3(数据) | `t_service_token` 仅测试猎户库存在,生产库无表、无 token 可验 | — |

生产数据变更的唯一通道仍是标变(提交 + 审批 + 看门狗执行),本变更不触碰。

## 7. 审计

中控新增开放执行审计表(与现有 SQL/DML 审计并列,不混表):

```sql
CREATE TABLE t_open_exec_audit (
  id            BIGINT PRIMARY KEY,
  token_name    VARCHAR(64)  NOT NULL,
  op            VARCHAR(16)  NOT NULL,   -- QUERY/EXECUTE/BATCH
  schema_code   VARCHAR(128) NOT NULL,
  sql_text      TEXT         NOT NULL,   -- 原始参数化 SQL
  params_digest VARCHAR(64),             -- 参数摘要(条数 + hash,不存明文值)
  result        VARCHAR(16)  NOT NULL,   -- SUCCESS/REJECTED/ERROR
  affected_rows INT,
  row_count     INT,
  error_msg     VARCHAR(512),
  trace_id      VARCHAR(128),            -- 调用方透传,造数平台=build-{id}-step-{n}
  cost_ms       INT,
  created_at    DATETIME
);
```

- 审计查询页进测试管理端;按 token / schemaCode / traceId / 时间检索。
- `auditId` 回传给调用方,造数平台把它记进 build 步骤轨迹,双向可追。

## 8. 排期与联调

- 总量约 6-7 人日,可与造数平台 M1/M2 并行;**本接口规范先冻结,两边按契约各自开发**。
- 联调冒烟清单(对齐造数平台 M2 末):
  1. query:count 单值查询,columns/rows 结构正确;
  2. execute:UPDATE 单行,affectedRows=1;
  3. execute-batch:双表 INSERT 成功;人为制造第二条失败,验证第一条回滚;
  4. SCOPE_DENIED:用未授权 schemaCode 调用被拒;
  5. SQL_REJECTED:无 WHERE 的 UPDATE、TRUNCATE、多语句注入样例全部被拒;
  6. 审计:以上每次调用在审计页可查,traceId 与造数平台 build 轨迹对得上。

## 9. 开放问题

1. 中控现有"自定义查询"链路是否已有可复用的语句下发/结果回传通道(若有,query 端点是薄包装;若无,与 execute 一起走新指令协议)——进猎户仓后按实际代码定。
2. 看门狗对 mongo 类非 SQL 数据源的 execute 语义本期不做(scope 校验时直接拒绝非关系型 schemaCode),按需求再议。
3. QPS 限流放中控单机内存即可(测试猎户单实例);若中控多实例部署需换共享计数,本期不做。
