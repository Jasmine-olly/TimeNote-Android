package com.timenote.monitor

/**
 * 前台应用共享状态：
 * 无障碍服务写入实时包名，监督服务每秒轮询读取。
 */
object ForegroundState {

    /** 无障碍服务是否已连接（作为实时检测主通道） */
    @Volatile
    var accessibilityConnected: Boolean = false

    /** 当前前台应用包名 */
    @Volatile
    var currentPackage: String? = null
}
