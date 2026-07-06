# EP 造数平台（ep-data-builder）设计与开发文档

> 版本：v0.2（2026-07-06：确认可对猎户做变更，写通道方案由"直连 JDBC"改为"测试猎户新增数据执行开放接口"，直连降级为兜底通道；猎户侧变更方案见 [hunter-open-exec-api.md](hunter-open-exec-api.md)）
> 技术栈：Vue 3 + Element Plus / Spring Boot 2.1.14.RELEASE（Java 1.8）+ Gradle + Spring Data JPA + SQLite（→ 达梦/MySQL）
> 关联项目：ep-api-contract（契约平台，已启动）、ep-test-platform（LLM 测试用例生成流水线，规划中）、猎户（公司数据查询中心，我们自己维护，可做变更）

---

## 1. 背景与定位

在「LLM 自动生成单接口测试用例」体系里，模型生成的用例调接口之前，难的不是调接口，而是**前置数据准备**。培训方案中这一环节的最终形态是 **TestData Service**：把造数逻辑封装成 HTTP 服务，测试用例（不管什么语言、由人写还是由模型生成）在 setup 阶段调它拿数据；LLM 流水线在设计用例时，从契约反推需要哪些前置数据，再转换成对 TestData Service 的调用。

本项目（ep-data-builder）就是我们的 TestData Service 实现：**一个测试数据构造方案的管理、编排、执行与供给平台**。

### 1.1 在整个体系中的位置

```
┌──────────────────┐   契约JSON（唯一可信源）    ┌─────────────────────┐
│  ep-api-contract  │ ─────────────────────────▶ │  ep-test-platform    │
│  契约平台          │                            │  LLM 用例生成流水线   │
└────────┬─────────┘                            └──────────┬──────────┘
         │ API_CALL 步骤按 apiCode                          │ ① 从契约反推需要什么前置数据
         │ 拉取调用方式（method/path/参数）                   │ ② 检索"哪个工厂能造这种数据"
         ▼                                                 ▼ ③ 调开放API执行造数
┌─────────────────────────────────────────────────────────────────────┐
│                     ep-data-builder（本项目）                         │
│   造数工厂（Factory/Builder 语义）＋ 套餐（Recipe）＋ 数据池（Pool）     │
│   开放 API = TestData Service                                        │
└──────────────────┬────────────────────────────┬─────────────────────┘
                   │ 查写主通道（HUNTER）          │ 兜底通道（DIRECT）
                   ▼ query/execute，schemaCode 定位│ 仅项目自建库等边缘场景
       ┌───────────────────────────┐             ▼
       │ 测试猎户                    │      ┌──────────────────┐
       │ 数据执行开放接口（本次新增）   │      │ 直连 JDBC          │
       │ 中控 → 网关/Nacos → 看门狗   │      └────────┬─────────┘
       └────────────┬──────────────┘               │
                    ▼                              ▼
       ┌─────────────────────────────────────────────────┐
       │        各测试环境数据库（mysql/达梦/...）           │
       └─────────────────────────────────────────────────┘
```

三个消费方：

| 消费方 | 用法 |
|---|---|
| LLM 用例生成流水线 | 生成的用例 setup 里是对本平台开放 API 的调用；流水线用「工厂检索」接口找到能造某类数据的工厂（造数推理） |
| 人写的自动化用例 | 任何能发 HTTP 的测试框架（Java/Python）直接调开放 API，不关心造数逻辑内部 |
| 工程师日常 | 管理端页面上手工点一次造数（联调、复现问题时"给我来一个 5 年前的老用户"） |

### 1.2 设计原则（对应培训方法论）

1. **API 先行、SQL 补刀**：默认造数模式是"先调业务 API 保证数据合理、合法、完整，再用 UPDATE 精准修改个别字段"（如把创建时间改到 5 年前）。API 保证正确性并天然跟随表结构演进（规避"第六张表"问题），SQL 补足多样性（边界值、临界值）。纯 SQL 工厂允许存在，但要显式标记并承担表结构漂移风险。
2. **Builder 语义**：所有工厂参数都有默认值，空参即可造出一个"能用的默认数据"；调用方只覆盖自己关心的参数（withA/withB 的平台化）。参数组合的合法性由调用方负责，平台不做无穷尽的组合封装——这是培训里"被组合需求逼疯"的直接教训。
3. **套餐（Recipe）沉淀**：常用的复杂参数组合存成命名套餐，报套餐名即可复用，团队自己沉淀自己的套餐。
4. **死水/活水分类供给**：可反复共用的数据（死水）走数据池预埋（out-of-box），一次性数据（活水）实时造（on-the-fly）；工厂的前置依赖优先从死水池取（订单实时造，但买家/卖家/商品用预埋数据），避免"造数一条龙"。
5. **服务化、语言无关**：能力全部通过 HTTP 开放 API 暴露，Java/Python 团队同等待遇——这是 TestDataUtility 从 Jar 演进为 Service 的教训的直接落地，我们一步到位。
6. **一切留痕**：每次造数的完整执行轨迹（调了什么 API、发了什么报文、跑了什么 SQL、影响几行）全部落审计。数据争议时（"数据没造对"还是"发现 bug"）有据可查。

### 1.3 范围界定

- **只面向测试环境**。生产数据变更必须走猎户标变（提交 + 审批 + 看门狗执行），本平台不做、也不得成为绕过生产管控的通道。平台部署与目标数据源均在测试网。
- 本期不做压测级批量造数（几十万条 block insert），功能级造数优先；批量列入演进（M4）。
- 不做数据脱敏/生产数据回流，那是另一个课题。
- 不做通用低代码查询页（那是猎户配置/hunter-sdk 的地盘），平台内查询能力只服务于造数（存在性检查、结果核验、落表探测）。
- 用户体系与 ep-api-contract 同策略：先自建简单账号（预留 SSO 对接）。

---

## 2. 方法论 → 平台概念映射

培训内容里的每个方法论概念，在平台里都有一个确定的落点：

