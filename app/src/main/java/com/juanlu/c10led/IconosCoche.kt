package com.juanlu.c10led

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/** Dibujos para la pantalla de Android Auto (se pintan por codigo, sin archivos de imagen). */
object IconosCoche {
    private fun mezcla(a: Int, b: Int, f: Float): Int {
        val x = f.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * x).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * x).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * x).toInt()
        )
    }

    private fun baldosa(c: Canvas, s: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rr = s * 0.24f
        p.shader = LinearGradient(0f, 0f, 0f, s, mezcla(color, Color.WHITE, 0.30f), mezcla(color, Color.BLACK, 0.40f), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(0f, 0f, s, s), rr, rr, p)
        // brillo suave arriba
        val g = Paint(Paint.ANTI_ALIAS_FLAG)
        g.shader = LinearGradient(0f, 0f, 0f, s * 0.55f, Color.argb(70, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(s * 0.03f, s * 0.03f, s * 0.97f, s * 0.6f), rr * 0.85f, rr * 0.85f, g)
        // borde fino
        val b = Paint(Paint.ANTI_ALIAS_FLAG)
        b.style = Paint.Style.STROKE; b.strokeWidth = s * 0.018f; b.color = Color.argb(70, 255, 255, 255)
        c.drawRoundRect(RectF(s * 0.01f, s * 0.01f, s * 0.99f, s * 0.99f), rr, rr, b)
    }

    private fun trazo(w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = w; color = Color.WHITE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private fun relleno() = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }

    /** Dibuja un simbolo blanco centrado en (cx,cy) dentro de un cuadro de radio r. */
    private fun glifo(c: Canvas, g: String, cx: Float, cy: Float, r: Float) {
        val st = trazo(r * 0.22f); val fl = relleno()
        when (g) {
            "power" -> {
                c.drawArc(RectF(cx - r * 0.7f, cy - r * 0.62f, cx + r * 0.7f, cy + r * 0.78f), -50f, 280f, false, st)
                c.drawLine(cx, cy - r * 0.85f, cx, cy - r * 0.05f, st)
            }
            "sun" -> {
                c.drawCircle(cx, cy, r * 0.4f, fl)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    c.drawLine(cx + (Math.cos(a) * r * 0.66).toFloat(), cy + (Math.sin(a) * r * 0.66).toFloat(),
                        cx + (Math.cos(a) * r * 0.95).toFloat(), cy + (Math.sin(a) * r * 0.95).toFloat(), st)
                }
            }
            "moon" -> {
                val a = Path(); a.addCircle(cx, cy, r * 0.82f, Path.Direction.CW)
                val b = Path(); b.addCircle(cx + r * 0.42f, cy - r * 0.3f, r * 0.7f, Path.Direction.CW)
                a.op(b, Path.Op.DIFFERENCE); c.drawPath(a, fl)
                c.drawCircle(cx + r * 0.62f, cy + r * 0.5f, r * 0.09f, fl)
            }
            "restart" -> {
                val rad = r * 0.7f
                c.drawArc(RectF(cx - rad, cy - rad, cx + rad, cy + rad), 60f, 270f, false, st)
                val ang = Math.toRadians(330.0)
                val ex = cx + (Math.cos(ang) * rad).toFloat(); val ey = cy + (Math.sin(ang) * rad).toFloat()
                val tx = (-Math.sin(ang)).toFloat(); val ty = Math.cos(ang).toFloat()
                val nx = Math.cos(ang).toFloat(); val ny = Math.sin(ang).toFloat()
                val p = Path()
                p.moveTo(ex + tx * r * 0.45f, ey + ty * r * 0.45f)
                p.lineTo(ex - tx * r * 0.05f + nx * r * 0.42f, ey - ty * r * 0.05f + ny * r * 0.42f)
                p.lineTo(ex - tx * r * 0.05f - nx * r * 0.42f, ey - ty * r * 0.05f - ny * r * 0.42f)
                p.close(); c.drawPath(p, fl)
            }
            "auto" -> {
                val rect = RectF(cx - r * 0.8f, cy - r * 0.8f, cx + r * 0.8f, cy + r * 0.8f)
                c.drawCircle(cx, cy, r * 0.8f, st)
                c.drawArc(rect, 90f, 180f, true, fl)
            }
            "live" -> {
                val hs = floatArrayOf(0.45f, 0.85f, 1.0f, 0.65f, 0.35f)
                val w = r * 0.28f; val gap = r * 0.12f
                val x0 = cx - (5 * w + 4 * gap) / 2f
                for (k in 0 until 5) {
                    val h = hs[k] * r * 1.7f
                    c.drawRoundRect(RectF(x0 + k * (w + gap), cy + r * 0.85f - h, x0 + k * (w + gap) + w, cy + r * 0.85f), w / 2f, w / 2f, fl)
                }
            }
            "sparkle" -> {
                val p = Path()
                p.moveTo(cx, cy - r); p.quadTo(cx, cy, cx + r, cy); p.quadTo(cx, cy, cx, cy + r)
                p.quadTo(cx, cy, cx - r, cy); p.quadTo(cx, cy, cx, cy - r); c.drawPath(p, fl)
                val q = Path(); val x = cx + r * 0.78f; val y = cy - r * 0.78f; val m = r * 0.3f
                q.moveTo(x, y - m); q.quadTo(x, y, x + m, y); q.quadTo(x, y, x, y + m); q.quadTo(x, y, x - m, y); q.quadTo(x, y, x, y - m)
                c.drawPath(q, fl)
            }
            "clap" -> {
                c.drawRoundRect(RectF(cx - r * 0.9f, cy - r * 0.62f, cx + r * 0.9f, cy + r * 0.78f), r * 0.2f, r * 0.2f, st)
                val p = Path(); p.moveTo(cx - r * 0.2f, cy - r * 0.22f); p.lineTo(cx + r * 0.42f, cy + r * 0.08f); p.lineTo(cx - r * 0.2f, cy + r * 0.38f); p.close()
                c.drawPath(p, fl)
                c.drawLine(cx - r * 0.9f, cy - r * 0.2f, cx + r * 0.9f, cy - r * 0.2f, st)
            }
            "plus" -> { c.drawLine(cx - r * 0.7f, cy, cx + r * 0.7f, cy, st); c.drawLine(cx, cy - r * 0.7f, cx, cy + r * 0.7f, st) }
            "minus" -> c.drawLine(cx - r * 0.7f, cy, cx + r * 0.7f, cy, st)
            "stop" -> c.drawRoundRect(RectF(cx - r * 0.6f, cy - r * 0.6f, cx + r * 0.6f, cy + r * 0.6f), r * 0.16f, r * 0.16f, fl)
            "wave" -> {
                val p = Path(); p.moveTo(cx - r, cy)
                p.cubicTo(cx - r * 0.7f, cy - r * 0.7f, cx - r * 0.3f, cy - r * 0.7f, cx, cy)
                p.cubicTo(cx + r * 0.3f, cy + r * 0.7f, cx + r * 0.7f, cy + r * 0.7f, cx + r, cy)
                c.drawPath(p, st)
                c.drawPath(Path().also { q -> q.moveTo(cx - r, cy + r * 0.55f); q.cubicTo(cx - r * 0.7f, cy - r * 0.15f, cx - r * 0.3f, cy - r * 0.15f, cx, cy + r * 0.55f); q.cubicTo(cx + r * 0.3f, cy + r * 1.25f, cx + r * 0.7f, cy + r * 1.25f, cx + r, cy + r * 0.55f) }, st.apply { alpha = 150 })
            }
            "drop" -> {
                val p = Path(); p.moveTo(cx, cy - r)
                p.cubicTo(cx + r * 0.95f, cy - r * 0.05f, cx + r * 0.8f, cy + r * 0.9f, cx, cy + r * 0.9f)
                p.cubicTo(cx - r * 0.8f, cy + r * 0.9f, cx - r * 0.95f, cy - r * 0.05f, cx, cy - r); c.drawPath(p, fl)
            }
            "star" -> {
                val p = Path()
                for (k in 0 until 10) {
                    val a = Math.toRadians(-90.0 + k * 36.0); val rr = if (k % 2 == 0) r else r * 0.42f
                    val x = cx + (Math.cos(a) * rr).toFloat(); val y = cy + (Math.sin(a) * rr).toFloat()
                    if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close(); c.drawPath(p, fl)
            }
            "sunset" -> {
                c.drawArc(RectF(cx - r * 0.7f, cy - r * 0.5f, cx + r * 0.7f, cy + r * 0.9f), 180f, 180f, true, fl)
                c.drawLine(cx - r, cy + r * 0.4f, cx + r, cy + r * 0.4f, st)
                c.drawLine(cx - r * 0.55f, cy + r * 0.85f, cx + r * 0.55f, cy + r * 0.85f, st.apply { alpha = 150 })
            }
            "leaf" -> {
                val p = Path(); p.moveTo(cx - r * 0.85f, cy + r * 0.85f)
                p.quadTo(cx - r * 0.95f, cy - r * 0.75f, cx + r * 0.85f, cy - r * 0.85f)
                p.quadTo(cx + r * 0.95f, cy + r * 0.75f, cx - r * 0.85f, cy + r * 0.85f); c.drawPath(p, fl)
            }
            "comet" -> {
                val t = Path(); t.moveTo(cx + r * 0.05f, cy - r * 0.6f); t.lineTo(cx - r * 0.9f, cy + r * 0.9f); t.lineTo(cx + r * 0.6f, cy - r * 0.05f); t.close()
                c.drawPath(t, Paint(fl).apply { alpha = 150 })
                c.drawCircle(cx + r * 0.38f, cy - r * 0.38f, r * 0.4f, fl)
            }
            "film" -> {
                c.drawRoundRect(RectF(cx - r * 0.9f, cy - r * 0.7f, cx + r * 0.9f, cy + r * 0.7f), r * 0.16f, r * 0.16f, st)
                for (k in 0 until 3) {
                    val y = cy - r * 0.5f + k * r * 0.45f
                    c.drawRect(cx - r * 0.78f, y, cx - r * 0.5f, y + r * 0.22f, fl)
                    c.drawRect(cx + r * 0.5f, y, cx + r * 0.78f, y + r * 0.22f, fl)
                }
            }
            "dice" -> {
                c.drawRoundRect(RectF(cx - r * 0.8f, cy - r * 0.8f, cx + r * 0.8f, cy + r * 0.8f), r * 0.28f, r * 0.28f, st)
                for (d in listOf(-0.4f to -0.4f, 0.4f to -0.4f, 0f to 0f, -0.4f to 0.4f, 0.4f to 0.4f)) c.drawCircle(cx + d.first * r, cy + d.second * r, r * 0.11f, fl)
            }
        }
    }

    private fun glifoTexto(c: Canvas, s: Float, texto: String, tam: Float, dy: Float = 0f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textSize = tam; p.color = Color.WHITE; p.textAlign = Paint.Align.CENTER; p.isFakeBoldText = true
        p.setShadowLayer(s * 0.03f, 0f, s * 0.015f, Color.argb(120, 0, 0, 0))
        val m = p.fontMetrics
        c.drawText(texto, s / 2f, s / 2f - (m.ascent + m.descent) / 2f + dy, p)
    }

    /** Baldosa de color con un simbolo dibujado en el centro (nombres de glifo en "glifo"). Un numero ("1".."9") dibuja la cifra. */
    fun icono(g: String, color: Int, s: Int = 112): Bitmap {
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val f = s.toFloat()
        baldosa(c, f, color)
        // halo detras del simbolo
        val h = Paint(Paint.ANTI_ALIAS_FLAG)
        h.shader = RadialGradient(f / 2f, f / 2f, f * 0.42f, Color.argb(60, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        c.drawCircle(f / 2f, f / 2f, f * 0.42f, h)
        if (g.length == 1 && g[0].isDigit()) glifoTexto(c, f, g, f * 0.56f)
        else glifo(c, g, f / 2f, f / 2f, f * 0.27f)
        return b
    }

    /** Baldosa de brillo: del gris al amarillo segun el valor (0..100) con el numero. */
    fun brillo(v: Int, s: Int = 112): Bitmap {
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val f = s.toFloat()
        baldosa(c, f, mezcla(Color.rgb(51, 65, 85), Color.rgb(245, 158, 11), v / 100f))
        glifo(c, "sun", f / 2f, f * 0.3f, f * 0.16f)
        glifoTexto(c, f, "$v", f * 0.34f, f * 0.17f)
        return b
    }

    /** Texto de lo que esta haciendo la barra (para el monitor del coche). */
    fun accion(ble: BleController?): String {
        val e = ble?.estado
        if (ble == null || ble.conn != Conn.CONECTADO || e == null) return t("act_con")
        if (e.off) return t("act_off")
        if (e.h > 0) return t("act_show", t("th${e.h.coerceIn(1, 9)}"))
        if (!e.b) return t("act_wel")
        val v = ble.vivo
        val uniforme = v.isNotEmpty() && (v.maxOrNull() ?: 0) - (v.minOrNull() ?: 0) < 10
        return if (uniforme) t("act_fija") else t("act_anim")
    }

    /**
     * Monitor en vivo de la barra: que hace, brillo exacto, los 34 canales con su luz real y el medidor del sensor.
     * conectado=false -> todo en gris.
     */
    fun estado(conectado: Boolean, apagada: Boolean, show: Boolean, brilloPct: Int, vivo: IntArray, medidor: Int, s: Int = 384,
               titulo: String = "", etSensor: String = "SENSOR", etMov: String = "", etBrillo: String = "BRILLO"): Bitmap {
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val f = s / 320f
        val fondo = Paint(Paint.ANTI_ALIAS_FLAG)
        fondo.shader = LinearGradient(0f, 0f, 0f, s.toFloat(), Color.rgb(15, 27, 48), Color.rgb(5, 8, 15), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(0f, 0f, s.toFloat(), s.toFloat()), 28f * f, 28f * f, fondo)

        val colorEstado = when {
            !conectado -> Color.rgb(100, 116, 139)
            apagada -> Color.rgb(244, 63, 94)
            show -> Color.rgb(168, 85, 247)
            else -> Color.rgb(34, 197, 94)
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val x0 = 16f * f; val ancho = s - 32f * f

        // Cabecera: punto de estado + lo que hace la barra
        p.color = colorEstado
        c.drawCircle(x0 + 8f * f, 30f * f, 8f * f, p)
        p.textAlign = Paint.Align.LEFT; p.isFakeBoldText = true; p.textSize = 19f * f; p.color = Color.WHITE
        var tit = titulo
        while (tit.length > 3 && p.measureText(tit) > ancho - 26f * f) tit = tit.dropLast(2)
        if (tit != titulo) tit += "…"
        c.drawText(tit, x0 + 24f * f, 37f * f, p)

        // Brillo grande + etiqueta
        p.textAlign = Paint.Align.CENTER; p.textSize = 92f * f
        p.color = if (conectado && !apagada) Color.rgb(56, 189, 248) else Color.rgb(100, 116, 139)
        val txt = if (!conectado) "…" else if (apagada) "OFF" else "$brilloPct"
        c.drawText(txt, s / 2f - (if (conectado && !apagada) 12f * f else 0f), 128f * f, p)
        if (conectado && !apagada) {
            p.textAlign = Paint.Align.LEFT; p.textSize = 26f * f; p.color = Color.rgb(125, 211, 252)
            c.drawText("%", s / 2f + 40f * f + (if (brilloPct >= 100) 26f * f else 0f), 128f * f, p)
        }
        p.textAlign = Paint.Align.CENTER; p.textSize = 12.5f * f; p.color = Color.rgb(100, 116, 139)
        c.drawText(etBrillo, s / 2f, 148f * f, p)

        // Barra de brillo (0..100)
        val yb = 158f * f; val hb = 7f * f
        p.color = Color.rgb(30, 41, 59)
        c.drawRoundRect(RectF(x0, yb, x0 + ancho, yb + hb), 4f * f, 4f * f, p)
        if (conectado && !apagada) {
            val rb = Paint(Paint.ANTI_ALIAS_FLAG)
            rb.shader = LinearGradient(x0, 0f, x0 + ancho, 0f, Color.rgb(14, 116, 144), Color.rgb(253, 224, 71), Shader.TileMode.CLAMP)
            c.save(); c.clipRect(x0, yb, x0 + ancho * (brilloPct / 100f).coerceIn(0f, 1f), yb + hb)
            c.drawRoundRect(RectF(x0, yb, x0 + ancho, yb + hb), 4f * f, 4f * f, rb)
            c.restore()
        }

        // Los 34 canales, como se ven en la barra real (luz calida) y con brillo suave
        val n = 34
        val paso = ancho / n
        val yTop = 178f * f; val alto = 56f * f
        var suma = 0f
        for (i in 0 until n) suma += if (conectado && !apagada && vivo.size > i) vivo[i].coerceIn(0, 255) / 255f else 0f
        val media = suma / n
        if (media > 0.02f) {
            val g = Paint(Paint.ANTI_ALIAS_FLAG)
            g.maskFilter = BlurMaskFilter(14f * f, BlurMaskFilter.Blur.NORMAL)
            for (i in 0 until n) {
                val nv = if (vivo.size > i) vivo[i].coerceIn(0, 255) / 255f else 0f
                if (nv > 0.05f) {
                    g.color = Color.argb((110 * nv).toInt(), 255, 214, 140)
                    c.drawRoundRect(RectF(x0 + i * paso, yTop, x0 + (i + 1) * paso, yTop + alto), 3f * f, 3f * f, g)
                }
            }
        }
        for (i in 0 until n) {
            val nv = if (conectado && !apagada && vivo.size > i) (vivo[i].coerceIn(0, 255) / 255f) else 0f
            p.color = if (nv < 0.03f) Color.rgb(20, 28, 42) else Color.rgb((255 * (0.25f + 0.75f * nv)).toInt(), (255 * (0.22f + 0.66f * nv)).toInt(), (255 * (0.12f + 0.48f * nv)).toInt())
            c.drawRoundRect(RectF(x0 + i * paso + 1f * f, yTop, x0 + (i + 1) * paso - 1f * f, yTop + alto), 3f * f, 3f * f, p)
        }

        // Medidor del sensor (100 = linea de deteccion)
        val ym = 262f * f; val hm = 15f * f
        p.textAlign = Paint.Align.LEFT; p.textSize = 12.5f * f; p.color = Color.rgb(100, 116, 139)
        c.drawText(etSensor, x0, ym - 6f * f, p)
        if (conectado && medidor >= 100 && etMov.isNotEmpty()) {
            p.textAlign = Paint.Align.RIGHT; p.color = Color.rgb(248, 113, 113)
            c.drawText(etMov, x0 + ancho, ym - 6f * f, p)
        }
        p.color = Color.rgb(30, 41, 59)
        c.drawRoundRect(RectF(x0, ym, x0 + ancho, ym + hm), 7f * f, 7f * f, p)
        val fr = (medidor / 150f).coerceIn(0f, 1f)
        if (conectado && fr > 0f) {
            val rm = Paint(Paint.ANTI_ALIAS_FLAG)
            rm.shader = LinearGradient(x0, 0f, x0 + ancho, 0f, intArrayOf(Color.rgb(34, 197, 94), Color.rgb(250, 204, 21), Color.rgb(239, 68, 68)), null, Shader.TileMode.CLAMP)
            c.save(); c.clipRect(x0, ym, x0 + ancho * fr, ym + hm)
            c.drawRoundRect(RectF(x0, ym, x0 + ancho, ym + hm), 7f * f, 7f * f, rm)
            c.restore()
        }
        p.color = Color.WHITE
        val xl = x0 + ancho * (100f / 150f)
        c.drawRect(xl - 1.5f * f, ym - 3f * f, xl + 1.5f * f, ym + hm + 3f * f, p)
        return b
    }
}
