@file:OptIn(ExperimentalMaterial3Api::class)

package com.picbite.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.common.Barcode as MlBarcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val vm: AppVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val d by vm.data.collectAsState()
            PicBiteTheme(d.theme) { App(vm) }
        }
    }
}

// ======================= Тема =======================

@Composable
fun PicBiteTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFFFF7A45), onPrimary = Color(0xFF2B1000),
            background = Color(0xFF17120F), surface = Color(0xFF241C18),
            onBackground = Color(0xFFF3E9E2), onSurface = Color(0xFFF3E9E2),
            secondaryContainer = Color(0xFF4A2A1B), onSecondaryContainer = Color(0xFFFFDBCB),
            surfaceContainer = Color(0xFF241C18),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFFE0510B), onPrimary = Color.White,
            background = Color(0xFFFFF6EE), surface = Color.White,
            onBackground = Color(0xFF2A1A10), onSurface = Color(0xFF2A1A10),
            secondaryContainer = Color(0xFFFFDCCB), onSecondaryContainer = Color(0xFF5A1F00),
            surfaceContainer = Color.White,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun muted() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = content,
    )
}

// ======================= Корень =======================

@Composable
fun App(vm: AppVM) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }

    BackHandler(enabled = showSettings) { showSettings = false }

    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            val r = snack.showSnackbar(ev.text, ev.actionLabel, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) ev.action?.invoke()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (showSettings) "Настройки" else "🥗 PicBite", 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    if (showSettings) {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Назад")
                        }
                    }
                },
                actions = {
                    if (!showSettings) {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Календарь")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки")
                        }
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (showSettings) {
                SettingsScreen(vm)
            } else {
                MainScreen(vm)
            }
        }
    }

    // Диалог календаря
    if (showDatePicker) {
        val initialMillis = vm.day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.setDay(date) // ВАЖНО: добавьте этот метод в AppVM
                    }
                    showDatePicker = false
                }) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    vm.pending?.let { ResultDialog(vm, it) }
    vm.manual?.let { ManualDialog(vm, it) }
}

// ======================= Главный экран (Дашборд без прокрутки) =======================

val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM, EEE", Locale.forLanguageTag("ru"))
val MEAL_EMOJI = mapOf("b" to "🍳", "l" to "🍲", "d" to "🍽", "s" to "🍎")
private val C_PROT = Color(0xFF3F8EF5)
private val C_FAT = Color(0xFFF5A623)
private val C_CARB = Color(0xFF43A047)