| 培训概念 | 平台概念 | 说明 |
|---|---|---|
| TestDataUtility 函数 | **Factory（造数工厂）** | 一种数据实体的造数方案，由若干步骤编排而成 |
| 先调 API 再 UPDATE | **Step（步骤）**：API_CALL + SQL_EXEC | 工厂内按序执行的步骤链，上一步输出可作为下一步输入 |
| Builder 模式 `withA().withB()` | **Param（工厂参数）** | 全部有默认值，调用时选择性覆盖 |
| `withRecipe(xxx)` 套餐文件 | **Recipe（套餐）** | 工厂参数的命名预设，存平台里按名引用 |
| 买家/卖家/商品用预埋数据 | **REF 步骤 + POOL_FIRST 策略** | 工厂依赖其他实体时，优先从数据池取，取不到再嵌套造 |
| out-of-box 开箱即用 | **Pool（数据池）+ 预埋任务** | 用工厂+套餐批量生产，填进池子 |
| on-the-fly 实时造 | **开放 API 同步造数** | 用例 setup 里当场调 build |
| 死水 / 活水 | **Pool 的领用模式** SHARED / EXCLUSIVE | 死水共享领用不锁定；活水独占领用、用完标记消耗 |
| 反向赋值（看 API 落了哪几张表） | **落表探测工具** | 造数前后对比表行数变化，辅助编写 SQL 步骤（M3） |
| 契约反推前置数据 → 转成造数调用 | **工厂检索开放 API** | 工厂的 `description` 写清"造的是什么数据、什么场景用"，供 LLM 检索匹配 |
| 航班号 MU/HO 开头这类业务特征 | 不在本平台重复维护 | 业务特征规则属于契约（字段级 `businessRule`），本平台参数默认值/套餐里体现其结果 |

---

## 3. 核心概念与领域模型

```
Environment（目标环境，如 test-1）
  ├── ModuleEndpoint（模块 → 该环境的 base URL，API 步骤用）
  └── DataSource（schemaCode → 该环境的 JDBC 连接，SQL 步骤用）

Factory（造数工厂，如 user-center.user）
  ├── Param（参数定义：名称/类型/默认值/说明）
  ├── Step（有序步骤链）
  │     ├── API_CALL   调业务接口（按 apiCode 从契约平台取调用方式）
  │     ├── SQL_EXEC   对某 schemaCode 执行 DML（模板渲染）
  │     ├── REF        引用其他工厂（嵌套造前置实体，或从池领用）
  │     └── CHECK      存在性/结果核验（DataChannel：猎户为主，直连兜底）
  └── Recipe（套餐：参数命名预设）

Pool（数据池）
  └── PoolItem（池内数据条目：标识 JSON + 状态 + 领用记录）

Build（一次造数执行）
  └── BuildStepLog（每步的渲染结果/报文/影响行数/输出变量）
```

### 3.1 Factory 与 Step

工厂是平台的核心资产。一个典型工厂「用户（可指定注册时长）」：

```
Factory: user-center.user
参数:
  userName        string  默认 ${"test_" + randomStr(8)}   登录名
  userType        enum    默认 PERSONAL                     用户类型
  createdDaysAgo  int     默认 0                            注册于几天前（0=当天）

步骤:
  1. API_CALL  user-center.createUser
       body: { "userAccount": { "userName": "${userName}", "userType": "${userType}" } }
       期望: httpStatus=200 且 $.code == "0"
       输出: userId ← $.data.userId
  2. SQL_EXEC  schemaCode=mysql.TMy27MAIN-user_center
       仅当 createdDaysAgo > 0 执行（condition）
       sql: UPDATE t_user SET created_time = ${daysAgo(createdDaysAgo)} WHERE id = ${userId}
       期望影响行数: 1

输出（对调用方）: { userId, userName }
```

要点：

- **步骤上下文（BuildContext）**：参数（默认值 ← 套餐 ← 调用方覆盖，右侧优先）+ 各步骤输出变量，构成一个变量池；后续步骤的模板都从这个池取值。
- **模板函数**：`${now()}`、`${daysAgo(n)}`、`${randomStr(n)}`、`${randomInt(a,b)}`、`${uuid()}` 等内置函数直接解决"时间动态 SQL"这一造数最常见卡点（数据服务类 SQL 大多含相对时间条件）。
- **条件执行**：步骤可挂简单条件表达式（如 `createdDaysAgo > 0`），让一个工厂覆盖"默认/特化"两种路径，而不是拆成两个工厂。
- **期望校验**：API 步骤校验状态码 + JSONPath 断言；SQL 步骤校验影响行数。不符即整次 build 失败——造数失败必须显式失败，不能吐半成品数据。

### 3.2 REF 步骤与"造数一条龙"治理

订单工厂需要买家/卖家/商品。REF 步骤的领用策略：

| 策略 | 行为 | 对应场景 |
|---|---|---|
| `POOL_FIRST` | 先从指定数据池领（SHARED 模式），池空则回退嵌套造 | 死水前置数据（买家/卖家/商品） |
| `ALWAYS_BUILD` | 每次都嵌套执行被引用工厂 | 前置数据本身也是活水（如"造一笔退款要先造一笔已支付订单"） |
| `POOL_ONLY` | 只从池领，池空即失败 | 强约束预埋数据的场景（数据构造成本极高，不允许实时造） |

REF 嵌套深度限制为 3 层，禁止循环引用（保存时校验），从机制上掐死"一条龙"失控。

### 3.3 Pool 与死水/活水

死水/活水**不是数据的固有属性，而是用法**（测改密码时用户就是活水）——所以分类落在池的领用模式上，而不是实体上：

- **SHARED（死水池）**：领用不改变条目状态，多个用例可同时用同一条数据。适合"只读用"的预埋数据。
- **EXCLUSIVE（活水池）**：领用即租约（LEASED，带租期），调用方用完后 release（归还为可用）或 consume（标记 USED 不再供给）；租约超时自动回收为可用并计数告警（说明有用例领了不还）。适合"用一次就脏"的数据。

