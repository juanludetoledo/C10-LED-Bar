package com.juanlu.c10led

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Android Auto como app de PLANTILLAS (categoria IOT): solo el icono en la fila de apps y, al tocarlo,
 * una rejilla de botones de colores. Sin reproductor ni tarjeta de multimedia.
 * Mientras esta abierta mantiene la conexion Bluetooth con la centralita (la app del movil se cierra sola).
 */
class C10CarService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = C10Session()
}

class C10Session : Session() {
    private var ctl: CocheCtl? = null

    override fun onCreateScreen(intent: Intent): Screen {
        I18n.iniciar(carContext.applicationContext)
        val c = CocheCtl(carContext)
        ctl = c
        lifecycle.addObserver(LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_CREATE -> c.iniciar()
                Lifecycle.Event.ON_DESTROY -> c.detener()
                else -> {}
            }
        })
        return PantallaRaiz(carContext, c)
    }
}

/** Conexion Bluetooth + ordenes (misma logica que la antigua pantalla multimedia). */
class CocheCtl(private val cc: CarContext) {
    private val main = Handler(Looper.getMainLooper())
    var ble: BleController? = null
    private var activo = false
    private var ultimoIntento = 0L
    private var claveBase = ""
    private var claveVivo = ""
    private val escuchas = mutableListOf<RejillaScreen>()

    fun alta(s: RejillaScreen) { if (!escuchas.contains(s)) escuchas.add(s) }
    fun baja(s: RejillaScreen) { escuchas.remove(s) }

    fun iniciar() {
        if (activo) return
        activo = true
        val espera = if (MainActivity.cerrarPorCoche()) 900L else 0L
        main.postDelayed({
            if (!activo) return@postDelayed
            ble = BleController(cc.applicationContext)
            ble?.conectar()
            ultimoIntento = System.currentTimeMillis()
            latido()
        }, espera)
    }

    fun detener() {
        activo = false
        main.removeCallbacksAndMessages(null)
        try { ble?.desconectar() } catch (e: Exception) { }
        ble = null
    }

    private fun latido() {
        if (!activo) return
        val b = ble
        if (b != null && b.conn == Conn.DESCONECTADO && System.currentTimeMillis() - ultimoIntento > 8000) {
            ultimoIntento = System.currentTimeMillis()
            b.conectar()
        }
        val e = b?.estado
        val kb = "${b?.conn}|${e?.off}|${e?.n}|${e?.h}"
        val kv = kb + "|${b?.medidor}|${(b?.vivo ?: IntArray(0)).joinToString(",")}"
        val cambioB = kb != claveBase
        val cambioV = kv != claveVivo
        claveBase = kb; claveVivo = kv
        if (cambioB || cambioV) {
            for (s in escuchas.toList()) {
                try { if (cambioB || s.vivo) s.invalidate() } catch (x: Exception) { }
            }
        }
        main.postDelayed({ latido() }, 1000)
    }

    private fun enviar(cmd: String) { ble?.enviar(cmd) }

    private fun reiniciar() {
        if (ble?.estado?.off == true) { enviar("ON"); main.postDelayed({ enviar("RESTART") }, 1500) }
        else enviar("RESTART")
    }

    fun ejecutar(cmd: String) {
        when (cmd) {
            "RESTART" -> reiniciar()
            "BRUP" -> ble?.estado?.let { enviar("BR:${(it.n + 50).coerceIn(0, 1000)}") }
            "BRDN" -> ble?.estado?.let { enviar("BR:${(it.n - 50).coerceIn(0, 1000)}") }
            "AUTO" -> enviar(Prog.cargar(cc.applicationContext).ordenActual())
            else -> enviar(cmd)
        }
    }

    fun resumen(): String {
        val b = ble
        val e = b?.estado
        return when {
            b?.conn != Conn.CONECTADO || e == null -> t("car_st_con")
            e.off -> t("car_st_off")
            e.h > 0 -> t("car_st_show")
            else -> t("car_st_on") + " · " + t("car_sub", "${(e.n + 5) / 10}")
        }
    }
}

class Elem(val titulo: String, val icono: Bitmap, val texto: String? = null, val accion: () -> Unit)

abstract class RejillaScreen(cc: CarContext, protected val ctl: CocheCtl, private val sub: Boolean, val vivo: Boolean = false) : Screen(cc) {
    private var pagina = 0

    init {
        lifecycle.addObserver(LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> ctl.alta(this)
                Lifecycle.Event.ON_STOP -> ctl.baja(this)
                else -> {}
            }
        })
    }

    abstract fun titulo(): String
    abstract fun elementos(): List<Elem>

    protected fun aviso(txt: String) {
        try { CarToast.makeText(carContext, txt, CarToast.LENGTH_SHORT).show() } catch (e: Exception) { }
    }

    protected fun cmd(tit: String, icono: Bitmap, orden: String, msg: String? = null) =
        Elem(tit, icono) { ctl.ejecutar(orden); aviso(msg ?: tit) }

    protected fun carpeta(tit: String, icono: Bitmap, abrir: () -> Screen) =
        Elem(tit, icono) { screenManager.push(abrir()) }

    private fun icon(b: Bitmap) = CarIcon.Builder(IconCompat.createWithBitmap(b)).build()

    override fun onGetTemplate(): Template {
        val cm = carContext.getCarService(ConstraintManager::class.java)
        val limite = maxOf(3, try { cm.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID) } catch (e: Exception) { 6 })
        val todos = elementos()
        val paginas = if (todos.size <= limite) 1 else (todos.size + limite - 2) / (limite - 1)
        if (pagina >= paginas) pagina = 0
        val visibles = if (paginas == 1) todos else todos.drop(pagina * (limite - 1)).take(limite - 1)
        val lista = ItemList.Builder()
        for (el in visibles) {
            val g = GridItem.Builder().setTitle(el.titulo).setImage(icon(el.icono), GridItem.IMAGE_TYPE_LARGE)
                .setOnClickListener { el.accion() }
            if (el.texto != null) g.setText(el.texto)
            lista.addItem(g.build())
        }
        if (paginas > 1) {
            lista.addItem(
                GridItem.Builder().setTitle("${pagina + 1}/$paginas ▸")
                    .setImage(icon(IconosCoche.icono("plus", Color.rgb(71, 85, 105))), GridItem.IMAGE_TYPE_LARGE)
                    .setOnClickListener { pagina = (pagina + 1) % paginas; invalidate() }.build()
            )
        }
        return GridTemplate.Builder()
            .setTitle(titulo())
            .setHeaderAction(if (sub) Action.BACK else Action.APP_ICON)
            .setSingleList(lista.build())
            .build()
    }
}

