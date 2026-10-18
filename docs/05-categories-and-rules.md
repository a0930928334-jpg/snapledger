# 05 默认分类表与自动分类规则

## 1. 交易类型和分类是两件事

| 类型 | 计入什么 | 例子 |
|---|---|---|
| `EXPENSE` 支出 | 计入支出统计，归到某个支出分类 | 超市购物、给朋友转 AA 钱（归入"个人转账"） |
| `INCOME` 收入 | 计入收入统计，归到某个收入分类 | 奖学金、朋友转回来的钱 |
| `REFUND` 退款 | **从对应支出分类的支出里扣除**，不算收入 | M&S 退货退回来的 £25 → 当月"服饰鞋包"的支出减少 £25 |
| `TRANSFER` 转账 | **不计入收支**，没有分类 | 自己账户之间的资金往来（目前基本用不到，为以后的多账户预留） |

统计口径：**本月支出 = Σ 支出 − Σ 退款**。"个人转账"作为一个普通的支出分类，计入总支出，但在图表里单独显示。

## 2. 默认分类表

分类代码格式是 `一级.二级`，这个代码就是提示词里给 AI 用的代码。以后改中英文名称不会影响代码。

### 支出（14 个一级分类）

| 一级 | 二级（代码 — 中文 / English） |
|---|---|
| **groceries** 买菜超市 / Groceries | `supermarket` 超市 / Supermarket · `asian_grocery` 亚洲超市 / Asian Grocery · `convenience` 便利店 / Convenience |
| **dining** 餐饮 / Eating Out | `restaurant` 堂食 / Restaurant · `takeaway` 外卖 / Takeaway · `fast_food` 快餐 / Fast Food · `cafe` 咖啡奶茶 / Café & Drinks |
| **transport** 交通 / Transport | `public` 公交地铁 / Public Transport · `rail` 火车 / Rail · `taxi` 打车 / Taxi · `other` 其他交通 / Other |
| **housing** 住房 / Housing | `rent` 房租 / Rent · `utilities` 水电燃气 / Utilities · `internet_phone` 网络话费 / Internet & Phone · `household` 家居日用 / Household |
| **shopping** 购物 / Shopping | `clothing` 服饰鞋包 / Clothing · `electronics` 数码电子 / Electronics · `personal_care` 个护美妆 / Personal Care · `online` 网购其他 / Online & Other |
| **education** 学习 / Education | `tuition` 学费 / Tuition · `books` 书籍资料 / Books & Materials · `courses` 课程考试 / Courses & Exams |
| **subscriptions** 订阅 / Subscriptions | `streaming` 影音 / Streaming · `ai_tools` AI 工具 / AI Tools · `software` 软件云服务 / Software & Cloud |
| **entertainment** 娱乐 / Entertainment | `events` 演出电影 / Events & Cinema · `games` 游戏 / Games · `hobbies` 兴趣爱好 / Hobbies |
| **health** 健康 / Health | `pharmacy` 药店 / Pharmacy · `medical` 医疗 / Medical · `fitness` 运动健身 / Fitness |
| **travel** 旅行 / Travel | `flights` 机票 / Flights · `accommodation` 住宿 / Accommodation · `activities` 景点活动 / Activities |
| **personal_transfer** 个人转账 / Personal Transfers | `general` 个人转账 / Personal Transfers |
| **social** 人情 / Gifts & Social | `gifts` 礼物 / Gifts · `treats` 请客 / Treats |
| **fees** 费用 / Fees & Admin | `bank` 银行费用 / Bank Fees · `visa` 签证证件 / Visa & Documents · `other` 其他费用 / Other Fees |
| **other** 其他 / Other | `general` 其他支出 / Other |

### 收入（1 个一级分类）

| 一级 | 二级 |
|---|---|
| **income** 收入 / Income | `salary` 工资兼职 / Salary · `scholarship` 奖学金 / Scholarship · `family` 家庭汇款 / Family · `personal` 个人转入 / From Individuals · `interest` 利息 / Interest · `other` 其他收入 / Other |

另外还有一个系统分类 **"未分类 / Uncategorised"**：不能删除，在预览页和流水列表里会明显标出来。

### 自定义规则

- 可以增加、改名、改图标、排序、隐藏任意分类；默认分类不能删除，但可以隐藏。
- 新增的分类会自动生成代码，比如 `dining.hotpot`。重新"复制 AI 提示词"后，AI 就能用上新分类。
- 删除一个自定义分类时，要先选择把它下面的交易和规则**迁移到哪个分类**。

## 3. 自动分类规则

### 3.1 优先级

```
1. 你手动设置过的（source = MANUAL）    → 永不被自动覆盖
2. 你的规则（USER_RULE）                → 商户名或收款人完全匹配
3. 内置关键词规则（BUILTIN_RULE）        → 原始描述包含关键词
4. 个人转账的默认规则（BUILTIN_RULE）    → 收款对象是个人，且是支出
5. AI 建议（AI_SUGGESTION）              → 代码有效时采用
6. 未分类（NONE）
```

每笔交易都会记录分类来源，界面上用小标记显示（👤 你的规则 · 📘 内置规则 · ✨ AI 建议），你一眼就能看出它为什么被分到这一类。

### 3.2 你的规则：从你的修正中学习

触发条件：在预览页或编辑页，你修改了一笔交易的分类。

