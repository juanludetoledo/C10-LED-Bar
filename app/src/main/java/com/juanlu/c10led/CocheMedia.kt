package com.juanlu.c10led

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat

/**
 * Pantalla de Android Auto (modo "app multimedia"), SOLO como menu de cuadricula con iconos de colores.
 * No se activa la sesion de reproduccion para que Android Auto no muestre el mini reproductor
 * (el "widget" con el boton de play). El estado en vivo (barra de 34 canales, brillo y sensor) va dentro de
 * la carpeta "Estado en vivo", que se redibuja cada segundo mientras esta abierta.
 * Mientras esta abierta mantiene la conexion Bluetooth con la centralita (la app del movil se cierra sola).
 */
class CocheMedia : MediaBrowserServiceCompat() {
    private lateinit var sesion: MediaSessionCompat
    private val main = Handler(Looper.getMainLooper())
    private var ble: BleController? = null
    private var activo = false
    private var ultimoIntento = 0L
    private var ultimaClave = ""

    companion object {
        private const val A_REINICIAR = "reiniciar"
        private const val A_NOCHE = "noche"
        private const val A_DIA = "dia"
        private const val A_PARAR = "parar"
        private const val ESTILO = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        private const val ESTILO_B = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val ESTILO_P = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val LISTA = 1
        private const val CUADRICULA = 2
    }

