package com.picbite.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Net {
    class AiResult(val dish: String, val comment: String, val items: List<Item>)
    class OffResult(val entry: BcEntry?, val hasKcal: Boolean)

    private fun anyNum(v: Any?): Double = when (v) {
        is Number -> v.toDouble()
        is String -> num(v)
        else -> 0.0
    }

    private fun readBody(conn: HttpURLConnection): Pair<Int, String> {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
    }

    /** Запрос к ИИ-серверу (Cloudflare Worker): {image: dataURL, note} -> {text|result}. */
    suspend fun askAi(url: String, token: String, image: String, note: String): AiResult =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15_000
                conn.readTimeout = 25_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-App-Token", token)
                val body = JSONObject().put("image", image).put("note", note).toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val (code, text) = readBody(conn)
                val obj = try { JSONObject(text) } catch (e: Exception) { null }
                if (code !in 200..299) {
                    val msg = obj?.optString("error")?.takeIf { it.isNotBlank() } ?: "Ошибка сервера"
                    throw IOException("$msg ($code)")
                }
                if (obj == null) throw IOException("Сервер вернул не JSON")
                val raw = obj.optString("text").ifBlank { obj.optString("result") }
                val js = extractJson(raw) ?: throw IOException("ИИ ответил не в том формате")
                parse(JSONObject(js))
            } finally {
                conn.disconnect()
            }
        }

    /** Вытаскивает первый корректный JSON-объект из ответа модели (в т.ч. из ```json блока). */
    fun extractJson(s: String): String? {
        if (s.isBlank()) return null
        val m = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE).find(s)
        val t = m?.groupValues?.get(1) ?: s
        var st = t.indexOf('{')
        while (st != -1) {
            var depth = 0
            var inStr = false
            var esc = false
            var i = st
            while (i < t.length) {
                val c = t[i]
                if (inStr) {
                    if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') inStr = false
                } else if (c == '"') {
                    inStr = true
                } else if (c == '{') {
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0) {
                        val cand = t.substring(st, i + 1)
                        try {
                            JSONObject(cand)
                            return cand
                        } catch (e: Exception) {
                            break
                        }
                    }
                }
                i++
            }
            st = t.indexOf('{', st + 1)
        }
        return null
    }

    private fun parse(j: JSONObject): AiResult {
        val arr = j.optJSONArray("items")
        val items = ArrayList<Item>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val g0 = anyNum(o.opt("grams") ?: o.opt("g")).coerceAtLeast(0.0)
                val g = if (g0 == 0.0) 100.0 else g0
                items += Item(
                    name = o.optString("name", "?").ifBlank { "?" },
                    g = g,
                    b = Base(
                        g = g,
                        k = anyNum(o.opt("kcal") ?: o.opt("k")),
                        p = anyNum(o.opt("protein") ?: o.opt("p")),
                        f = anyNum(o.opt("fat") ?: o.opt("f")),
                        c = anyNum(o.opt("carbs") ?: o.opt("c")),
                    ),
                )
            }
        }
        return AiResult(
            dish = j.optString("dish").ifBlank { "Блюдо" },
            comment = j.optString("comment"),
            items = items,
        )
    }

    /** Поиск продукта в Open Food Facts. Бросает исключение при проблемах со связью. */
    suspend fun lookupBarcode(code: String): OffResult = withContext(Dispatchers.IO) {
        val u = "https://world.openfoodfacts.org/api/v2/product/$code.json" +
            "?fields=product_name,product_name_ru,brands,serving_quantity,nutriments"
        val conn = URL(u).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "PicBite/1.0 (Android)")
            val (_, text) = readBody(conn)
            val d = try { JSONObject(text) } catch (e: Exception) { return@withContext OffResult(null, false) }
            val p = (if (d.optInt("status") == 1) d.optJSONObject("product") else null)
                ?: return@withContext OffResult(null, false)
            val n = p.optJSONObject("nutriments") ?: JSONObject()
            val has = n.has("energy-kcal_100g") || n.has("energy_100g")
            val k = if (n.has("energy-kcal_100g")) anyNum(n.opt("energy-kcal_100g"))
            else anyNum(n.opt("energy_100g")) / 4.184
            val n1 = (p.optString("product_name_ru").ifBlank { p.optString("product_name") }).trim()
            val br = p.optString("brands").split(',').first().trim()
            val name = when {
                n1.isNotEmpty() && br.isNotEmpty() && !n1.contains(br, ignoreCase = true) -> "$n1 · $br"
                n1.isNotEmpty() -> n1
                br.isNotEmpty() -> br
                else -> "Продукт $code"
            }
            val sv = anyNum(p.opt("serving_quantity"))
            OffResult(
                BcEntry(
                    n = name,
                    k = k.coerceAtLeast(0.0),
                    p = anyNum(n.opt("proteins_100g")),
                    f = anyNum(n.opt("fat_100g")),
                    c = anyNum(n.opt("carbohydrates_100g")),
                    s = if (sv > 0 && sv <= 2000) sv else 100.0,
                ),
                has,
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Уменьшает фото до max px по длинной стороне, учитывает поворот EXIF, возвращает data:URL. */
    suspend fun loadScaledDataUrl(ctx: Context, uri: Uri, max: Int = 1024): String =
        withContext(Dispatchers.IO) {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) throw IOException("Не удалось открыть фото")
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > max * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: throw IOException("Не удалось открыть фото")
            val orient = cr.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val m = Matrix()
            when (orient) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            }
            val k = minOf(1f, max.toFloat() / maxOf(bmp.width, bmp.height))
            m.postScale(k, k)
            val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            val bos = ByteArrayOutputStream()
            out.compress(Bitmap.CompressFormat.JPEG, 80, bos)
            "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        }
}