池的补给 = **预埋任务**：指定 工厂 + 套餐 + 目标数量 + 环境，平台异步循环执行 build 把产出灌进池子。这就是 out-of-box：环境准备完之后跑一轮预埋，后续用例直接领。

### 3.4 与契约平台的关系

- API_CALL 步骤只填 `apiCode` + 参数模板；method/path/contentType 通过契约平台开放 API（`GET /open-api/v1/apis/{apiCode}/contract`）拉取并本地缓存（TTL + 手动刷新）。**调用方式以契约为准，本平台不重复维护接口定义**。
- 契约未收录的接口允许手工填 method/path 兜底（displayed 标记"脱契约"），但这是过渡态——它同时也是推动接口上契约平台的抓手。
- LLM 造数推理链：流水线读契约发现前置对象（如 UserAccount）→ 调本平台 `factories/search?q=用户` → 拿到工厂参数 schema → 组装 build 调用。工厂 `description` 的书写质量决定检索命中率，编辑器要引导（同契约平台 8.3 的思路）。

---

## 4. 与猎户的结合

### 4.1 决策：为造数出接口，查写统一走猎户

猎户是公司统一的数据查询 + 标变（DML）执行平台（中控 + 看门狗 + hunter-sdk），schemaCode（`<dbtype>.<cluster>-<schema>`）是其全局定位 key。**猎户由我们自己团队维护，可以做变更**——这解锁了最优解：测试猎户新增一组面向服务调用方的「数据执行开放接口」（query / execute / execute-batch，token 鉴权，免审批但全量审计），造数平台的查、写两类数据库操作统一走它。

候选方案比较：

| 方案 | 结论 | 理由 |
|---|---|---|
| 本平台直连 JDBC | 降级为**兜底通道** | 要自持全套测试库凭证 + 驱动矩阵，数据源接入是重复建设；但作为兜底保留（见 4.2） |
| **测试猎户新增数据执行开放接口** | ✅ **主通道** | 零凭证、数据源覆盖直接继承测试猎户（≈DBA 管的测试库全集）、驱动收敛在看门狗、审计统一沉淀在猎户；路由复用现有 schemaCode → 集群 → 网关 → Nacos → 看门狗 链路，猎户侧改动小 |
| 绕过中控直调看门狗 | 否决 | 破坏"看门狗只接受中控指令"的架构约束，等于自己重新实现路由与权限 |
| 嵌入 hunter-sdk | 否决 | 它解决低代码 CRUD 页面问题，凭证仍要自己拿，与造数编排是两回事 |
| 造数能力做进猎户 | 否决 | 造数还要编排业务 API 调用（API_CALL 步骤），完全不是猎户这个数据中间层的职责；两个平台各管各的 |

**免审批的正当性**：测试库改数据的公司惯例本来就是 DataGrip 直连免审批，猎户开放执行接口只是把这一惯例服务化，并补上了 DataGrip 没有的 token 授权、SqlGuard 底线与全量审计——治理是加强而不是放松。生产侧不受影响：生产数据变更唯一通道仍是标变审批流，开放接口在生产猎户不部署（网络 + 配置开关 + 运行时环境标 + 无 token 表，四道闸，详见变更方案 §6）。

猎户侧变更的完整设计（接口规范、ServiceToken 模型、SqlGuard 规则、生产隔离、审计表、排期与联调清单）独立成文：**[hunter-open-exec-api.md](hunter-open-exec-api.md)**，可直接拿去猎户仓排期。规模约 6-7 人日（中控 3 + 看门狗 2 + 管理端 1 + 联调 1），与本平台 M1/M2 并行开发，接口规范先冻结。

### 4.2 通道设计（DataChannel 接口）

```
                 ┌──────────────────────────────────┐
                 │      ep-data-builder 执行引擎       │
                 │  SqlExecExecutor / CheckExecutor   │
                 └──────────────┬───────────────────┘
                                │ DataChannel 接口（query / execute / executeBatch）
              ┌─────────────────┴──────────────────┐
              ▼ 默认                                ▼ 兜底
   ┌─────────────────────────┐          ┌─────────────────────────┐
   │ HunterDataChannel        │          │ DirectJdbcDataChannel    │
   │ 调测试猎户开放接口          │          │ 本平台自持凭证直连         │
   │ 透传 traceId=build-id-步号 │          │ （动态 Hikari 池）         │
   └─────────────────────────┘          └─────────────────────────┘
```

- **DataChannel 做成接口、按数据源配置实现**，默认 HUNTER。DIRECT 兜底覆盖三类场景：项目自建库（极个别，不进测试资源中心、猎户不覆盖）；猎户接口就绪前的过渡期（本平台 M1 不被猎户排期阻塞）；猎户故障时的应急切换。
- 模板渲染的产物就是「带 `?` 占位的 SQL + 绑定参数数组」，与猎户接口、JDBC PreparedStatement 天然同构——两个实现共享同一份渲染结果，切换通道对工厂定义零影响。
- HUNTER 通道数据源登记只需 schemaCode + 通道标记，**无凭证**；DIRECT 通道才需要 JDBC URL + 账密（AES 加密存储，密钥走环境变量）。
- traceId 关联：每次调猎户传 `build-{buildId}-step-{n}`，猎户审计回传 `auditId` 记入本平台步骤轨迹，双向可追。
- SQL 守护分两层：猎户侧 SqlGuard 管底线（单语句、类型白名单、UPDATE/DELETE 强制 WHERE、影响行数上限），本平台管业务期望（`expectAffectedRows` 等）。DIRECT 通道则由本平台同时承担两层。
- 生产写通道：若将来要支持"生产预埋基础数据"，唯一正道是对接猎户**标变提交 API**（走审批），作为 DataChannel 的第三实现（M4 预留，不承诺）。

### 4.3 落表探测（反向赋值工具化）

编写工厂时最大的认知成本是"这个 API 到底落了哪几张表、我该 UPDATE 哪里"。M3 提供探测工具：

