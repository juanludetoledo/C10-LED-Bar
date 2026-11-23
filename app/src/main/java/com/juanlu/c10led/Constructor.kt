package com.juanlu.c10led

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// =============================================================================================
// CONSTRUCTOR DE ANIMACIONES
//  - Una animacion = pasos (34 canales, 0..255) + duracion de cada paso (unidades de 20 ms, 3..255)
//  - 4 ranuras locales (las mismas 4 de la barra). Se guardan en el movil.
// =============================================================================================
const val CAN = 34
const val MAX_PASOS = 64

class Anim(
    var suave: Boolean = true,
    var pico: Int = 100,
    val pasos: MutableList<IntArray> = mutableListOf(),
    val durs: MutableList<Int> = mutableListOf(),
    var nombre: String = ""
) {
    fun copia() = Anim(suave, pico, pasos.map { it.copyOf() }.toMutableList(), durs.toMutableList(), nombre)
    fun vacia() = pasos.isEmpty()
    fun totalMs(): Long { var s = 0L; for (d in durs) s += d * 20L; return s }
    /** Misma suma que calcula la barra (duraciones + niveles) para comprobar que ha llegado todo. */
    fun suma(): Int {
        var s = 0
        for (i in pasos.indices) { s += durs[i]; for (v in pasos[i]) s += v }
        return s and 0xFFFF
    }
    fun hex(i: Int): String {
        val sb = StringBuilder()
        for (v in pasos[i]) { val x = v.coerceIn(0, 255); sb.append("0123456789ABCDEF"[x shr 4]); sb.append("0123456789ABCDEF"[x and 15]) }
        return sb.toString()
    }
}

/** Historial para deshacer: copia del estado "tranquilo" de una ranura y lista de estados anteriores. */
class UndoBuf(var baseSel: Int, var base: Anim?) {
    val hist = ArrayList<Pair<Int, Anim?>>()
}

fun mismoAnim(x: Anim?, y: Anim?): Boolean {
    if (x == null || y == null) return x == null && y == null
    if (x.suave != y.suave || x.pico != y.pico || x.nombre != y.nombre || x.pasos.size != y.pasos.size) return false
    for (i in x.pasos.indices) { if (x.durs[i] != y.durs[i] || !x.pasos[i].contentEquals(y.pasos[i])) return false }
    return true
}

object AnimStore {
    private const val CLAVE = "anims_v1"
    fun cargar(ctx: Context): Array<Anim?> {
        val res = arrayOfNulls<Anim>(4)
        try {
            val txt = ctx.getSharedPreferences("c10prefs", 0).getString(CLAVE, "") ?: ""
            if (txt.isEmpty()) return res
            val arr = JSONArray(txt)
            for (i in 0 until min(4, arr.length())) {
                val o = arr.optJSONObject(i) ?: continue
                val d = o.getJSONArray("d"); val v = o.getJSONArray("v")
                val a = Anim(o.optInt("s", 1) != 0, o.optInt("p", 100), mutableListOf(), mutableListOf(), o.optString("n", ""))
                for (k in 0 until min(d.length(), v.length())) {
                    val h = v.getString(k)
                    if (h.length < CAN * 2) continue
                    a.pasos.add(IntArray(CAN) { c -> h.substring(c * 2, c * 2 + 2).toInt(16) })
                    a.durs.add(d.getInt(k).coerceIn(3, 255))
                }
                if (!a.vacia()) res[i] = a
            }
        } catch (e: Exception) { }
        return res
    }

    fun guardar(ctx: Context, anims: List<Anim?>) {
        try {
            val arr = JSONArray()
            for (a in anims) {
                if (a == null || a.vacia()) { arr.put(JSONObject()); continue }
                val o = JSONObject()
                o.put("n", a.nombre); o.put("s", if (a.suave) 1 else 0); o.put("p", a.pico)
                val d = JSONArray(); val v = JSONArray()
                for (i in a.pasos.indices) { d.put(a.durs[i]); v.put(a.hex(i)) }
                o.put("d", d); o.put("v", v)
                arr.put(o)
            }
            ctx.getSharedPreferences("c10prefs", 0).edit().putString(CLAVE, arr.toString()).apply()
        } catch (e: Exception) { }
    }
}

/** Igual que la barra: niveles 0..1 de los 34 canales en el instante tMs. */
fun nivelesAnim(a: Anim, tMs: Long, bucle: Boolean, out: FloatArray) {
    if (a.vacia()) { java.util.Arrays.fill(out, 0f); return }
    val n = a.pasos.size
    val cum = LongArray(n + 1)
    for (i in 0 until n) cum[i + 1] = cum[i] + a.durs[i] * 20L
    val total = max(1L, cum[n])
    val t = if (bucle) tMs % total else min(tMs, total - 1)
    var k = 0
    while (k + 1 < n && cum[k + 1] <= t) k++
    val f = ((t - cum[k]).toFloat() / (a.durs[k] * 20f)).coerceIn(0f, 1f)
    val k2 = if (k + 1 < n) k + 1 else if (bucle) 0 else k
    for (p in 0 until CAN) {
        var v = a.pasos[k][p].toFloat()
        if (a.suave) v += (a.pasos[k2][p] - v) * f
        out[p] = v / 255f
    }
}

// ---------------------------------------------------------------------------------------------
// Movimientos basicos (generan los pasos automaticamente)
// ---------------------------------------------------------------------------------------------
object Gen {
    const val TIPOS = 10

    fun crear(tipo: Int, ciclo: Float, cola: Float, pico: Int, suave: Boolean): Anim {
        val n = (ciclo * 1000f / 60f).toInt().coerceIn(12, 48)
        val dur = (ciclo * 50f / n).roundToInt().coerceIn(3, 255)
        val frames = frames(tipo, n, ciclo, cola)
        val a = Anim(suave, pico)
        for (f in frames) {
            a.pasos.add(IntArray(CAN) { (f[it].coerceIn(0f, 1f) * 255f).roundToInt() })
            a.durs.add(dur)
        }
        return a
    }

    private fun cometas(n: Int, ciclo: Float, cola: Float, vel: Float, pos: (Float) -> FloatArray): List<FloatArray> {
        val sub = 3
        val dtSub = ciclo / (n * sub)
        val tau = max(0.04f, cola * ciclo / (37f * vel))
        val dec = exp(-dtSub / tau)
        val lv = FloatArray(CAN)
        val out = ArrayList<FloatArray>()
        for (vuelta in 0 until 3) {
            for (i in 0 until n) {
                for (s in 0 until sub) {
                    val fase = ((i * sub + s + 1).toFloat()) / (n * sub)
                    for (c in 0 until CAN) lv[c] *= dec
                    for (x in pos(fase)) for (c in 0 until CAN) {
                        val d = c - x
                        val g = exp(-d * d / 1.8f)
                        if (g > lv[c]) lv[c] = g
                    }
                }
                if (vuelta == 2) out.add(lv.copyOf())
            }
        }
        return out
    }