```
弹出提示："以后 [Golden Lantern] 都归为 [堂食]？"  ☑ 是（默认勾选）  ☐ 只改这一笔
如果勾选：
    match_field = merchant（商户名，App 规范化后大小写不敏感；没有商户名时用规范化后的原始描述）
    match_type  = EXACT
    同一个商户已经有规则的话，直接覆盖
    额外选项："同时应用到过去 N 笔同商户、来源不是 MANUAL 的交易"
```

收款人是个人时也一样，比如"以后转给 Alex Chen 的钱都归为 餐饮 · 堂食"，适合固定 AA 吃饭的朋友。

### 3.3 内置关键词规则（初版，约 60 条，以数据文件形式随 App 发布）

匹配方式：`normalize(rawDescription)` **包含**关键词。多条规则同时命中时，**关键词更长的优先**（所以 `UBER EATS` 会排在 `UBER` 前面）。

| 关键词 | 分类 |
|---|---|
| TESCO, SAINSBURY, ASDA, ALDI, LIDL, MORRISONS, CO OP, WAITROSE, ICELAND | groceries.supermarket |
| MARKS SPENCER, M S SIMPLY FOOD | groceries.supermarket ⚠️ 见下面的说明 |
| WING YIP, SEE WOO, HANG SANG | groceries.asian_grocery |
| DELIVEROO, UBER EATS, JUST EAT | dining.takeaway |
| MCDONALDS, KFC, GREGGS, BURGER KING, SUBWAY, NANDOS, FIVE GUYS | dining.fast_food |
| STARBUCKS, COSTA, PRET A MANGER, CAFFE NERO, GAIL S | dining.cafe |
| TFL, FIRST BUS, FIRST WEST YORKSHIRE, ARRIVA, STAGECOACH | transport.public |
| TRAINLINE, NORTHERN, LNER, CROSSCOUNTRY, AVANTI | transport.rail |
| UBER (不包括 UBER EATS), BOLT, FREENOW | transport.taxi |
| OCTOPUS ENERGY, BRITISH GAS, EDF, YORKSHIRE WATER | housing.utilities |
| VODAFONE, GIFFGAFF, VOXI, LEBARA, EE LIMITED, THREE, VIRGIN MEDIA | housing.internet_phone |
| IKEA, WILKO, B M, HOME BARGAINS, DUNELM | housing.household |
| PRIMARK, UNIQLO, ZARA, H M, JD SPORTS | shopping.clothing |
| CURRYS, ARGOS | shopping.electronics |
| AMAZON, AMZN, EBAY | shopping.online |
| NETFLIX, SPOTIFY, DISNEY PLUS, YOUTUBE PREMIUM | subscriptions.streaming |
| OPENAI, CHATGPT, ANTHROPIC, CLAUDE AI | subscriptions.ai_tools |
| APPLE COM BILL, GOOGLE ONE, MICROSOFT, ICLOUD | subscriptions.software |
| ODEON, CINEWORLD, VUE, TICKETMASTER | entertainment.events |
| STEAM, PLAYSTATION, NINTENDO | entertainment.games |
| BOOTS, SUPERDRUG | health.pharmacy |
| PUREGYM, THE GYM GROUP | health.fitness |
| RYANAIR, EASYJET, JET2, BRITISH AIRWAYS | travel.flights |
| AIRBNB, BOOKING COM, PREMIER INN, TRAVELODGE | travel.accommodation |
| UNIVERSITY OF LEEDS | education.tuition |
| UKVI, HOME OFFICE | fees.visa |
| ACCOUNT FEE, OVERDRAFT FEE, NON STERLING TRANSACTION FEE | fees.bank |

说明：
- **多义的商户**：M&S 既卖食品也卖衣服，默认归为超市，你改一次之后"你的规则"就会接管。Boots 也是同样的情况。
- **短关键词误伤**：`EE`、`THREE`、`VUE` 这类短词容易误匹配。所以这类规则要求**按完整单词匹配**（关键词两边必须是空格或字符串边界），并且都要有单元测试。
- 内置规则是一个 `builtin-rules.json` 数据文件，修改它不需要改代码。每次修改都要跑一遍分类准确率的回归测试（用虚构样本）。

### 3.4 个人转账的默认规则

```
counterpartyType == person 且 direction == out → personal_transfer.general
counterpartyType == person 且 direction == in  → income.personal
```

它排在内置规则之后、AI 建议之前。所以即使 AI 认为某笔转账是"餐饮"，没有你的规则时也会先归为"个人转账"，这符合你定下的口径。

### 3.5 退款的分类

1. 如果 AI 给出了 `suggestedType = refund`，或者方向是 in、对方是商户：
2. 在过去 90 天里找**同一商户的支出**。如果找到了，就用最近一笔的分类（来源记为 BUILTIN_RULE）。
3. 找不到的话，再按上面 3.1 的流程分类。
4. 在预览页里，退款行会显示"退款 → 冲减 [服饰鞋包]"，可以修改。

## 4. 分类准确率怎么衡量（作品集加分项）

- 用一组**虚构的、带标准答案的**描述样本（比如 200 条，覆盖上面所有关键词和容易混淆的情况），在 CI 里跑分类引擎，输出准确率和混淆最多的分类。
- 自己使用时，App 在本地统计"自动分类后被你修改的比例"。这个数据**只在本地显示**，可以用来判断规则还需要补充哪些。
