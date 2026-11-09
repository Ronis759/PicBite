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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.net.SocketTimeoutException
import java.time.LocalDate

/** Результат распознавания, открытый в диалоге (eid != null — редактирование записи дневника). */
class Pending(
    val dish: String,
    val comment: String,
    val items: List<Item>,
    val meal: String,
    val eid: Long?,
    val barcode: String?,
)

class ManualBc(val code: String, val title: String, val pre: BcEntry?)

class Ev(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

class AppVM(app: Application) : AndroidViewModel(app) {
    private val file = File(app.filesDir, "calories.json")
    private val _data = MutableStateFlow(readFile())
    val data: StateFlow<AppData> = _data

    var day by mutableStateOf(LocalDate.now())
        private set
    var pending by mutableStateOf<Pending?>(null)
        private set
    var manual by mutableStateOf<ManualBc?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    var messageErr by mutableStateOf(false)
        private set

    val events = MutableSharedFlow<Ev>(extraBufferCapacity = 8)

    // ---------- хранение ----------

    private fun readFile(): AppData {
        if (!file.exists()) return AppData()
        return try {
            AppJson.decodeFromString(AppData.serializer(), file.readText())
        } catch (e: Exception) {
            // повреждённый файл не затираем молча — сохраняем копию
            try { file.copyTo(File(file.path + ".bak"), overwrite = true) } catch (_: Exception) {}
            AppData()
        }
    }

    private fun persist() {
        viewModelScope.launch(Dispatchers.IO) {
            synchronized(file) {
                try {
                    val snap = _data.value // всегда пишем самое свежее состояние
                    val tmp = File(file.path + ".tmp")
                    tmp.writeText(AppJson.encodeToString(AppData.serializer(), snap))
                    tmp.renameTo(file)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun mutate(block: (AppData) -> AppData) {
        _data.update(block)
        persist()
    }

    fun toast(t: String) {
        events.tryEmit(Ev(t))
    }

    private fun setMsg(t: String, err: Boolean) {
        message = t
        messageErr = err
    }

    // ---------- день ----------

    fun setDay(d: LocalDate) { day = d }
    fun shiftDay(n: Long) { day = day.plusDays(n) }
    fun goToday() { day = LocalDate.now() }

    // ---------- ИИ ----------

    fun analyze(uri: Uri?, note: String) {
        val d = _data.value
        if (busy) return
        if (uri == null && note.isBlank()) {
            setMsg("Впиши в поле, что съел, или сними фото", true); return
        }
        if (d.url.isBlank() || d.token.isBlank()) {
            setMsg("Сначала впиши адрес сервера и пароль в настройках", true); return
        }
        busy = true
        setMsg(if (uri != null) "Анализирую фото…" else "Считаю…", false)
        viewModelScope.launch {
            try {
                val img = uri?.let { Net.loadScaledDataUrl(getApplication(), it) } ?: ""
                val r = Net.askAi(d.url, d.token, img, note.trim())
                if (r.items.isEmpty()) {
                    setMsg("Еда не найдена. Попробуй по-другому.", true)
                } else {
                    setMsg("", false)
                    pending = Pending(r.dish, r.comment, r.items, autoMeal(), null, null)
                }
            } catch (e: SocketTimeoutException) {
                setMsg("Превышено время ожидания сервера", true)
            } catch (e: Exception) {
                setMsg("Не вышло: " + (e.message ?: e.javaClass.simpleName), true)
            } finally {
                busy = false
            }
        }
    }

    fun closePending() { pending = null }

    fun edit(e: Entry) {
        pending = Pending(e.dish, "", e.items, e.mealKey(), e.id, null)
    }

    fun commit(p: Pending, meal: String, items: List<Item>) {
        if (p.eid != null) {
            mutate { d ->
                d.copy(entries = d.entries.map { if (it.id == p.eid) it.copy(meal = meal, items = items) else it })
            }
        } else {
            val e = Entry(
                id = System.currentTimeMillis(),
                day = day.toString(),
                meal = meal,
                dish = p.dish,
                items = items,
            )
            mutate { it.copy(entries = it.entries + e) }
        }
        pending = null
    }

    fun delete(e: Entry) {
        mutate { d -> d.copy(entries = d.entries.filter { it.id != e.id }) }
        events.tryEmit(Ev("Удалено", "Отменить") { mutate { d -> d.copy(entries = d.entries + e) } })
    }

    // ---------- избранное ----------

    fun addFav(dish: String, items: List<Item>) {
        if (_data.value.favs.any { it.dish == dish }) {
            toast("Уже в избранном"); return
        }
        mutate { it.copy(favs = it.favs + Fav(System.currentTimeMillis(), dish, items)) }
        toast("Добавлено в избранное")
    }

    fun removeFav(id: Long) {
        mutate { d -> d.copy(favs = d.favs.filter { it.id != id }) }
    }

    fun addFavToDay(f: Fav) {
        val e = Entry(System.currentTimeMillis(), day.toString(), autoMeal(), f.dish, f.items)
        mutate { it.copy(entries = it.entries + e) }
        toast("Добавлено: ${f.dish}")
    }

    // ---------- штрихкоды ----------

    private fun openBc(code: String, l: BcEntry) {
        val g = if (l.s > 0) l.s else 100.0
        pending = Pending(
            dish = l.n,
            comment = "Штрихкод $code" + if (l.m == 1) " · твои данные" else " · Open Food Facts",
            items = listOf(Item(l.n, g, Base(100.0, l.k, l.p, l.f, l.c))),
            meal = autoMeal(),
            eid = null,
            barcode = code,
        )
    }

    fun barcodeFound(raw: String) {
        val code = raw.filter { it.isDigit() }
        if (code.length !in 8..14) {
            toast("Код должен содержать от 8 до 14 цифр"); return
        }
        _data.value.barcodes[code]?.let { openBc(code, it); return }
        if (busy) return
        busy = true
        setMsg("Ищу $code…", false)
        viewModelScope.launch {
            try {
                val r = Net.lookupBarcode(code)
                val e = r.entry
                setMsg("", false)
                when {
                    e == null -> manual = ManualBc(code, "Этого продукта нет в базе. Введи данные с упаковки на 100 г", null)
                    !r.hasKcal -> manual = ManualBc(code, "В базе нет калорий для этого продукта. Введи данные с упаковки на 100 г", e.copy(k = 0.0))
                    else -> {
                        mutate { it.copy(barcodes = it.barcodes + (code to e)) }
                        openBc(code, e)
                    }
                }
            } catch (e: Exception) {
                setMsg("", false)
                val t = if (e is SocketTimeoutException) "База не ответила вовремя. Можно ввести данные вручную"
                else "Нет связи с базой. Можно ввести данные вручную"
                manual = ManualBc(code, t, null)
            } finally {
                busy = false
            }
        }
    }

    fun closeManual() { manual = null }

    fun saveManual(m: ManualBc, name: String, k: Double, p: Double, f: Double, c: Double) {
        val e = BcEntry(
            n = name,
            k = k.coerceAtLeast(0.0),
            p = p.coerceAtLeast(0.0),
            f = f.coerceAtLeast(0.0),
            c = c.coerceAtLeast(0.0),
            s = m.pre?.s?.takeIf { it > 0 } ?: 100.0,
            m = 1,
        )
        mutate { it.copy(barcodes = it.barcodes + (m.code to e)) }
        manual = null
        openBc(m.code, e)
    }

    // ---------- настройки и бэкап ----------

    fun saveSettings(url: String, token: String, goal: Double) {
        mutate { it.copy(url = url, token = token, goal = goal.coerceAtLeast(0.0)) }
        toast("Сохранено")
    }

    fun setTheme(t: String) { mutate { it.copy(theme = t) } }

    /** Адрес сервера и пароль в бэкап не попадают. */
    fun exportJson(): String {
        mutate { it.copy(lastBackup = System.currentTimeMillis()) }
        return AppJson.encodeToString(AppData.serializer(), _data.value.copy(url = "", token = ""))
    }

    fun importJson(text: String): Boolean = try {
        val j = AppJson.decodeFromString(AppData.serializer(), text)
        val cur = _data.value
        mutate { j.copy(url = cur.url, token = cur.token) }
        true
    } catch (e: Exception) {
        false
    }
}
