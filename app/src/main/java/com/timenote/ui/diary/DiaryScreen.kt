package com.timenote.ui.diary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timenote.data.entity.Diary
import com.timenote.ui.theme.Green
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** F3 日记本主界面：日历 / 封面索引 双入口 + 左右翻页翻阅 + 编辑 */
@Composable
fun DiaryScreen(vm: DiaryViewModel) {
    val diaries by vm.diaries.collectAsState()
    val diaryDates by vm.diaryDates.collectAsState()
    val pagerDates by vm.pagerDates.collectAsState()
    val selected by vm.selected.collectAsState()
    val selectedDiary by vm.selectedDiary.collectAsState()

    var mode by rememberSaveable { mutableStateOf(DiaryMode.Calendar) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var month by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var readingDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }

    // 封面点选 → 全屏翻阅：直接显示该日日记，左右滑动看相邻天
    readingDate?.let { initial ->
        DiaryReader(
            vm = vm,
            diaries = diaries,
            pagerDates = pagerDates,
            initial = initial,
            selected = selected,
            selectedDiary = selectedDiary,
            editing = editing,
            editText = editText,
            onEditText = { editText = it },
            onStartEdit = { d ->
                editing = true
                editText = d.content
            },
            onCancelEdit = { editing = false },
            onSaveEdit = { vm.saveEdit(editText); editing = false },
            onBack = {
                readingDate = null
                editing = false
            },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // ---- 入口切换：日历 | 封面 ----
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == DiaryMode.Calendar,
                onClick = { mode = DiaryMode.Calendar },
                label = { Text("日历") },
            )
            FilterChip(
                selected = mode == DiaryMode.Cover,
                onClick = { mode = DiaryMode.Cover },
                label = { Text("封面") },
            )
        }

        Spacer(Modifier.height(8.dp))

        when (mode) {
            DiaryMode.Calendar -> CalendarMode(
                vm = vm,
                diaries = diaries,
                diaryDates = diaryDates,
                pagerDates = pagerDates,
                selected = selected,
                selectedDiary = selectedDiary,
                editing = editing,
                editText = editText,
                onEditText = { editText = it },
                onStartEdit = { d ->
                    editing = true
                    editText = d.content
                },
                onCancelEdit = { editing = false },
                onSaveEdit = { vm.saveEdit(editText); editing = false },
                month = month,
                onMonth = { month = it },
            )

            DiaryMode.Cover -> CoverMode(
                diaries = diaries,
                onOpen = { date ->
                    vm.selectDate(date)
                    editing = false
                    readingDate = date
                },
            )
        }
    }
}

/** 全屏翻阅（F3.3）：封面点选进入，直接显示该日日记，左右滑动翻看相邻天 */
@Composable
private fun DiaryReader(
    vm: DiaryViewModel,
    diaries: List<Diary>,
    pagerDates: List<LocalDate>,
    initial: LocalDate,
    selected: LocalDate,
    selectedDiary: Diary?,
    editing: Boolean,
    editText: String,
    onEditText: (String) -> Unit,
    onStartEdit: (Diary) -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text(
                "翻阅",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        when {
            editing -> DiaryEditForm(
                date = selectedDiary?.date ?: initial.toString(),
                editText = editText,
                onEditText = onEditText,
                onCancel = onCancelEdit,
                onSave = onSaveEdit,
            )
            else -> Box(Modifier.weight(1f)) {
                DiaryPager(
                    vm = vm,
                    diaries = diaries,
                    pagerDates = pagerDates,
                    selected = initial,
                    onStartEdit = onStartEdit,
                )
            }
        }
    }
}

/** 日历模式：月份导航 + 月历 + 日记正文区（编辑表单 / 空状态 / 左右翻页） */
@Composable
private fun CalendarMode(
    vm: DiaryViewModel,
    diaries: List<Diary>,
    diaryDates: Set<String>,
    pagerDates: List<LocalDate>,
    selected: LocalDate,
    selectedDiary: Diary?,
    editing: Boolean,
    editText: String,
    onEditText: (String) -> Unit,
    onStartEdit: (Diary) -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    month: LocalDate,
    onMonth: (LocalDate) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // ---- 月份导航 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                val today = LocalDate.now()
                onMonth(today.withDayOfMonth(1))
                vm.selectDate(today)
            }) { Text("今日") }
            TextButton(onClick = { onMonth(month.minusMonths(1)) }) { Text("‹") }
            Text(
                "${month.year}年${month.monthValue}月",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = { onMonth(month.plusMonths(1)) }) { Text("›") }
        }

        // ---- 日历 ----
        CalendarGrid(
            month = month,
            diaryDates = diaryDates,
            selected = selected,
            onSelect = { vm.selectDate(it) },
        )

        Spacer(Modifier.height(4.dp))

        // ---- 正文区 ----
        when {
            editing -> DiaryEditForm(
                date = selectedDiary?.date ?: selected.toString(),
                editText = editText,
                onEditText = onEditText,
                onCancel = onCancelEdit,
                onSave = onSaveEdit,
            )

            selectedDiary == null -> EmptyState(
                selected = selected,
                onAssemble = { vm.assembleFor(selected) },
            )

            else -> DiaryPager(
                vm = vm,
                diaries = diaries,
                pagerDates = pagerDates,
                selected = selected,
                onStartEdit = onStartEdit,
            )
        }
    }
}

