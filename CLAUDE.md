# SnapLedger：Claude 工作约定

个人记账 App：银行流水截图 → ChatGPT 或 Claude 转成 JSON（导入格式 v1）→ App 负责校验、截图间对齐、去重、日终余额对账、自动分类和统计。
技术栈：Kotlin Multiplatform + Compose Multiplatform（安卓 + Windows），数据库用 SQLDelight（SQLite）；第二阶段用 Java + Spring Boot 做同步服务。
需求和设计全部在 `docs/` 目录（从 `docs/README.md` 开始读）。**这些文档是唯一的需求来源。**

## 沟通

- 全程用**中文**和用户（Hongyi）沟通。代码、注释、提交信息、README 用英文。
- 用户熟悉 Java 和 Spring Boot，Kotlin 和 Compose 是新学的：第一次用到 Kotlin 或 Compose 的特有概念时，用一两句话解释，可以和 Java 做类比。
- 有多个可选方案时，给出推荐和理由，不要只罗列选项。
- 不确定的东西（库的最新版本、API 用法）先查证，不要凭记忆编造。

## 硬性规则

1. **真实银行数据永远不进仓库**，包括截图、流水、真实姓名、余额、账号、卡号。`samples/`、测试数据、README 里的截图一律使用虚构数据。用户在对话里发的真实截图只用来理解问题，**里面的任何值都不能写进文件**。真实数据只能放在仓库外的 `C:\dev\snapledger-private\`。
2. **API key 和其他密钥不能写进代码或仓库。** 以后需要时，安卓存进 Android Keystore，Windows 用凭据管理器。
3. `.gitignore` 必须在**第一次提交之前**就配好；gitleaks 要同时在 pre-commit 钩子和 CI 里运行。
4. 金额一律用**整数最小单位**（`Long`）加币种表示，不允许用 `Double` 或 `Float` 表示金额。
5. `core` 模块必须是纯 Kotlin，不依赖 UI、数据库或任何平台 API。
6. MVP 的安卓 App 不申请 `INTERNET` 权限。
7. 修改了设计，就要同步更新 `docs/`，重要的决策写成 ADR，放在 `docs/adr/`。修改导入格式时，Schema、提示词（`docs/04-ai-prompt.md`）、示例和测试四处都要同步改。
8. 按 `docs/06-milestones.md` 的里程碑顺序推进。每个里程碑的完成标准要**逐条验证**，再向用户汇报，等用户确认后才进入下一个。
9. 推送代码、在 GitHub 上建仓库、发布 Release 这类对外操作，每次都要先问用户。
