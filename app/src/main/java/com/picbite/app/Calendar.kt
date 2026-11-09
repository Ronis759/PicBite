package com.picbite.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val RU = Locale.forLanguageTag("ru")
private val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

val ColorOk = Color(0xFF43A047)    // в пределах нормы
val ColorLow = Color(0xFFFFB300)   // недобор
val ColorOver = Color(0xFFD32F2F)  // перебор

/** Суммарная пищевая ценность по каждому дню (ключ — ISO-дата). */
fun List<Entry>.dayTotals(): Map<String, Tot> =
    groupBy { it.day }.mapValues { (_, v) -> v.flatMap { it.items }.tot() }

/** Цвет дня относительно цели: 85–105 % — норма, меньше — недобор, больше — перебор. */
fun heatColor(k: Double, goal: Double, primary: Color): Color = when {
    goal <= 0 -> primary
    k > goal * 1.05 -> ColorOver
    k >= goal * 0.85 -> ColorOk
    else -> ColorLow
}

// ======================= Календарь =======================

@Composable
fun CalendarScreen(vm: AppVM, openDay: () -> Unit) {
    val d by vm.data.collectAsState()
    val byDay = remember(d.entries) { d.entries.dayTotals() }
    val primary = MaterialTheme.colorScheme.primary

    var ym by rememberSaveable { mutableStateOf(YearMonth.from(vm.day).toString()) }
    val month = YearMonth.parse(ym)
    val today = LocalDate.now()
    val sel = vm.day

    val monthDays = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val logged = monthDays.mapNotNull { dt -> byDay[dt.toString()]?.takeIf { it.k > 0 } }
    val monthSum = logged.sumOf { it.k }
    val monthAvg = if (logged.isNotEmpty()) monthSum / logged.size else 0.0
    val maxK = logged.maxOfOrNull { it.k } ?: 0.0
    val inGoal = if (d.goal > 0) logged.count { it.k <= d.goal * 1.05 && it.k >= d.goal * 0.85 } else 0

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Календарь", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                // --- заголовок месяца ---
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { ym = month.minusMonths(1).toString() }) { Text("‹", fontSize = 22.sp) }
                    Text(
                        month.month.getDisplayName(TextStyle.FULL_STANDALONE, RU)
                            .replaceFirstChar { it.uppercase() } + " " + month.year,
                        Modifier.weight(1f).clickable {
                            ym = YearMonth.from(today).toString()
                            vm.setDay(today)
                        },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { ym = month.plusMonths(1).toString() }) { Text("›", fontSize = 22.sp) }
                }

                // --- дни недели ---
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    WEEKDAYS.forEachIndexed { i, w ->
                        Text(
                            w, Modifier.weight(1f), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (i >= 5) primary else mutedColor(),
                        )
                    }
                }

                // --- сетка ---
                val offset = month.atDay(1).dayOfWeek.value - 1
                val rows = (offset + month.lengthOfMonth() + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val n = r * 7 + c - offset + 1
                            if (n < 1 || n > month.lengthOfMonth()) {
                                Spacer(Modifier.weight(1f).aspectRatio(0.78f))
                            } else {
                                val date = month.atDay(n)
                                DayCell(
                                    date = date,
                                    tot = byDay[date.toString()],
                                    goal = d.goal,
                                    maxK = maxK,
                                    selected = date == sel,
                                    isToday = date == today,
                                    modifier = Modifier.weight(1f),
                                    onClick = { vm.setDay(date) },
                                )
                            }
                        }
                    }
                }

                // --- легенда ---
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (d.goal > 0) {
                        Legend(ColorLow, "меньше"); Legend(ColorOk, "норма"); Legend(ColorOver, "больше")
                    } else {
                        Text(
                            "Задай цель в настройках — дни окрасятся по норме",
                            style = MaterialTheme.typography.bodySmall, color = mutedColor(),
                        )
                    }
                }
            }
        }

        // --- итоги месяца ---
        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(vertical = 14.dp, horizontal = 8.dp)) {
                Stat("Дней с записями", logged.size.toString(), Modifier.weight(1f))
                Stat("В среднем, ккал", if (logged.isEmpty()) "—" else monthAvg.roundToInt().toString(), Modifier.weight(1f))
                if (d.goal > 0) Stat("В норме", "$inGoal / ${logged.size}", Modifier.weight(1f))
                else Stat("Всего, ккал", monthSum.roundToInt().toString(), Modifier.weight(1f))
            }
        }

        // --- выбранный день ---
        SelectedDayPanel(vm, d, byDay[sel.toString()], openDay)
    }
}

@Composable
private fun mutedColor() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