/** 左右翻页：页 = 有日记的日期（升序），滑动在相邻日记间切换（F3.3） */
@Composable
private fun DiaryPager(
    vm: DiaryViewModel,
    diaries: List<Diary>,
    pagerDates: List<LocalDate>,
    selected: LocalDate,
    onStartEdit: (Diary) -> Unit,
) {
    val pagerState = rememberPagerState { pagerDates.size }

    // 外部选中变化（日历/封面点选、今日按钮）→ 跳到对应页
    LaunchedEffect(selected, pagerDates) {
        val idx = pagerDates.indexOf(selected)
        if (idx >= 0 && pagerState.currentPage != idx) pagerState.scrollToPage(idx)
    }
    // 翻页（滑动）→ 同步选中日期（日历高亮随之移动）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            pagerDates.getOrNull(page)?.let { d -> if (d != selected) vm.selectDate(d) }
        }
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val date = pagerDates[page]
        val diary = diaries.firstOrNull { it.date == date.toString() }
        if (diary != null) {
            DiaryPage(
                diary = diary,
                onAssemble = { vm.assembleFor(date) },
                onStartEdit = onStartEdit,
                setCover = vm::setCover,
            )
        }
    }
}

/** 单篇日记页：封面条（天气大字+心情底色）+ 操作行（封面/重新汇编/编辑）+ 正文。
 *  整页可滚动：封面选择器展开后内容变高也不会被底部裁掉。 */
@Composable
private fun DiaryPage(
    diary: Diary,
    onAssemble: () -> Unit,
    onStartEdit: (Diary) -> Unit,
    setCover: (LocalDate, String?, String?) -> Unit,
) {
    val date = remember(diary.date) { runCatching { LocalDate.parse(diary.date) }.getOrNull() }
    val (weather, mood) = remember(diary.cover) { Covers.parse(diary.cover) }
    val moodColor = Covers.moodColor(mood)
    var showPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ---- 封面条 ----
        if (weather != null || mood != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(moodColor ?: MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(weather ?: "📝", fontSize = 36.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            date?.display() ?: diary.date,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF37474F),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            coverSubtitle(weather, mood),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF37474F).copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }

        // ---- 操作行 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                date?.display() ?: diary.date,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onAssemble() }) { Text("重新汇编") }
            if (date != null) {
                TextButton(onClick = { showPicker = !showPicker }) { Text("封面") }
            }
            TextButton(onClick = { onStartEdit(diary) }) { Text("编辑") }
        }

        // ---- 封面选择器 ----
        if (showPicker && date != null) {
            CoverPicker(
                weather = weather,
                mood = mood,
                date = date,
                setCover = setCover,
            )
        }

        // ---- 正文（随整页滚动） ----
        DiaryContent(
            markdown = diary.content,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 封面选择器：天气 emoji 一排 + 心情 emoji 一排 + 清除 */
@Composable
private fun CoverPicker(
    weather: String?,
    mood: String?,
    date: LocalDate,
    setCover: (LocalDate, String?, String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            Covers.WEATHER.forEach { w ->
                FilterChip(
                    selected = weather == w,
                    onClick = { setCover(date, if (weather == w) null else w, mood) },
                    label = { Text(w) },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            Covers.MOODS.forEach { m ->
                FilterChip(
                    selected = mood == m.emoji,
                    onClick = { setCover(date, weather, if (mood == m.emoji) null else m.emoji) },
                    label = { Text(m.emoji) },
                )
            }
        }
        if (weather != null || mood != null) {
            TextButton(onClick = { setCover(date, null, null) }) { Text("清除封面") }
        }
    }
}

/** 封面索引：全部日记封面的总览网格，点某篇跳到该天（F3.3） */
@Composable
private fun CoverMode(diaries: List<Diary>, onOpen: (LocalDate) -> Unit) {
    if (diaries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "还没有日记，去「日历」页汇编一篇吧",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(diaries, key = { it.date }) { d ->
            CoverCell(diary = d, onOpen = onOpen)
        }
    }
}

/** 封面索引的一个格子：心情底色 + 天气 emoji + 日期 */
@Composable
private fun CoverCell(diary: Diary, onOpen: (LocalDate) -> Unit) {
    val date = remember(diary.date) { runCatching { LocalDate.parse(diary.date) }.getOrNull() } ?: return
    val (weather, mood) = remember(diary.cover) { Covers.parse(diary.cover) }
    val bg = Covers.moodColor(mood)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg ?: MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onOpen(date) }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(weather ?: "📝", fontSize = 30.sp)
        Text(
            date.display(),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF37474F),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

/** 空状态：无日记时提示；今天是引导汇编 */
@Composable
private fun EmptyState(selected: LocalDate, onAssemble: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (selected == LocalDate.now()) "今天还没有日记" else "该日暂无日记",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selected == LocalDate.now()) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onAssemble) { Text("汇编今日日记") }
            }
        }
    }
}

