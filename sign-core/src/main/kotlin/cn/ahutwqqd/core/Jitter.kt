package cn.ahutwqqd.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 签到位置偏移。
 *
 * 与 Python 参考实现的关键差异：原脚本用 ±0.01 度，在马鞍山校区纬度（约 31.68°N）上
 * 相当于纬度方向约 ±1.1 公里、经度方向约 ±0.95 公里，远超学校要求的「宿舍楼 200 米内」。
 * 这里改为按「米」计算的圆内均匀分布，默认半径 100 米，留出安全余量。
 */
object Jitter {

    const val DEFAULT_RADIUS_METERS = 100.0

    private const val METERS_PER_DEGREE_LAT = 111_320.0

    /**
     * 在以 (lat, lng) 为圆心、radiusMeters 为半径的圆内均匀取一点。
     *
     * 用 sqrt(u) 而不是 u 来生成半径，否则点会向圆心聚集。
     */
    fun locationInCircle(
        lat: Double,
        lng: Double,
        radiusMeters: Double = DEFAULT_RADIUS_METERS,
        random: Random = Random.Default
    ): Pair<Double, Double> {
        if (radiusMeters <= 0.0) return lat to lng
        val distance = radiusMeters * sqrt(random.nextDouble())
        val theta = random.nextDouble() * 2 * Math.PI
        val deltaLat = distance * cos(theta) / METERS_PER_DEGREE_LAT
        val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(1e-6)
        val deltaLng = distance * sin(theta) / (METERS_PER_DEGREE_LAT * cosLat)
        return (lat + deltaLat) to (lng + deltaLng)
    }

    /** 两点间距离（米），等距圆柱近似，用于校验偏移半径 */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val avgLat = Math.toRadians((lat1 + lat2) / 2)
        val dx = (lng2 - lng1) * METERS_PER_DEGREE_LAT * cos(avgLat)
        val dy = (lat2 - lat1) * METERS_PER_DEGREE_LAT
        return sqrt(dx * dx + dy * dy)
    }
}