1. 选定环境 + 关注的 schemaCode 列表 → 平台经 DataChannel（猎户 query）对各表做行数/自增值快照；
2. 执行一次目标 API（或一次 build）；
3. 再快照，对比输出"疑似被写入的表 + 新增行主键"，辅助工程师补写 SQL 步骤。

这是把培训里"调完 API 反向看哪几张表有变更"的人工经验做成一键工具。表结构变更后工厂失效的排查（第六张表问题）同样用它。

---

## 5. 功能需求

### 5.1 环境与数据源管理

- 环境 CRUD：环境编码（如 `test-1`）、名称、描述。所有造数执行都在指定环境上进行。
- 模块端点：环境 × 模块（moduleCode 对齐契约平台）→ base URL、公共 Header（如测试网关鉴权头）。API 步骤执行时 `base URL + 契约 path` 拼装。
- 数据源：环境 × schemaCode → 数据库类型 + 通道选择（HUNTER/DIRECT，默认 HUNTER）。HUNTER 通道到此为止（凭证在猎户，登记即用）；DIRECT 通道额外填 JDBC URL、账号、密码（加密）。支持连通性测试按钮（HUNTER 通道跑一条 `SELECT 1` 走猎户验证 scope；DIRECT 通道测 JDBC 连接）。

### 5.2 工厂管理（核心）

- 工厂 CRUD：编码（`{moduleCode}.{实体名}`，如 `user-center.user`）、名称、**功能描述**（写给人也写给 LLM：造的是什么数据、什么场景用、默认产出什么状态）、负责人、启用/停用。
- 参数定义：名称、类型（string/int/long/boolean/date/datetime/enum）、默认值（支持模板函数）、说明、枚举取值。
- 步骤编排：有序列表增删改、上移下移；四类步骤（API_CALL/SQL_EXEC/REF/CHECK）各自的配置表单；条件表达式；输出变量提取（JSONPath）。
- 保存校验：REF 无循环引用且深度 ≤3；SQL 步骤 WHERE 校验；输出变量引用检查（后步引用了前步未定义的变量即报错）。
- 工厂试跑：编辑页内直接选环境试跑一次，查看完整轨迹（等价 build，标记 source=TRIAL）。
- 复制工厂（以既有工厂为模板改）。

### 5.3 套餐管理

- 套餐 CRUD：归属工厂、编码、名称、说明、参数值集合（JSON）。
- 套餐值在编辑时即时校验（参数名存在、类型匹配、枚举合法）。
- 调用时的参数优先级：**调用方显式参数 > 套餐 > 参数默认值**。

### 5.4 造数执行

- 管理端造数控制台：选工厂 + 环境 + 套餐（可选）+ 覆盖参数 → 同步执行 → 展示输出与逐步轨迹。
- 开放 API 同步执行（见 §6），超时上限可配（默认 60s，含嵌套）。
- 执行记录查询：按工厂/环境/状态/来源/时间筛选，进入详情看逐步轨迹（渲染后的报文与 SQL、响应、影响行数、输出变量、耗时）。
- 失败处理：任一步骤失败即终止并标记 FAILED，已执行步骤不回滚（跨 API+DB 无法分布式事务，如实呈现半成品轨迹供排查；清理靠 M4 的 teardown 机制或人工）。

### 5.5 数据池与预埋

- 池 CRUD：编码、名称、环境、关联工厂、领用模式（SHARED/EXCLUSIVE）、说明。
- 预埋任务：工厂 + 套餐 + 数量 → 异步执行，进度可视（成功/失败计数），产出自动入池（条目内容 = build 的输出 JSON）。
- 池监控：可用/租用/已消耗数量；EXCLUSIVE 池低水位提示（本期提示，自动补货 M4）。
- 领用/归还开放 API（见 §6）；管理端可手工失效某条目（INVALID，发现脏数据时踢出）。

### 5.6 检索（造数推理入口）

- 管理端：按模块、名称、描述筛选工厂。
- 开放 API：`factories/search?q=` 按名称 + 功能描述模糊检索，返回 topN（工厂编码 + 名称 + 描述 + 参数摘要）。本期 LIKE + 简单分词，预留语义检索升级位（与契约平台同策略）。

### 5.7 用户与权限

| 角色 | 权限 |
|---|---|
| ADMIN | 全部；含环境/数据源/凭证、token、用户管理 |
| EDITOR | 工厂/套餐/池编辑、试跑、预埋、控制台造数 |
| VIEWER | 只读（含执行记录） |

### 5.8 审计

所有写操作 + 每次 build 全轨迹入库。数据源凭证的查看/修改单独记审计。执行记录保留策略：默认保留 90 天，过期归档清理（SQLite 阶段直接删，切库后再谈归档）。

---

## 6. 开放 API 规范（TestData Service 交付物）

`/open-api/v1`，Header `X-Builder-Token`（与契约平台同款 token 机制）。这是对 LLM 流水线与各语言测试框架的正式交付面，格式一旦发布保持向后兼容（只增不改不删）。

### 6.1 工厂发现

```
GET /open-api/v1/factories/search?q=用户&topN=10
→ [ { "factoryCode": "user-center.user", "name": "用户",
      "description": "创建一个平台用户账号，默认 NORMAL 状态个人用户；可指定注册时长（造老用户）、用户类型…",
      "params": [ { "name": "createdDaysAgo", "type": "int", "default": "0", "description": "注册于几天前" }, ... ],
      "recipes": [ { "code": "old-vip-user", "name": "5年老VIP用户" } ] } ]

GET /open-api/v1/factories/{factoryCode}        # 单工厂完整参数 schema + 套餐列表
```

### 6.2 造数（on-the-fly）

```
POST /open-api/v1/builds
{
  "factoryCode": "user-center.user",
  "envCode": "test-1",
  "recipeCode": "old-vip-user",          // 可选
  "params": { "createdDaysAgo": 1825 }   // 可选，最高优先级
}
→
{
  "code": 0,
  "data": {
    "buildId": 1024,
    "status": "SUCCESS",
    "outputs": { "userId": 10001, "userName": "test_ab3kx9pq" },
    "durationMs": 843
  }
}
```

