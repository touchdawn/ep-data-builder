-- EP 造数平台 SQLite 初始化脚本（事实基准，切库时据此翻译 mysql/dm 目录）
-- 约定：字符串 TEXT，布尔/小整数 INTEGER，时间 INTEGER（epoch 毫秒）

-- 目标环境
CREATE TABLE t_environment (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  code        TEXT NOT NULL,               -- 环境编码，如 test-1
  name        TEXT NOT NULL,
  description TEXT,
  created_at INTEGER, updated_at INTEGER, created_by TEXT, updated_by TEXT
);
CREATE UNIQUE INDEX uk_env_code ON t_environment(code);

-- 模块端点（API 步骤的 base URL）
CREATE TABLE t_module_endpoint (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  env_id      INTEGER NOT NULL,
  module_code TEXT NOT NULL,               -- 对齐契约平台 moduleCode
  base_url    TEXT NOT NULL,
  headers     TEXT,                        -- 公共Header JSON，如网关鉴权头
  created_at INTEGER, updated_at INTEGER, created_by TEXT, updated_by TEXT
);
CREATE UNIQUE INDEX uk_endpoint ON t_module_endpoint(env_id, module_code);

-- 数据源（SQL_EXEC/CHECK 步骤的目标）
CREATE TABLE t_datasource (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  env_id        INTEGER NOT NULL,
  schema_code   TEXT NOT NULL,             -- 对齐猎户 schemaCode：<dbtype>.<cluster>-<schema>
  db_type       TEXT NOT NULL,             -- MYSQL/DM/ORACLE/SQLITE...（工厂作者写 SQL 时需知道方言）
  channel       TEXT NOT NULL DEFAULT 'HUNTER',  -- HUNTER（主）/DIRECT（兜底）
  jdbc_url      TEXT,                      -- 以下三列仅 DIRECT 通道需要
  username      TEXT,
  password_enc  TEXT,                      -- AES/GCM 加密
  created_at INTEGER, updated_at INTEGER, created_by TEXT, updated_by TEXT
);
CREATE UNIQUE INDEX uk_ds ON t_datasource(env_id, schema_code);

-- 造数工厂
CREATE TABLE t_factory (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  code         TEXT NOT NULL,              -- {moduleCode}.{实体名}，全局唯一
  name         TEXT NOT NULL,
  description  TEXT,                       -- 功能描述（LLM 检索依据，重点引导填写）
  owner        TEXT,
  enabled      INTEGER NOT NULL DEFAULT 1,
  pure_sql     INTEGER NOT NULL DEFAULT 0, -- 标记"纯SQL工厂"（无API步骤，承担表结构漂移风险）
  lock_version INTEGER NOT NULL DEFAULT 0,
  deleted      INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER, updated_at INTEGER, created_by TEXT, updated_by TEXT
);
CREATE UNIQUE INDEX uk_factory_code ON t_factory(code);

-- 工厂参数
CREATE TABLE t_factory_param (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  factory_id    INTEGER NOT NULL,
  name          TEXT NOT NULL,
  data_type     TEXT NOT NULL,             -- string/int/long/boolean/date/datetime/enum
  default_value TEXT,                      -- 支持模板函数，如 test_${randomStr(8)}
  description   TEXT,
  enums         TEXT,                      -- 枚举JSON [{value,meaning}]，仅 enum 类型
  sort_no       INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER, updated_at INTEGER, created_by TEXT, updated_by TEXT
);
CREATE INDEX idx_param_factory ON t_factory_param(factory_id);

-- 工厂步骤
CREATE TABLE t_factory_step (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  factory_id   INTEGER NOT NULL,
  sort_no      INTEGER NOT NULL,
  step_type    TEXT NOT NULL,              -- API_CALL/SQL_EXEC/REF/CHECK
  name         TEXT,                       -- 步骤名，轨迹里展示
  condition_expr TEXT,                     -- 条件表达式，空=总是执行
  config       TEXT NOT NULL,              -- 类型专属配置JSON
  outputs      TEXT,                       -- 输出提取JSON [{var, expr}]
  created_at INTEGER, updated_at INTEGER, created_by TEXT, updated_by TEXT
);
CREATE INDEX idx_step_factory ON t_factory_step(factory_id);

-- 套餐（M2 启用）
CREATE TABLE t_recipe (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  factory_id  INTEGER NOT NULL,
  code        TEXT NOT NULL,
  name        TEXT NOT NULL,
  description TEXT,
  params_json TEXT NOT NULL,               -- 参数值集合 {name: value}
  created_at INTEGER, updated_at INTEGER, created_by TEXT, updated_by TEXT
);
CREATE UNIQUE INDEX uk_recipe ON t_recipe(factory_id, code);

