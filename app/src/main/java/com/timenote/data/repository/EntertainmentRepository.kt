package com.timenote.data.repository

import com.timenote.data.dao.EntertainmentAppDao
import com.timenote.data.entity.EntertainmentApp
import kotlinx.coroutines.flow.Flow

/** 娱乐应用清单仓库（F1.1） */
class EntertainmentRepository(
    private val dao: EntertainmentAppDao,
) {
    fun observeApps(): Flow<List<EntertainmentApp>> = dao.observeAll()

    suspend fun setAppSelected(packageName: String, label: String, selected: Boolean) {
        if (selected) {
            dao.insert(EntertainmentApp(packageName = packageName, label = label))
        } else {
            dao.deleteByPackage(packageName)
        }
    }
}
