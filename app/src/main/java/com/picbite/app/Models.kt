package com.picbite.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** Формат полностью совместим с экспортом PWA-версии (calories-YYYY-MM-DD.json). */
val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

/** Пищевая ценность на b.g граммов (обычно 100). */
@Serializable
data class Base(
    val g: Double = 100.0,
    val k: Double = 0.0,
    val p: Double = 0.0,
    val f: Double = 0.0,
    val c: Double = 0.0,
)

@Serializable
data class Item(
    val name: String = "?",
    val g: Double = 100.0,
    val b: Base = Base(),
)

@Serializable
data class Entry(
    val id: Long = 0,
    val day: String = "",
    val meal: String = "",
    val dish: String = "",
    val items: List<Item> = emptyList(),
    val thumb: String = "",
)

@Serializable
data class Fav(
    val id: Long = 0,
    val dish: String = "",
    val items: List<Item> = emptyList(),
    val thumb: String = "",
)

/** Продукт по штрихкоду, значения на 100 г. s — размер порции, m=1 — введён вручную. */
@Serializable
data class BcEntry(
    val n: String = "",
    val k: Double = 0.0,
    val p: Double = 0.0,
    val f: Double = 0.0,
    val c: Double = 0.0,
    val s: Double = 100.0,
    val m: Int = 0,
)

@Serializable
data class AppData(
    val entries: List<Entry> = emptyList(),
    val favs: List<Fav> = emptyList(),
    val goal: Double = 0.0,
    val url: String = "",
    val token: String = "",
    val lastBackup: Long = 0,
    val theme: String = "auto",
    val pal: String = "sun",
    val cust: String = "#e0510b",
    val barcodes: Map<String, BcEntry> = emptyMap(),
)

val MEALS = listOf("b" to "Завтрак", "l" to "Обед", "d" to "Ужин", "s" to "Перекус")

fun mealByHour(h: Int) = when {
    h < 11 -> "b"
    h < 16 -> "l"
    h < 21 -> "d"
    else -> "s"
}

fun autoMeal(): String = mealByHour(LocalTime.now().hour)

fun Entry.mealKey(): String = meal.ifEmpty {
    val ms = if (id > 0) id else System.currentTimeMillis()
    mealByHour(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).hour)
}

data class Tot(val k: Double = 0.0, val p: Double = 0.0, val f: Double = 0.0, val c: Double = 0.0)

private fun Item.part(v: Double) = if (b.g > 0) v * g / b.g else v

fun Item.kcal() = part(b.k)

fun List<Item>.tot() = Tot(
    k = sumOf { it.part(it.b.k) },
    p = sumOf { it.part(it.b.p) },
    f = sumOf { it.part(it.b.f) },
    c = sumOf { it.part(it.b.c) },
)

fun Tot.bzu() = "Б ${p.roundToInt()} · Ж ${f.roundToInt()} · У ${c.roundToInt()}"

fun fmt(d: Double): String =
    if (d % 1.0 == 0.0) d.toLong().toString() else (Math.round(d * 10) / 10.0).toString()

fun num(v: String): Double =
    v.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