-- 数据池（M3 启用）
CREATE TABLE t_pool (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  code         TEXT NOT NULL,
  name         TEXT NOT NULL,
  env_id       INTEGER NOT NULL,
  factory_id   INTEGER NOT NULL,           -- 池内数据由哪个工厂生产
  acquire_mode TEXT NOT NULL,              -- SHARED（死水）/EXCLUSIVE（活水）
  lease_minutes INTEGER NOT NULL DEFAULT 30,  -- EXCLUSIVE 租期
  description  TEXT,
  deleted      INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER, updated_at INTEGER, created_by TEXT, updated_by TEXT
);
CREATE UNIQUE INDEX uk_pool_code ON t_pool(code);

-- 池内条目（M3 启用）
CREATE TABLE t_pool_item (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  pool_id      INTEGER NOT NULL,
  data_json    TEXT NOT NULL,              -- build 输出，如 {"userId":10001}
  status       TEXT NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE/LEASED/USED/INVALID
  lease_id     TEXT,                       -- 当前租约
  lease_holder TEXT,                       -- token 名
  lease_expire INTEGER,
  build_id     INTEGER,                    -- 生产它的 build
  created_at INTEGER, updated_at INTEGER, created_by TEXT, updated_by TEXT
);
CREATE INDEX idx_item_pool ON t_pool_item(pool_id, status);

-- 预埋任务（M3 启用）
CREATE TABLE t_seed_task (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  pool_id      INTEGER NOT NULL,
  recipe_id    INTEGER,
  target_count INTEGER NOT NULL,
  success_count INTEGER NOT NULL DEFAULT 0,
  fail_count   INTEGER NOT NULL DEFAULT 0,
  status       TEXT NOT NULL DEFAULT 'RUNNING',  -- RUNNING/FINISHED/STOPPED
  created_at INTEGER, updated_at INTEGER, created_by TEXT, updated_by TEXT
);

-- 造数执行记录
CREATE TABLE t_build (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  factory_id  INTEGER NOT NULL,
  env_id      INTEGER NOT NULL,
  source      TEXT NOT NULL,               -- CONSOLE/OPEN_API/SEED/TRIAL/REF
  parent_build_id INTEGER,                 -- REF 嵌套时指向父 build
  token_name  TEXT,                        -- OPEN_API 来源时记录消费方
  recipe_id   INTEGER,
  params_json TEXT,                        -- 合并后的最终参数
  status      TEXT NOT NULL,               -- RUNNING/SUCCESS/FAILED
  outputs_json TEXT,
  error_msg   TEXT,
  duration_ms INTEGER,
  created_at INTEGER, created_by TEXT
);
CREATE INDEX idx_build_factory ON t_build(factory_id, created_at);

-- 步骤执行轨迹
CREATE TABLE t_build_step_log (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  build_id    INTEGER NOT NULL,
  sort_no     INTEGER NOT NULL,
  step_type   TEXT NOT NULL,
  step_name   TEXT,
  skipped     INTEGER NOT NULL DEFAULT 0,  -- 条件不满足被跳过
  request     TEXT,                        -- 渲染后的报文/SQL（凭证脱敏）
  response    TEXT,                        -- 响应摘要/影响行数
  outputs_json TEXT,
  status      TEXT NOT NULL,               -- SUCCESS/FAILED/SKIPPED
  error_msg   TEXT,
  duration_ms INTEGER,
  created_at INTEGER
);
CREATE INDEX idx_steplog_build ON t_build_step_log(build_id);

-- 开放API令牌
CREATE TABLE t_open_token (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  name         TEXT NOT NULL,              -- 消费方名称，如 test-gen-pipeline
  token_hash   TEXT NOT NULL,              -- SHA-256，明文只在创建时展示一次
  enabled      INTEGER NOT NULL DEFAULT 1,
  qps_limit    INTEGER NOT NULL DEFAULT 5,
  last_used_at INTEGER,
  created_at INTEGER, updated_at INTEGER, created_by TEXT, updated_by TEXT
);
CREATE UNIQUE INDEX uk_token_hash ON t_open_token(token_hash);

-- 用户
CREATE TABLE t_user (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  username      TEXT NOT NULL,
  password_hash TEXT NOT NULL,             -- BCrypt
  display_name  TEXT,
  role          TEXT NOT NULL DEFAULT 'EDITOR',  -- ADMIN/EDITOR/VIEWER
  enabled       INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER, updated_at INTEGER, created_by TEXT, updated_by TEXT
);
CREATE UNIQUE INDEX uk_username ON t_user(username);

-- 操作日志（留痕/反向追溯）
CREATE TABLE t_operation_log (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  biz_type   TEXT NOT NULL,                -- ENV/DATASOURCE/FACTORY/RECIPE/POOL/TOKEN/USER
  biz_id     INTEGER NOT NULL,
  action     TEXT NOT NULL,                -- CREATE/UPDATE/DELETE/...
  detail     TEXT,                         -- 动作详情JSON
  operator   TEXT NOT NULL,
  created_at INTEGER
);
CREATE INDEX idx_log_biz ON t_operation_log(biz_type, biz_id);