- 同步阻塞返回；失败时 `code != 0`，`data.buildId` 仍返回，凭它查轨迹排障。
- `GET /open-api/v1/builds/{buildId}`：执行轨迹（逐步的请求/SQL/响应摘要），供用例失败时归因"数据没造对 vs 系统 bug"。

### 6.3 数据池（out-of-box）

```
POST /open-api/v1/pools/{poolCode}/acquire
{ "count": 1 }                            // SHARED 池：直接返回数据，不锁定
                                          // EXCLUSIVE 池：返回数据 + leaseId，租期默认 30 分钟
→ { "code": 0, "data": { "items": [ { "itemId": 55, "leaseId": "L-9f2c", "data": { "userId": 10001 } } ] } }

POST /open-api/v1/pools/{poolCode}/release   { "leaseId": "L-9f2c" }   // 没用，归还
POST /open-api/v1/pools/{poolCode}/consume   { "leaseId": "L-9f2c" }   // 用了，标记消耗
```

### 6.4 设计要点

- 开放 API 与管理端写路径隔离；token 校验独立 Filter（SHA-256 查表），异步记 `last_used_at`。
- 造数是写操作，**幂等性不做承诺**（调两次造两条），流水线侧自行控制；防误用提供每 token 的 QPS 限流（简单令牌桶，默认 5/s）。

---

## 7. 总体架构与技术选型

单体应用。平台自身元数据（工厂/池/审计）在 SQLite；执行时动态连接各测试环境目标库。

### 7.1 技术选型

与 ep-api-contract 完全同构（Java 1.8 + Boot 2.1.14.RELEASE + Gradle Wrapper 5.6.4 + JPA + SQLite/Flyway + jjwt/BCrypt + springfox 2.9.2 + Lombok/Jackson；前端 Vue 3.4 + Vite 5 + Element Plus 2.x + Pinia + Vue Router 4 + Axios），差异与新增项：

| 项 | 选型 | 说明 |
|---|---|---|
| HTTP 客户端（API 步骤） | RestTemplate | Boot 2.1 内置；超时可配；不引 WebClient（避免 reactor 复杂度） |
| JSONPath 提取 | com.jayway.jsonpath:json-path 2.4.0 | 输出变量提取与断言；2.4.x 兼容 Java 8 |
| 模板渲染 | 自研 `${}` 替换 + 内置函数 | 几十行的事，不引模板引擎（避免表达式注入面扩大）；条件表达式用同一套解析器只支持 `变量 op 常量` 与 and/or |
| 目标库操作主通道 | HunterDataChannel（RestTemplate 调猎户开放接口） | 零凭证零驱动；接口规范见 [hunter-open-exec-api.md](hunter-open-exec-api.md) |
| 目标库操作兜底通道 | DirectJdbcDataChannel：Spring JdbcTemplate + 自管 `Map<dsId, HikariDataSource>`（每源 max 2，LRU 驱逐） | 不走 JPA——目标库是任意业务库，无实体可映射 |
| 目标库驱动（仅 DIRECT 通道） | mysql-connector-java 8.0.x（内置）；DmJdbcDriver18、ojdbc（手动入私服，按需加依赖） | 主通道走猎户后，驱动矩阵只服务兜底场景，按需最小化引入 |
| 凭证加密（仅 DIRECT 通道） | AES/GCM 自研工具类 | 密钥从环境变量 `EP_BUILDER_SECRET` 读取；Java 8 原生支持 GCM |
| 异步（预埋任务） | Spring `@Async` + 固定线程池（2） | 单实例内存队列即可，不引 MQ |

### 7.2 SQLite 与切库策略

全盘沿用契约平台 §5.2 的六条纪律：Flyway vendor 目录（`db/migration/{sqlite,mysql,dm}`）、实体只用跨库特性、仓储层禁 native SQL、切库动作收敛在配置层、SQLite 单写者（池=1 + WAL + busy_timeout）、并发上来即切库信号。

一个本项目特有的注意：**平台元数据库（SQLite）与目标业务库（JdbcTemplate 动态连）是两个世界**，Flyway/JPA 纪律只约束前者；后者本来就是多方言的，SQL 步骤里写什么方言由工厂作者对目标库负责。

---

## 8. 数据库设计

约定与契约平台一致：SQLite 版为事实基准（`db/migration/sqlite/V1__init.sql`）；字符串 `TEXT`、布尔/小整数 `INTEGER`、时间 `INTEGER`（epoch 毫秒）；除日志表外均含审计四列；`t_factory`、`t_pool` 含 `deleted` 逻辑删除列。

```sql
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
  db_type       TEXT NOT NULL,             -- MYSQL/DM/ORACLE/...（工厂作者写 SQL 时需知道方言）
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
  config       TEXT NOT NULL,              -- 类型专属配置JSON（见 §9.4）
  outputs      TEXT,                       -- 输出提取JSON [{var, expr}]
  created_at INTEGER, updated_at INTEGER, created_by TEXT, updated_by TEXT
);
CREATE INDEX idx_step_factory ON t_factory_step(factory_id);

-- 套餐
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

-- 数据池
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

-- 池内条目
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

-- 预埋任务
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
  status      TEXT NOT NULL,               -- SUCCESS/FAILED
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

-- 开放API令牌 / 用户 / 操作日志：与契约平台同结构
CREATE TABLE t_open_token (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  name         TEXT NOT NULL,
  token_hash   TEXT NOT NULL,
  enabled      INTEGER NOT NULL DEFAULT 1,
  qps_limit    INTEGER NOT NULL DEFAULT 5,
  last_used_at INTEGER,
  created_at INTEGER, updated_at INTEGER, created_by TEXT, updated_by TEXT
);
CREATE UNIQUE INDEX uk_token_hash ON t_open_token(token_hash);

CREATE TABLE t_user (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  username      TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  display_name  TEXT,
  role          TEXT NOT NULL DEFAULT 'EDITOR',
  enabled       INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER, updated_at INTEGER, created_by TEXT, updated_by TEXT
);
CREATE UNIQUE INDEX uk_username ON t_user(username);

CREATE TABLE t_operation_log (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  biz_type   TEXT NOT NULL,                -- ENV/DATASOURCE/FACTORY/RECIPE/POOL/TOKEN/USER
  biz_id     INTEGER NOT NULL,
  action     TEXT NOT NULL,
  detail     TEXT,
  operator   TEXT NOT NULL,
  created_at INTEGER
);
CREATE INDEX idx_log_biz ON t_operation_log(biz_type, biz_id);
```

