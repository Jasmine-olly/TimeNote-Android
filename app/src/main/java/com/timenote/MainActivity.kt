package com.timenote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timenote.ui.diary.DiaryScreen
import com.timenote.ui.diary.DiaryViewModel
import com.timenote.ui.entertainment.MonitorScreen
import com.timenote.ui.entertainment.MonitorViewModel
import com.timenote.ui.export.ExportScreen
import com.timenote.ui.export.ExportViewModel
import com.timenote.ui.question.QuestionScreen
import com.timenote.ui.question.QuestionViewModel
import com.timenote.ui.stats.StatsScreen
import com.timenote.ui.stats.StatsViewModel
import com.timenote.ui.theme.TimeNoteTheme

class MainActivity : ComponentActivity() {

    private val monitorViewModel: MonitorViewModel by viewModels()
    private val questionViewModel: QuestionViewModel by viewModels()
    private val diaryViewModel: DiaryViewModel by viewModels()
    private val exportViewModel: ExportViewModel by viewModels()
    private val statsViewModel: StatsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimeNoteTheme {
                AppRoot(monitorViewModel, questionViewModel, diaryViewModel, exportViewModel, statsViewModel)
            }
        }
    }
}

/** 应用根导航：底部五个入口（监督 / 提问 / 日记 / 统计 / 导出） */
@Composable
private fun AppRoot(
    monitorViewModel: MonitorViewModel,
    questionViewModel: QuestionViewModel,
    diaryViewModel: DiaryViewModel,
    exportViewModel: ExportViewModel,
    statsViewModel: StatsViewModel,
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.Monitor) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Text(t.glyph) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (tab) {
                MainTab.Monitor -> MonitorScreen(monitorViewModel)
                MainTab.Ask -> QuestionScreen(questionViewModel)
                MainTab.Diary -> DiaryScreen(diaryViewModel)
                MainTab.Stats -> StatsScreen(statsViewModel)
                MainTab.Export -> ExportScreen(exportViewModel)
            }
        }
    }
}

enum class MainTab(val title: String, val glyph: String) {
    Monitor("监督", "⏱"),
    Ask("提问", "❓"),
    Diary("日记", "📓"),
    Stats("统计", "📊"),
    Export("导出", "📤"),
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(32.dp),
        )
    }
}