@Composable
private fun Legend(color: Color, text: String) {
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(" $text", style = MaterialTheme.typography.bodySmall, color = mutedColor())
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = mutedColor(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    tot: Tot?,
    goal: Double,
    maxK: Double,
    selected: Boolean,
    isToday: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val has = tot != null && tot.k > 0
    val bg = when {
        !has -> Color.Transparent
        goal > 0 -> heatColor(tot!!.k, goal, primary).copy(alpha = 0.24f)
        else -> primary.copy(alpha = (0.10f + 0.30f * (tot!!.k / maxK.coerceAtLeast(1.0)).toFloat()))
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .padding(2.dp)
            .aspectRatio(0.78f)
            .clip(shape)
            .background(bg)
            .then(
                when {
                    selected -> Modifier.border(BorderStroke(2.dp, primary), shape)
                    isToday -> Modifier.border(BorderStroke(1.dp, primary.copy(alpha = 0.6f)), shape)
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                fontSize = 15.sp,
                fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
                color = if (isToday) primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (has) tot!!.k.roundToInt().toString() else "·",
                fontSize = 10.sp,
                maxLines = 1,
                fontWeight = if (has) FontWeight.SemiBold else FontWeight.Normal,
                color = if (has) MaterialTheme.colorScheme.onSurface else mutedColor().copy(alpha = 0.35f),
            )
        }
    }
}

@Composable
private fun SelectedDayPanel(vm: AppVM, d: AppData, tot: Tot?, openDay: () -> Unit) {
    val day = vm.day
    val entries = d.entries.filter { it.day == day.toString() }
    val k = tot?.k ?: 0.0
    val primary = MaterialTheme.colorScheme.primary

    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (day == LocalDate.now()) "Сегодня" else day.format(DAY_FMT),
                        fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (tot == null || k <= 0) "Записей нет" else tot.bzu(),
                        style = MaterialTheme.typography.bodySmall, color = mutedColor(),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${k.roundToInt()} ккал",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        color = heatColor(k, d.goal, primary).takeIf { k > 0 && d.goal > 0 } ?: MaterialTheme.colorScheme.onSurface,
                    )
                    if (d.goal > 0 && k > 0) {
                        val left = d.goal - k
                        Text(
                            if (left >= 0) "осталось ${left.roundToInt()}" else "перебор ${(-left).roundToInt()}",
                            style = MaterialTheme.typography.bodySmall, color = mutedColor(),
                        )
                    }
                }
            }

            if (d.goal > 0 && k > 0) {
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))) {
                    Box(
                        Modifier.fillMaxWidth((k / d.goal).toFloat().coerceIn(0.02f, 1f)).height(8.dp)
                            .clip(CircleShape).background(heatColor(k, d.goal, primary))
                    )
                }
            }

            MEALS.forEach { (key, label) ->
                val list = entries.filter { it.mealKey() == key }
                if (list.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("${MEAL_EMOJI[key]} $label", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text("${list.flatMap { it.items }.tot().k.roundToInt()} ккал", color = mutedColor())
                    }
                    list.forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(start = 4.dp)) {
                            Text(e.dish, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(
                                "${e.items.tot().k.roundToInt()}",
                                style = MaterialTheme.typography.bodyMedium, color = mutedColor(),
                            )
                        }
                    }
                }
            }

            Button(onClick = openDay, Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(if (entries.isEmpty()) "Добавить еду на этот день" else "Открыть в дневнике")
            }
        }
    }
}

// ======================= Полоса недели (для дневника) =======================

@Composable
fun WeekStrip(vm: AppVM, byDay: Map<String, Tot>, goal: Double) {
    val primary = MaterialTheme.colorScheme.primary
    val today = LocalDate.now()
    val start = vm.day.minusDays((vm.day.dayOfWeek.value - 1).toLong())
    val days = (0..6).map { start.plusDays(it.toLong()) }
    val top = maxOf(goal, days.maxOf { byDay[it.toString()]?.k ?: 0.0 }, 1.0)

    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 10.dp)) {
            days.forEach { date ->
                val k = byDay[date.toString()]?.k ?: 0.0
                val selected = date == vm.day
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (selected) primary.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { vm.setDay(date) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        WEEKDAYS[date.dayOfWeek.value - 1],
                        style = MaterialTheme.typography.labelSmall, color = mutedColor(),
                    )
                    Box(Modifier.height(36.dp).padding(vertical = 4.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.size(width = 8.dp, height = 28.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            if (k > 0) {
                                Box(
                                    Modifier.size(width = 8.dp, height = (28f * (k / top).toFloat().coerceIn(0.12f, 1f)).dp)
                                        .clip(CircleShape).background(heatColor(k, goal, primary))
                                )
                            }
                        }
                    }
                    Text(
                        date.dayOfMonth.toString(),
                        fontWeight = if (date == today || selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (date == today) primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (k > 0) k.roundToInt().toString() else "·",
                        fontSize = 10.sp, color = mutedColor(),
                    )
                }
            }
        }
    }
}
