# TimeNote 开发进度记录

> 最后更新：2026-08-05（v1.0.0 正式版交付：移除演示功能 + FIX-15~17 修复）
> 配套文档：`PRD.md`（产品需求）、`docs/使用说明书.md`（用户手册+开发者附录）、`docs/技术栈说明.md`（技术选型+环境）、`docs/修复日志.md`（问题修复记录）

---

## ✅ 已完成

### 1. 产品需求定义（PRD.md）
- 三大核心功能已与用户对齐：
  - **F1 娱乐计时器**：手动勾选娱乐清单 → 悬浮计时 → 阈值弹窗（再玩5分钟/退出应用/关闭提醒）→ 退出引导页+10秒倒计时
  - **F2 定时提问**：自定义「时间→问题」配对 → 到点弹窗 → 错过进「待回答」补答
  - **F3 日记本**：自动汇编 + 手动编辑 + 日历翻阅 + 封面索引 + 左右翻页
- 关键决策：纯本地存储、零联网、仅手动导出

### 2. 技术选型与文档（docs/技术栈说明.md）
- 技术栈：Kotlin + Jetpack Compose + Room + WorkManager/AlarmManager + UsageStatsManager + AccessibilityService 兜底 + SYSTEM_ALERT_WINDOW
- 已写入「本机开发环境」一节

### 3. 环境体检（已确认可用）
| 组件 | 状态 | 位置 |
|---|---|---|
| JDK 17 | ✅ Temurin 17.0.20（含 javac） | `<JDK 安装路径>` |
| Android SDK | ✅ 完整（platforms 35/36） | `<Android SDK 路径>` |
| Gradle | ✅ 9.3.1，wrapper 已生成 | 项目自带 `gradlew` |

### 4. Task #1：初始化 Android 项目骨架 ✅（2026-08-03 完成）
- **Gradle Wrapper 9.3.1 已生成**（`gradlew` / `gradlew.bat` / `gradle/wrapper/`）
- 项目文件齐备：
  - `settings.gradle.kts` / `build.gradle.kts` / `gradle.properties` / `local.properties` / `.gitignore`
  - `app/build.gradle.kts`（Compose + Room 2.7.2 + WorkManager 2.10.5）
  - `AndroidManifest.xml`（已声明 F1.5 三类权限：悬浮窗 / 使用情况访问 / 通知）
  - `MainActivity.kt` + 主题 + 自适应启动图标（柔和绿时钟）
- **版本组合**：AGP 8.12.0 · Kotlin 2.1.20 · Compose BOM 2025.05.01 · compileSdk 35 / minSdk 26 / targetSdk 35
- **`gradlew assembleDebug` 一次通过**，产物 `app/build/outputs/apk/debug/app-debug.apk`（约 10.6 MB）
- 已知非阻塞提示：AGP 8.12 在 Gradle 9.3.1 下有 deprecated 警告（后续版本兼容 Gradle 10），不影响构建

### 5. Task #2：搭建本地数据库（Room）✅（2026-08-03 完成）
- **KSP 接入**：`com.google.devtools.ksp` 2.1.20-1.0.32（匹配 Kotlin 2.1.20），`room-compiler` 2.7.2
- **4 类实体**（`app/src/main/java/com/timenote/data/entity/`）：
  - `EntertainmentApp` 娱乐清单（包名唯一 + 显示名）
  - `QuestionPlan` 定时问题（分钟数 + 问题 + 星期 bitmask + 启用/暂停）
  - `AnswerRecord` 作答记录（问题快照 + 场景信息 + 原始/补答时间）
  - `Diary` 日记（日期唯一 + 天气 + Markdown 正文 + 封面预留字段）
- **4 个 DAO**（`data/dao/`）：增删改查 + Flow 观察，含日记汇编用的按日区间查询
- **数据库** `data/TimeNoteDatabase.kt`：单例 + schema 导出到 `app/schemas/`
- **`gradlew assembleDebug` 通过**；schema `1.json` 已生成，4 张表齐全

