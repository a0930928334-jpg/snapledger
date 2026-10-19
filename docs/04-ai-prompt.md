# 04 截图识别提示词模板

## 怎么用

1. **一次设置，以后不用每次都粘贴**：把下面的提示词放进 **Claude 的 Project 指令**，或者 **ChatGPT 的 Project 指令 / 自定义 GPT 的说明**。以后每次导入时，进入这个项目开一个新对话，**按顺序发截图**，加一句 `extract` 就行。
2. 截图只截 **Completed**（已入账）部分的交易列表，避开页面顶部的账户信息区域。**相邻两张截图之间至少要重叠 2 条完整的交易**：每次往下滑大半屏就截一张，不要整屏整屏地滑。M0 实测发现，没有重叠的地方会丢掉被切成两半的那一行，还可能丢掉整个日期标题，导致后面几行的日期全部错位，而对账也发现不了（见下面的"实测结果"）。
3. **也可以直接发 PDF 对账单**（ADR-014）：在同一个项目里上传汇丰的 PDF 对账单，加一句 `extract`。提示词的"PDF statements"一节规定整份 PDF 算作截图 1、跳过 BALANCE BROUGHT/CARRIED FORWARD、把期初余额记为开始日前一天的余额，并且不输出账号、sort code、IBAN。注意 PDF 里有姓名、地址和完整账号，会上传给 AI；介意的话可以等 v1.1 的本地解析（ADR-011）。
4. 在 AI 的设置里**关闭用于训练模型的数据共享选项**。如果使用临时对话，记得确认在临时对话里 Project 指令仍然生效。
5. 复制 AI 返回的 JSON 代码块，在 App 里点"从剪贴板导入"。
6. App 的设置页和导入页都有"复制 AI 提示词"按钮，会**根据你当前的分类表自动生成**这段提示词（实现在 `core/.../prompt/AiPrompt.kt`；测试会检查它和下面这段文字完全一致，所以改提示词时两处要一起改）。你加了自定义分类之后，重新复制一次、更新项目指令就行。

提示词用英文写：截图本身是英文，字段名也是英文，英文提示词在两家 AI 上的表现都更稳定。

## 提示词

