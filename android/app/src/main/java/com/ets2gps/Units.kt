package com.ets2gps

import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** One of the two games: Euro Truck Simulator 2 or American Truck Simulator (port of Game.cs). */
data class GameInfo(val id: String, val name: String, val short: String, val imperial: Boolean, val currency: String, val scale: Float, val filePrefix: String)

object Games {
    val ETS2 = GameInfo("ets2", "Euro Truck Simulator 2", "ETS2", false, "€", 19f, "ets")
    val ATS = GameInfo("ats", "American Truck Simulator", "ATS", true, "$", 20f, "ats")

    /** The game the app is working with now (switched when the telemetry reports the other game). */
    @Volatile var current: GameInfo = ETS2

    fun byId(id: String?): GameInfo = if (id == "ats") ATS else ETS2

    /** The game from the telemetry's game name ("ETS2", "ATS"), or null when it says nothing. */
    fun fromName(name: String?): GameInfo? {
        if (name.isNullOrEmpty()) return null
        val n = name.uppercase(Locale.US)
        return if (n.contains("ATS") || n.contains("AMERICAN")) ATS else if (n.contains("ETS") || n.contains("EURO")) ETS2 else null
    }
}

/**
 * Numbers as the current game shows them: metric for ETS2; miles, mph, feet, US gallons, pounds and dollars for ATS.
 * Inputs are metric (game metres, km, km/h, litres, kg), as the telemetry and the map give them.
 */
object Units {
    private const val M_PER_MILE = 1609.344f
    private const val M_PER_FOOT = 0.3048f
    private const val L_PER_GALLON = 3.785411784f
    private val us = Locale.US
    val imperial get() = Games.current.imperial
    val speedUnit get() = if (imperial) "mph" else "km/h"
    val distUnit get() = if (imperial) "mi" else "km"
    val money get() = Games.current.currency

    fun speed(kmh: Float) = if (imperial) kmh * 1000f / M_PER_MILE else kmh
    fun long(km: Float) = if (imperial) km * 1000f / M_PER_MILE else km
    fun fuel(l: Float) = if (imperial) l / L_PER_GALLON else l
    fun alt(m: Float) = if (imperial) m / M_PER_FOOT else m

    fun longText(km: Float) = String.format(us, "%,.0f %s", long(km), distUnit)
    fun speedText(kmh: Float) = "${Math.round(speed(kmh).coerceAtLeast(0f))} $speedUnit"
    fun altText(m: Float) = "${Math.round(alt(m))} " + if (imperial) "ft" else "m"
    fun fuelText(l: Float) = String.format(us, "%,.0f %s", fuel(l), if (imperial) "gal" else "L")
    fun mass(kg: Float) = if (imperial) String.format(us, "%,d lb", (kg * 2.20462262f / 100f).roundToInt() * 100) else String.format(us, "%.1f t", kg / 1000f)
    fun consumption(lPerKm: Float) =
        if (!imperial) String.format(us, "%.1f L/100 km", lPerKm * 100f)
        else (if (lPerKm > 0f) String.format(us, "%.1f", M_PER_MILE / 1000f / (lPerKm / L_PER_GALLON)) else "-") + " mpg"

    /** Short distance for the screen, from metres: "300 m" / "1.2 km", or "500 ft" / "0.3 mi". */
    fun short(m: Float): String {
        if (!imperial) return when {
            m < 1000f -> "${max(10, (m / 10f).roundToInt() * 10)} m"
            m < 10000f -> String.format(us, "%.1f km", m / 1000f)
            else -> "${(m / 1000f).roundToInt()} km"
        }
        val mi = m / M_PER_MILE
        return when {
            mi < 0.1f -> "${max(50, (m / M_PER_FOOT / 50f).roundToInt() * 50)} ft"
            mi < 10f -> String.format(us, "%.1f mi", mi)
            else -> "${mi.roundToInt()} mi"
        }
    }

    /** Distance as spoken, from metres: "300 meters" / "1.5 kilometers", or "500 feet" / "half a mile" / "3 miles". */
    fun spoken(m: Float): String {
        if (!imperial) return when {
            m < 950f -> "${max(50, (m / 50f).roundToInt() * 50)} meters"
            m < 10000f -> {
                val half = (m / 500f).roundToInt() / 2f
                if (half == half.toInt().toFloat()) "${half.toInt()} kilometers" else "$half kilometers"
            }
            else -> "${(m / 1000f).roundToInt()} kilometers"
        }
        val mi = m / M_PER_MILE
        return when {
            mi < 0.19f -> "${max(100, (m / M_PER_FOOT / 100f).roundToInt() * 100)} feet"
            mi < 0.38f -> "a quarter mile"
            mi < 0.63f -> "half a mile"
            mi < 0.88f -> "three quarters of a mile"
            mi < 1.25f -> "1 mile"
            mi < 10f -> { val h = (mi * 2f).roundToInt() / 2f; if (h == h.toInt().toFloat()) "${h.toInt()} miles" else "$h miles" }
            else -> "${mi.roundToInt()} miles"
        }
    }
}