/** 编辑表单：整篇 Markdown 编辑，可滚动 + 键盘避让 */
@Composable
private fun DiaryEditForm(
    date: String,
    editText: String,
    onEditText: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(top = 4.dp),
    ) {
        Text(date, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = editText,
            onValueChange = onEditText,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp),
            label = { Text("日记内容（Markdown）") },
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text("取消") }
            Button(onClick = onSave) { Text("保存") }
        }
    }
}

/** 简单月历：周一为首列，有日记的日期显示小圆点 */
@Composable
private fun CalendarGrid(
    month: LocalDate,
    diaryDates: Set<String>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val firstDay = month.dayOfWeek.value // 周一=1 … 周日=7
    val daysInMonth = month.lengthOfMonth()
    val leadingEmpty = firstDay - 1
    val totalCells = leadingEmpty + daysInMonth
    val rows = (totalCells + 6) / 7

    Column {
        Row {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { d ->
                Text(
                    d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (r in 0 until rows) {
            Row {
                for (c in 0 until 7) {
                    val idx = r * 7 + c
                    val dayNum = idx - leadingEmpty + 1
                    val date = if (dayNum in 1..daysInMonth) month.withDayOfMonth(dayNum) else null
                    val iso = date?.toString() ?: ""
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clickable(enabled = date != null) { date?.let(onSelect) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (date != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val isSelected = date == selected
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Green else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$dayNum",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (iso in diaryDates) Green else Color.Transparent),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 极简 Markdown 渲染：标题 / 列表 / 引用 / 内联粗体与斜体，够读即可；
 *  底层垫「笔记本横线」，治愈手账感 */
@Composable
private fun DiaryContent(markdown: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        // 笔记本横线底（淡）
        Canvas(Modifier.matchParentSize()) {
            val gap = 22.dp.toPx()
            var y = gap
            while (y < size.height) {
                drawLine(Color(0xFFE4DED0), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += gap
            }
        }
        Column(Modifier.fillMaxWidth()) {
            markdown.split('\n').forEach { line ->
            when {
                line.startsWith("### ") -> HeadingText(line.removePrefix("### "), MaterialTheme.typography.titleMedium)
                line.startsWith("## ") -> HeadingText(line.removePrefix("## "), MaterialTheme.typography.titleLarge)
                line.startsWith("# ") -> HeadingText(line.removePrefix("# "), MaterialTheme.typography.headlineSmall)
                line.startsWith("> ") -> MarkdownLine(
                    line.removePrefix("> "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(start = 8.dp),
                )
                line.startsWith("- ") -> MarkdownLine(
                    "• ${line.removePrefix("- ")}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                else -> MarkdownLine(line, style = MaterialTheme.typography.bodyMedium)
            }
            }
        }
    }
}

/** 单行渲染：解析内联 **粗体** 与 *斜体*，避免把 Markdown 标记符显示出来 */
@Composable
private fun MarkdownLine(
    text: String,
    style: TextStyle,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight = FontWeight.Normal,
    fontStyle: FontStyle = FontStyle.Normal,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(text) { buildAnnotatedString { appendMarkdownInline(text) } }
    Text(
        annotated,
        style = style,
        color = color,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        modifier = modifier,
    )
}

private fun AnnotatedString.Builder.appendMarkdownInline(input: String) {
    // 先按 ** 切段处理粗体，段内再按 * 处理斜体
    input.split("**").forEachIndexed { index, part ->
        if (index % 2 == 1) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendItalicInline(part) }
        } else {
            appendItalicInline(part)
        }
    }
}

private fun AnnotatedString.Builder.appendItalicInline(input: String) {
    input.split("*").forEachIndexed { index, part ->
        if (index % 2 == 1) {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(part) }
        } else {
            append(part)
        }
    }
}

@Composable
private fun HeadingText(text: String, style: TextStyle) {
    Text(
        text,
        style = style,
        fontWeight = FontWeight.Bold,
        color = Green,
    )
}

private fun LocalDate.display(): String =
    format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))

/** 封面条副标题：只列出已设置的部分 */
private fun coverSubtitle(weather: String?, mood: String?): String {
    val parts = mutableListOf<String>()
    weather?.let { parts += "天气 $it" }
    mood?.let { m -> parts += "心情 ${Covers.MOODS.firstOrNull { it.emoji == m }?.label ?: m}" }
    return parts.joinToString(" · ").ifEmpty { "" }
}

private enum class DiaryMode { Calendar, Cover }