### 6. Task #3：实现娱乐计时器 ✅ 编码完成（2026-08-03）
> ⚠️ 仅完成编码与编译验证；**运行时行为（悬浮窗/前台检测）需真机验证**，见任务清单 #8
- **F1.1 娱乐清单**：`MonitorScreen` 列出可启动应用供勾选（含图标/搜索），持久化到 Room
- **F1.2 前台检测**：无障碍服务为主（实时）+ UsageStats 兜底（每 5 秒轮询）；`SessionTracker` 状态机管理会话
- **F1.2 悬浮计时器**：`FloatTimerService` 前台服务 + WindowManager 悬浮胶囊，显示 `已使用 mm:ss`，切出即隐藏重置
- **F1.3 阈值弹窗**：默认 30/60/90 分钟可配置（SharedPreferences）；三按钮：再玩5分钟（顺延5分钟）/ 退出应用 / 关闭提醒（跳下一阈值）
- **F1.4 退出引导**：全屏蒙层 + 10 秒倒计时 + 「我准备好了」提前收起
- **F1.5 权限引导**：`PermissionUtils` + 权限卡片（悬浮窗/使用情况/无障碍/通知）
- 新增文件：`monitor/`（4 个）+ `ui/entertainment/`（2 个）+ `util/`（2 个）+ 布局资源（4 个）
- `assembleDebug` 通过；Manifest 已含 FG 服务（specialUse）+ 无障碍服务 + `<queries>`

### 7. Task #4：实现定时提问 ✅ 编码完成（2026-08-03）
> ⚠️ 仅完成编码与编译验证；**到点弹窗/闹钟触发需真机验证**，见任务清单 #8
- **F2.1 计划管理**：`QuestionScreen` 列表 + 新增/编辑/删除/暂停开关；时间、问题文本、星期规则（每日/工作日/自定义周一周日 chips）
- **F2.2 到点弹窗**：`QuestionScheduler`（AlarmManager 精确闹钟，未授权时降级窗口闹钟）+ `QuestionAlarmReceiver` + `QuestionPromptActivity`（悬浮对话框，自动附带当前前台应用场景信息）
- **F2.3 补答**：闹钟先落一条空记录 → 弹窗没答则留在「待回答」列表；打开 App 补答并标注原始/补答时间（>5 分钟视为补答）
- 重复机制：闹钟触发后接收器为该计划重设下一次触发，weekDays bitmask 决定每日/每周
- 新增文件：`question/`（4 个）+ `ui/question/`（2 个）
- `assembleDebug` 通过

---

## 🔄 进行中

### Task #8：构建验证核心闭环（真机 vivo V2203A / Android 14）in_progress
**已完成验证**：
- **F1.2 悬浮计时**：进入娱乐应用出现 → 计时走动 → 切出消失 → 重进从 0 重置 ✅
- **F1.1 清单**：小红书/淘宝勾选持久化 ✅
- **F2.2 到点弹窗**：21:40 触发，弹窗含问题/时间/场景信息 ✅
- **F2.3 补答**：待回答 → 补答 → 入库（isSupplementary=1）✅
- **F3.1 汇编**：娱乐统计(UsageStats) + 回答 → Markdown ✅
- **F3.2 编辑**：✅（修复了保存按钮被挤出屏幕的 bug，见下）
- **F3.3 日历**：小圆点 + 详情 ✅

**真机发现并修复的问题**：
1. **定时提问延迟 ~50s** → 根因：Android 14 默认不授 `SCHEDULE_EXACT_ALARM`，走了 setWindow(10min) 降级
   - 修复：App 权限卡片新增「精确闹钟」引导；setExact 加 try/catch；授权后延迟降到 ~19s（剩余为 vivo Doze 节流）