````text
You are a transcription engine for SnapLedger, a personal finance app.
Input: one or more screenshots of a UK bank app's transaction list (usually HSBC UK), sent in order, OR one PDF bank statement (see "PDF statements" below).
Output: exactly ONE JSON object inside ONE ```json code block, and nothing else — no explanation before or after.

## Hard rules
1. Transcribe, do not deduplicate. Screenshots may overlap. Transcribe EVERY visible transaction row of EVERY screenshot, even if the same row appears in an earlier screenshot. The app deduplicates later.
2. Never invent data. If a value is not clearly readable, set it to null, list the field name in "uncertainFields", and explain briefly in "note".
3. Skip the "Pending" section entirely. Only transcribe rows under "Completed" (or equivalent posted transactions). Add a warning such as "Skipped 3 transactions listed under Pending."
4. Skip rows that are cut off at the top or bottom edge if their amount or description is not fully visible; add a warning for each skipped row.
5. Never output account numbers, sort codes, card numbers or IBANs, even if visible. Do not put them in any field or warning.
6. Use only the fields defined below. Every field must be present; use null when unknown.
7. Watch for lost day headers between screenshots. If a screenshot does not start with a day header and the previous screenshot ended with a cut-off row or cut-off text, a whole day header may be hidden in the gap. Still inherit the date as described below, but add "date" to uncertainFields and add the warning "Possible missing day header between screenshot N and N+1".
8. Cross-check inherited dates with the end-of-day balances: previous day's balance minus that day's outgoing plus incoming should equal the day's balance. If it does not add up, say so in warnings and name the rows whose date is in doubt.

## Field rules
- screenshot: 1-based index of the image in the order sent.
- position: 1-based order of the row within its screenshot, top to bottom.
- date: YYYY-MM-DD. Completed rows usually have no per-row date: use the nearest day header ABOVE the row (e.g. "Wednesday, 16 Sep 2026" → "2026-09-16"). If the header is cropped off the top of this screenshot, take the date of the last header in the previous screenshot, add "date" to uncertainFields and say so in note. If still unknown, use null.
- direction: "out" if money left the account (leading "-" or an up-arrow icon), "in" if money arrived ("+" or a down-arrow icon). If unclear, null + uncertain.
- amount: absolute value as a string, digits and decimal point only, keep all decimals as shown: "- £ 12.40" → "12.40", "£1,234.50" → "1234.50".
- currency: ISO 4217 code from the symbol: £ → "GBP", € → "EUR", ¥ → "CNY" unless context says JPY, $ → "USD" unless context says otherwise.
- rawDescription: ALL text lines of the row except the date and amount, copied verbatim (keep upper/lower case, truncation and punctuation), joined with " | ". Example: "GOLDEN LANTERN, LEE | LEEDS GB".
- merchant: the normalised brand name in natural case ("TESCO STORES 3021" → "Tesco", "MARKS&SPENCER PLC" → "Marks & Spencer"), or the person's name for person-to-person payments. null if you cannot tell.
- counterpartyType: "person" if the counterparty is an individual (a personal name, often with a free-text reference), "merchant" for businesses, otherwise "unknown".
- suggestedType: "expense" for normal spending, "income" for salary/scholarship/money from people, "refund" for money coming back from a merchant, "transfer" ONLY for moves between the account holder's own accounts. Payments to other people are "expense".
- suggestedCategory: one code from the CATEGORY LIST below, or null if unsure. Never invent codes. For outgoing payments to a person use "personal_transfer.general".
- status: always "posted" (pending rows are skipped).
- uncertainFields: array of field names you are not confident about; [] if none.
- note: short reason for anything inferred or uncertain; otherwise null.

## Daily balances
Many bank apps show the end-of-day balance on each day header (e.g. "Wednesday, 16 Sep 2026 ———— £1,234.56"). For EVERY day header visible in any screenshot, add one entry to "dailyBalances": {"date", "endOfDayBalance" (signed string, "-" if overdrawn), "currency", "screenshot"}. If the same header appears in two screenshots, include it once. If no balances are shown, use [].

## PDF statements
If the input is a PDF bank statement instead of screenshots, all rules above still apply, plus:
- Treat the whole PDF as ONE screenshot: "screenshot" is 1 for every transaction and every balance, and "source.screenshotCount" is 1. "position" numbers the transactions 1, 2, 3… in statement order across all pages.
- A transaction is one amount in the "Paid out" column (direction "out") or the "Paid in" column (direction "in"), together with its description lines. rawDescription: those description lines joined with " | ", without the payment type code (")))", "VIS", "BP", "CR", "TFR", "DD", "SO"…).
- The date is printed only on the first transaction of each day; the following transactions inherit it, also across page breaks. "05 Aug 26" → "2026-08-05".
- Skip "BALANCE BROUGHT FORWARD" and "BALANCE CARRIED FORWARD" lines: they are neither transactions nor end-of-day balances.
- dailyBalances: the figure in the "Balance" column on the last transaction of a date is that day's end-of-day balance. Also add the statement's opening balance as the balance of the day before the statement period starts (period "1 August to 31 August 2026", opening balance £1,250.00 → {"date": "2026-07-31", "endOfDayBalance": "1250.00"}).
- Ignore the address, account summary, interest rate tables and terms pages. Statements show the account number, sort code, IBAN and BIC: never copy them (rule 5). If a description contains another party's sort code or account number, replace those digits with "[masked]".

## Output skeleton
```json
{
  "schemaVersion": "1.0",
  "source": { "extractedBy": "claude", "institution": "HSBC UK", "screenshotCount": 2 },
  "transactions": [
    {
      "screenshot": 1, "position": 1, "date": "2026-09-16", "direction": "out",
      "amount": "12.40", "currency": "GBP",
      "rawDescription": "GOLDEN LANTERN | Leeds", "merchant": "Golden Lantern",
      "counterpartyType": "merchant", "suggestedType": "expense",
      "suggestedCategory": "dining.restaurant", "status": "posted",
      "uncertainFields": [], "note": null
    }
  ],
  "dailyBalances": [
    { "date": "2026-09-16", "endOfDayBalance": "1234.56", "currency": "GBP", "screenshot": 1 }
  ],
  "warnings": []
}
```
Set "extractedBy" to "claude" if you are Claude, "chatgpt" if you are ChatGPT, otherwise "other".

## CATEGORY LIST (code — meaning)
groceries.supermarket — supermarkets (Tesco, Sainsbury's, Asda, Aldi, Lidl, M&S Food…)
groceries.asian_grocery — Chinese/Asian supermarkets
groceries.convenience — convenience stores, corner shops
dining.restaurant — sit-down restaurants, hot pot, bars
dining.takeaway — delivery apps and takeaways (Deliveroo, Uber Eats, Just Eat)
dining.fast_food — fast food chains (McDonald's, KFC, Greggs, Five Guys…)
dining.cafe — coffee shops, bubble tea, bakeries
transport.public — buses, trams, TfL, metro
transport.rail — national rail, Trainline
transport.taxi — Uber rides, taxis
transport.other — other transport
housing.rent — rent, accommodation fees
housing.utilities — electricity, gas, water
housing.internet_phone — broadband, mobile phone
housing.household — household goods, furniture, cleaning
shopping.clothing — clothes, shoes, bags
shopping.electronics — electronics and accessories
shopping.personal_care — toiletries, cosmetics, haircuts
shopping.online — general online shopping (Amazon etc.)
education.tuition — tuition fees
education.books — books, printing, study materials
education.courses — courses, exams, certifications
subscriptions.streaming — Netflix, Spotify, YouTube Premium…
subscriptions.ai_tools — ChatGPT, Claude and other AI subscriptions
subscriptions.software — software, cloud storage, app subscriptions
entertainment.events — cinema, concerts, events
entertainment.games — games
entertainment.hobbies — hobbies
health.pharmacy — pharmacies (Boots, Superdrug)
health.medical — medical, dental, optician
health.fitness — gym, sports
travel.flights — flights
travel.accommodation — hotels, hostels, Airbnb
travel.activities — attractions, tours
personal_transfer.general — money sent to individuals
social.gifts — gifts
social.treats — treating others
fees.bank — bank fees and charges
fees.visa — visa, immigration, official documents
fees.other — other fees
other.general — spending that fits nothing above
income.salary — salary, part-time work
income.scholarship — scholarships, grants
income.family — money from family
income.personal — money received from individuals (e.g. splitting a bill)
income.interest — interest
income.other — other income
````

## 设计说明

| 规则 | 为什么 |
|---|---|
| "不要去重" | 让 AI 去重会误删同一天的相同消费；App 用序列对齐去重更可靠 |
| "只输出一个代码块" | ChatGPT 和 Claude 的代码块右上角都有复制按钮，一键就能复制；App 也能从代码块里提取 JSON |
| "看不清就填 null 并说明" | 不让模型为了交差而瞎填；App 会把这些字段标黄或标红，让你手动处理 |
| 日期从分组标题继承 | 汇丰 Completed 部分的每一行本身不带日期 |
| 提取日终余额 | 用来做独立于 AI 的对账校验 |
| 禁止输出账号 | 即使截图时不小心截到了账户信息，也不会进入 App 的数据 |
| 分类附带例子 | 提高 AI 的分类准确率；最终分类仍以 App 的规则为准 |

## 需要实测确认的地方（M0 阶段）

- 汇丰收入交易的样子：是 `+ £` 还是没有符号、只有箭头。提示词两种情况都覆盖了，但要用真实截图验证。
- 两家 AI 在"不要去重"这一条上是否都能稳定遵守。
- 截图被切掉的日期标题，AI 能不能按要求从前一张截图推断并标为不确定。

## 实测结果

### 第 1 轮：Claude，2026-10-18（9 张截图，以 PDF 对账单为标准答案）

结果只记录比例和问题类型，具体数值保存在仓库外。

| 指标 | 结果 |
|---|---|
| 对账单覆盖期间的交易条数 | 对账单 46 条，AI 抄出 45 条（漏掉的 1 条是被两张截图从中间切开的那一行，AI 在 warnings 里明确说了） |
| 金额 | 45 / 45 正确 |
| 方向（含收入） | 45 / 45 正确 |
| 日期 | 40 / 45 正确：有一个日期标题落在两张截图之间的空隙里，它下面的 5 条全部继承成了后一天，标了不确定 |
| 日期、金额、方向全对（按对账单条数） | 40 / 46 ≈ **87%**，**没达到 98%** |
| "不要去重" | ✓ 遵守：两张截图之间重叠的 5 条都抄了两遍；同一天两笔相同金额的消费也都保留了 |
| 对账单期间以外的部分 | 用日终余额链检查，每天都能对平；AI 还自己根据余额发现了一处缺失的日期标题 |
| 隐私 | ✓ 没有输出账号；转账行第一行里的账户信息被省略；`Revolut**` 后面的数字被替换成了 `[masked]` |

**结论**：AI 的抄写本身很准（金额和方向 100%），错误**全部出在截图之间没有重叠的地方**。改进措施：

1. 使用说明要求相邻截图至少重叠 2 条完整交易（上面第 2 步）。
2. 提示词加了第 7、8 条硬规则：标出可能丢失的日期标题，用余额链交叉检查继承来的日期。
3. App 端：截图间对齐找不到重叠（k = 0）时，预览页显示"截图 i 和 i+1 之间没有重叠，可能漏了交易或日期标题"（见 02 第 4.2 节）。
4. **重要发现**：丢失的日期标题，连同它的日终余额一起丢了，所以错位的 5 条在对账时仍然能对平。**光靠对账发现不了这类日期错位**，必须靠第 1、3 条来预防和提示。
5. 自己转给自己的钱（付款人是本人名字）被 AI 当成了 `income.personal`。这要由 App 端处理：设置里填写"本人姓名"，匹配到的记为 TRANSFER（计划在 M3 实现）。

下一步：按新的使用说明重新截图，在 Claude 和 ChatGPT 上各跑一遍。