    private fun frames(tipo: Int, n: Int, ciclo: Float, cola: Float): List<FloatArray> {
        when (tipo) {
            1 -> return cometas(n, ciclo, cola, 1f) { f -> floatArrayOf(-3f + 40f * f) }
            2 -> return cometas(n, ciclo, cola, 1f) { f -> floatArrayOf(36f - 40f * f) }
            3 -> return cometas(n, ciclo, cola, 2f) { f -> floatArrayOf(16.5f - 16.5f * sin(2f * PI.toFloat() * f)) }
            4 -> return cometas(n, ciclo, cola, 1f) { f ->
                if (f < 0.7f) { val p = f / 0.7f; floatArrayOf(16.5f * p, 33f - 16.5f * p) } else floatArrayOf()
            }
            5 -> return cometas(n, ciclo, cola, 1f) { f ->
                if (f < 0.7f) { val p = f / 0.7f; floatArrayOf(16.5f - 16.5f * p, 16.5f + 16.5f * p) } else floatArrayOf()
            }
        }
        val res = ArrayList<FloatArray>()
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val lv = FloatArray(CAN)
            for (c in 0 until CAN) {
                lv[c] = when (tipo) {
                    6 -> { // expansion / contraccion desde el centro
                        val tri = 1f - abs(2f * t - 1f)
                        val w = 18f * tri
                        ((w - abs(c - 16.5f)) / 2.5f).coerceIn(0f, 1f)
                    }
                    7 -> { // olas
                        val o = (0.5f + 0.5f * sin(2f * PI.toFloat() * (1.4f * c / 33f - t))).pow(1.7f)
                        0.08f + 0.92f * o
                    }
                    8 -> 0.05f + 0.9f * (0.5f - 0.5f * cos(2f * PI.toFloat() * t)) // respiracion
                    9 -> { // destellos
                        val ph = ((c * 7919 + 13) % 97) / 97f
                        var d = abs(t - ph); if (d > 0.5f) d = 1f - d
                        val x = (1f - d / 0.12f).coerceIn(0f, 1f)
                        x * x
                    }
                    else -> { // 10 relleno y vaciado
                        if (t < 0.5f) (34f * t * 2f - c).coerceIn(0f, 1f)
                        else (c + 1f - 34f * (t - 0.5f) * 2f).coerceIn(0f, 1f)
                    }
                }
            }
            res.add(lv)
        }
        return res
    }
}

// ---------------------------------------------------------------------------------------------
// Emulador de la secuencia entera (las partes de fabrica son aproximadas)
// ---------------------------------------------------------------------------------------------
class CfgSec(
    val w: Int, val a: Int, val b: Int,        // 0 = de fabrica, 1..4 = animacion
    val x: Int, val r: Int,                    // segundos parado hasta la parte 2 / duracion de la parte 2
    val p: Int, val pa: Int,                   // efecto de la parte 3 (de Ajustes) y segundos de cada uno
    val rn: Int, val rx: Int, val nivel: Int   // limites de la respiracion y brillo fijo (escala 0..1000)
)

private fun suave(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

private fun cometaFab(t: Float, out: FloatArray, esc: Float) {
    val dt = 0.05f
    for (c in 0 until CAN) {
        var m = 0f
        for (k in 0 until 30) {
            val tt = t - k * dt
            val x = 16.5f - 16.5f * sin(2f * PI.toFloat() * tt / 3.6f)
            val d = c - x
            val g = exp(-d * d / 1.8f) * exp(-k * dt / 0.32f)
            if (g > m) m = g
        }
        out[c] = 0.10f + esc * m
    }
}

private fun olasFab(t: Float, out: FloatArray, esc: Float) {
    for (c in 0 until CAN) {
        val o = (0.5f + 0.5f * sin(2f * PI.toFloat() * (1.4f * c / 33f - t / 4f))).pow(1.7f)
        out[c] = 0.10f + esc * (0.08f + 0.62f * o)
    }
}

private fun respFab(t: Float, out: FloatArray, esc: Float) {
    val v = 0.10f + esc * (0.1f + 0.8f * (0.5f - 0.5f * cos(2f * PI.toFloat() * t / 6f)))
    java.util.Arrays.fill(out, v)
}

private fun fiestaFab(t: Float, out: FloatArray, esc: Float) {
    java.util.Arrays.fill(out, 0f)
    val per = floatArrayOf(2.8f, 3.6f, 4.7f)
    val ph = floatArrayOf(0f, 2.1f, 4.2f)
    for (j in 0 until 3) {
        for (k in 0 until 22) {
            val tt = t - k * 0.05f
            val x = 16.5f - 16.5f * sin(2f * PI.toFloat() * tt / per[j] + ph[j])
            val dec = exp(-k * 0.05f / 0.33f)
            for (c in 0 until CAN) {
                val d = c - x
                val g = exp(-d * d / 1.8f) * dec
                if (g > out[c]) out[c] = g
            }
        }
    }
    for (c in 0 until CAN) out[c] = 0.10f + esc * out[c]
}

/** Efecto por defecto de la 3.a parte segun el modo de Ajustes. Devuelve en out los niveles. */
private fun parteBFabrica(cfg: CfgSec, t: Float, out: FloatArray) {
    val esc = 0.45f
    when (cfg.p) {
        0 -> java.util.Arrays.fill(out, cfg.nivel / 1000f)
        1 -> cometaFab(t, out, esc)
        3 -> olasFab(t, out, esc)
        else -> {
            val sec = when (cfg.p) { 4 -> intArrayOf(2, 1); 5 -> intArrayOf(1, 2); 7 -> intArrayOf(1, 0, 3); else -> intArrayOf(0, 1) }
            val per = max(5, cfg.pa).toFloat()
            val i = ((t / per).toInt()) % sec.size
            val tl = t - (t / per).toInt() * per
            var mez = 1f
            if (tl < 1.2f) mez = suave(tl / 1.2f) else if (tl > per - 1.2f) mez = suave((per - tl) / 1.2f)
            val tmp = FloatArray(CAN)
            when (sec[i]) { 0 -> cometaFab(t, tmp, esc); 1 -> olasFab(t, tmp, esc); 3 -> fiestaFab(t, tmp, esc); else -> respFab(t, tmp, esc) }
            for (c in 0 until CAN) out[c] = 0.10f + (tmp[c] - 0.10f) * mez
        }
    }
}

/** Duracion de la bienvenida (s). */
fun duracionBienvenida(cfg: CfgSec, anims: List<Anim?>): Float {
    val an = if (cfg.w in 1..4) anims.getOrNull(cfg.w - 1) else null
    return if (an != null && !an.vacia()) max(2f, an.totalMs() / 1000f) else 6f
}

/** Calcula los niveles en el segundo t de la secuencia. Devuelve la clave de la fase. movDesde = segundo en que hay movimiento. */
fun nivelesSecuencia(cfg: CfgSec, anims: List<Anim?>, t: Float, movDesde: Float?, out: FloatArray): String {
    val fijo = cfg.nivel / 1000f
    val wd = duracionBienvenida(cfg, anims)
    if (movDesde != null && t >= movDesde) {
        // expansion suave desde el centro hacia la luz fija
        val f = suave((t - movDesde) / 1.0f)
        for (c in 0 until CAN) {
            val reach = f * 18f
            out[c] = if (abs(c - 16.5f) <= reach) fijo else fijo * 0.15f
        }
        return "cb_ph_mov"
    }
    if (t < wd) {
        val an = if (cfg.w in 1..4) anims.getOrNull(cfg.w - 1) else null
        if (an != null && !an.vacia()) nivelesAnim(an, (t * 1000f).toLong(), false, out)
        else {
            val f = suave(t / 3f)
            for (c in 0 until CAN) { val reach = f * 18f; out[c] = if (abs(c - 16.5f) <= reach) fijo else 0f }
        }
        return "cb_ph_w"
    }
    val t1 = t - wd
    if (t1 < cfg.x) { java.util.Arrays.fill(out, fijo); return "cb_ph_fix" }
    val t2 = t1 - cfg.x
    if (cfg.r > 0 && t2 < cfg.r) {
        val an = if (cfg.a in 1..4) anims.getOrNull(cfg.a - 1) else null
        if (an != null && !an.vacia()) nivelesAnim(an, (t2 * 1000f).toLong(), true, out)
        else {
            val rn = cfg.rn / 1000f; val rx = cfg.rx / 1000f
            val v = if (t2 < 3f) fijo + (rn - fijo) * suave(t2 / 3f)
            else rn + (rx - rn) * (0.5f - 0.5f * cos(2f * PI.toFloat() * (t2 - 3f) / 4f))
            java.util.Arrays.fill(out, v)
        }
        return "cb_ph_a"
    }
    val t3 = if (cfg.r > 0) t2 - cfg.r else t2
    val an = if (cfg.b in 1..4) anims.getOrNull(cfg.b - 1) else null
    if (an != null && !an.vacia()) nivelesAnim(an, (t3 * 1000f).toLong(), true, out)
    else parteBFabrica(cfg, t3, out)
    return "cb_ph_b"
}

// ---------------------------------------------------------------------------------------------
// Dibujo de la barra (34 segmentos)
// ---------------------------------------------------------------------------------------------
@Composable
fun Barra34(niv: FloatArray, modifier: Modifier = Modifier, alto: Int = 56) {
    // Estilo HUD: segmentos cuadrados, tono cian->blanco segun brillo, reflejo glossy (igual que el monitor).
    Canvas(modifier.fillMaxWidth().height(alto.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF05080F))) {
        val gap = 2.2.dp.toPx()
        val sw = (size.width - gap * (CAN - 1)) / CAN
        val h = size.height
        val rad = 1.5.dp.toPx()
        for (c in 0 until CAN) {
            val vf = niv[c].coerceIn(0f, 1f)
            val x = c * (sw + gap)
            val lit = vf > 0.03f
            val lum = 0.22f + 0.78f * vf
            val col = if (lit) Color((0.12f + 0.88f * vf) * lum, (0.72f + 0.28f * vf) * lum, (0.88f + 0.12f * vf) * lum)
                      else Color(0xFF1C2433)
            if (lit) drawRoundRect(col.copy(alpha = 0.30f * vf), Offset(x - gap, 0f), Size(sw + 2f * gap, h), CornerRadius(rad))
            drawRoundRect(col, Offset(x, h * 0.06f), Size(sw, h * 0.88f), CornerRadius(rad))
            if (lit) drawRoundRect(Color.White.copy(alpha = 0.18f * vf), Offset(x, h * 0.06f), Size(sw, h * 0.30f), CornerRadius(rad))
        }
    }
}