2. **后台弹窗可能被 ROM 节流** → 新增到点通知兜底（点通知可回答）
3. **开机后闹钟丢失** → 新增 `BootReceiver` 开机重排（需 ROM 允许自启动）
4. **日记编辑保存按钮被挤出屏幕** → 根因：日记页整页不可滚动
   - 修复：日记页加 `verticalScroll` + `imePadding`，已验证保存入库
5. **【关键】前台检测失效**（计时器不显示）→ 根因：无障碍服务把**任何**窗口状态变化当成了前台，后台窗口（如 MainActivity）的事件会覆盖真实前台包名
   - 修复：改用 `getWindows()` 的**活动窗口**判断真实前台（`flagRetrieveInteractiveWindows` + `canRetrieveWindowContent`，仅取包名不读内容）
   - 验证：修复后 active-window 正确报告小红书，会话正常启动
6. **前台服务被 ROM 后台清理** → 修复：`onStartCommand` 返回 `START_STICKY`，被系统杀掉后自动重建
   - 验证：连续运行 2 分钟服务存活；另心跳循环加 try/catch 防单次异常中断

**F1.3/F1.4 真机验证**（阈值临时调为 1 分钟测试，验证后已无需保留）：
- 阈值弹窗：白色对话框「时间到 ⏰」+ 三个按钮 ✅
- 退出引导页：全屏「该走了 🌿」+ 10 秒倒计时自动收起 ✅

> ⚠️ 测试后将提醒阈值临时设为 1 分钟，**需在 App 内恢复为 30,60,90**（监督页→提醒阈值）。

---

## ✨ 体验优化（2026-08-03，6 项，编译通过待装机）

| # | 改动 | 涉及文件 |
|---|---|---|
| 1 | 提问时间改为**时/分下拉选择**（24h，默认 6:00），替换手输文本框 | `ui/question/QuestionScreen.kt` |
| 2 | 每周重复改为**两行**：周一~周三 / 周四~周日 | 同上 |
| 3 | 娱乐应用清单**按名称排序**（Collator 中文拼音序） | `ui/entertainment/MonitorViewModel.kt` |
| 4 | **提问修改同步日记**：AnswerRecord 增加 `questionPlanId`，计划问题变更时同步历史记录快照；Room **v1→v2 迁移**（加列+按文本回填） | `data/entity/AnswerRecord.kt`、`data/dao/AnswerRecordDao.kt`、`data/TimeNoteDatabase.kt`、`question/QuestionAlarmReceiver.kt`、`ui/question/QuestionViewModel.kt` |
| 5 | 日记月视图加**「今日」跳转按钮** | `ui/diary/DiaryScreen.kt` |
| 6 | 日记 Markdown **内联 `**粗体**` / `*斜体*` 渲染**，不再显示字面 `**` | `ui/diary/DiaryScreen.kt` |

> 注意 #4：修改问题后需**重新汇编当日日记**才能看到新问题文本（历史日记是已生成的快照）。
> Material3 1.3.2 时间下拉用 `MenuAnchorType`（`ExposedDropdownMenuAnchorType` 是 1.4 的命名）。

---

## 🔄 日记自动同步回答（2026-08-04，编译通过已装机待验证）

**背景**：日记是汇编时刻的快照，之后新增/补答的提问不会自动进已汇编日记（真机反馈）。

**改动**：
1. `Diary` 增加 `edited` 字段（用户手动编辑标记），Room **v2→v3** 迁移（`diaries` 加列，默认 0）
2. 新增 `diary/DiaryAutoSync.kt`：回答记录变化 / 点「重新汇编」时更新当天日记 ——
   **未手动编辑过**整篇重写；**手动编辑过**只替换「娱乐使用」「定时提问」两个数据小节，
   天气/小结/其他手动内容保留（真机反馈「重新汇编冲掉手动编辑」后改为合并策略）
3. 接入 5 个回答变更点：闹钟落记录（`QuestionAlarmReceiver`）/ 弹窗作答（`QuestionPromptViewModel`）/ 补答（`QuestionViewModel.answerPending`）/ 改题同步（`updatePlan` 先 `getByPlan` 取受影响日期）/ 删除记录（`dismissPending`）；`AnswerRecordDao` 新增 `getByPlan`
4. 日记页有日记时显示「重新汇编」按钮（同样走合并策略，不冲手动修改）；`saveEdit` 置 `edited=true`

