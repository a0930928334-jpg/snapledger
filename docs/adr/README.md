# 架构决策记录（ADR）

每篇 ADR 记录一个重要决策：背景、决策、理由、后果。决策被推翻时不删旧文，新写一篇并把旧的标为"已被 ADR-xxx 取代"。

| 编号 | 标题 | 状态 |
|---|---|---|
| [001](001-kotlin-multiplatform.md) | 用 Kotlin Multiplatform + Compose Multiplatform | 已采纳 |
| [002](002-local-first-offline.md) | 本地优先，MVP 不联网 | 已采纳 |
| [003](003-ai-extraction-outside-app.md) | AI 识别放在 App 外，App 只认导入格式 v1 | 已采纳 |

模板：

```markdown
# ADR-NNN：标题

- 状态：提议 / 已采纳 / 已被 ADR-xxx 取代
- 日期：YYYY-MM-DD

## 背景
## 决策
## 理由
## 考虑过的其他方案
## 后果
```