说明：

- 步骤的类型专属配置放 `config` JSON 而不是拆列：四类步骤字段差异大，且步骤结构演进频繁；`config` 的 schema 由后端 DTO 严格校验，不是自由 JSON。
- `t_build` 高频写入。SQLite 阶段用单写连接自然串行；这是最先感知切库压力的表（见 §12）。
- 池领用的并发正确性：EXCLUSIVE 领用 = `UPDATE t_pool_item SET status='LEASED', lease_id=... WHERE id=(SELECT id FROM t_pool_item WHERE pool_id=? AND status='AVAILABLE' LIMIT 1) AND status='AVAILABLE'`，SQLite 单写者下天然原子；切 MySQL 后同语句配合行锁依然成立（JPQL 实现，不用 native——用"先查再带乐观条件更新，失败重试"的等价写法）。

---

## 9. 后端设计

### 9.1 工程结构

```
ep-data-builder-server/
├── build.gradle
├── settings.gradle
└── src/main/java/com/ep/databuilder/
    ├── DataBuilderApplication.java
    ├── config/            # CORS、Jackson、springfox、拦截器、SQLiteDialect、@Async线程池
    ├── common/            # Result<T>、业务异常、分页、枚举、AES 工具、模板引擎
    ├── security/          # JWT Filter、OpenApiTokenFilter（含QPS令牌桶）、@RequireRole
    ├── user/              # 用户与登录
    ├── env/               # 环境、模块端点、数据源（含连接测试、凭证加密）
    ├── factory/           # 工厂/参数/步骤/套餐 CRUD 与校验
    ├── engine/            # 执行引擎（见 9.3）：BuildContext、StepExecutor 族、TemplateRenderer
    │   ├── channel/       # DataChannel 接口 + HunterDataChannel（主）+ DirectJdbcDataChannel（兜底）
    │   └── datasource/    # 动态数据源管理器（Hikari 池缓存，仅 DIRECT 通道使用）
    ├── contract/          # 契约平台客户端（拉契约、缓存、失效刷新）
    ├── pool/              # 数据池、领用/租约、预埋任务
    ├── build/             # 执行记录与轨迹查询
    ├── open/              # 开放API controller
    └── log/               # 操作日志 AOP + 查询
src/main/resources/
    ├── application.yml
    └── db/migration/sqlite/V1__init.sql
```

### 9.2 管理端 REST API（`/api/v1`，JWT）

统一响应/分页约定与契约平台一致。

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | /auth/login、GET /auth/me | 登录/当前用户 | — |
| CRUD | /environments、/environments/{id}/endpoints、/environments/{id}/datasources | 环境/端点/数据源 | ADMIN |
| POST | /datasources/{id}/test-connection | 连接测试 | ADMIN |
| GET | /factories | 工厂列表（模块/关键词/状态筛选，分页） | VIEWER |
| POST/PUT/DELETE | /factories... | 工厂及参数/步骤全量保存（校验见 §5.2） | EDITOR |
| POST | /factories/{id}/copy | 复制工厂 | EDITOR |
| POST | /factories/{id}/trial | 试跑（body: envId/recipeId/params）→ 同步返回 build 结果 | EDITOR |
| CRUD | /factories/{id}/recipes | 套餐 | EDITOR |
| CRUD | /pools | 数据池 | EDITOR |
| POST | /pools/{id}/seed | 发起预埋任务（recipeId/count） | EDITOR |
| GET | /seed-tasks、POST /seed-tasks/{id}/stop | 预埋任务进度/停止 | EDITOR |
| PUT | /pool-items/{id}/invalidate | 手工失效脏数据条目 | EDITOR |
| POST | /builds | 控制台造数（同 trial，source=CONSOLE） | EDITOR |
| GET | /builds、/builds/{id} | 执行记录与轨迹 | VIEWER |
| POST | /probe/snapshot、/probe/diff | 落表探测：建快照/对比（M3） | EDITOR |
| CRUD | /tokens、/users、GET /logs | 系统管理 | ADMIN |

### 9.3 执行引擎

```
BuildService.execute(factory, env, recipe, callerParams, source)
  1. 参数合并：默认值 ← 套餐 ← 调用方（右侧覆盖左侧），渲染默认值中的模板函数
  2. 创建 BuildContext（变量池 = 合并后参数）
  3. 逐步执行：
       条件表达式判定 → 不满足记 SKIPPED
       StepExecutor.execute(step, ctx)      # 按类型分派
       输出提取（JSONPath）写回 ctx
       失败 → 终止，Build=FAILED
  4. 汇总输出（工厂声明的输出变量集）→ Build=SUCCESS
  全程逐步写 t_build_step_log（渲染后内容脱敏：Authorization/password 类字段打码）
```

四个 StepExecutor：

- **ApiCallExecutor**：`contract/` 客户端按 apiCode 取调用方式（本地缓存，TTL 10 分钟）→ 端点表拼 URL → 渲染 header/path/query/body 模板 → RestTemplate 执行（连接 5s/读 30s）→ 期望校验（httpStatus + JSONPath 断言）。
- **SqlExecExecutor**：渲染 SQL（产物 = 带 `?` 占位的语句 + 绑定参数数组，模板只决定"绑哪个值"，不做字符串拼接，杜绝注入）→ 按数据源配置取 DataChannel 执行（HUNTER：调猎户 execute，透传 traceId，回记 auditId；DIRECT：JdbcTemplate）→ 影响行数校验。
- **RefExecutor**：按策略（POOL_FIRST/ALWAYS_BUILD/POOL_ONLY）领池或递归调 BuildService（source=REF，parent_build_id 关联；深度计数器防线，>3 抛错）。
- **CheckExecutor**：走 DataChannel.query 执行 SELECT → 取 `rows[0][0]` 做断言（`count == 0` 等）。