**验证**（真机，2026-08-04 完成）：
- [x] 补答一条提问 → 当天已汇编日记自动出现该回答（未手动编辑过）
- [x] 手动编辑过当天的日记（改天气/小结）→ 再补答/点「重新汇编」→ 手动内容**保留**，仅提问回答小节更新
- [x] 改计划问题文本 → 受影响日期的已汇编日记同步新问题文本（手动内容保留）
- [x] 已有数据在 v3 迁移后完好（清单/计划/日记/回答记录不丢）

---

## 📓 封面索引 + 左右翻页（2026-08-04，编译通过已装机待验证）

**需求**（PRD F3.3 剩余部分）：每篇日记可设封面；像翻实体日记本一样左右翻页；封面总览墙。

**改动**：
1. 新建 `ui/diary/CoverOptions.kt`：天气 emoji（☀️⛅🌧⛈❄️🌈🌫️）+ 心情（😊开心/😄兴奋/🤩惊喜/😐平静/😢低落/😴疲惫，各配底色）；复用 `Diary.cover` 字段编码 `"☀️|😊"`（天气|心情），**零数据库迁移**
2. `DiaryViewModel`：新增 `pagerDates`（有日记日期升序，翻页页序）、`setCover(date, weather, mood)`、`assembleFor(date)`（翻页器内「重新汇编」对准当前页）
3. `DiaryScreen` 重构：
   - 顶部「**日历 | 封面**」FilterChip 切换
   - 日历模式：月份导航 + 月历 + 正文区（空状态/编辑表单/**HorizontalPager 左右翻页**）
   - 每页日记：封面条（天气大字 + 心情底色）→ 操作行（封面/重新汇编/编辑）→ 可展开封面选择器 → 正文
   - 封面模式：`LazyVerticalGrid` 封面墙（心情底色 + 天气 emoji + 日期），点某篇跳到该天

**验证**（真机，2026-08-04 完成）：
- [x] 左右滑动翻页：日记间平滑切换，日历高亮跟随
- [x] 某天日记点「封面」→ 选天气 emoji + 心情 → 页面顶部出现封面条（大 emoji + 底色）
- [x] 「封面」Tab → 看到封面墙，点某篇封面跳回该天
- [x] 重新汇编 / 编辑 / 自动同步回答仍正常（回归）
- [x] 封面选择器溢出裁切修复（FIX-09：整页可滚动，「清除封面」不再被挡）

---

## ✨ V1.1：当日小结增强 + 每周重复细化（2026-08-04，编译通过已装机待验证）

### ① 当日小结增强（`diary/DiaryAssembler.kt`）
- **娱乐高峰时段**：`peakUsageHour` 用 UsageEvents 按小时拆分当天娱乐时长（跨小时/跨天桶），找出使用最多的小时，文案如「晚上21点是娱乐高峰」；无使用/无事件自动省略
- **最常用应用 + 占比**：取使用最长应用，占比 ≥20% 才强调（「最常用的是小红书（约45%）」）
- **提问/补答统计**：完成条数 + 其中补答条数；没答则提示「还有 N 条提问没回答」
- 保留原有分档（<30 分 / <90 分 / ≥90 分）

### ② 每周重复细化（`data/entity/QuestionPlan.kt`、`question/QuestionScheduler.kt`、`ui/question/QuestionScreen.kt`）
- **周末预设**：每日 / 工作日 / 周末 三个一键预设
- **每隔 N 天**：新增 `intervalDays`（N≥2）+ `intervalAnchor`（基准日）字段，Room **v3→v4** 迁移；调度器 `nextOccurrenceMillis` 按「距基准日天数 % N == 0」计算下一次触发；列表显示「每3天（8月1日起）」
- 对话框增加「按星期 / 每隔N天」模式切换；N 用 2..30 下拉，基准日默认今天

**验证**（真机，2026-08-04 完成）：
- [x] 重新汇编当天日记 → 小结刷新（FIX-10：合并刷新纳入「小结」，含回答统计「完成了 N 条…其中 X 条补答」）
- [x] 新增计划 → 选「周末」预设，列表显示「周末」；选「每隔2天」，列表显示「每2天（8月4日起）」
- [x] 间隔计划到点触发一次
- [x] 已有数据 v4 迁移后完好（计划/回答/日记）

---

## 📤 V1.2：手动导出 Markdown / JSON（2026-08-04，编译通过已装机待验证）

**改动**：
1. `util/Exporter.kt`（新）：`buildMarkdown`（全部日记合并为一个 .md）、`buildJson`（清单/计划/回答/日记结构化备份，`org.json` 格式化缩进）、`writeToUri`（SAF 写文件）
2. `ui/export/ExportScreen.kt` + `ExportViewModel.kt`（新）：两个导出按钮，`CreateDocument` 系统文件选择器（SAF，**无需存储权限**，Android 26+ 通用）；导出后显示状态
3. `MainActivity` 底部新增第 4 个 Tab「导出」📤
4. `DiaryDao` / `AnswerRecordDao` 各加 `suspend getAll()`

**隐私**：只写用户选定的本地位置，零联网。

**验证**（真机，2026-08-04 完成）：
- [x] 底部「导出」Tab → 点「导出 Markdown」→ 系统选择器选位置 → 提示成功
- [x] 打开导出的 .md：含全部日记、`# TimeNote 日记导出` 头、每篇以 `---` 分隔
- [x] 点「导出 JSON」→ 选位置 → 打开：含 entertainmentApps / questionPlans / answerRecords / diaries 四组
- [x] 文件名带时间戳，默认建议 `TimeNote_日记_YYYYMMDD_HHmm.md`

---

## 📊 V1.2：统计周报/月报可视化（2026-08-04，编译通过已装机待验证）

**改动**：
1. `ui/stats/StatsViewModel.kt`（新）：`StatsPeriod`（周=7 天 / 月=30 天滚动到今日）、`StatsReport`；UsageStats 按天/按应用聚合娱乐时长 + 回答记录统计（总/答/补答/未答、回答率）
2. `ui/stats/StatsScreen.kt`（新）：周报/月报 FilterChip 切换；总览 stat tiles（总时长/日均/提问/回答率）；每日时长柱状图（`Canvas`，主题绿单色、今天深绿、细网格线、圆角柱）；应用排行横条（top5）；提问统计（按时答/补答/未答 + 回答率条）
3. `MainActivity` 底部新增第 5 个 Tab「统计」📊

**图表规范**（dataviz）：单色 sequential（不循环上色）、文本用墨色非系列色、网格线 recessive 细线、柱带圆角与间隙；调色板已用 `validate_palette.js` 对奶油 surface 校验（`#4C6F4C` 对比度达标）。

**验证**（真机，2026-08-04 完成）：
- [x] 底部「统计」Tab → 周报：总览四格 + 每日柱状图（今天深色柱）+ 应用排行 + 提问统计
- [x] 切「月报」→ 30 天柱状图、峰值标注
- [x] 娱乐应用勾选 + 使用情况授权后数据真实（与监督页记录一致）
- [x] 回答率条与待回答列表一致

**补充（演示数据 + 图表体验）**：
- 「生成演示数据」按钮（Prefs 标志，仅内存合成不写 Room）：多天图表预览，可一键清除
- 月报横轴两位日期被窄格截断 → 改为**画布内精确绘制**标签（按槽位居中，两位完整）
- 柱状图增加**纵轴时长刻度** + **点柱查看当天时长**（选中深色高亮 + 下方提示）
- 日记**封面点选 → 全屏翻阅**（直接打开该日日记，左右滑动看相邻天，不再落到日历）
- 日记「生成演示日记」按钮（正文带标记，一键清除）：用于验证左右翻页/封面墙

---

## ⏳ PRD 未决事项补全（2026-08-04，编译通过已装机待验证）

**① 「再玩5分钟」上限**（`monitor/SessionTracker.kt`、`monitor/FloatTimerService.kt`）
- 单次会话最多「再玩5分钟」**3 次**；达到上限后按钮禁用、文案「再玩5分钟（已用完）」
- `snooze()` 返回布尔；会话切换/离开娱乐应用时计数重置

**② 悬浮计时器拖动 + 记忆位置**（`monitor/FloatTimerService.kt`、`util/Prefs.kt`）
- 计时胶囊可**拖动**（去掉 NOT_TOUCHABLE，OnTouchListener 拖动 updateViewLayout）
- 位置持久化到 Prefs，下次显示在记忆位置；首次显示水平居中

**③ 封面默认建议**（`diary/DiaryAssembler.kt`、`diary/DiaryAutoSync.kt`、`ui/diary/DiaryViewModel.kt`）
- 汇编/自动同步时**未设封面**自动建议：正文「天气」文本 → 天气 emoji；回答情绪关键词 → 心情 emoji
- 已手动设置的封面不被覆盖

**验证**（真机，2026-08-04 完成）：
- [x] 阈值弹窗连续点「再玩5分钟」3 次 → 第 4 次禁用显示「已用完」
- [x] 悬浮计时胶囊可拖动，松手后切出重进仍停在记忆位置
- [x] 重新汇编今天日记 → 若未设封面，自动出现天气/心情封面；手动设过的封面不变
- [x] 封面情绪关键词加宽（好/不错/还行/可以 → 😊；累/困/没睡 → 😴；不好/糟糕/烦 → 😢）

**前台检测修复（FIX-11）**：
- 下拉通知栏/状态栏/输入法 = 系统窗口，不算前台（保持上次真实前台）
- **小窗/悬浮层不打断监督**：只有「基本铺满屏幕（≥85% 面积）」的应用窗口才算真前台
- 锁屏显式重置会话（`ACTION_SCREEN_OFF` 接收器），保证「锁屏后计时停止」仍生效

---

## 🎨 线条手绘治愈风主题（2026-08-04，已真机验证）

**配色**（用户选定：鼠尾草绿 + 奶油纸 + 暖墨棕）：
- 背景/卡片：奶油纸 `#F6F1E5`；文字：暖墨棕 `#4A4438`；主色：鼠尾草绿 `#8FA98B`
- Material3 `shapes` 更大圆角（卡片 18-26dp）
- 启动图标：恢复原「白色时钟」样式，改为**鼠尾草绿底 + 深绿表针**

**手绘/纸感细节**：
- 悬浮计时胶囊、提醒/退出弹窗 → **奶油纸 + 手绘虚线描边**；弹窗按钮纸感样式（实心绿/描边纸/文字）
- 统计/导出页标题下 → **手绘波浪线**（`ui/common/HandDrawn.kt` 多周期正弦波+抖动）
- 日记正文 → **笔记本横线底**（手账感）
- 统计图表柱色同步深鼠尾草绿

**涉及**：`ui/theme/Theme.kt`、`ui/common/HandDrawn.kt`（新）、全部 drawable/layout（图标/胶囊/弹窗）、`ui/stats/StatsScreen.kt`、`ui/diary/DiaryScreen.kt`、`ui/export/ExportScreen.kt`

---

## 💾 导入恢复 + release 正式包 + 精确检测开关（2026-08-04，已验证）

**① 导入恢复**（`util/Importer.kt` 新、4 个 DAO 加 `deleteAll`、`ui/export/`）
- 读之前导出的 JSON 备份，支持**合并**（保留现有、跳过冲突）或**覆盖**（清空后导入）
- 导出页新增「导入恢复」卡片：SAF 选文件 → 预览备份内容 → 选合并/覆盖 → 恢复并重排闹钟

**② release 正式包**
- `app/build.gradle.kts`：release 开 **minify（R8）** + **debug 签名**（个人自装升级）
- 体积：debug 10.76 MB → **release 1.67 MB**（约 -84%）

**③ 精确检测开关**（`util/Prefs.kt`、`monitor/FloatTimerService.kt`、`ui/entertainment/`）
- 监督页「精确检测」开关：开 = 无障碍实时（进入/切出即时）；关 = 系统 5 秒延时、无需无障碍
- **无障碍引导并入该卡片**（权限卡去掉了单独一行）：开但未授权时卡片内显示「去开启无障碍」
- 两种模式时长数据一致（都来自系统 UsageStats）

---

## ✅ 今日收尾（2026-08-04）

- **全新安装**：卸载旧版（清空全部测试数据）→ 安装最新 release 正式包（1.7 MB，含导入/精确检测/治愈主题全部改动）
- **首次使用**：重新授权（悬浮窗/使用情况/通知/精确闹钟/无障碍可选）、重新勾选娱乐清单、重新添加提问计划；提醒阈值回默认 30/60/90
- **隐私清理**：真机拉取的数据库副本（`/tmp/timenote_device.db`）已删除；手机端测试数据随卸载清空；全程零联网、零上传
- 注：今天测试导出到手机的文件（`TimeNote_日记_*.md` / `TimeNote_备份_*.json`）在手机本地，若不需要可自行删除

---

## ✅ 今日更新（2026-08-05，v1.0.0 正式版）

**变更**：
1. **移除演示数据功能**：统计页「生成演示数据」、日记页「生成演示日记」按钮及底层代码全部删除（正式版无测试入口）；`Prefs.stats_demo`、`DiaryDao.deleteByMarker` 等一并清理。
2. **FIX-15 删题同步**：删除问题计划后，自动清理该计划全部作答记录（含待回答）并刷新受影响日期的日记。
3. **FIX-16 性能**：回答弹窗「回答/稍后再答」卡顿根除——`FloatTimerService` 心跳的 UsageStats 查询移到后台线程、`submit` 入库后立即关弹窗、场景信息后台获取。
4. **FIX-17 通知残留**：作答/稍后再答后，提问通知自动取消（新增 `QuestionNotification` 统一 post/cancel）。
5. **版本**：versionCode 2 / versionName 1.0.0，全新安装交付，正式投入使用。

**隐私扫描**：全项目无 API key / Token / 私钥 / 手机号 / 身份证 / 邮箱；文档中本机路径已脱敏为占位符。

---

## 📋 任务清单（MVP 共 6 项）

| # | 任务 | 状态 |
|---|---|---|
| 1 | 初始化 Android 项目骨架 | ✅ 完成 |
| 2 | 搭建本地数据库（Room） | ✅ 完成 |
| 3 | 实现娱乐计时器（前台检测+悬浮+阈值弹窗） | ✅ 编码完成（待真机验证） |
| 4 | 实现定时提问（自定义+到点弹窗+补答） | ✅ 编码 + 真机验证 |
| 5 | 实现日记（汇编+编辑+日历+封面+翻页） | ✅ 编码 + 真机验证 |
| 6 | 构建验证核心闭环（assembleDebug + 真机/模拟器） | ✅ 真机验证全部完成 |

---

## 📌 当前状态（2026-08-05）

**v1.0.0 正式版已交付**：全新安装（卸载清空 → 装 release 包），演示数据功能已移除，FIX-15~17 已修复并真机验证通过。

后续会话：
1. 有新的功能 / 缺陷反馈直接提出即可。
2. 构建命令（项目根目录）：
   ```
   export JAVA_HOME=<JDK 安装路径>
   export ANDROID_HOME=<Android SDK 路径>
   ./gradlew.bat assembleRelease
   ```
3. 上架前已做隐私扫描：无 API key / Token / 个人信息；文档中的本机路径已脱敏。
