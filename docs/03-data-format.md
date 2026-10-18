# 03 统一数据格式规范：导入格式 v1

- Schema 文件：[schemas/import-v1.schema.json](../schemas/import-v1.schema.json)（JSON Schema 2020-12）
- 示例：[samples/](../samples/)。3 份都是虚构数据，**都已用 Python `jsonschema` 校验通过**，余额也验算过（见第 5 节）。

## 1. 设计原则

1. **AI 只抄写，不判断。** 去重、对账、最终分类都由 App 完成。AI 给出的类型和分类只是"建议"。
2. **不去重，按截图逐条抄写。** 每条记录带上 `screenshot` 和 `position`，App 靠它们做截图间对齐。如果让 AI 自己去重，它会把同一天的两杯咖啡合并成一杯。
3. **所有字段都必须出现，不知道就填 `null`。** 结构固定，AI 的输出更稳定，解析也更简单。
4. **不确定就明说。** 用 `uncertainFields` 列出不确定的字段，用 `note` 说明原因。App 在预览页把这些字段标黄。
5. **金额用字符串。** 比如 `"14.80"`。如果用 JSON 数字，可能变成 `14.8`，或者出现浮点误差。App 再把它转换成整数最小单位（1480 便士）。
6. **不收集账户信息。** 格式里没有卡号、账号、sort code 的字段，Schema 也禁止额外字段（`additionalProperties: false`）。

## 2. 顶层结构

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `schemaVersion` | `"1.0"` | ✓ | 格式版本 |
| `source.extractedBy` | `chatgpt` / `claude` / `app` / `other` | ✓ | 由谁生成 |
| `source.institution` | string \| null | | 仅供参考，比如 `"HSBC UK"` |
| `source.screenshotCount` | integer ≥ 1 | ✓ | 这一批有几张截图 |
| `transactions` | array | ✓ | 交易记录，见下一节 |
| `dailyBalances` | array | ✓ | 每天的日终余额，没有就是空数组 |
| `warnings` | string[] | ✓ | 跳过了什么、推断了什么、哪里看不清 |

## 3. 交易记录（transactions[]）

| 字段 | 类型 | 说明 | 汇丰截图上对应什么 |
|---|---|---|---|
| `screenshot` | int ≥ 1 | 第几张截图，按发送顺序 | — |
| `position` | int ≥ 1 | 在这张截图里从上往下数第几条 | — |
| `date` | `YYYY-MM-DD` \| null | 交易日期 | Completed 部分取**上方最近的日期分组标题**；标题被切掉的话从前一张截图推断，并标为不确定 |
| `direction` | `out` / `in` / null | 钱流出还是流入 | `- £` 是 out；`+ £` 或收入标记是 in |
| `amount` | 十进制字符串 \| null | 金额的**绝对值**，不带符号和千分位 | `- £ 12.40` → `"12.40"` |
| `currency` | ISO 4217 \| null | 币种 | `£` → `GBP` |
| `rawDescription` | string | 这一行里除日期、金额以外的**全部文字，原样照抄**，多行用 `" \| "` 连接 | `GOLDEN LANTERN, LEE \| LEEDS GB` |
| `merchant` | string \| null | 规范化后的商户品牌名或人名 | `Golden Lantern` |
| `counterpartyType` | `merchant` / `person` / `unknown` | 收款或付款方是商户还是个人 | 人名或者带个人附言的通常是 person |
| `suggestedType` | `expense` / `income` / `refund` / `transfer` / null | AI 建议的交易类型 | 商户退回来的钱 → refund |
| `suggestedCategory` | 分类代码 \| null | 必须来自提示词里给的分类表，见 05 | — |
| `status` | `posted` / `pending` | 已入账 / 待入账（MVP 只应该出现 posted） | Completed / Pending 区域 |
| `uncertainFields` | 字段名数组 | AI 不确定的字段 | — |
| `note` | string \| null | 简短说明 | — |

## 4. 日终余额（dailyBalances[]）

