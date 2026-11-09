package com.picbite.app

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import java.util.Locale

@Serializable
data class Base(
    val k: Double = 0.0, // Ккал
    val p: Double = 0.0, // Белки
    val f: Double = 0.0, // Жиры
    val c: Double = 0.0  // Углеводы
)

@Serializable
data class Item(
    val name: String,
    val g: Double, // граммы
    val b: Base    // БЖУ на 100г
)

@Serializable
data class Entry(
    val id: String,
    val day: String,
    val meal: String,
    val dish: String,
    val items: List<Item>
)

@Serializable
data class Fav(
    val id: String,
    val dish: String,
    val items: List<Item>
)

@Serializable
data class AppData(
    val entries: List<Entry> = emptyList(),
    val favs: List<Fav> = emptyList(),
    val goal: Double = 2000.0,
    val url: String = "",
    val token: String = "",
    val theme: String = "auto",
    val lastBackup: Long = 0L
)

// Модели для диалогов
data class Pending(
    val dish: String,
    val comment: String,
    val items: List<Item>,
    val meal: String,
    val eid: String? = null
)

// Класс для предзаполнения данных (добавлен, чтобы исправить ошибку)
data class PreManual(
    val n: String = "", // Название
    val k: Double = 0.0,
    val p: Double = 0.0,
    val f: Double = 0.0,
    val c: Double = 0.0
)

// Обновленный класс ManualBc с нужными полями
data class ManualBc(
    val code: String,
    val title: String,
    val pre: PreManual? = null
)

data class UiEvent(
    val text: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null
)

// Вспомогательные функции (расширения)
fun List<Item>.tot(): Base {
    var k = 0.0; var p = 0.0; var f = 0.0; var c = 0.0
    for (item in this) {
        val ratio = item.g / 100.0
        k += item.b.k * ratio
        p += item.b.p * ratio
        f += item.b.f * ratio
        c += item.b.c * ratio
    }
    return Base(k, p, f, c)
}

fun Item.kcal(): Double = (b.k * (g / 100.0))

fun Base.bzu(): String = "Б: ${p.roundToInt()} / Ж: ${f.roundToInt()} / У: ${c.roundToInt()}"

fun Entry.mealKey(): String = meal

// Форматирование чисел (2 знака после запятой, но без лишних нулей)
fun fmt(d: Double): String {
    return if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.US, "%.1f", d)
}

// Парсинг чисел из строки
fun num(s: String): Double = s.replace(",", ".").toDoubleOrNull() ?: 0.0
