package com.timenote.ui.export

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.timenote.util.Exporter
import com.timenote.util.Importer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 导出页 ViewModel：生成内容并写入用户选择的本地位置（V1.2） */
class ExportViewModel(application: Application) : AndroidViewModel(application) {

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun exportMarkdown(uri: Uri) {
        viewModelScope.launch {
            val content = Exporter.buildMarkdown(getApplication())
            _status.value = if (Exporter.writeToUri(getApplication(), uri, content)) {
                "✅ 已导出 ${content.length} 字符的日记 Markdown 到你选择的位置"
            } else {
                "❌ 导出失败，请重试"
            }
        }
    }

    fun exportJson(uri: Uri) {
        viewModelScope.launch {
            val content = Exporter.buildJson(getApplication())
            _status.value = if (Exporter.writeToUri(getApplication(), uri, content)) {
                "✅ 已导出 JSON 数据备份到你选择的位置"
            } else {
                "❌ 导出失败，请重试"
            }
        }
    }

    // ---- 导入恢复（V1.2 补充） ----

    /** 待确认的导入：uri + 备份内容预览 */
    private val _pendingImport = MutableStateFlow<Pair<Uri, Importer.Preview>?>(null)
    val pendingImport: StateFlow<Pair<Uri, Importer.Preview>?> = _pendingImport.asStateFlow()

    /** 用户选中备份文件后：校验并展示预览，等待选择合并/覆盖 */
    fun onPickedBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val p = Importer.preview(getApplication(), uri)
            _pendingImport.value = if (p == null) null else uri to p
            if (p == null) _status.value = "❌ 不是有效的 TimeNote 备份文件"
        }
    }

    /** 确认导入 */
    fun importPending(mode: Importer.Mode, uri: Uri) {
        _pendingImport.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val r = Importer.restore(getApplication(), uri, mode)
            _status.value = if (r.ok) "✅ ${r.message}" else "❌ ${r.message}"
        }
    }

    fun cancelImport() {
        _pendingImport.value = null
    }
}