/** Editor: 34 columnas; se toca o se arrastra para dibujar. herramienta 0 = altura, 1 = pincel, 2 = borrar. */
@Composable
private fun EditorBarra(paso: IntArray, ver: Int, herramienta: Int, pincel: Int, onCambio: () -> Unit) {
    val herr by rememberUpdatedState(herramienta)
    val pin by rememberUpdatedState(pincel)
    val pasoAct by rememberUpdatedState(paso)
    val cb by rememberUpdatedState(onCambio)
    var tam by remember { mutableStateOf(Size.Zero) }
    fun aplicar(o: Offset) {
        if (tam.width <= 0f || tam.height <= 0f) return
        val c = ((o.x / tam.width) * CAN).toInt().coerceIn(0, CAN - 1)
        val v = when (herr) {
            0 -> ((1f - o.y / tam.height) * 255f).roundToInt().coerceIn(0, 255)
            1 -> pin
            else -> 0
        }
        pasoAct[c] = v
        cb()
    }
    Canvas(
        Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF05080F))
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .onSizeChanged { tam = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) { detectTapGestures(onTap = { aplicar(it) }) }
            .pointerInput(Unit) { detectDragGestures(onDragStart = { aplicar(it) }, onDrag = { ch, _ -> aplicar(ch.position) }) }
    ) {
        @Suppress("UNUSED_VARIABLE") val forzar = ver
        val w = size.width / CAN
        for (c in 0 until CAN) {
            val h = size.height * (paso[c] / 255f)
            drawRect(Brush.verticalGradient(listOf(Color(0xFF7DD3FC), Color(0xFF2563EB))), Offset(c * w + 1f, size.height - h), Size(w - 2f, h))
        }
    }
}


// Colores de las 4 partes de la secuencia (los mismos en la linea de tiempo y en las tarjetas)
private val ColP1 = Color(0xFF38BDF8)   // bienvenida
private val ColP2 = Color(0xFF34D399)   // tras X s parado
private val ColP3 = Color(0xFFA78BFA)   // despues, hasta moverte
private val ColP4 = Color(0xFFFBBF24)   // en movimiento
private val ColFijo = Color(0xFF94A3B8) // luz fija de espera

@Composable
private fun TituloSeccion(num: Int, titulo: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Insignia(num, Acento)
        Spacer(Modifier.width(10.dp))
        Text(titulo, color = Txt, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
    }
    Nota(desc)
}

/** Tarjeta de una parte de la secuencia: franja de color, numero, que es, y que esta elegido ahora. */
@Composable
private fun ParteCard(num: Int, color: Color, titulo: String, desc: String, resumen: String, content: @Composable ColumnScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).height(IntrinsicSize.Min).clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xF7131D2E), Color(0xFA0C121E))))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(color))
        Column(Modifier.weight(1f).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Insignia(num, color)
                Spacer(Modifier.width(10.dp))
                Text(titulo, color = Txt, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            }
            Nota(desc)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("cb_now") + ": ", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(resumen, color = color, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
            content()
        }
    }
}