**模板引擎**（common 包，自研）：`${varName}` 变量替换 + `${fn(args)}` 内置函数（now/daysAgo/hoursAgo/randomStr/randomInt/uuid/seq）。SQL 场景下 `${}` 输出的是绑定参数占位，非文本拼接。条件表达式仅支持 `var op 常量`（op: == != > < >= <=）与 and/or，超出即报错——刻意保持弱表达力，复杂逻辑应拆步骤或拆工厂。

### 9.4 步骤 config JSON 结构（后端 DTO 校验）

```jsonc
// API_CALL
{ "apiCode": "user-center.createUser",
  "overrideUrl": null,                       // 脱契约兜底：method+path 手工指定
  "bodyTemplate": "{ \"userAccount\": { \"userName\": \"${userName}\" } }",
  "headerTemplate": {}, "queryTemplate": {},
  "expect": { "httpStatus": 200, "asserts": [ { "expr": "$.code", "op": "==", "value": "0" } ] } }

// SQL_EXEC
{ "schemaCode": "mysql.TMy27MAIN-user_center",
  "sql": "UPDATE t_user SET created_time = ${daysAgo(createdDaysAgo)} WHERE id = ${userId}",
  "expectAffectedRows": 1 }

// REF
{ "factoryCode": "org-service.org", "strategy": "POOL_FIRST", "poolCode": "static-orgs",
  "paramMapping": { "orgType": "ENTERPRISE" } }

// CHECK
{ "schemaCode": "mysql.TMy27MAIN-user_center",
  "sql": "SELECT count(*) FROM t_user WHERE user_name = ${userName}",
  "assert": { "op": "==", "value": 0 } }
```

### 9.5 关键实现注意（Java 1.8 / Boot 2.1 / SQLite）

契约平台 §7.5 的注意事项全部适用（javax.* 命名空间、不用 JPA 关联映射、自带 SQLiteDialect、Wrapper 5.6.4 + JDK 8 构建）。本项目额外：

- HunterDataChannel 客户端：独立 RestTemplate 实例（连接 3s/读 35s，比猎户侧 30s 执行超时略长）；猎户返回的错误码原样透传进步骤轨迹（SQL_REJECTED/SCOPE_DENIED 等直接指向配置问题，报错信息要能自解释）；猎户不可达时 build 失败并明示，不自动降级 DIRECT（静默降级会掩盖通道问题，切换必须是显式配置动作）。
- 动态数据源的 Hikari 池（仅 DIRECT 通道）：`maximumPoolSize=2, minimumIdle=0, idleTimeout=60s`；管理器持有 `ConcurrentHashMap<Long dsId, HikariDataSource>`，数据源配置更新/删除时 `evict + close`。JVM 关闭钩子统一 close。
- 预埋任务线程池独立于 Web 线程（`@Async("seedExecutor")`，2 线程），任务内逐条 build，单条失败计数不中断，可 stop（volatile 标志位）。
- 契约客户端对契约平台不可用要有降级：缓存未过期继续用；缓存无 + 拉取失败 → build 失败并明示"契约平台不可达"，不猜。
- `build.gradle` 在契约平台版本基础上增加：`com.jayway.jsonpath:json-path:2.4.0`、`mysql:mysql-connector-java:8.0.28`（达梦/Oracle 驱动按需手动加）。

---

## 10. 前端设计

### 10.1 工程结构

```
ep-data-builder-web/
├── vite.config.ts            # dev 代理 /api、/open-api → 后端
└── src/
    ├── api/  router/  stores/          # 同契约平台骨架
    ├── views/
    │   ├── login/
    │   ├── env/               # 环境列表 + 端点/数据源二级页（含连接测试）
    │   ├── factory/
    │   │   ├── FactoryList.vue
    │   │   ├── FactoryEditor.vue     # 核心页面
    │   │   └── RecipeManage.vue
    │   ├── pool/              # PoolList / PoolDetail（条目状态、预埋任务进度）
    │   ├── build/             # BuildList / BuildDetail（轨迹时间线）
    │   ├── probe/             # 落表探测（M3）
    │   └── system/            # 用户、token、操作日志
    └── components/
        ├── StepListEditor.vue        # 步骤编排（核心组件：有序卡片列表+类型化表单）
        ├── ParamDefEditor.vue        # 参数定义表格
        ├── ParamFillForm.vue         # 按参数schema动态渲染的填参表单（控制台/试跑/预埋共用）
        ├── BuildTraceView.vue        # 执行轨迹时间线（el-timeline，逐步展开报文/SQL）
        └── StatusTag.vue
```

### 10.2 核心页面交互

**工厂编辑器 FactoryEditor.vue**

- 顶部：工厂名 / code / 启用开关 / 操作（保存、试跑、复制）。
- 主体 el-tabs：`基本信息 | 参数定义 | 步骤编排 | 套餐`。
- 步骤编排 = StepListEditor：垂直卡片列表，每张卡片头部是「序号 + 类型徽标 + 步骤名 + 条件表达式摘要」，展开后是该类型的配置表单；卡片可上下移、复制、删除；API_CALL 卡片内 apiCode 用 el-select 远程搜索契约平台接口，选中后展示契约摘要（method/path/必填参数），bodyTemplate 旁列出可用变量（参数 + 前序步骤输出）供点击插入。
- 试跑抽屉：选环境 + 套餐 + ParamFillForm 覆盖参数 → 执行 → 内嵌 BuildTraceView 展示结果。

