package com.picbite.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.UUID

class AppVM(app: Application) : AndroidViewModel(app) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file = File(app.filesDir, "picbite_data.json")

    private val _data = MutableStateFlow(AppData())
    val data = _data.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()

    // Состояния интерфейса
    var day by mutableStateOf(LocalDate.now())
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    var messageErr by mutableStateOf(false)
        private set

    var pending by mutableStateOf<Pending?>(null)
        private set
    var manual by mutableStateOf<ManualBc?>(null)
        private set

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            if (file.exists()) {
                try {
                    val text = file.readText()
                    val loaded = json.decodeFromString<AppData>(text)
                    _data.value = loaded
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                file.writeText(json.encodeToString(AppData.serializer(), _data.value))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Навигация по датам ---
    fun setDay(date: LocalDate) {
        day = date
    }

    fun shiftDay(days: Long) {
        day = day.plusDays(days)
    }

    fun goToday() {
        day = LocalDate.now()
    }

    // --- Уведомления ---
    fun toast(text: String) {
        viewModelScope.launch { _events.emit(UiEvent(text)) }
    }

    // --- Логика ИИ ---
    fun analyze(uri: Uri?, note: String) {
        if (busy) return
        val d = _data.value
        if (d.url.isBlank() || d.token.isBlank()) {
            message = "Сначала укажите сервер и пароль в настройках"
            messageErr = true
            return
        }

        viewModelScope.launch {
            busy = true
            message = "Анализируем..."
            messageErr = false
            try {
                val base64 = uri?.let { uriToBase64(it) }
                val result = Net.analyze(d.url, d.token, base64, note)
                pending = result.copy(meal = guessMeal())
                message = ""
            } catch (e: Exception) {
                message = e.message ?: "Ошибка распознавания"
                messageErr = true
            } finally {
                busy = false
            }
        }
    }

    private fun guessMeal(): String {
        val hour = java.time.LocalTime.now().hour
        return when (hour) {
            in 4..10 -> "b"
            in 11..15 -> "l"
            in 16..21 -> "d"
            else -> "s"
        }
    }

    private suspend fun uriToBase64(uri: Uri): String = withContext(Dispatchers.IO) {
        val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes() ?: ByteArray(0)
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    // --- Штрихкоды ---
    fun barcodeFound(code: String) {
        viewModelScope.launch {
            busy = true
            try {
                val res = Net.fetchBarcode(code)
                if (res != null) {
                    val (base, name) = res
                    pending = Pending(
                        dish = name ?: "Продукт $code",
                        comment = "Данные из Open Food Facts",
                        items = listOf(Item(name ?: "Продукт", 100.0, base)),
                        meal = guessMeal()
                    )
                } else {
                    manual = ManualBc(code = code, title = "Продукт не найден. Введите данные вручную.")
                }
            } catch (e: Exception) {
                toast("Ошибка сети: ${e.message}")
            } finally {
                busy = false
            }
        }
    }

    // --- Ручной ввод ---
    fun saveManual(m: ManualBc, name: String, k: Double, p: Double, f: Double, c: Double) {
        val base = Base(k, p, f, c)
        pending = Pending(
            dish = name,
            comment = "Добавлено вручную",
            items = listOf(Item(name, 100.0, base)),
            meal = guessMeal()
        )
        manual = null
    }

    // --- Сохранение / Редактирование ---
    fun commit(p: Pending, meal: String, items: List<Item>) {
        val currentData = _data.value
        val dayStr = day.toString()

        if (p.eid != null) {
            // Редактирование существующей
            val newEntries = currentData.entries.map { e ->
                if (e.id == p.eid) e.copy(meal = meal, dish = p.dish, items = items) else e
            }
            _data.value = currentData.copy(entries = newEntries)
        } else {
            // Новая запись
            val newEntry = Entry(
                id = UUID.randomUUID().toString(),
                day = dayStr,
                meal = meal,
                dish = p.dish,
                items = items
            )
            _data.value = currentData.copy(entries = currentData.entries + newEntry)
        }
        save()
        pending = null
    }

    fun edit(e: Entry) {
        pending = Pending(dish = e.dish, comment = "", items = e.items, meal = e.meal, eid = e.id)
    }

    fun delete(e: Entry) {
        val currentData = _data.value
        _data.value = currentData.copy(entries = currentData.entries.filter { it.id != e.id })
        save()
        viewModelScope.launch {
            _events.emit(UiEvent("Удалено", "Отменить") {
                _data.value = _data.value.copy(entries = _data.value.entries + e)
                save()
            })
        }
    }

    // --- Избранное ---
    fun addFav(dish: String, items: List<Item>) {
        val currentData = _data.value
        val newFav = Fav(id = UUID.randomUUID().toString(), dish = dish, items = items)
        _data.value = currentData.copy(favs = currentData.favs + newFav)
        save()
        toast("Добавлено в избранное")
    }

    fun removeFav(id: String) {
        val currentData = _data.value
        _data.value = currentData.copy(favs = currentData.favs.filter { it.id != id })
        save()
    }

    fun addFavToDay(f: Fav) {
        val newEntry = Entry(
            id = UUID.randomUUID().toString(),
            day = day.toString(),
            meal = guessMeal(),
            dish = f.dish,
            items = f.items
        )
        _data.value = _data.value.copy(entries = _data.value.entries + newEntry)
        save()
        toast("Добавлено: ${f.dish}")
    }

    // --- Настройки ---
    fun saveSettings(url: String, token: String, goal: Double) {
        _data.value = _data.value.copy(url = url, token = token, goal = goal)
        save()
        toast("Настройки сохранены")
    }

    fun setTheme(theme: String) {
        _data.value = _data.value.copy(theme = theme)
        save()
    }

    // --- Экспорт / Импорт ---
    fun exportJson(): String {
        return json.encodeToString(AppData.serializer(), _data.value)
    }

    fun importJson(text: String): Boolean {
        return try {
            val imported = json.decodeFromString<AppData>(text)
            _data.value = imported.copy(lastBackup = System.currentTimeMillis())
            save()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun closePending() { pending = null }
    fun closeManual() { manual = null }
}