private val AZUL = Color.rgb(14, 116, 144)
private val VERDE = Color.rgb(22, 163, 74)
private val ROJO = Color.rgb(225, 29, 72)
private val MORADO = Color.rgb(124, 58, 237)
private val AMBAR = Color.rgb(217, 119, 6)
private val GRIS = Color.rgb(71, 85, 105)
private val NOCHE = Color.rgb(67, 56, 202)
private val CIAN = Color.rgb(8, 145, 178)

class PantallaRaiz(cc: CarContext, ctl: CocheCtl) : RejillaScreen(cc, ctl, false) {
    override fun titulo() = "C10 LED · " + ctl.resumen()
    override fun elementos(): List<Elem> {
        val p = Prog.cargar(carContext.applicationContext)
        return listOf(
            cmd(t("car_on"), IconosCoche.icono("power", VERDE), "ON"),
            cmd(t("car_off"), IconosCoche.icono("power", ROJO), "OFF"),
            cmd(t("car_day"), IconosCoche.icono("sun", AMBAR), "DAY:${p.valorDia}"),
            cmd(t("car_night"), IconosCoche.icono("moon", NOCHE), "NIGHT:${p.valorNoche}"),
            cmd(t("car_auto"), IconosCoche.icono("auto", Color.rgb(13, 148, 136)), "AUTO"),
            cmd(t("car_restart"), IconosCoche.icono("restart", GRIS), "RESTART"),
            carpeta(t("car_br"), IconosCoche.brillo(70)) { PantallaBrillo(carContext, ctl) },
            carpeta(t("car_fx"), IconosCoche.icono("sparkle", MORADO)) { PantallaShows(carContext, ctl) },
            carpeta(t("car_wel"), IconosCoche.icono("clap", CIAN)) { PantallaBienvenida(carContext, ctl) },
            carpeta(t("car_live"), IconosCoche.icono("live", AZUL)) { PantallaVivo(carContext, ctl) }
        )
    }
}

class PantallaBrillo(cc: CarContext, ctl: CocheCtl) : RejillaScreen(cc, ctl, true) {
    override fun titulo() = t("car_br")
    override fun elementos(): List<Elem> {
        val l = mutableListOf(
            cmd(t("car_brm"), IconosCoche.icono("minus", GRIS, 128), "BRDN"),
            cmd(t("car_brp"), IconosCoche.icono("plus", AMBAR, 128), "BRUP")
        )
        for (v in listOf(20, 40, 60, 66, 80, 100)) l += cmd("${t("car_br")} $v", IconosCoche.brillo(v, 128), "BR:${v * 10}")
        return l
    }
}

class PantallaShows(cc: CarContext, ctl: CocheCtl) : RejillaScreen(cc, ctl, true) {
    override fun titulo() = t("car_fx")
    override fun elementos(): List<Elem> {
        val gl = listOf("wave", "drop", "star", "sparkle", "sunset", "leaf", "comet", "film", "dice")
        val col = listOf(AZUL, Color.rgb(2, 132, 199), Color.rgb(202, 138, 4), MORADO, Color.rgb(219, 39, 119), VERDE, Color.rgb(234, 88, 12), NOCHE, GRIS)
        val l = mutableListOf(cmd(t("car_stop"), IconosCoche.icono("stop", ROJO, 112), "SHOW:STOP"))
        for (i in 1..9) l += cmd(t("th$i"), IconosCoche.icono(gl[i - 1], col[i - 1], 112), "SHOW:$i,30")
        return l
    }
}

class PantallaBienvenida(cc: CarContext, ctl: CocheCtl) : RejillaScreen(cc, ctl, true) {
    override fun titulo() = t("car_wel")
    override fun elementos(): List<Elem> {
        val col = listOf(AZUL, MORADO, VERDE, AMBAR, Color.rgb(219, 39, 119))
        return (1..5).map { cmd(t("w$it"), IconosCoche.icono("$it", col[it - 1], 128), "WPLAY:$it") }
    }
}

class PantallaVivo(cc: CarContext, ctl: CocheCtl) : RejillaScreen(cc, ctl, true, vivo = true) {
    override fun titulo() = t("car_live")
    override fun elementos(): List<Elem> {
        val b = ctl.ble
        val con = b?.conn == Conn.CONECTADO
        val e = b?.estado
        val pct = ((e?.n ?: 0) + 5) / 10
        val img = IconosCoche.estado(con && e != null, e?.off == true, e != null && e.h > 0, pct, b?.vivo ?: IntArray(34), b?.medidor ?: 0, 384,
            IconosCoche.accion(b), t("act_sens"), t("act_mov"), t("act_bri"))
        val sub = if (con && e != null) t("car_sensor") + " ${b?.medidor ?: 0}" else null
        return listOf(Elem(IconosCoche.accion(b), img, sub) { })
    }
}
