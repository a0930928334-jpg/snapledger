# ADR-001：用 Kotlin Multiplatform + Compose Multiplatform

- 状态：已采纳
- 日期：2026-10-18

## 背景

App 要同时跑在安卓手机和 Windows 电脑上，两边都会用来导入流水。导入流水线（解析、对齐、去重、对账、分类）是整个项目最复杂、最需要正确性的部分，两个平台上的行为必须完全一致。开发者熟悉 Java 和 Spring Boot，Kotlin 和 Compose 是新学的。

## 决策

- 用 **Kotlin Multiplatform**，目标平台是 Android 和 JVM 桌面。
- 界面用 **Compose Multiplatform + Material 3**，两个平台共用页面代码。
- 模块划分：`core`（纯 Kotlin 引擎）、`data`（SQLDelight）、`ui`（Compose）、`androidApp`、`desktopApp`。依赖方向 `ui → core ← data`。

## 理由

1. **引擎只写一次**：导入流水线放在 `core`，两个平台运行的是同一份代码、同一套测试，不会出现"安卓去重了、电脑没去重"的情况。
2. **和 Java 背景衔接**：Kotlin 跑在 JVM 上，可以直接用 `javax.crypto` 等标准库；概念上大多能和 Java 一一对应。
3. **求职价值**：Kotlin 是英国安卓岗位的主流语言，KMP 和 Compose 是当前的方向。
4. **界面也能共用**：Compose Multiplatform 的桌面端已经稳定，两边的页面代码可以基本共用，只有剪贴板、分享菜单、指纹解锁这类平台能力需要分别实现。

## 考虑过的其他方案

| 方案 | 不选的原因 |
|---|---|
| 安卓原生 + 单独的桌面应用（JavaFX 等） | 两套界面、两套逻辑，维护成本翻倍，行为容易不一致 |
| Flutter | 要学 Dart；核心引擎没法和以后的 Java/Spring Boot 后端共享 JVM 生态 |
| Electron / Web 技术 | Windows 端可以，但安卓端体验差；JS 处理金额要格外小心 |
| 只做安卓，电脑上用模拟器 | 不符合"两边都会用来导入"的需求 |

## 后果

- 正面：核心逻辑可以完全在 JVM 上做单元测试，速度快；以后加 iOS 只需要一台 Mac。
- 负面：Compose Multiplatform 的生态比安卓原生小，图表等组件可能要自己画（见 02 第 9 节）；Windows 安装包要自带 JVM 运行时，体积较大。
- 构建配置受 AGP 9 约束：KMP 模块的 Android 目标使用 `com.android.kotlin.multiplatform.library`，APK 只在 `androidApp` 中生成（版本细节见 02 第 1.1 节）。