/** Linea de tiempo: cuanto dura cada parte, con los mismos colores que las tarjetas de abajo. */
@Composable
private fun LineaTiempo(x: Int, y: Int) {
    @Composable
    fun RowScope.tramo(peso: Float, color: Color, nombre: String, sub: String) {
        Column(
            Modifier.weight(peso).heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.18f)).border(1.dp, color, RoundedCornerShape(10.dp)).padding(horizontal = 3.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text(nombre, color = color, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 12.sp)
            if (sub.isNotEmpty()) Text(sub, color = Muted, fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tramo(1.1f, ColP1, "1 · " + t("cb_tl_w"), "")
        tramo(1f, ColFijo, t("cb_tl_fix"), "$x s")
        tramo(1f, ColP2, "2 · " + t("cb_tl_a"), if (y == 0) "–" else "$y s")
        tramo(1f, ColP3, "3 · " + t("cb_tl_b"), "∞")
        tramo(1.2f, ColP4, "4 · " + t("cb_tl_mov"), "")
    }
}

/** Mis 4 animaciones como opciones (con su nombre, o "vacia"). */
@Composable
private fun MisAnim(sel: Int, nombre: (Int) -> String, onSel: (Int) -> Unit) {
    Text(t("cb_grp_mine"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    for (fila in 0 until 2) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (col in 0 until 2) {
                val i = fila * 2 + col + 1
                Chip("$i · " + nombre(i), sel == i, Modifier.weight(1f), alto = 44) { onSel(i) }
            }
        }
    }
}

@Composable
private fun Emulador(anims: List<Anim?>, sel: Int, ver: Int, cfg: CfgSec) {
    var modoEm by rememberSaveable { mutableStateOf(0) }
    var reproduciendo by rememberSaveable { mutableStateOf(true) }
    var velocidad by rememberSaveable { mutableStateOf(1) }
    var tSeg by remember { mutableStateOf(0f) }
    var movDesde by remember { mutableStateOf<Float?>(null) }
    val niv = remember { FloatArray(CAN) }
    var refresco by remember { mutableStateOf(0) }
    val vel by rememberUpdatedState(velocidad)
    LaunchedEffect(reproduciendo) {
        var ultimo = 0L
        while (reproduciendo) {
            val ahora = withFrameNanos { it }
            if (ultimo != 0L) tSeg += (ahora - ultimo) / 1.0e9f * vel
            ultimo = ahora
            refresco++
        }
    }
    @Suppress("UNUSED_VARIABLE") val dep = refresco + ver
    var fase = ""
    if (modoEm == 0) {
        val an = anims.getOrNull(sel)
        if (an != null && !an.vacia()) nivelesAnim(an, (tSeg * 1000f).toLong(), true, niv) else java.util.Arrays.fill(niv, 0f)
    } else {
        fase = nivelesSecuencia(cfg, anims, tSeg, movDesde, niv)
    }
    Tarjeta {
        TituloSeccion(4, t("cb_s4"), t("cb_s4_d"))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(t("cb_em_one"), modoEm == 0, Modifier.weight(1f)) { modoEm = 0; tSeg = 0f; movDesde = null }
            Chip(t("cb_em_seq"), modoEm == 1, Modifier.weight(1f)) { modoEm = 1; tSeg = 0f; movDesde = null }
        }
        Spacer(Modifier.height(10.dp))
        Barra34(niv, alto = 64)
        if (modoEm == 1) {
            Spacer(Modifier.height(6.dp))
            val txtFase = if (fase == "cb_ph_a") t("cb_ph_a", "${cfg.x}") else t(fase)
            Text("$txtFase · %.0f s".format(tSeg), color = Acento, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Boton(if (reproduciendo) t("cb_pause") else t("cb_play"), Modifier.weight(1f), pequeno = true) { reproduciendo = !reproduciendo }
            Boton(t("cb_restart"), Modifier.weight(1f), pequeno = true) { tSeg = 0f; movDesde = null }
        }
        if (modoEm == 1) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 4, 10).forEach { v -> Chip("×$v", velocidad == v, Modifier.weight(1f)) { velocidad = v } }
            }
            Spacer(Modifier.height(6.dp))
            Boton(t("cb_move"), Modifier.fillMaxWidth(), pequeno = true) { movDesde = tSeg }
            Nota(t("cb_approx"))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pantalla del constructor
// ---------------------------------------------------------------------------------------------
@Composable
fun PantallaConstructor(ble: BleController, est: EstadoC?, conectado: Boolean, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(true) { onCerrar() }

    val anims = remember { mutableStateListOf<Anim?>().apply { addAll(AnimStore.cargar(ctx)) } }
    var sel by rememberSaveable { mutableStateOf(0) }
    var ver by remember { mutableStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val verLeido = ver   // hace que la pantalla se redibuje al editar
    var paso by rememberSaveable { mutableStateOf(0) }
    var herramienta by rememberSaveable { mutableStateOf(0) }
    var pincel by rememberSaveable { mutableStateOf(255) }
    var tipo by rememberSaveable { mutableStateOf(1) }
    var ciclo by rememberSaveable { mutableStateOf(3f) }
    var cola by rememberSaveable { mutableStateOf(5f) }
    var picoGen by rememberSaveable { mutableStateOf(100) }
    var suaveGen by rememberSaveable { mutableStateOf(true) }
    var msg by remember { mutableStateOf("") }
    var subiendo by remember { mutableStateOf(false) }

    // Configuracion de la secuencia (se lee de la barra una vez)
    var cw by rememberSaveable { mutableStateOf(0) }
    var cwf by rememberSaveable { mutableStateOf(1) }
    var ca by rememberSaveable { mutableStateOf(0) }
    var cb by rememberSaveable { mutableStateOf(0) }
    var cx by rememberSaveable { mutableStateOf(15) }
    var cr by rememberSaveable { mutableStateOf(45) }
    var cp by rememberSaveable { mutableStateOf(1) }
    var cpa by rememberSaveable { mutableStateOf(20) }
    var leida by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(est != null) {
        if (!leida && est != null) {
            leida = true
            cw = est.qw; cwf = est.w; ca = est.qa; cb = est.qb; cx = est.e; cr = est.r; cp = est.p; cpa = est.pa
        }
    }

    fun actual(): Anim {
        var a = anims[sel]
        if (a == null) { a = Anim(); anims[sel] = a }
        return a
    }
    fun persistir() { AnimStore.guardar(ctx, anims.toList()); ver++ }
    val a = anims[sel]
    val nPasos = a?.pasos?.size ?: 0
    if (paso >= nPasos && nPasos > 0) paso = nPasos - 1

    // ---------- barra apagada mientras se edita ----------
    val prefsCb = remember { ctx.getSharedPreferences("c10prefs", Context.MODE_PRIVATE) }
    // Modo de la barra mientras editas: 1 = en vivo (se ve lo que dibujas) · 0 = apagada · 2 = normal (como estaba)
    var modo by remember { mutableStateOf(prefsCb.getInt("cb_modo", 1).coerceIn(0, 2)) }
    val apagarAlEditar = modo != 2
    val envivo = modo == 1
    var pausaVivo by remember { mutableStateOf(false) }
    var estVivo by remember { mutableStateOf(0) }    // 0 nada · 1 enviando · 2 ya en la barra
    val yo = remember { BooleanArray(2) }            // [0] = la he apagado yo · [1] = ya se hizo el apagado inicial
    var yoApague by remember { mutableStateOf(false) }
    val vivoAct = remember { BooleanArray(1) }       // el editor directo (SHOW:11) esta en marcha en la barra
    val fwOk = (est?.v?.toFloatOrNull() ?: 0f) >= 2.6f   // el dibujo en vivo necesita el sketch 2.6 o posterior
    // Al salir del constructor la barra vuelve a su estado normal (ON = reinicio limpio con el brillo que tenia).
    DisposableEffect(Unit) { onDispose { if (vivoAct[0] || ble.estado?.h == 11) ble.enviar("SHOW:STOP"); if (yo[0]) ble.enviar("ON") } }
    /** Entra en el editor directo: la barra se apaga suave y solo luce lo que dibujes (sin destellos). */
    suspend fun entrarVivo() {
        if (yo[0] || ble.estado?.off == true) { ble.enviar("ON"); yo[0] = false; yoApague = false; delay(1500) }
        ble.enviar("SHOW:11,0"); vivoAct[0] = true
        delay(450)
    }
    LaunchedEffect(conectado, est != null) {
        if (!yo[1] && conectado && est != null) {
            yo[1] = true
            if (modo == 1 && fwOk) entrarVivo()
            else if (modo == 0 && !est.off) { ble.enviar("OFF"); yo[0] = true; yoApague = true }
        }
    }
    /** Enciende la barra antes de una prueba (hace falta verla). */
    suspend fun encenderParaProbar() {
        if (yo[0]) { ble.enviar("ON"); yo[0] = false; yoApague = false; delay(1500) }
        vivoAct[0] = false          // la prueba sustituye al editor directo
    }
    /** Tras parar una prueba la barra vuelve al modo elegido (editor directo u apagada). */
    suspend fun apagarTrasProbar() {
        delay(600)
        if (modo == 1 && fwOk) { ble.enviar("SHOW:11,0"); vivoAct[0] = true }
        else if (modo == 0 && !yo[0]) { ble.enviar("OFF"); yo[0] = true; yoApague = true }
    }
    fun cambiarModo(m: Int) {
        if (m == modo) return
        modo = m; prefsCb.edit().putInt("cb_modo", m).apply()
        pausaVivo = false
        val parar = (vivoAct[0] || est?.h == 11) && m != 1
        if (parar) { ble.enviar("SHOW:STOP"); vivoAct[0] = false; estVivo = 0 }
        if (m == 2) {
            if (yo[0]) { ble.enviar("ON"); yo[0] = false; yoApague = false }
        } else if (m == 0) {
            if (!yo[0] && (est?.off == false || parar)) scope.launch { if (parar) delay(600); ble.enviar("OFF"); yo[0] = true; yoApague = true }
        } else if (fwOk && conectado) scope.launch { entrarVivo() }
    }
    fun pausarVivo() {
        pausaVivo = true; estVivo = 0
        if (vivoAct[0]) { ble.enviar("SHOW:STOP"); vivoAct[0] = false }
    }

    // ---------- dibujo en vivo: lo que marcas se ve al instante en la barra (comando PIX, sketch 2.6) ----------
    // Cada 50 ms se mira si el paso cambio (tambien mientras arrastras el dedo) y se manda a la barra.
    LaunchedEffect(envivo, pausaVivo, conectado, fwOk) {
        if (!envivo || pausaVivo || !conectado || !fwOk) return@LaunchedEffect
        fun clave(): String {
            val an = anims[sel] ?: return ""
            if (an.vacia() || paso !in an.pasos.indices) return ""
            return "$sel/$paso/${an.pico}/${an.hex(paso)}"
        }
        var ultima = clave()                       // al abrir no se toca la barra
        while (true) {
            delay(50)
            if (subiendo) continue
            val k = clave()
            if (k.isEmpty()) {                       // animacion vaciada: la barra se queda a oscuras
                if (ultima.isNotEmpty() && vivoAct[0]) ble.enviar("PIX:100," + "00".repeat(34))
                ultima = ""; continue
            }
            if (k == ultima) continue
            ultima = k
            val an = anims[sel] ?: continue
            estVivo = 1
            if (!vivoAct[0] || ble.estado?.h != 11) entrarVivo()
            ble.enviar("PIX:${an.pico},${an.hex(paso.coerceIn(0, an.pasos.size - 1))}")
            estVivo = 2
        }
    }

    // ---------- deshacer ----------
    val und = remember { UndoBuf(sel, anims[sel]?.copia()) }
    var hver by remember { mutableStateOf(0) }
    fun confirmarCambios() {
        val cur = anims[und.baseSel]
        if (!mismoAnim(cur, und.base)) {
            und.hist.add(und.baseSel to und.base); if (und.hist.size > 40) und.hist.removeAt(0)
            und.base = cur?.copia(); hver++
        }
        if (und.baseSel != sel) { und.baseSel = sel; und.base = anims[sel]?.copia() }
    }
    LaunchedEffect(ver, sel) { delay(600); confirmarCambios() }
    fun deshacer() {
        confirmarCambios()
        if (und.hist.isEmpty()) return
        val (s0, an) = und.hist.removeAt(und.hist.size - 1)
        sel = s0; anims[s0] = an?.copia(); und.base = anims[s0]?.copia(); und.baseSel = s0
        paso = 0; persistir(); hver++
    }
    val puedeDeshacer = hver >= 0 && (und.hist.isNotEmpty() || !mismoAnim(anims[und.baseSel], und.base))

    // ---------- subida y comprobacion ----------
    suspend fun subir(an: Anim): Boolean {
        if (an.vacia()) { msg = t("cb_empty_anim"); return false }
        if (!conectado) { msg = t("cb_need_con"); return false }
        for (intento in 0 until 3) {
            ble.enviar("ANIM:B,${an.pasos.size},${if (an.suave) 1 else 0},${an.pico}")
            for (i in an.pasos.indices) {
                while (ble.pendientes() > 16) delay(40)
                ble.enviar("ANIM:S,$i,${an.durs[i]},${an.hex(i)}")
                msg = t("cb_up_prog").replace("{0}", "${i + 1}").replace("{1}", "${an.pasos.size}")
            }
            var espera = 0
            while (ble.pendientes() > 0 && espera < 200) { delay(50); espera++ }
            delay(900)
            val e = ble.estado
            if (e != null && e.ax == an.pasos.size && e.ac == an.suma()) { msg = t("cb_up_ok"); return true }
            delay(600)
            val e2 = ble.estado
            if (e2 != null && e2.ax == an.pasos.size && e2.ac == an.suma()) { msg = t("cb_up_ok"); return true }
        }
        msg = t("cb_up_fail")
        return false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(t("cb_title"), color = Txt, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Boton(t("cb_close"), Modifier.width(110.dp), pequeno = true) { onCerrar() }
        }
        Spacer(Modifier.height(12.dp))

        Tarjeta {
            Text(t("cb_mode_t"), color = Txt, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Nota(t("cb_mode_d"))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(t("cb_mode_live"), modo == 1, Modifier.weight(1f), alto = 46) { cambiarModo(1) }
                Chip(t("cb_mode_off"), modo == 0, Modifier.weight(1f), alto = 46) { cambiarModo(0) }
                Chip(t("cb_mode_norm"), modo == 2, Modifier.weight(1f), alto = 46) { cambiarModo(2) }
            }
            Nota(t(when (modo) { 1 -> "cb_mode_live_d"; 0 -> "cb_mode_off_d"; else -> "cb_mode_norm_d" }))
            if (yoApague) Aviso(t("cb_off_now"))
            if (modo == 1 && est != null && !fwOk) Aviso(t("cb_fw_old"))
        }
        Spacer(Modifier.height(12.dp))

        // ======= MONITOR EN VIVO (ver la barra sin bajarse del coche) =======
        TarjetaVivo(ble)
        Spacer(Modifier.height(12.dp))

        // ======================= 1 · TUS ANIMACIONES =======================
        Tarjeta {
            TituloSeccion(1, t("cb_s1"), t("cb_s1_d"))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until 4) {
                    val enBarra = ((est?.sa ?: 0) shr i) and 1 == 1
                    val an = anims[i]
                    val rotulo = t("cb_slot", "${i + 1}") + (if (an != null && !an.vacia()) " ✓" else "") + (if (enBarra) " ▮" else "")
                    Chip(rotulo, sel == i, Modifier.weight(1f), alto = 46) { sel = i; paso = 0; ver++ }
                }
            }
            Nota(t("cb_legend"))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = a?.nombre ?: "", onValueChange = { v -> actual().nombre = v.take(16); persistir() },
                label = { Text(t("cb_name")) }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Boton(t("cb_clear"), Modifier.weight(1f), pequeno = true) { anims[sel] = null; paso = 0; persistir() }
                var confBorrar by remember { mutableStateOf(false) }
                Boton(t("cb_del_bar"), Modifier.weight(1f), enabled = conectado, pequeno = true, peligro = true) { confBorrar = true }
                if (confBorrar) AlertDialog(
                    onDismissRequest = { confBorrar = false },
                    title = { Text(t("cb_save_t")) }, text = { Text(t("cb_del_c", "${sel + 1}")) },
                    confirmButton = { TextButton(onClick = { confBorrar = false; ble.enviar("ANIM:D,${sel + 1}") }) { Text(t("ok")) } },
                    dismissButton = { TextButton(onClick = { confBorrar = false }) { Text(t("back")) } }
                )
            }
        }

        // ======================= 2 · MOVIMIENTOS BASICOS =======================
        Spacer(Modifier.height(12.dp))
        Tarjeta {
            TituloSeccion(2, t("cb_s2"), t("cb_s2_d"))
            Spacer(Modifier.height(8.dp))
            for (fila in 0 until 4) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (col in 0 until 3) {
                        val i = fila * 3 + col + 1
                        if (i <= Gen.TIPOS) Chip(t("cb_t$i"), tipo == i, Modifier.weight(1f), alto = 46) { tipo = i }
                        else Spacer(Modifier.weight(1f))
                    }
                }
            }
            FilaValor(t("cb_cycle"), "%.1f s".format(ciclo))
            SliderPM(ciclo, 1f..10f, 0.5f) { v, _ -> ciclo = (v * 2f).roundToInt() / 2f }
            if (tipo <= 5) {
                FilaValor(t("cb_tail"), "${cola.roundToInt()}")
                SliderPM(cola, 1f..12f, 1f) { v, _ -> cola = v.roundToInt().toFloat() }
            }
            FilaValor(t("cb_peak"), "$picoGen %")
            SliderPM(picoGen.toFloat(), 10f..100f, 5f) { v, _ -> picoGen = (v / 5f).roundToInt() * 5 }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(t("cb_smooth"), color = Txt, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = suaveGen, onCheckedChange = { suaveGen = it })
            }
            Spacer(Modifier.height(10.dp))
            Boton(t("cb_gen"), Modifier.fillMaxWidth(), principal = true) {
                val nombre = a?.nombre ?: ""
                val g = Gen.crear(tipo, ciclo, cola, picoGen, suaveGen)
                g.nombre = if (nombre.isEmpty()) t("cb_t$tipo") else nombre
                anims[sel] = g; paso = 0; persistir()
            }
        }

        // ======================= 3 · EDITOR CANAL A CANAL =======================
        Spacer(Modifier.height(12.dp))
        Tarjeta {
            TituloSeccion(3, t("cb_s3"), t("cb_s3_d"))
            if (a == null || a.vacia()) {
                Spacer(Modifier.height(8.dp))
                Boton(t("cb_start_empty"), Modifier.fillMaxWidth()) {
                    val n = actual(); n.pasos.add(IntArray(CAN)); n.durs.add(25); paso = 0; persistir()
                }
            } else {
                val p = a.pasos[paso]
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Boton("◀", Modifier.width(56.dp), enabled = paso > 0) { paso-- }
                    Text(t("cb_steps", "${paso + 1}").replace("{1}", "$nPasos"), color = Txt, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Boton("▶", Modifier.width(56.dp), enabled = paso < nPasos - 1) { paso++ }
                }
                if (envivo) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val ok = androidx.compose.ui.graphics.Color(0xFF22C55E); val am = androidx.compose.ui.graphics.Color(0xFFFBBF24)
                        val (txt, col) = when {
                            !conectado -> t("cb_lv_nocon") to Muted
                            !fwOk -> t("cb_fw_short") to am
                            pausaVivo -> t("cb_lv_pause") to Muted
                            estVivo == 1 -> t("cb_lv_sending") to am
                            estVivo == 2 -> t("cb_lv_sent") to ok
                            else -> t("cb_lv_wait") to Muted
                        }
                        Text(txt, color = col, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Boton(if (pausaVivo) t("cb_lv_resume") else t("cb_lv_pausebtn"), Modifier.width(130.dp), enabled = conectado, pequeno = true) {
                            if (pausaVivo) pausaVivo = false else pausarVivo()
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                EditorBarra(p, ver, herramienta, pincel) { ver++ }
                Spacer(Modifier.height(4.dp))
                val ligero = remember(ver, paso, sel) { FloatArray(CAN) { c -> p[c] / 255f } }
                Barra34(ligero, alto = 28)
                Spacer(Modifier.height(8.dp))
                Boton("↶ " + t("cb_undo"), Modifier.fillMaxWidth(), enabled = puedeDeshacer, pequeno = true) { deshacer() }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(t("cb_tool_draw"), herramienta == 0, Modifier.weight(1f), alto = 52) { herramienta = 0 }
                    Chip(t("cb_tool_brush"), herramienta == 1, Modifier.weight(1f), alto = 52) { herramienta = 1 }
                    Chip(t("cb_tool_erase"), herramienta == 2, Modifier.weight(1f), alto = 52) { herramienta = 2 }
                }
                if (herramienta == 1) {
                    FilaValor(t("cb_brush"), "${(pincel * 100f / 255f).roundToInt()} %")
                    SliderPM(pincel.toFloat(), 0f..255f, 1f) { v, _ -> pincel = v.roundToInt() }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Boton(t("cb_all_on"), Modifier.weight(1f), pequeno = true) { java.util.Arrays.fill(p, pincel); persistir() }
                    Boton(t("cb_all_off"), Modifier.weight(1f), pequeno = true) { java.util.Arrays.fill(p, 0); persistir() }
                    Boton(t("cb_invert"), Modifier.weight(1f), pequeno = true) { for (c in 0 until CAN) p[c] = 255 - p[c]; persistir() }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Boton(t("cb_mirror"), Modifier.weight(1f), pequeno = true) { p.reverse(); persistir() }
                    Boton(t("cb_shl"), Modifier.weight(1f), pequeno = true) {
                        val f = p[0]; for (c in 0 until CAN - 1) p[c] = p[c + 1]; p[CAN - 1] = f; persistir()
                    }
                    Boton(t("cb_shr"), Modifier.weight(1f), pequeno = true) {
                        val l = p[CAN - 1]; for (c in CAN - 1 downTo 1) p[c] = p[c - 1]; p[0] = l; persistir()
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Boton(t("cb_add"), Modifier.weight(1f), pequeno = true, enabled = nPasos < MAX_PASOS) {
                        a.pasos.add(paso + 1, p.copyOf()); a.durs.add(paso + 1, a.durs[paso]); paso++; persistir()
                    }
                    Boton(t("cb_delstep"), Modifier.weight(1f), pequeno = true, enabled = nPasos > 1) {
                        a.pasos.removeAt(paso); a.durs.removeAt(paso); if (paso >= a.pasos.size) paso = a.pasos.size - 1; persistir()
                    }
                    Boton(t("cb_copy_all"), Modifier.weight(1f), pequeno = true) {
                        for (q in a.pasos.indices) if (q != paso) a.pasos[q] = p.copyOf(); persistir()
                    }
                }
                FilaValor(t("cb_dur"), "%.2f s".format(a.durs[paso] * 0.02f))
                SliderPM(a.durs[paso].toFloat(), 3f..255f, 1f) { v, _ -> a.durs[paso] = v.roundToInt().coerceIn(3, 255); persistir() }
                Spacer(Modifier.height(4.dp))
                Boton(t("cb_dur_all"), Modifier.fillMaxWidth(), pequeno = true) {
                    val d = a.durs[paso]; for (q in a.durs.indices) a.durs[q] = d; persistir()
                }
                FilaValor(t("cb_total"), "%.2f s".format(a.totalMs() / 1000f))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t("cb_smooth"), color = Txt, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(checked = a.suave, onCheckedChange = { a.suave = it; persistir() })
                }
                FilaValor(t("cb_peak"), "${a.pico} %")
                SliderPM(a.pico.toFloat(), 10f..100f, 5f) { v, _ -> a.pico = (v / 5f).roundToInt() * 5; persistir() }
            }
        }

        // ======================= 4 · EMULADOR =======================
        Spacer(Modifier.height(12.dp))
        Emulador(anims, sel, ver, CfgSec(cw, ca, cb, cx, cr, cp, cpa, est?.rn ?: 200, est?.rx ?: 700, est?.n ?: 655))

        // ======================= 5 · PROBAR EN LA BARRA =======================
        Spacer(Modifier.height(12.dp))
        Tarjeta {
            TituloSeccion(5, t("cb_s5"), t("cb_s5_d"))
            Spacer(Modifier.height(8.dp))
            Boton(t("cb_test_show"), Modifier.fillMaxWidth(), enabled = conectado && !subiendo && !(a == null || a.vacia()), principal = true) {
                scope.launch {
                    subiendo = true
                    encenderParaProbar()
                    if (subir(actual())) ble.enviar("SHOW:10,0")
                    subiendo = false
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Boton(t("cb_test_w"), Modifier.weight(1f), enabled = conectado && !subiendo && !(a == null || a.vacia()), pequeno = true) {
                    scope.launch {
                        subiendo = true
                        encenderParaProbar()
                        if (subir(actual())) ble.enviar("WPLAY:10")
                        subiendo = false
                    }
                }
                Boton(t("cb_test_stop"), Modifier.weight(1f), enabled = conectado, pequeno = true, peligro = true) { ble.enviar("SHOW:STOP"); scope.launch { apagarTrasProbar() } }
            }
            if (msg.isNotEmpty()) Nota(msg)
            if (!conectado) Nota(t("cb_need_con"))
        }

        // ======================= 6 · SECUENCIA DEFINITIVA =======================
        Spacer(Modifier.height(12.dp))
        Tarjeta {
            TituloSeccion(6, t("cb_s6"), t("cb_s6_d"))
            Spacer(Modifier.height(10.dp))
            LineaTiempo(cx, cr)
        }
        fun nom(i: Int): String {
            val n = anims[i - 1]
            val base = if (n != null && n.nombre.isNotEmpty()) n.nombre else t("cb_slot", "$i")
            return if (n == null || n.vacia()) base + t("cb_empty_s") else base
        }

        // ---- Parte 1: bienvenida ----
        ParteCard(1, ColP1, t("cb_q1"), t("cb_q1_d"), if (cw == 0) t("w$cwf") else nom(cw)) {
            Text(t("cb_grp_fab"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            for (fila in 0 until 2) {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 0 until 3) {
                        val i = fila * 3 + col + 1
                        if (i <= 5) Chip(t("w$i"), cw == 0 && cwf == i, Modifier.weight(1f), alto = 44) { cw = 0; cwf = i; ble.enviar("WSEL:$i") }
                        else Spacer(Modifier.weight(1f))
                    }
                }
            }
            MisAnim(cw, { nom(it) }) { cw = it }
        }

        // ---- Parte 2: tras X segundos parado ----
        ParteCard(2, ColP2, t("cb_q2"), t("cb_q2_d"), if (ca == 0) t("cb_q_breath") else nom(ca)) {
            FilaValor(t("cb_q_x"), t("se_sec", "$cx"))
            SliderPM(cx.toFloat(), 5f..300f, 1f) { v, _ -> cx = v.roundToInt() }
            FilaValor(t("cb_q_y"), if (cr == 0) "0" else t("se_sec", "$cr"))
            SliderPM(cr.toFloat(), 0f..600f, 5f) { v, _ ->
                val q = (v / 5f).roundToInt() * 5
                cr = if (q in 1..9) (if (v < cr) 0 else 10) else q
            }
            OpcionFila(t("cb_q_breath"), t("cb_q_breath_d"), ca == 0, ColP2) { ca = 0 }
            MisAnim(ca, { nom(it) }) { ca = it }
        }

        // ---- Parte 3: despues, hasta que te muevas ----
        ParteCard(3, ColP3, t("cb_q3"), t("cb_q3_d"),
            if (cb != 0) nom(cb) else if (cp == 0) t("cb_q_fixed") else t(when (cp) { 1 -> "se_pp_co"; 3 -> "se_pp_ol"; 4 -> "se_pp_ro"; 5 -> "se_pp_or"; 7 -> "se_pp_rot3"; 8 -> "se_pp_lista"; else -> "se_pp_cl2" })
        ) {
            OpcionFila(t("cb_q_set"), t("cb_q_set_d"), cb == 0 && cp != 0, ColP3) { cb = 0; if (cp == 0) cp = 1 }
            if (cb == 0 && cp != 0) {
                Column(Modifier.padding(start = 10.dp)) {
                    SelectorModoParado(cp, ColP3, false, incluirRot3 = (est?.v?.toFloatOrNull() ?: 0f) >= 2.9f) { cp = it }
                    if (cp >= 4) {
                        FilaValor(t("se_alt"), t("se_sec", "$cpa"))
                        SliderPM(cpa.toFloat(), 5f..300f, 1f) { v, _ -> cpa = v.roundToInt() }
                    }
                }
            }
            OpcionFila(t("cb_q_fixed"), t("cb_q_fixed_d"), cb == 0 && cp == 0, ColP3) { cb = 0; cp = 0 }
            MisAnim(cb, { nom(it) }) { cb = it }
        }

        // ---- Parte 4: en movimiento ----
        ParteCard(4, ColP4, t("cb_q4"), t("cb_q4_d"), t("cb_q_fixed")) { }

        Spacer(Modifier.height(10.dp))
        Tarjeta {
            Boton(t("cb_seq_try"), Modifier.fillMaxWidth(), enabled = conectado) {
                scope.launch {
                    encenderParaProbar()
                    val e = est
                    ble.enviar("SEQ:$cw,$ca,$cb")
                    if (e != null) ble.enviar("SET:$cx,$cr,$cp,${e.k},${e.d},${e.rn.coerceIn(10, 980)},${e.rx.coerceIn(e.rn.coerceIn(10, 980) + 10, 1000)},$cpa,${e.pn},${e.px}")
                    msg = t("cb_seq_tried")
                }
            }
            Nota(t("cb_seq_try_d"))
            if (msg.isNotEmpty()) Nota(msg)
        }

        // ======================= 7 · GUARDAR EN LA BARRA =======================
        Spacer(Modifier.height(12.dp))
        var confGuardar by remember { mutableStateOf(false) }
        var entiendo by remember { mutableStateOf(false) }
        var confFabrica by remember { mutableStateOf(false) }
        var guardando by remember { mutableStateOf(false) }
        var resultado by remember { mutableStateOf("") }
        var resultadoMal by remember { mutableStateOf(false) }
        Tarjeta(borde = Color(0xFF92400E)) {
            TituloSeccion(7, t("cb_s7"), t("cb_s7_d"))
            AvisoAmarillo(t("cb_warn"))
            Spacer(Modifier.height(8.dp))
            val usaAnim = cw in 1..4 || ca in 1..4 || cb in 1..4
            val faltan = listOf(cw, ca, cb).filter { it in 1..4 && (anims[it - 1]?.vacia() != false) }
            if (faltan.isNotEmpty()) Aviso(t("cb_missing"))
            Boton(t("cb_save"), Modifier.fillMaxWidth(), enabled = conectado && !guardando && faltan.isEmpty(), principal = true) {
                entiendo = false; confGuardar = true
            }
            if (!usaAnim) Nota(t("cb_save_noanim"))
            if (resultado.isNotEmpty()) { if (resultadoMal) Aviso(resultado) else Nota(resultado) }
            Spacer(Modifier.height(10.dp))
            Boton(t("cb_seq_reset"), Modifier.fillMaxWidth(), enabled = conectado && !guardando, peligro = true) { confFabrica = true }
        }
        if (confGuardar) {
            AlertDialog(
                onDismissRequest = { confGuardar = false },
                title = { Text(t("cb_save_t")) },
                text = {
                    Column {
                        Text(t("cb_save_m"))
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = entiendo, onCheckedChange = { entiendo = it })
                            Text(t("cb_save_chk"), fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    TextButton(enabled = entiendo, onClick = {
                        confGuardar = false
                        scope.launch {
                            guardando = true; resultado = t("cb_saving"); resultadoMal = false
                            var ok = true
                            // 1) las animaciones usadas se suben y se graban en su ranura
                            val usadas = listOf(cw, ca, cb).filter { it in 1..4 }.distinct()
                            for (sl in usadas) {
                                val an = anims[sl - 1]
                                if (an == null || !subir(an)) { ok = false; break }
                                ble.enviar("ANIM:E,$sl")
                                delay(700)
                            }
                            if (ok) {
                                val e = est
                                ble.enviar("SEQ:$cw,$ca,$cb")
                                if (e != null) ble.enviar("SET:$cx,$cr,$cp,${e.k},${e.d},${e.rn.coerceIn(10, 980)},${e.rx.coerceIn(e.rn.coerceIn(10, 980) + 10, 1000)},$cpa,${e.pn},${e.px}")
                                ble.enviar("WSEL:$cwf")
                                ble.enviar("SEQSAVE")
                                ble.enviar("SAVE")
                                var espera = 0
                                while (ble.pendientes() > 0 && espera < 100) { delay(50); espera++ }
                                delay(1800)
                                val s = ble.estado
                                var bien = s != null && s.qw == cw && s.qa == ca && s.qb == cb && s.e == cx && s.r == cr && s.p == cp
                                if (bien && cp >= 4) bien = s!!.pa == cpa
                                if (bien) for (sl in usadas) if (((s!!.sa shr (sl - 1)) and 1) == 0) bien = false
                                ok = bien
                            }
                            resultado = if (ok) t("cb_saved") else t("cb_save_fail")
                            resultadoMal = !ok
                            guardando = false
                        }
                    }) { Text(t("cb_save")) }
                },
                dismissButton = { TextButton(onClick = { confGuardar = false }) { Text(t("back")) } }
            )
        }
        if (confFabrica) {
            AlertDialog(
                onDismissRequest = { confFabrica = false },
                title = { Text(t("cb_save_t")) }, text = { Text(t("cb_seq_reset_c")) },
                confirmButton = {
                    TextButton(onClick = {
                        confFabrica = false
                        cw = 0; ca = 0; cb = 0
                        ble.enviar("SEQ:0,0,0"); ble.enviar("SEQSAVE")
                        resultado = t("cb_seq_reset_ok"); resultadoMal = false
                    }) { Text(t("ok")) }
                },
                dismissButton = { TextButton(onClick = { confFabrica = false }) { Text(t("back")) } }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
