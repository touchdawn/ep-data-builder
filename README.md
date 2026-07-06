# ep-data-builder（EP 造数平台 / TestData Service）

测试数据构造方案的管理、编排、执行与供给平台，是 EP 测试平台体系（LLM 自动生成单接口测试用例）的前置数据供给方。

- 设计与开发文档：[docs/data-builder-design.md](docs/data-builder-design.md)
- 猎户侧配套变更方案（测试猎户数据执行开放接口）：[docs/hunter-open-exec-api.md](docs/hunter-open-exec-api.md)
- 关联项目：
  - `ep-api-contract` —— 契约平台（API_CALL 步骤的调用方式来源）
  - `ep-test-platform` —— LLM 用例生成流水线（开放 API 的主要消费方）
  - 猎户 —— 公司数据查询中心（查写主通道：测试猎户为本平台新增数据执行开放接口，零凭证；直连 JDBC 仅兜底；schemaCode 命名对齐）

核心概念速览：**Factory**（造数工厂：API 先行 + SQL 补刀的步骤编排）· **Recipe**（参数套餐）· **Pool**（死水/活水数据池：SHARED/EXCLUSIVE 领用）· **Build**（一次造数执行，全轨迹留痕）。

技术栈：Vue 3 + Element Plus / Spring Boot 2.1.14.RELEASE（Java 1.8）+ Gradle（Wrapper 5.6.4）+ Spring Data JPA + SQLite（Flyway vendor 目录，预留切达梦/MySQL）。

> 边界：只面向测试环境。生产数据变更必须走猎户标变流程，本平台不做。

## 快速启动

### 一键启停（推荐）

```bash
./start.sh   # 一键启动前后端（后端 9599 / 前端 7588）
./stop.sh    # 一键关闭
```

- **前置要求：本机需安装 Java 8**（Amazon Corretto 8 等均可）。本项目为 Spring Boot 2.1 + Gradle Wrapper 5.6.4，只能用 JDK 8 运行；若用 JDK 17+ 会导致 Gradle 崩溃、后端无法启动，进而前端代理 `/api` 报 500。`start.sh` 会自动通过 `/usr/libexec/java_home -v 1.8` 定位 Java 8，无需手动切换全局 JDK。
- PID 与日志写入 `.run/`（已 gitignore）；查看日志：`tail -f .run/logs/backend.log`（或 `frontend.log`）。
- 前端首次启动若无 `node_modules` 会自动 `npm install`。

### 手动启动

后端（需 JDK 8，端口 9599）：

```bash
cd ep-data-builder-server
mkdir -p data   # SQLite 数据目录（首次）
JAVA_HOME=$(/usr/libexec/java_home -v 1.8) ./gradlew bootRun
```

前端（端口 7588，代理 /api、/open-api 到 9599）：

```bash
cd ep-data-builder-web
npm install
npm run dev
```

默认账号 `admin / admin123`（首启自动初始化，登录后请改密码）。Swagger 在 `http://localhost:9599/swagger-ui.html`。

对接配置（application.yml 的 `ep.*`）：`ep.contract.*` 契约平台开放 API（未配置时 API_CALL 步骤用 overrideUrl 兜底）；`ep.hunter.*` 测试猎户数据执行开放接口（未就绪前数据源用 DIRECT 通道）；`EP_BUILDER_SECRET` 环境变量为 DIRECT 通道凭证的加密密钥。

## M1 已交付

- 环境/模块端点/数据源管理（HUNTER/DIRECT 双通道，凭证 AES-GCM 加密，连通性测试）
- 造数工厂：参数（默认值支持模板函数）+ 步骤编排（API_CALL / SQL_EXEC，条件表达式，JSONPath 输出提取，断言）
- 执行引擎：参数合并 → 逐步执行 → 全轨迹留痕；SqlGuard（单语句/强制 WHERE/类型白名单）
- 控制台造数 + 执行记录/轨迹查询
- 开放 API（TestData Service）：工厂检索、参数 schema、同步造数、轨迹查询；token 鉴权 + QPS 限流
- HunterDataChannel 已按接口规范实现完毕，猎户侧接口上线后配置 `ep.hunter.*` 即通

M2 起：套餐（Recipe）、REF/CHECK 步骤、数据池（M3）——见设计文档 §11。