@Composable
fun MainScreen(vm: AppVM) {
    val d by vm.data.collectAsState()
    val ctx = LocalContext.current
    var note by rememberSaveable { mutableStateOf("") }
    var camUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showBc by remember { mutableStateOf(false) }
    var selectedMeal by remember { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) camUri?.let { vm.analyze(it, note) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.analyze(uri, note)
    }

    LaunchedEffect(vm.pending) { if (vm.pending != null) note = "" }

    fun takePhoto() {
        val dir = File(ctx.cacheDir, "images").apply { mkdirs() }
        val f = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        camUri = u
        camera.launch(u)
    }

    val dayKey = vm.day.toString()
    val entries = d.entries.filter { it.day == dayKey }
    val total = entries.flatMap { it.items }.tot()
    val isToday = vm.day == LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Строка с датой
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isToday) "Сегодня · " + vm.day.format(DAY_FMT) else vm.day.format(DAY_FMT),
                color = muted(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            if (!isToday) {
                TextButton(onClick = { vm.goToday() }, contentPadding = PaddingValues(0.dp)) { Text("К сегодня") }
            }
        }

        // 2. Кольцо калорий и БЖУ (занимает чуть больше половины)
        Panel(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.1f)
        ) {
            Column(
                Modifier.fillMaxSize().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Ring(total.k, d.goal)
                if (d.goal > 0) {
                    val left = d.goal - total.k
                    Text(
                        if (left >= 0) "Осталось ${left.roundToInt()} ккал" else "Перебор на ${(-left).roundToInt()} ккал",
                        color = if (left >= 0) muted() else Color(0xFFE53935), // Цвет перебора
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    MacroCell("Белки", total.p, C_PROT, Modifier.weight(1f))
                    MacroCell("Жиры", total.f, C_FAT, Modifier.weight(1f))
                    MacroCell("Углеводы", total.c, C_CARB, Modifier.weight(1f))
                }
            }
        }

        // 3. Ввод (компактный)
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Описание еды...", style = MaterialTheme.typography.bodySmall) },
                    minLines = 1,
                    maxLines = 2,
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = ::takePhoto, enabled = !vm.busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("📷 Фото", style = MaterialTheme.typography.bodySmall) }
                    OutlinedButton(
                        onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !vm.busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)
                    ) { Text("🖼 Галерея", style = MaterialTheme.typography.bodySmall) }
                    OutlinedButton(onClick = { showBc = true }, enabled = !vm.busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("📦 Код", style = MaterialTheme.typography.bodySmall) }
                }
                Button(
                    onClick = { vm.analyze(null, note) },
                    enabled = !vm.busy && note.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("✨ Посчитать по тексту", style = MaterialTheme.typography.bodySmall) }
                if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (vm.message.isNotEmpty()) {
                    Text(vm.message, color = if (vm.messageErr) MaterialTheme.colorScheme.error else muted(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 4. Приемы пищи (Занимают оставшееся место)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.5f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MEALS.forEach { (key, label) ->
                val list = entries.filter { it.mealKey() == key }
                val kcal = list.flatMap { it.items }.tot().k.roundToInt()
                MealSummaryCard(
                    emoji = MEAL_EMOJI[key] ?: "",
                    title = label,
                    kcal = kcal,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedMeal = key }
                )
            }
        }
    }

    // Нижнее модальное окно (BottomSheet) с деталями приема пищи
    if (selectedMeal != null) {
        val mealKey = selectedMeal!!
        val mealLabel = MEALS.find { it.first == mealKey }?.second ?: ""
        val list = entries.filter { it.mealKey() == mealKey }

        ModalBottomSheet(
            onDismissRequest = { selectedMeal = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${MEAL_EMOJI[mealKey]} $mealLabel", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                
                if (list.isEmpty()) {
                    Text("Пока пусто. Добавь еду, чтобы она появилась здесь.", color = muted())
                    Spacer(Modifier.height(32.dp))
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list, key = { it.id }) { e ->
                            EntryCard(
                                e,
                                onEdit = { vm.edit(e); selectedMeal = null },
                                onFav = { vm.addFav(e.dish, e.items) },
                                onDel = { vm.delete(e) },
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    if (showBc) BarcodeDialog(vm, onDismiss = { showBc = false })
}

@Composable
fun MealSummaryCard(
    emoji: String,
    title: String,
    kcal: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Panel(modifier = modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                if (kcal > 0) "$kcal ккал" else "Добавить",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (kcal > 0) MaterialTheme.colorScheme.primary else muted()
            )
        }
    }
}

@Composable
fun MacroCell(label: String, grams: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 24.dp, height = 4.dp).clip(CircleShape).background(color))
        Text("${grams.roundToInt()} г", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        Text(label, color = muted(), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun Ring(eaten: Double, goal: Double) {
    val over = goal > 0 && eaten > goal
    val target = if (goal > 0) (eaten / goal).toFloat().coerceIn(0f, 1f) else 0f
    val frac by animateFloatAsState(target, label = "ring")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val col = if (over) Color(0xFFE53935) else MaterialTheme.colorScheme.primary
    Box(Modifier.size(160.dp).padding(8.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 16.dp.toPx()
            val s = Size(size.width - w, size.height - w)
            val tl = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, topLeft = tl, size = s, style = Stroke(w, cap = StrokeCap.Round))
            if (frac > 0f) {
                drawArc(col, -90f, 360f * frac, false, topLeft = tl, size = s, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(eaten.roundToInt().toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(if (goal > 0) "из ${goal.roundToInt()} ккал" else "ккал", color = muted(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun EntryCard(e: Entry, onEdit: () -> Unit, onFav: () -> Unit, onDel: () -> Unit) {
    val t = e.items.tot()
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.dish, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text("${t.k.roundToInt()} ккал", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Text(
                e.items.joinToString(" · ") { "${it.name} ${fmt(it.g)} г" },
                color = muted(), style = MaterialTheme.typography.bodySmall,
            )
            Text(t.bzu(), color = muted(), style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = onEdit) { Text("✏️") }
                TextButton(onClick = onFav) { Text("⭐") }
                TextButton(onClick = onDel) { Text("🗑") }
            }
        }
    }
}

// ======================= Диалоги =======================

class EditRow(val name: String, val b: Base, g0: Double) {
    var g by mutableStateOf(g0)
}

@Composable
fun EditRowView(r: EditRow, onRemove: () -> Unit) {
    var txt by remember { mutableStateOf(fmt(r.g)) }
    val kcal = Item(r.name, r.g, r.b).kcal().roundToInt()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.name, maxLines = 2)
            Text("$kcal ккал", color = muted(), style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = txt,
            onValueChange = { txt = it; r.g = num(it) },
            modifier = Modifier.width(104.dp),
            singleLine = true,
            suffix = { Text("г") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        TextButton(onClick = onRemove) { Text("✕") }
    }
}

@Composable
fun ResultDialog(vm: AppVM, p: Pending) {
    var meal by remember(p) { mutableStateOf(p.meal) }
    val rows: SnapshotStateList<EditRow> = remember(p) { p.items.map { EditRow(it.name, it.b, it.g) }.toMutableStateList() }
    val items = rows.map { Item(it.name, it.g, it.b) }
    val t = items.tot()

    AlertDialog(
        onDismissRequest = { vm.closePending() },
        title = { Text(p.dish) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (p.comment.isNotBlank()) Text(p.comment, color = muted(), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MEALS.forEach { (k, l) ->
                        FilterChip(selected = meal == k, onClick = { meal = k }, label = { Text(l) })
                    }
                }
                rows.toList().forEach { r -> key(r) { EditRowView(r) { rows.remove(r) } } }
                HorizontalDivider()
                Text("Итого: ${t.k.roundToInt()} ккал", fontWeight = FontWeight.Bold)
                Text(t.bzu(), color = muted(), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { vm.commit(p, meal, items) }, enabled = items.isNotEmpty()) {
                Text(if (p.eid != null) "Сохранить" else "Добавить")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.addFav(p.dish, items) }, enabled = items.isNotEmpty()) { Text("⭐") }
                TextButton(onClick = { vm.closePending() }) { Text("Отмена") }
            }
        },
    )
}

@Composable
fun ManualDialog(vm: AppVM, m: ManualBc) {
    val pre = m.pre
    var name by remember(m) { mutableStateOf(pre?.n ?: "") }
    var k by remember(m) { mutableStateOf(if (pre != null && pre.k > 0) fmt(pre.k) else "") }
    var p by remember(m) { mutableStateOf(if (pre != null) fmt(pre.p) else "") }
    var f by remember(m) { mutableStateOf(if (pre != null) fmt(pre.f) else "") }
    var c by remember(m) { mutableStateOf(if (pre != null) fmt(pre.c) else "") }
    val dec = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = { vm.closeManual() },
        title = { Text("Код ${m.code}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(m.title, color = muted(), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(k, { k = it }, label = { Text("Ккал на 100 г") }, singleLine = true, keyboardOptions = dec, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(p, { p = it }, label = { Text("Белки, г") }, singleLine = true, keyboardOptions = dec, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(f, { f = it }, label = { Text("Жиры, г") }, singleLine = true, keyboardOptions = dec, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(c, { c = it }, label = { Text("Углеводы, г") }, singleLine = true, keyboardOptions = dec, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    name.isBlank() -> vm.toast("Впиши название")
                    k.isBlank() -> vm.toast("Впиши калории на 100 г")
                    else -> vm.saveManual(m, name.trim(), num(k), num(p), num(f), num(c))
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = { vm.closeManual() }) { Text("Отмена") } },
    )
}

@Composable
fun BarcodeDialog(vm: AppVM, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var code by remember { mutableStateOf("") }

    fun scan() {
        val opts = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(MlBarcode.FORMAT_EAN_13, MlBarcode.FORMAT_EAN_8, MlBarcode.FORMAT_UPC_A, MlBarcode.FORMAT_UPC_E)
            .build()
        GmsBarcodeScanning.getClient(ctx, opts).startScan()
            .addOnSuccessListener { b -> b.rawValue?.let { vm.barcodeFound(it) } }
            .addOnFailureListener { vm.toast("Сканер недоступен: " + (it.message ?: "неизвестная ошибка")) }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Штрихкод") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::scan, modifier = Modifier.fillMaxWidth()) { Text("📷 Сканировать камерой") }
                Text("или введи цифры вручную", color = muted(), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    code, { code = it.filter(Char::isDigit) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("4600000000000") },
                )
            }
        },
        confirmButton = {
            Button(onClick = { vm.barcodeFound(code); onDismiss() }, enabled = code.length >= 8) { Text("Найти") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ======================= Настройки =======================

@Composable
fun SettingsScreen(vm: AppVM) {
    val d by vm.data.collectAsState()
    val ctx = LocalContext.current
    var url by remember { mutableStateOf(d.url) }
    var tok by remember { mutableStateOf(d.token) }
    var goal by remember { mutableStateOf(if (d.goal > 0) fmt(d.goal) else "") }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray(Charsets.UTF_8)) }
                vm.toast("Резервная копия сохранена")
            } catch (e: Exception) {
                vm.toast("Не удалось сохранить файл")
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = try {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } catch (e: Exception) {
                null
            }
            if (text != null && vm.importJson(text)) vm.toast("Данные загружены") else vm.toast("Данные не подошли")
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ИИ-сервер", fontWeight = FontWeight.SemiBold)
                Text(
                    "Адрес и пароль твоего Cloudflare Worker — те же, что в веб-версии.",
                    color = muted(), style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(url, { url = it }, label = { Text("Адрес сервера (https://…)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(tok, { tok = it }, label = { Text("Пароль (X-App-Token)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(goal, { goal = it }, label = { Text("Цель, ккал в день") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Button(onClick = { vm.saveSettings(url.trim(), tok.trim(), num(goal)) }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Тема", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("auto" to "Авто", "light" to "Светлая", "dark" to "Тёмная").forEach { (k, l) ->
                        FilterChip(selected = d.theme == k, onClick = { vm.setTheme(k) }, label = { Text(l) })
                    }
                }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Резервная копия", fontWeight = FontWeight.SemiBold)
                Text(
                    "Формат файла совпадает с веб-версией: можно переносить дневник между PWA и этим приложением. " +
                        "Адрес сервера и пароль в копию не попадают.",
                    color = muted(), style = MaterialTheme.typography.bodySmall,
                )
                if (d.lastBackup > 0) {
                    val dt = Instant.ofEpochMilli(d.lastBackup).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    Text("Последняя копия: $dt", color = muted(), style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exporter.launch("calories-${LocalDate.now()}.json") }, modifier = Modifier.weight(1f)) { Text("Экспорт") }
                    OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("Импорт") }
                }
            }
        }

        Text(
            "🔒 Дневник хранится только на этом телефоне. На сервер уходят лишь фото и текст для распознавания.",
            color = muted(), style = MaterialTheme.typography.bodySmall,
        )
    }
}
