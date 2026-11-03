package cn.ahutwqqd.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

class JitterTest {

    private val dormLat = 31.2345
    private val dormLng = 117.1234

    @Test
    @DisplayName("偏移落在 100 米半径内")
    fun withinRadius() {
        val random = Random(20261102)
        var max = 0.0
        repeat(5000) {
            val (lat, lng) = Jitter.locationInCircle(dormLat, dormLng, 100.0, random)
            val d = Jitter.distanceMeters(dormLat, dormLng, lat, lng)
            assertTrue(d <= 100.0 + 1e-9, "偏移 $d 米超出 100 米半径")
            if (d > max) max = d
        }
        // 均匀分布在圆内，必须能取到接近边界的点，否则说明半径参数没生效
        assertTrue(max > 95.0, "最大偏移只有 $max 米，半径疑似未生效")
    }

    @Test
    @DisplayName("零半径时坐标不变")
    fun zeroRadius() {
        val (lat, lng) = Jitter.locationInCircle(dormLat, dormLng, 0.0, Random(1))
        assertEquals(dormLat, lat)
        assertEquals(dormLng, lng)
    }

    @Test
    @DisplayName("经度方向的米数换算受纬度影响")
    fun longitudeScalesWithLatitude() {
        val random = Random(7)
        var maxLng = 0.0
        repeat(2000) {
            val (_, lng) = Jitter.locationInCircle(dormLat, dormLng, 100.0, random)
            val delta = abs(lng - dormLng)
            if (delta > maxLng) maxLng = delta
        }
        // 31.7 度纬线上，100 米约等于 0.00106 度经度；若误用纬度系数会偏小
        assertTrue(maxLng > 0.0009, "经度偏移 $maxLng 度偏小，疑似未按纬度折算")
        assertTrue(maxLng < 0.0012, "经度偏移 $maxLng 度偏大，超出 100 米")
    }
}