| 字段 | 类型 | 说明 |
|---|---|---|
| `date` | `YYYY-MM-DD` | 日期分组标题上的日期 |
| `endOfDayBalance` | 带符号的十进制字符串 | 分组标题右侧的余额，透支时为负数，比如 `"-12.40"` |
| `currency` | ISO 4217 | |
| `screenshot` | int ≥ 1 | 出自第几张截图 |

## 5. 示例

| 文件 | 演示的场景 | App 预期的处理结果 |
|---|---|---|
| [example-1-basic.json](../samples/example-1-basic.json) | 一张截图、两天的交易，包括一笔个人转账 | 4 条全部新增；9/15 对账：1532.40 − 12.35 − 2.55 − 8.00 = **1509.50 ✓** |
| [example-2-overlap.json](../samples/example-2-overlap.json) | 两张截图重叠了 2 条；同一天有两笔相同的 £3.50 咖啡；第二张截图顶部的日期标题被切掉 | 截图间对齐找到 k = 2，合并后剩 4 条，**两杯咖啡都保留**；9/16 对账：842.10 − 3.50 − 3.50 − 9.80 = **825.30 ✓** |
| [example-3-special.json](../samples/example-3-special.json) | 退款、朋友转回来的 AA 钱、一笔金额看不清的交易、跳过了 Pending | 9/20 对账得到 640.00，实际是 636.80，**差 −3.20**；预览页提示"Costa Coffee 的金额很可能是 £3.20" |

下面是 example-1 的完整内容：

```json
{
  "schemaVersion": "1.0",
  "source": { "extractedBy": "claude", "institution": "HSBC UK", "screenshotCount": 1 },
  "transactions": [
    {
      "screenshot": 1, "position": 1, "date": "2026-09-15", "direction": "out",
      "amount": "12.35", "currency": "GBP",
      "rawDescription": "TESCO STORES 3021 | LEEDS GB", "merchant": "Tesco",
      "counterpartyType": "merchant", "suggestedType": "expense",
      "suggestedCategory": "groceries.supermarket", "status": "posted",
      "uncertainFields": [], "note": null
    },
    {
      "screenshot": 1, "position": 3, "date": "2026-09-15", "direction": "out",
      "amount": "8.00", "currency": "GBP",
      "rawDescription": "Alex Chen | dinner", "merchant": "Alex Chen",
      "counterpartyType": "person", "suggestedType": "expense",
      "suggestedCategory": "personal_transfer.general", "status": "posted",
      "uncertainFields": [], "note": null
    }
  ],
  "dailyBalances": [
    { "date": "2026-09-15", "endOfDayBalance": "1509.50", "currency": "GBP", "screenshot": 1 }
  ],
  "warnings": []
}
```

（为了篇幅，这里省略了第 2 和第 4 条记录，以及 9/14 的余额，完整内容见示例文件。）

## 6. App 端怎么解析（宽进严出）

- **宽进**：先从文本里提取 JSON：如果有 ` ```json ` 代码块就取代码块里的内容，否则取第一个 `{` 到最后一个 `}` 之间的内容。这样即使 AI 在 JSON 前后多说了几句话，也能正常导入。
- **严出**：提取出来的 JSON 必须严格符合 Schema。出错时报告具体位置，比如"第 3 条记录的 amount：`£12.40` 不是合法金额（不能带货币符号）"。
- 必填的业务值（日期、方向、金额、币种）是 `null` 时，这一行在预览页标红，**必须手动填上才能导入**。
- `status = pending` 的记录忽略，并在预览页提示。
- `suggestedCategory` 不在当前分类表里的，当作 `null` 处理。

## 7. 版本演进

- 只增加可选字段的改动，升小版本（`1.1`），App 向下兼容。
- 改字段含义或增加必填字段，升大版本（`2.0`），App 会同时支持旧版本至少一个大版本周期。
- 以后 App 内调用 LLM API 时，把这个 Schema 直接作为 structured output 的约束，输出格式和手动流程完全一样。
