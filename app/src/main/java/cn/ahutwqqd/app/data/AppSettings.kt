package cn.ahutwqqd.app.data

import org.json.JSONObject

/**
 * 全局设置。时间窗口用「当天第几分钟」表示，0..1439。
 *
 * 默认 21:30–22:00：学校 21:30 开放签到，起点再留 40 秒缓冲避开刚开放的抖动。
 */
data class AppSettings(
    /**
     * 自动签到总开关，**默认关闭**。
     *
     * 自动签到依赖权限、后台白名单、厂商自启动等多项外部条件，默认打开很容易
     * 让用户以为「已经会自己签了」。改为默认关闭，由用户在「自动签到保障」
     * 里逐项确认后再手动开启。
     */
    val autoSignEnabled: Boolean = false,
    val windowStart: Int = 21 * 60 + 30,
    val windowEnd: Int = 22 * 60,
    val jitterRadiusMeters: Int = 100,
    val notifyOnSuccess: Boolean = true,
    val notifyOnFailure: Boolean = true,
    /** 已排定的下一次自动签到时刻，0 表示尚未排定 */
    val nextRunAt: Long = 0L,
    /** 本次重试轮次，用于限制窗口内重试次数 */
    val retryAttempt: Int = 0,
    /**
     * 常驻守护开关。
     *
     * 国产 ROM（vivo/小米/OPPO…）会在应用退到后台后冻结它并移除 AlarmManager 闹钟，
     * 光靠精确闹钟根本等不到触发时刻。守护服务用前台服务把进程维持在活跃状态，
     * 闹钟才留得住。
     */
    val guardEnabled: Boolean = true,
    /**
     * 已排定但尚未被执行的签到时刻，0 表示没有待执行的安排。
     *
     * 排闹钟时写入，SignService 接到闹钟后立刻清 0。守护服务据此判断
     * 「到点后 120 秒仍没接单」——说明闹钟被系统清掉了，需要提醒用户。
     */
    val pendingRunAt: Long = 0L,
    /**
     * 用户在「自动签到指引」里手动确认已完成的应用自启动设置。
     *
     * 国产 ROM 的自启动管理是厂商私有实现，没有公开 API 可以查询，
     * 只能由用户自己勾选确认。
     */
    val ackAutostart: Boolean = false,
    /** 同上，对应「后台高耗电 / 允许后台活动」这一类厂商开关 */
    val ackBackgroundPower: Boolean = false,
    /**
     * 明暗模式：SYSTEM 跟随系统、LIGHT 始终浅色、DARK 始终深色。
     * 存字符串而不是枚举，方便以后加值时不破坏旧数据。
     */
    val themeMode: String = "SYSTEM",
    /** 配色风格：BLUE / TEAL / VIOLET / SUNSET / ROSE / GRAPHITE */
    val themeStyle: String = "BLUE"
) {

    /** 窗口长度不足 6 分钟时不再留缓冲，直接在整个窗口内随机 */
    val picksInsideBufferedWindow: Boolean
        get() = windowEnd - windowStart >= 6

    fun toJson(): JSONObject = JSONObject().apply {
        put("autoSignEnabled", autoSignEnabled)
        put("windowStart", windowStart)
        put("windowEnd", windowEnd)
        put("jitterRadiusMeters", jitterRadiusMeters)
        put("notifyOnSuccess", notifyOnSuccess)
        put("notifyOnFailure", notifyOnFailure)
        put("nextRunAt", nextRunAt)
        put("retryAttempt", retryAttempt)
        put("guardEnabled", guardEnabled)
        put("pendingRunAt", pendingRunAt)
        put("ackAutostart", ackAutostart)
        put("ackBackgroundPower", ackBackgroundPower)
        put("themeMode", themeMode)
        put("themeStyle", themeStyle)
    }

    companion object {
        fun fromJson(o: JSONObject): AppSettings = AppSettings(
            autoSignEnabled = o.optBoolean("autoSignEnabled", false),
            windowStart = o.optInt("windowStart", 21 * 60 + 30),
            windowEnd = o.optInt("windowEnd", 22 * 60),
            jitterRadiusMeters = o.optInt("jitterRadiusMeters", 100),
            notifyOnSuccess = o.optBoolean("notifyOnSuccess", true),
            notifyOnFailure = o.optBoolean("notifyOnFailure", true),
            nextRunAt = o.optLong("nextRunAt", 0L),
            retryAttempt = o.optInt("retryAttempt", 0),
            guardEnabled = o.optBoolean("guardEnabled", true),
            pendingRunAt = o.optLong("pendingRunAt", 0L),
            ackAutostart = o.optBoolean("ackAutostart", false),
            ackBackgroundPower = o.optBoolean("ackBackgroundPower", false),
            themeMode = o.optString("themeMode", "SYSTEM"),
            themeStyle = o.optString("themeStyle", "BLUE")
        )
    }
}
