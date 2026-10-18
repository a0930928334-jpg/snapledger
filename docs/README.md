# SnapLedger：需求与设计文档（待确认稿 v0.1）

> 日期：2026-10-18 状态：**待 Hongyi 确认**。确认前不写代码，也不建仓库。
> 仓库名暂定 `snapledger`，协议 MIT。名字随时可以改。

## 文档目录

| # | 文档 | 内容 |
|---|---|---|
| 1 | [01-requirements.md](01-requirements.md) | 需求文档：功能清单，区分 MVP 和后续版本 |
| 2 | [02-architecture.md](02-architecture.md) | 技术方案：技术栈、架构图、关键算法、安全设计、关键决策的理由 |
| 3 | [03-data-format.md](03-data-format.md) | 统一数据格式规范（导入格式 v1） |
|   | [schemas/import-v1.schema.json](../schemas/import-v1.schema.json) | JSON Schema 文件 |
|   | [samples/](../samples/) | 3 份虚构示例数据，已通过 Schema 校验 |
| 4 | [04-ai-prompt.md](04-ai-prompt.md) | 给 ChatGPT 或 Claude 用的截图识别提示词模板 |
| 5 | [05-categories-and-rules.md](05-categories-and-rules.md) | 默认分类表和自动分类规则设计 |
| 6 | [06-milestones.md](06-milestones.md) | 开发里程碑和完成标准 |

## 已确认的决策汇总

| 主题 | 决策 |
|---|---|
| 账户 | MVP 只有一个账户：汇丰英国借记卡（GBP）。数据模型保留账户字段 |
| 币种 | 每笔交易带币种字段，多币种分开统计，不做汇率换算（换算放到后续版本） |
| 记账频率 | 每两三天到一周导入一次，截图之间会有重叠 |
| 主要目标 | 看清钱花在哪、花了多少；预算、趋势、月度总额是次要目标 |
| AI 识别 | MVP 在 App 外完成（手动发截图给 ChatGPT 或 Claude）；后续版本加 App 内调用 LLM API，输出同一种格式 |
| 数据格式 | JSON，用 JSON Schema 定义 |
| 导入方式 | 粘贴文本、从剪贴板一键导入、安卓系统分享菜单 |
| 去重 | 截图之间按顺序对齐；和数据库已有记录按"每天、每个指纹"计数比较；完全匹配的自动跳过（预览页可撤销）；疑似重复标黄 |
| 校验 | 每次导入都先显示预览、可以修改；用日终余额按天对账 |
| Pending（待入账） | MVP 跳过不导入；以后做"待入账 → 已入账"的自动匹配 |
| 分类 | 两级分类，中英双语，可以自定义 |
| 自动分类 | 优先级：你的修正 > 内置关键词规则 > AI 建议 > 未分类 |
| 转账、退款 | 自己账户之间的转账不计入支出；转给个人的钱算支出，单独设"个人转账"分类；退款冲减原分类的支出 |
| 平台 | 安卓 + Windows（安卓和电脑都会用来导入） |
| 同步 | MVP 靠手动导出、导入加密备份；第二阶段做 Java + Spring Boot 同步服务 |
| 技术栈 | Kotlin Multiplatform + Compose Multiplatform，数据库用 SQLDelight（SQLite） |
| 安全 | App 解锁（安卓指纹或 PIN，Windows 可选 PIN）；备份文件用密码加密；MVP 的安卓 App 不申请联网权限 |
| 截图隐私 | 不遮挡；截图时避开账户信息区域；关闭 AI 的训练数据选项；提示词要求 AI 不复述账号 |
| 界面 | 中英双语，跟随系统语言；深色模式跟随系统；首页是"本月概览"，风格参考 Monzo |
| 工程 | 一个仓库放所有代码（monorepo）、GitHub Actions、ADR、gitleaks 密钥扫描、MIT 协议；示例数据全部虚构 |
