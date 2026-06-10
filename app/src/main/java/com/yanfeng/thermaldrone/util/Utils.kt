package com.yanfeng.thermaldrone.util

import java.security.SecureRandom
import java.util.Locale

/** Pure-JVM utilities (unit-testable). */
object TokenGenerator {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no ambiguous 0/O/1/I
    private val rng = SecureRandom()

    /** Random 6-char alphanumeric session token. */
    fun generate(length: Int = 6): String =
        buildString(length) { repeat(length) { append(ALPHABET[rng.nextInt(ALPHABET.length)]) } }
}

object Format {
    fun cToF(c: Float): Float = c * 9f / 5f + 32f

    fun temp(tempC: Float, fahrenheit: Boolean): String =
        if (fahrenheit) String.format(Locale.US, "%.1f°F", cToF(tempC))
        else String.format(Locale.US, "%.1f°C", tempC)

    fun gps(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.6f, %.6f", lat, lon)

    fun flightTime(sec: Long): String = String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
}
