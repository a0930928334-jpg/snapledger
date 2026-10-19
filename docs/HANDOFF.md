# 交接：给下一个会话（2026-10-19 更新）

> 写给接手的新 Claude 会话。`CLAUDE.md` 会自动加载，里面的规则始终有效。这份文件说明**现在做到哪了、怎么构建、下一步做什么**。
> 用户的每周用量比较紧：**回答要简短**，先读这份文件，不要把 `docs/` 全部重读一遍，需要哪份再读哪份。

## 1. 用户偏好（重要）

- 全程中文，简短直接；给明确推荐，不要列一长串选项。
- 用户已授权：可以直接提交并推送到 GitHub（`a0930928334-jpg/snapledger`），不用每次都问。
- **提交信息里不要加任何 Claude 署名**（不要 `Co-Authored-By`、不要 "Generated with Claude Code"）。作者用本机 git 配置。
- 目标是"尽快用上"：已经从 M0–M6 改成快速路线，见 `docs/06-milestones.md` 开头的"快速路线"。
- 每个阶段做完后逐条验证，汇报，等用户确认再继续（CLAUDE.md 第 8 条）。

## 2. 当前进度

| 阶段 | 状态 |
|---|---|
| M0 仓库骨架 | ✓ CI、gitleaks（pre-commit 和 CI 都有）、ADR-001～003 |
| M0 提示词实测 | **未完成**：Claude 第 1 轮 ≈87%（错误全出在截图之间没有重叠），提示词已改进（见 `docs/04` 末尾"实测结果"）。等用户按新规则重新截图，在 Claude 和 ChatGPT 上各跑一遍 |
| 阶段 A：Windows 可用 | ✓ 导入、预览（去重、疑似重复、对账、分类）、流水、本月概览、导入历史和撤销 |
| 阶段 B：安卓 + 同步 | ✓ 安卓版、分享菜单收文本、加密备份导出导入合并（ADR-012），模拟器上双向验证过 |
| 界面设计 | ✓ `DESIGN.md`（Wise 结构 + 靛蓝，浅色和深色），实现在 `ui/.../theme/Theme.kt`（ADR-013） |
| 内置 AI 提示词 | ✓ 设置页和导入页可以一键复制（IMP-12），`AiPromptTest` 保证它和 `docs/04` 一字不差 |
| PDF 对账单走 AI | ✓ 同一份提示词也接受 PDF 对账单（ADR-014），样例 `example-4-statement.json` |
| 打包 | ✓ Windows exe/msi 安装包 + 免安装版；安卓 debug APK。当前版本 0.5.0 |

用户手上：桌面上有 `SnapLedger-0.5.0-安装包.exe` 和 `SnapLedger-0.5.0.apk`。**安装时不能选 D 盘**（Windows Installer 在 D 盘建 `Config.Msi` 会报错 5），用默认位置。

## 3. 下一步候选（等用户选，推荐顺序）

1. **M0 提示词实测第 2 轮**：用户把截图和两家 AI 的输出放到 `C:\dev\snapledger-private\prompt-test\batch-02\`，对账单 PDF 也在 `C:\dev\snapledger-private\`。以对账单为标准答案逐条比对，只汇报比例，**真实数值不写进仓库**。
2. **阶段 C（边用边补）**：手动记一笔、搜索、分类管理、"本人姓名"规则（自己转给自己 → TRANSFER）、图表和趋势、指纹解锁、中英双语。按用户实际使用中的反馈排优先级。
3. **发布流程**：打 tag 后用 GitHub Actions 自动生成 exe 和 apk，放到 Releases 页面。
4. **iPhone**：用户没有 Mac。两条路已经讨论过：KMP 的 iOS 版（GitHub macOS runner 编译，安装用 TestFlight $99/年，或者 Sideloadly 免费但 7 天要重签）；或者微信小程序（要另写一套界面，存储约 10MB）。建议排在阶段 C 之后。
5. v1.1：本地解析 PDF 对账单（ADR-011）。

## 4. 构建（这台 Windows 机器的坑）

- 必须先设置短路径的临时目录，否则 Gradle 报 "Unable to establish loopback connection"：

  ```powershell
  $env:TEMP="C:\dev\.tmp"; $env:TMP="C:\dev\.tmp"; $env:JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:\dev\.tmp"
  ```

  PowerShell 里运行 Gradle 要关掉沙箱（`dangerouslyDisableSandbox`），并用 `cmd /c "C:\dev\snapledger\gradlew.bat ..."`。改了 TEMP 以后要先执行一次 `gradlew --stop`。
- 常用命令：`build`（编译、测试、ktlint、detekt、Android lint，包括桌面 UI 测试）、`ktlintFormat`、`:desktopApp:run`、`:desktopApp:packageExe`、`:androidApp:assembleDebug`。
- 桌面 UI 测试会把各页面截图写到 `desktopApp/build/screenshots/`（含 `theme-light-*`、`theme-dark-*`），改界面后看这些图就能检查。
- 安卓：SDK 在 `E:\Android\Sdk`，模拟器 AVD 叫 `snapledger_api36`。用 adb 发分享请求要先把脚本推到设备上执行（PowerShell 会吞掉参数里的引号），见 git 历史里阶段 B 的做法。
- gitleaks 在 `%LOCALAPPDATA%\Microsoft\WinGet\Links`，Bash 里要先把这个目录加进 PATH，pre-commit 钩子才能找到它。
- `gh` 已登录，CI 状态用 `gh run list` / `gh run watch` 查看。

## 5. 代码地图

- `core`（纯 Kotlin）：`importer/`（解析、截图对齐、对账、导入流水线）、`category/`（分类表、内置规则、分类器）、`ledger/`（模型、仓库接口、统计、备份接口）、`prompt/AiPrompt.kt`。
- `data`：SQLDelight 的 `Ledger.sq`、`SqlLedgerRepository`、`backup/`（加密在 `jvmShared` 源码集里）。
- `ui`：`App.kt`（导航）、四个页面、`theme/Theme.kt`、`Components.kt`、`PromptCard.kt`。
- `desktopApp` / `androidApp`：入口、文件选择、分享菜单。
- 测试：core 25 个、data 10 个（含加密已知答案测试，期望值由 Node.js 生成）、desktop UI 3 个。

## 6. 真实数据（硬性规则，见 CLAUDE.md 第 1 条）

用户发过真实截图、AI 输出和汇丰对账单 PDF。这些只用来理解问题，**任何真实数值、姓名、账号都不能写进仓库**。私人文件只放在 `C:\dev\snapledger-private\`。
