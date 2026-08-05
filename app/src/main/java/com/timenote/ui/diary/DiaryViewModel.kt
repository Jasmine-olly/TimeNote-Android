package com.timenote.ui.diary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.timenote.data.TimeNoteDatabase
import com.timenote.data.entity.Diary
import com.timenote.diary.DiaryAssembler
import com.timenote.diary.DiaryAutoSync
import com.timenote.ui.diary.Covers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 日记页 ViewModel：日历数据 + 汇编 + 编辑（F3.1 / F3.2 / F3.3） */
class DiaryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = TimeNoteDatabase.get(application)
    private val diaryDao = db.diaryDao()

    /** 全部日记（新→旧） */
    val diaries: StateFlow<List<Diary>> = diaryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 有日记的日期集合（日历小圆点标记） */
    val diaryDates: StateFlow<Set<String>> = diaries
        .map { list -> list.map { it.date }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** 有日记的日期（升序，翻页器页序，F3.3 左右翻页用） */
    val pagerDates: StateFlow<List<LocalDate>> = diaries
        .map { list -> list.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.sorted() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selected = MutableStateFlow(LocalDate.now())
    val selected: StateFlow<LocalDate> = _selected.asStateFlow()

    /** 当前选中日期的日记 */
    val selectedDiary: StateFlow<Diary?> = combine(diaries, _selected) { list, date ->
        list.firstOrNull { it.date == date.format(ISO) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun selectDate(date: LocalDate) {
        _selected.value = date
    }

    /** 汇编 / 重新汇编选中日期的日记（F3.1） */
    fun assembleSelected() {
        assembleFor(_selected.value)
    }

    /** 汇编 / 重新汇编指定日期的日记：
     *  已有日记走 [DiaryAutoSync.refreshDiary]（手动编辑过只刷新数据小节，不覆盖修改），
     *  无日记则新建。翻页器里每页的「重新汇编」用此方法对准该页日期。 */
    fun assembleFor(date: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            if (diaryDao.getByDate(date.format(ISO)) != null) {
                DiaryAutoSync.refreshDiary(getApplication(), date)
            } else {
                val markdown = DiaryAssembler.build(getApplication(), date)
                val now = System.currentTimeMillis()
                val answers = db.answerRecordDao().getOnDay(date)
                diaryDao.upsert(
                    Diary(
                        date = date.format(ISO),
                        content = markdown,
                        cover = DiaryAssembler.suggestCover(markdown, answers),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    /** 设置指定日期的封面（F3.3：天气 emoji + 心情），null 清除 */
    fun setCover(date: LocalDate, weather: String?, mood: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val iso = date.format(ISO)
            val existing = diaryDao.getByDate(iso) ?: return@launch
            diaryDao.upsert(
                existing.copy(
                    cover = Covers.encode(weather, mood),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** 保存手动编辑（F3.2）：标记为已编辑，回答变化时不再自动覆盖 */
    fun saveEdit(content: String) {
        val date = _selected.value
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val existing = diaryDao.getByDate(date.format(ISO))
            diaryDao.upsert(
                existing?.copy(content = content, updatedAt = now, edited = true)
                    ?: Diary(date = date.format(ISO), content = content, createdAt = now, updatedAt = now, edited = true),
            )
        }
    }

    companion object {
        private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