**造数控制台（BuildList 页顶部快捷入口）**：选工厂 → 选环境/套餐 → 填参 → 执行 → 轨迹。给日常"手要一条数据"的工程师最短路径。

**池详情 PoolDetail.vue**：条目表格（状态筛选、手工失效）、水位卡片（可用/租用/已消耗）、预埋任务发起与进度条、领用示例代码片段（curl）展示。

**执行轨迹 BuildTraceView**：el-timeline 逐步渲染，成功绿/失败红/跳过灰；每步可展开看渲染后的请求与响应（JSON 高亮）；REF 步骤内嵌子 build 链接。

### 10.3 信息质量引导

工厂 `description` 决定 LLM 造数推理的检索命中率，编辑器同样做三件事：

1. description 保存时非空校验 + 引导 placeholder：「写清楚：造的是什么数据、默认产出什么状态、可以定制哪些关键维度。例：创建平台用户，默认当天注册的 NORMAL 个人用户；可指定注册时长（createdDaysAgo）造老用户」。
2. 参数 description 缺失时保存告警（可强行保存，列表页标黄）。
3. 纯 SQL 工厂（无 API_CALL 步骤）保存时强提醒表结构漂移风险，列表页常驻"纯SQL"标签。

---

## 11. 开发计划

| 里程碑 | 内容 | 交付标准 | 预估 |
|---|---|---|---|
| M1 基础闭环 | 工程骨架（前后端）、Flyway、登录/JWT、环境/端点/数据源管理、DataChannel 接口 + DirectJdbcDataChannel（HUNTER 通道接口先立、实现随 M2）、工厂（参数 + API_CALL/SQL_EXEC 步骤）、模板引擎与内置函数、契约平台客户端、控制台造数 + 轨迹、开放 API `builds` + token | 「5 年前注册的老用户」一键造出：createUser API + UPDATE 时间戳两步工厂跑通，流水线可经开放 API 调用拿到 userId | 1.5 周 |
| 猎户侧变更（**并行轨道**，猎户仓排期） | 中控开放接口 3 端点 + ServiceToken + SqlGuard + 审计、看门狗 EXECUTE/EXECUTE_BATCH 指令、测试管理端 token/审计页（详见 [hunter-open-exec-api.md](hunter-open-exec-api.md)） | 冒烟清单 6 项全过（见变更方案 §8） | 6-7 人日 |
| M2 Builder 语义完整 | 套餐、REF 步骤（三策略中先做 ALWAYS_BUILD）、CHECK 步骤、条件表达式、工厂试跑/复制、工厂检索开放 API、执行记录查询完善、QPS 限流、**HunterDataChannel 客户端 + 与猎户联调** | 订单工厂（嵌套买家/商品）跑通；LLM 流水线可检索工厂并按参数 schema 组装调用；至少一个数据源经 HUNTER 通道完成真实造数 | 1 周 |
| M3 数据池与通道收口 | 数据池 SHARED/EXCLUSIVE + 租约/回收、预埋任务、REF 的 POOL_FIRST/POOL_ONLY、领用/归还/消耗开放 API、**默认通道切 HUNTER**（DIRECT 仅保留给自建库）、落表探测工具（走猎户查询） | out-of-box 全链路：预埋 100 个用户 → 用例领用；订单工厂前置数据从池取；常规数据源零凭证运行 | 1.5 周 |
| M4 演进（按需） | 池低水位自动补货、teardown/清理步骤与数据回收、生产写通道对接猎户标变（若立项）、压测级批量造数（依托 execute-batch 扩容）、语义检索、供 Java/Python 的薄封装 SDK | — | — |

联调建议：M1 结束即与 ep-test-platform 流水线联调（哪怕流水线还是脚本雏形），用 2~3 个真实接口跑"契约 → 生成用例 → setup 调造数"最小闭环；开放 API 的请求/响应格式在 M2 前冻结；猎户开放接口规范（变更方案 §3）在两边动工前冻结。

---

## 12. 风险与开放问题

1. **与猎户变更的排期耦合**：主通道依赖猎户侧 6-7 人日的变更。对策：接口规范先冻结、两边按契约并行；DataChannel 接口隔离，M1 用 DIRECT 通道先跑闭环，猎户就绪后 M2/M3 逐步切换，任何时点不阻塞。次生风险是开放接口上线后其他团队也想接入（这是好事但会带需求）：ServiceToken 天然多租户，按调用方发放即可，猎户侧按平台能力管理。
2. **DIRECT 兜底通道的治理纪律**：主通道走猎户后，直连仅剩自建库与应急场景，但凭证加密、全量审计、SQL 守护的纪律不因量小而放松；每次从 HUNTER 切 DIRECT 必须是显式配置动作并记操作日志（不做故障自动降级，避免掩盖通道问题）。
3. **工厂质量依赖契约质量**：契约缺失或 description 敷衍时，API_CALL 配置成本高、LLM 检索不准。对策：与契约平台的推广节奏绑定，按"即将被 LLM 生成用例的接口"优先补契约、建工厂，不追求全量。
4. **数据漂移与脏数据**：池内数据可能被其他人/用例改状态（死水变质）。对策：CHECK 步骤核验 + 手工失效入口 + EXCLUSIVE 租约；池水位与失效率做监控指标，失效率高的池说明该数据根本不是死水，应改成 on-the-fly。
5. **跨 API+DB 无事务**：build 失败留下半成品数据。本期如实暴露轨迹，靠 M4 teardown 机制清理；测试环境对垃圾数据容忍度高，不为此上分布式事务。
6. **SQLite 阶段性定位**：`t_build`/`t_build_step_log` 写入频繁，预埋任务时最明显。单实例 + WAL 足够支撑试点；开放 API 调用量上来（流水线全量接入）即切 MySQL/达梦，动作按契约平台 §5.2 清单执行。
7. **模板函数的随机性与可复现**：randomStr 造出的数据排障时难对应。对策：轨迹里记录渲染后的最终值（已设计），params_json 存合并后参数，任何一次 build 可完整复现输入。