    override fun onCreate() {
        super.onCreate()
        I18n.iniciar(applicationContext)
        sesion = MediaSessionCompat(this, "C10LED")
        sesion.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) { ejecutar(mediaId) }
        })
        // Sin sesion activa y sin estado: Android Auto no dibuja el mini reproductor.
        sesion.isActive = false
        sesion.setPlaybackState(
            PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)
                .setState(PlaybackStateCompat.STATE_NONE, 0L, 1f).build()
        )
        sessionToken = sesion.sessionToken
    }

    override fun onDestroy() {
        activo = false
        main.removeCallbacksAndMessages(null)
        try { ble?.desconectar() } catch (e: Exception) { }
        ble = null
        sesion.release()
        super.onDestroy()
    }

    // ---- Conexion y ordenes -------------------------------------------------------------------

    private fun iniciar() {
        if (activo) return
        activo = true
        // Si la app del movil estaba abierta se cierra primero (la centralita solo admite un movil).
        val espera = if (MainActivity.cerrarPorCoche()) 900L else 0L
        main.postDelayed({
            if (!activo) return@postDelayed
            ble = BleController(applicationContext)
            ble?.conectar()
            ultimoIntento = System.currentTimeMillis()
            latido()
        }, espera)
    }

    private fun latido() {
        if (!activo) return
        val b = ble
        if (b != null && b.conn == Conn.DESCONECTADO && System.currentTimeMillis() - ultimoIntento > 8000) {
            ultimoIntento = System.currentTimeMillis()
            b.conectar()
        }
        refrescar(false)
        main.postDelayed({ latido() }, 1000)
    }

    private fun enviar(cmd: String) { ble?.enviar(cmd) }

    private fun cambiarBrillo(delta: Int) {
        val e = ble?.estado ?: return
        enviar("BR:${(e.n + delta).coerceIn(0, 1000)}")
    }

    private fun reiniciar() {
        if (ble?.estado?.off == true) { enviar("ON"); main.postDelayed({ enviar("RESTART") }, 1500) }
        else enviar("RESTART")
    }

    private fun ejecutar(id: String?) {
        if (id == null) return
        val cmd = id.removePrefix("cmd:")
        if (cmd == id) return
        when (cmd) {
            "RESTART" -> reiniciar()
            "BRUP" -> cambiarBrillo(+50)
            "BRDN" -> cambiarBrillo(-50)
            "AUTO" -> enviar(Prog.cargar(applicationContext).ordenActual())
            else -> enviar(cmd)
        }
    }

    // ---- Estado en vivo (carpeta "vivo") ------------------------------------------------------

    private fun refrescar(forzar: Boolean) {
        val b = ble
        val clave = "${b?.conn}|${b?.estado?.off}|${b?.estado?.n}|${b?.estado?.h}|${b?.medidor}|${(b?.vivo ?: IntArray(0)).joinToString(",")}"
        if (!forzar && clave == ultimaClave) return
        ultimaClave = clave
        notifyChildrenChanged("vivo")
        notifyChildrenChanged("raiz")
    }

    private fun tarjetaVivo(): MediaBrowserCompat.MediaItem {
        val b = ble
        val con = b?.conn == Conn.CONECTADO
        val e = b?.estado
        val apagada = e?.off == true
        val show = e != null && e.h > 0
        val pct = ((e?.n ?: 0) + 5) / 10
        val titulo = IconosCoche.accion(b)
        val sub = if (con && e != null) t("car_sub", "$pct") + "  ·  " + t("car_sensor") + " ${b?.medidor ?: 0}" else ""
        val img = IconosCoche.estado(con && e != null, apagada, show, pct, b?.vivo ?: IntArray(34), b?.medidor ?: 0, 384,
            IconosCoche.accion(b), t("act_sens"), t("act_mov"), t("act_bri"))
        return item("vivo_t", titulo, true, img, sub)
    }

    // ---- Navegacion (cuadricula con iconos) ----------------------------------------------------

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        iniciar()
        val ex = Bundle()
        ex.putBoolean(ESTILO, true)
        ex.putInt(ESTILO_B, CUADRICULA)
        ex.putInt(ESTILO_P, CUADRICULA)
        return BrowserRoot("raiz", ex)
    }

    private fun item(id: String, titulo: String, carpeta: Boolean, icono: android.graphics.Bitmap, sub: String? = null): MediaBrowserCompat.MediaItem {
        val d = MediaDescriptionCompat.Builder().setMediaId(id).setTitle(titulo).setIconBitmap(icono)
        if (sub != null) d.setSubtitle(sub)
        return MediaBrowserCompat.MediaItem(d.build(), if (carpeta) MediaBrowserCompat.MediaItem.FLAG_BROWSABLE else MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val l = mutableListOf<MediaBrowserCompat.MediaItem>()
        val azul = Color.rgb(14, 116, 144); val verde = Color.rgb(22, 163, 74); val rojo = Color.rgb(225, 29, 72)
        val morado = Color.rgb(124, 58, 237); val ambar = Color.rgb(217, 119, 6); val gris = Color.rgb(71, 85, 105)
        val verdeC = Color.rgb(22, 163, 74); val noche = Color.rgb(67, 56, 202); val cian = Color.rgb(8, 145, 178)
        when (parentId) {
            "raiz" -> {
                val sb = ble?.estado?.let { t("car_sub", "${(it.n + 5) / 10}") }
                l += item("vivo", t("car_live"), true, IconosCoche.icono("live", azul), sb)
                l += item("cmd:ON", t("car_on"), false, IconosCoche.icono("power", verdeC))
                l += item("cmd:OFF", t("car_off"), false, IconosCoche.icono("power", rojo))
                l += item("cmd:DAY:${Prog.cargar(applicationContext).valorDia}", t("car_day"), false, IconosCoche.icono("sun", ambar))
                l += item("cmd:NIGHT:${Prog.cargar(applicationContext).valorNoche}", t("car_night"), false, IconosCoche.icono("moon", noche))
                l += item("cmd:AUTO", t("car_auto"), false, IconosCoche.icono("auto", Color.rgb(13, 148, 136)))
                l += item("cmd:RESTART", t("car_restart"), false, IconosCoche.icono("restart", gris))
                l += item("br", t("car_br"), true, IconosCoche.brillo(70))
                l += item("fx", t("car_fx"), true, IconosCoche.icono("sparkle", morado))
                l += item("bv", t("car_wel"), true, IconosCoche.icono("clap", cian))
            }
            "vivo" -> { l += tarjetaVivo() }
            "vivo_t" -> { }
            "br" -> {
                l += item("cmd:BRDN", t("car_brm"), false, IconosCoche.icono("minus", gris, 128))
                l += item("cmd:BRUP", t("car_brp"), false, IconosCoche.icono("plus", ambar, 128))
                for (v in listOf(20, 40, 60, 66, 80, 100)) l += item("cmd:BR:${v * 10}", "${t("car_br")} $v", false, IconosCoche.brillo(v, 128))
            }
            "fx" -> {
                val gl = listOf("wave", "drop", "star", "sparkle", "sunset", "leaf", "comet", "film", "dice")
                val col = listOf(azul, Color.rgb(2, 132, 199), Color.rgb(202, 138, 4), morado, Color.rgb(219, 39, 119), verdeC, Color.rgb(234, 88, 12), noche, gris)
                for (i in 1..9) l += item("cmd:SHOW:$i,30", t("th$i"), false, IconosCoche.icono(gl[i - 1], col[i - 1], 96))
                l += item("cmd:SHOW:STOP", t("car_stop"), false, IconosCoche.icono("stop", rojo, 96))
            }
            "bv" -> {
                val col = listOf(azul, morado, verdeC, ambar, Color.rgb(219, 39, 119))
                for (i in 1..5) l += item("cmd:WPLAY:$i", t("w$i"), false, IconosCoche.icono("$i", col[i - 1], 128))
            }
        }
        result.sendResult(l)
    }
}
