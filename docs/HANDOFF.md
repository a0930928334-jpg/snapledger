# 交接：从需求阶段进入开发阶段

> 写给接手的新 Claude 会话。2026-10-18 用户已确认需求（"需求确认，可以开始"）。
> `CLAUDE.md` 会自动加载，里面的规则始终有效。这份文件只说明**接下来做什么**，以及**文档里没有、但需要知道的背景**。

## 1. 先读（按顺序）

1. `docs/README.md`：已确认决策的汇总
2. `docs/01-requirements.md`：功能清单（MVP 和后续版本）
3. `docs/02-architecture.md`：技术栈、模块划分、**导入流水线和去重、对账、分类算法**
4. `docs/03-data-format.md`，以及 `schemas/import-v1.schema.json`、`samples/*.json`
5. `docs/05-categories-and-rules.md`
6. `docs/06-milestones.md`：重点读 M0 和 M1
7. `docs/04-ai-prompt.md`：截图识别提示词。M0 的实测要用到

## 2. 当前任务：M0（仓库骨架与提示词验证）

范围和完成标准以 `docs/06-milestones.md` 中的 M0 为准。动手前：

1. **先列出 M0 的执行计划**，给用户确认后再开始。
2. **查证当前的稳定版本**：Kotlin、Compose Multiplatform、SQLDelight、AGP、Gradle、Koin、kotlinx 系列库、多平台版 lifecycle-viewmodel 和 navigation。不要凭记忆写版本号，也不要使用发布不到两周的版本。
3. 第一次提交之前，先放好 `.gitignore`。至少要排除：
   - `*.png`、`*.jpg`、`*.jpeg`、`*.heic`（`docs/` 下面用于 README 的虚构截图，以后用白名单单独放行）
   - `private/`、`*.slbackup`、`local.properties`、`.env*`、`*.jks`、`*.keystore`
   - 构建产物和 IDE 文件
4. 在 GitHub 上建公开仓库 `snapledger`（MIT 协议）、推送代码之前，**先问用户**。
5. 提示词实测需要用户提供真实截图：让用户把截图和 AI 的输出都放在 `C:\dev\snapledger-private\`（仓库外）。你只汇报准确率和需要改进的地方，**不要把任何真实值写进仓库里的文件**。

## 3. 文档里没有写全的背景

需求阶段，用户提供过汇丰英国 App 的真实截图（数值不在这里复述）。下面是从截图里观察到的**结构特征**，写解析逻辑、设计测试样例、调整提示词时会用到：

- 页面最上方是 **Pending** 区域，每行自带日期，格式是 `3 Sep 2026`。下面是 **Completed** 区域，按天分组。
- Completed 的分组标题格式是 `Wednesday, 2 Sep 2026 ———— £X,XXX.XX`，右侧是**当天的日终余额**。用户验算过一天的数据：前一天余额减去当天的交易，正好等于当天余额。
- Completed 里的每一行**不带日期**，日期取上方最近的分组标题。
- 每一行的格式：第一行是商户名（可能被截断，例如 `SOME CHICKEN SHOP, LEE`），第二行是地点（例如 `LEEDS GB`）或个人转账的附言；金额在右侧，支出的格式是 `- £ 12.40`。支出行左边有一个向上箭头的图标。收入行长什么样**还没见过**，要在 M0 实测时确认。
- 转给个人的款项，第一行是对方的姓名，第二行是附言。
- `Revolut**NNNN*` 这类描述是**付给别人的 Revolut 账户**，不是用户自己的账户，按普通支出处理。
- 同一家店当天的交易，Pending 和 Completed 两个区域里的描述格式可能不同。这正是 MVP 跳过 Pending 的原因。
- 用户用 iPhone 截图，在安卓手机和 Windows 电脑上都会导入；一般每两三天到一周导入一次。

## 4. 用户的偏好

- 倾向于接受理由充分的推荐。给出明确的推荐，不要列一长串选项让用户自己选。
- 期望渐进推进：每完成一个里程碑停下来汇报，等确认后再继续。
