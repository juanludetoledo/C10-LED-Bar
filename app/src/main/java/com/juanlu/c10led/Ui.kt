package com.juanlu.c10led

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

internal val Bg = Color(0xFF080C14)
internal val Muted = Color(0xFFABB7C9)
internal val Acento = Color(0xFF38BDF8)
internal val AcentoB = Color(0xFF2563EB)
internal val TxtAcento = Color.White
internal val Line = Color(0x33A3B4CC)
private val Condensada = FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))
internal val Txt = Color(0xFFF4F7FB)

/** PWM de una escala 0..1000 (igual que el sketch: 655 = 0x0650 exacto). */
private fun pwmDe(v: Int): Int =
    if (v == 655) 0x0650 else (4095.0 * (v / 1000.0).pow(2.2)).roundToInt().coerceIn(3, 4095)

private data class Ajustes(val e: Int, val r: Int, val p: Int, val k: Int, val w: Int, val d: Int, val rn: Int, val rx: Int, val pa: Int = 30, val pn: Int = 200, val px: Int = 700, val lista: List<Int> = emptyList())

@Composable
fun C10App(ble: BleController) {
    val ctx = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) { ble.conectar(); Auto.programar(ctx.applicationContext) } else ble.mensaje = "m_perm"
    }
    fun pedirYConectar() {
        val faltan = permisosNecesarios().any { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (faltan) launcher.launch(permisosNecesarios()) else ble.conectar()
    }
    LaunchedEffect(Unit) { pedirYConectar() }

    val conectado = ble.conn == Conn.CONECTADO
    val est = ble.estado
    val apagada = est?.off == true
    val showOn = (est?.h ?: 0) > 0

    var pestana by rememberSaveable { mutableStateOf(0) }
    var tecnica by rememberSaveable { mutableStateOf(false) }
    var abrirConst by rememberSaveable { mutableStateOf(false) }
    var avisoReinicio by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var toques by remember { mutableStateOf(listOf<Long>()) }

    // ---- Programacion (por movil) ----
    var prog by remember { mutableStateOf(Prog.cargar(ctx)) }
    fun cambiarProg(p: Prog) { prog = p; Prog.guardar(ctx, p); Auto.programar(ctx.applicationContext) }
    // Ubicacion para el modo sol: se refresca al abrir la app y al activar el modo
    fun refrescarUbicacion() { Sol.actualizar(ctx) { prog = Prog.cargar(ctx).copy(); Auto.programar(ctx.applicationContext) } }
    val lanzaUbic = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refrescarUbicacion() }
    LaunchedEffect(prog.sol) { if (prog.sol) { if (Sol.tienePermiso(ctx)) refrescarUbicacion() else lanzaUbic.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)) } }

    // ---- Brillo ----
    var brillo by rememberSaveable { mutableStateOf(655) }
    var arrastrando by remember { mutableStateOf(false) }
    var kPrev by rememberSaveable { mutableStateOf(5) }   // sensibilidad antes de ignorar el sensor
    LaunchedEffect(est?.n, arrastrando) { if (!arrastrando && est != null) brillo = est.n }
    var ultimoEnvio by remember { mutableStateOf(0L) }
    fun poner(v: Int, forzar: Boolean) {
        val n = v.coerceIn(0, 1000)
        brillo = n
        val ahora = System.currentTimeMillis()
        if (forzar || ahora - ultimoEnvio >= 40) {
            ultimoEnvio = ahora
            // Sketch 2.7+: BRI = brillo inmediato (sin fundido ni expansion), sigue al dedo en tiempo real.
            val inmediato = (est?.v?.toFloatOrNull() ?: 0f) >= 2.7f
            ble.enviar(if (inmediato) "BRI:$n" else "BR:$n")
        }
    }

    // ---- Aplicacion automatica con la app abierta ----
    // Al conectar (y al cambiar de periodo, y al acabar un show) se aplica el brillo del periodo actual
    // con expansion. Un brillo manual manda hasta el siguiente cambio de periodo.
    val progActual by rememberUpdatedState(prog)
    LaunchedEffect(conectado, prog.activa, prog.diaDesde, prog.nocheDesde, prog.sol, prog.lat, prog.lon) {
        var perAplicado: Boolean? = null
        var vistoShow = false
        while (conectado) {
            val e = ble.estado
            if (e != null && !e.off) {
                if (e.h > 0) vistoShow = true
                else if (progActual.activa) {
                    val dia = progActual.esDiaAhora()
                    if (dia != perAplicado || vistoShow) {
                        perAplicado = dia
                        vistoShow = false
                        ble.enviar(progActual.ordenActual())
                    }
                }
            }
            delay(2000)
        }
    }

    // ---- Ajustes en edicion ----
    var aj by remember { mutableStateOf<Ajustes?>(null) }
    LaunchedEffect(est != null) {
        val e = ble.estado
        if (aj == null && e != null) aj = Ajustes(e.e, e.r, e.p, e.k, e.w, e.d, e.rn, e.rx, e.pa, e.pn, e.px, e.lista)
    }
    // Al pulsar ON la centralita vuelve a sus ajustes guardados: la pantalla de ajustes se vuelve a leer.
    var ajReinit by remember { mutableStateOf(0) }
    LaunchedEffect(ajReinit) {
        if (ajReinit > 0) {
            delay(900)
            val e = ble.estado
            if (e != null) aj = Ajustes(e.e, e.r, e.p, e.k, e.w, e.d, e.rn, e.rx, e.pa, e.pn, e.px, e.lista)
        }
    }
    var avisoAj by remember { mutableStateOf("") }
    var confirmarReset by remember { mutableStateOf(false) }
    var confirmarGuardar by remember { mutableStateOf(false) }
    var guardadoTick by remember { mutableStateOf(0) }
    var enviadoAj by remember { mutableStateOf<List<Int>?>(null) }
    var verifFallo by remember { mutableStateOf(false) }
    var ultimoGuardado by remember { mutableStateOf(ctx.getSharedPreferences("c10prefs", 0).getString("ult_guardado", "") ?: "") }
    LaunchedEffect(guardadoTick) {
        val env = enviadoAj
        if (guardadoTick > 0 && env != null) {
            avisoAj = t("se_ver_wait"); verifFallo = false
            delay(1800)
            val e = ble.estado
            val ok = e != null && e.e == env[0] && e.r == env[1] && e.p == env[2] && e.k == env[3] &&
                kotlin.math.abs(e.d - env[4]) <= 1 && e.rn == env[5] && e.rx == env[6] && e.pa == env[7] && e.pn == env[8] && e.px == env[9]
            if (ok) {
                avisoAj = t("se_ver_ok"); verifFallo = false
                val fecha = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                val txt = fecha + " · " + t("se_base_short") + " " + env[4] + " · " + t("se_wait_short") + " " + env[0] + " s · " +
                    t("se_breath_short") + " " + env[1] + " s · " + env[5] + "–" + env[6]
                ctx.getSharedPreferences("c10prefs", 0).edit().putString("ult_guardado", txt).apply()
                ultimoGuardado = txt
            } else { avisoAj = t("se_ver_fail"); verifFallo = true }
        }
    }
    var recordarOff by remember { mutableStateOf(ctx.getSharedPreferences("c10prefs", 0).getBoolean("rem_off", false)) }
    var recordarSen by remember { mutableStateOf(ctx.getSharedPreferences("c10prefs", 0).getBoolean("rem_sen", false)) }
    var duracionShow by rememberSaveable { mutableStateOf(0) }

    // Vista en vivo de la barra: se pide mientras se ven las pestanas Control o Efectos.
    LaunchedEffect(conectado, pestana) {
        while (conectado && (pestana == 0 || pestana == 1)) { ble.enviar("LIVE:1"); delay(8000) }
    }

    // Medidor del sensor: se pide mientras se ve la pestana de ajustes o la pantalla tecnica.
    LaunchedEffect(conectado, pestana, tecnica) {
        while (conectado && (pestana == 2 || tecnica)) { ble.enviar("METER:1"); delay(8000) }
    }

    val pillTexto = when {
        ble.conn == Conn.BUSCANDO -> t("p_search")
        ble.conn == Conn.CONECTANDO -> t("p_conn")
        !conectado -> t("p_nocon")
        apagada -> t("p_off")
        showOn -> t("p_show")
        else -> t("p_normal")
    }
    val pillColor = when {
        !conectado -> Color(0xFFCBD5E1)
        apagada -> Color(0xFFFF8790)
        showOn -> Color(0xFFFDE68A)
        else -> Color(0xFF86EFAC)
    }
    val pillFondo = when {
        !conectado -> Color(0xFF1F2937)
        apagada -> Color(0xFF3B1720)
        showOn -> Color(0xFF3A2A08)
        else -> Color(0xFF123021)
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Bg, surface = Bg, primary = Acento, onPrimary = TxtAcento,
        secondary = Acento, tertiary = Acento, primaryContainer = Color(0xFF0C4A6E), onPrimaryContainer = Acento
    )) {
      // ---- Reescalado automatico para pantalla grande (coche) ----
      // En una pantalla grande (>=1000dp de ancho) la app se maqueta como si midiera
      // ANCHO_DISENO_COCHE dp de ancho, asi todo (texto, botones, sliders) sale grande
      // y tocable. En el movil no se toca nada: se usa la densidad normal.
      val cfgC = LocalConfiguration.current
      val densBase = LocalDensity.current
      val pantallaGrande = cfgC.screenWidthDp >= 1000
      val ANCHO_DISENO_COCHE = 1450f   // baja este numero = todo mas grande ; sube = mas pequeño
      // Alto de la pantalla en dp de diseño (para centrar Efectos y que llene a lo alto en el coche)
      val altoPantallaDp = ANCHO_DISENO_COCHE * (cfgC.screenHeightDp.toFloat() / cfgC.screenWidthDp.toFloat().coerceAtLeast(1f))
      val ALTO_CABECERA_DP = 150f      // margen reservado para pill + pestañas + paddings (ajustable)
      val densCoche = if (pantallaGrande)
          Density(with(densBase) { cfgC.screenWidthDp.dp.toPx() } / ANCHO_DISENO_COCHE, densBase.fontScale)
      else densBase
      CompositionLocalProvider(LocalDensity provides densCoche) {
        Surface(
            modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0F1A2E), Color(0xFF080C14), Color(0xFF070A11)))),
            color = Color.Transparent
        ) {
            if (abrirConst) PantallaConstructor(ble, est, conectado) { abrirConst = false } else
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .statusBarsPadding().navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                // ---- Idiomas ----
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    I18n.idiomas.forEach { (cod, nombre) ->
                        Chip(nombre, I18n.codigo == cod) { I18n.poner(ctx, cod) }
                    }
                }
                // ---- Cabecera (5 toques en el titulo = pantalla tecnica) ----
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, bottom = 12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("BARRA", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.5.sp)
                        Text(
                            "C10 LED", color = Acento, fontSize = 34.sp, fontFamily = Condensada, letterSpacing = 1.2.sp,
                            modifier = Modifier.clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                                val ahora = System.currentTimeMillis()
                                val l = toques.filter { ahora - it < 3000 } + ahora
                                if (l.size >= 5) { tecnica = !tecnica; toques = emptyList() } else toques = l
                            }
                        )
                        TiraLeds()
                        Text(t("sub"), color = Muted, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (ble.mensaje.isNotEmpty() && !conectado) {
                        Text(I18n.tm(ble.mensaje), color = Color(0xFFFBBF24), fontSize = 11.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(pillFondo).padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(pillColor))
                        Spacer(Modifier.width(7.dp))
                        Text(pillTexto, color = pillColor, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                if (!conectado) {
                    Boton(t("connect"), Modifier.fillMaxWidth().padding(bottom = 12.dp), enabled = ble.conn == Conn.DESCONECTADO, principal = true) { pedirYConectar() }
                }

                // ---- Pestanas ----
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("tab_ctl", "tab_fx", "tab_set", "tab_sys").forEachIndexed { i, k ->
                        Chip(t(k), pestana == i, Modifier.weight(1f), unaLinea = true) { pestana = i }
                    }
                }

                when (pestana) {
                    // =============================== CONTROL ===============================
                    0 -> {
                        // 4 tarjetas como lambdas: en movil se apilan (igual que siempre), en coche van en 2x2
                        val cardQuick: @Composable () -> Unit = {
                        Tarjeta {
                            Etiq(t("quick"))
                            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                                BigButton(Modifier.weight(1f), t("off_t"), t("off_s"), false, Icons.Filled.Close, conectado, if (est == null) 0 else if (apagada) 1 else -1) {
                                    ble.enviar(if (recordarOff) "OFFSAVE" else "OFF")
                                }
                                BigButton(Modifier.weight(1f), t("on_t"), t("on_s"), true, Icons.Filled.PowerSettingsNew, conectado, if (est == null) 0 else if (apagada) -1 else 1) {
                                    ble.enviar("ON"); avisoAj = ""; ajReinit++
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                                Checkbox(checked = recordarOff, onCheckedChange = {
                                    recordarOff = it
                                    ctx.getSharedPreferences("c10prefs", 0).edit().putBoolean("rem_off", it).apply()
                                })
                                Column(Modifier.padding(top = 10.dp)) {
                                    Text(t("se_remember"), color = Txt, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(t("se_remember_h"), color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                                }
                            }
                            if (apagada && est?.o == true) {
                                Aviso(t("off_saved"))
                            }
                            // ---- Sensor de movimiento ON / OFF (temporal o recordado), sin guardar ajustes ----
                            Spacer(Modifier.height(14.dp))
                            Etiq(t("sn_title"))
                            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                                BigButton(Modifier.weight(1f), t("sn_off_t"), t("sn_off_s"), false, Icons.Filled.SensorsOff, conectado, if (est == null || (est.v.toFloatOrNull() ?: 0f) < 2.8f) 0 else if (est.so) 1 else -1) {
                                    ble.enviar(if (recordarSen) "SOFFSAVE" else "SOFF")
                                }
                                BigButton(Modifier.weight(1f), t("sn_on_t"), t("sn_on_s"), true, Icons.Filled.Sensors, conectado, if (est == null || (est.v.toFloatOrNull() ?: 0f) < 2.8f) 0 else if (est.so) -1 else 1) {
                                    ble.enviar("SON")
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                                Checkbox(checked = recordarSen, onCheckedChange = {
                                    recordarSen = it
                                    ctx.getSharedPreferences("c10prefs", 0).edit().putBoolean("rem_sen", it).apply()
                                })
                                Column(Modifier.padding(top = 10.dp)) {
                                    Text(t("sn_rem"), color = Txt, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(t("sn_rem_h"), color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                                }
                            }
                            if (est?.so == true) Aviso(t("sn_state") + " " + t(if (est.sg) "sn_state_g" else "sn_state_t"))
                            else if (est != null && est.v.isNotEmpty() && (est.v.toFloatOrNull() ?: 0f) < 2.8f) Nota(t("sn_old"))
                            Spacer(Modifier.height(10.dp))
                            Boton(t("rst_b"), Modifier.fillMaxWidth(), enabled = conectado) {
                                if (apagada) { ble.enviar("ON"); scope.launch { delay(1500); ble.enviar("RESTART") } }
                                else ble.enviar("RESTART")
                                avisoReinicio = t("rst_ok")
                            }
                            Nota(t("rst_d"))
                            if (avisoReinicio.isNotEmpty()) Nota(avisoReinicio)
                            Nota(t("hold"))
                        }
                        }
                        val cardBrillo: @Composable () -> Unit = {
                        Tarjeta {
                            Etiq(t("b_title"))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${(brillo / 10.0).roundToInt()}", color = Acento, fontSize = 54.sp, fontWeight = FontWeight.ExtraBold)
                                Text("0 – 100", color = Muted, fontSize = 11.sp)
                            }
                            SliderPM((brillo / 10f).roundToInt().toFloat(), 0f..100f, 1f, conectado) { v, fin ->
                                if (!fin) { arrastrando = true; poner(v.roundToInt() * 10, false) }
                                else { poner(v.roundToInt() * 10, true); arrastrando = false }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(t("b_fine"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("$brillo", color = Txt, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            SliderPM(brillo.toFloat(), 0f..1000f, 1f, conectado) { v, fin ->
                                if (!fin) { arrastrando = true; poner(v.roundToInt(), false) }
                                else { poner(v.roundToInt(), true); arrastrando = false }
                            }
                            Nota(t("b_desc"))
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Boton("${t("b_night")} · ${prog.valorNoche}", Modifier.fillMaxWidth(), enabled = conectado) {
                                        brillo = prog.valorNoche; ble.enviar("NIGHT:${prog.valorNoche}")
                                    }
                                    CampoNumero(prog.valorNoche) { cambiarProg(prog.copy(valorNoche = it)) }
                                    Boton(t("b_use"), Modifier.fillMaxWidth(), pequeno = true) { cambiarProg(prog.copy(valorNoche = brillo)) }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Boton("${t("b_day")} · ${prog.valorDia}", Modifier.fillMaxWidth(), enabled = conectado) {
                                        brillo = prog.valorDia; ble.enviar("DAY:${prog.valorDia}")
                                    }
                                    CampoNumero(prog.valorDia) { cambiarProg(prog.copy(valorDia = it)) }
                                    Boton(t("b_use"), Modifier.fillMaxWidth(), pequeno = true) { cambiarProg(prog.copy(valorDia = brillo)) }
                                }
                            }
                            Nota(t("b_range"))
                        }
                        }
                        val cardMonitor: @Composable () -> Unit = { TarjetaVivo(ble) }
                        val cardProg: @Composable () -> Unit = {
                        Tarjeta {
                            Etiq(t("sc_title"))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(t("sc_on"), color = Txt, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Switch(checked = prog.activa, onCheckedChange = { cambiarProg(prog.copy(activa = it)) })
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Boton(t("sc_modo_hora"), Modifier.weight(1f), principal = !prog.sol) { cambiarProg(prog.copy(sol = false)) }
                                Boton(t("sc_modo_sol"), Modifier.weight(1f), principal = prog.sol) { cambiarProg(prog.copy(sol = true)) }
                            }
                            Spacer(Modifier.height(8.dp))
                            if (prog.sol) {
                                val sd = if (prog.lat.isNaN() || prog.lon.isNaN()) null else Sol.diaSol(prog.lat, prog.lon, java.util.Calendar.getInstance())
                                if (sd != null) Nota(t("sc_sol_hoy", Prog.hhmmMs(sd.first) + " / " + Prog.hhmmMs(sd.second)))
                                else if (!Sol.tienePermiso(ctx)) Nota(t("sc_sol_perm"))
                                else Nota(t("sc_sol_noloc"))
                                Nota(t("sc_sol_hint"))
                            }
                            if (!prog.sol) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(t("sc_dayfrom"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Boton(Prog.hhmm(prog.diaDesde), Modifier.fillMaxWidth()) {
                                        TimePickerDialog(ctx, { _, h, m -> cambiarProg(prog.copy(diaDesde = h * 60 + m)) }, prog.diaDesde / 60, prog.diaDesde % 60, true).show()
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(t("sc_nightfrom"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Boton(Prog.hhmm(prog.nocheDesde), Modifier.fillMaxWidth()) {
                                        TimePickerDialog(ctx, { _, h, m -> cambiarProg(prog.copy(nocheDesde = h * 60 + m)) }, prog.nocheDesde / 60, prog.nocheDesde % 60, true).show()
                                    }
                                }
                            }
                            if (prog.activa) Nota(t(if (prog.esDiaAhora()) "sc_period_day" else "sc_period_night"))
                            Nota(t("sc_hint"))
                            Nota(t("sc_bg"))
                        }
                        }
                        if (pantallaGrande) {
                            // Coche: 2x2 -> izquierda (Control rapido arriba, Brillo abajo), derecha (Programacion arriba, Monitor abajo)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) { cardQuick(); Spacer(Modifier.height(12.dp)); cardBrillo() }
                                Column(Modifier.weight(1f)) { cardProg(); Spacer(Modifier.height(12.dp)); cardMonitor() }
                            }
                        } else {
                            // Movil: apilado igual que siempre (Control rapido, Brillo, Monitor, Programacion)
                            cardQuick(); Spacer(Modifier.height(12.dp))
                            cardBrillo(); Spacer(Modifier.height(12.dp))
                            cardMonitor(); Spacer(Modifier.height(12.dp))
                            cardProg()
                        }
                    }
                    // =============================== EFECTOS ===============================
                    1 -> {
                        val cartaConstructor: @Composable () -> Unit = {
                            Tarjeta(borde = Acento) {
                                Etiq(t("cb_open_t"))
                                Nota(t("cb_open_d"))
                                Spacer(Modifier.height(8.dp))
                                Boton(t("cb_open_b"), Modifier.fillMaxWidth(), principal = true) { abrirConst = true }
                            }
                        }
                        if (pantallaGrande) {
                            // Pantalla ancha (coche): monitor y shows lado a lado, llenando el alto.
                            // Cada columna coge todo el alto disponible y centra su contenido ->
                            // el hueco sobrante queda repartido arriba y abajo (margen oscuro simetrico).
                            val altoBloque = (altoPantallaDp - ALTO_CABECERA_DP).coerceAtLeast(360f)
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = altoBloque.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                                    TarjetaVivo(ble)
                                }
                                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                                    CartaShows(est, conectado, ble, duracionShow, { duracionShow = it }, showOn)
                                    Spacer(Modifier.height(12.dp))
                                    cartaConstructor()
                                }
                            }
                        } else {
                            TarjetaVivo(ble)
                            Spacer(Modifier.height(12.dp))
                            CartaShows(est, conectado, ble, duracionShow, { duracionShow = it }, showOn)
                            Spacer(Modifier.height(12.dp))
                            cartaConstructor()
                        }
                    }
                    // =============================== AJUSTES ===============================
                    2 -> {
                        val a = aj
                        if (a != null) {
                            val fwSens = (est?.v?.toFloatOrNull() ?: 0f) >= 2.7f
                            val fijoOn = a.r == 0 && a.p == 0 && (a.k == 0 || !fwSens)
                            Tarjeta(borde = if (fijoOn) Color(0xFF22C55E) else Line) {
                                Etiq(t("mf_title"))
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(t("mf_sw"), color = Txt, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                        Nota(t("mf_sw_d"))
                                    }
                                    Switch(checked = fijoOn, onCheckedChange = { on ->
                                        if (on) {
                                            if (a.k > 0) kPrev = a.k
                                            aj = a.copy(r = 0, p = 0, d = 650, k = if (fwSens) 0 else a.k)
                                            if (prog.activa) cambiarProg(prog.copy(activa = false))
                                        } else {
                                            aj = a.copy(r = 30, p = 6, k = kPrev.coerceIn(1, 10))
                                        }
                                    })
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(t("mf_sens"), color = Txt, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Nota(t("mf_sens_d"))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Chip(t("mf_act"), a.k != 0, Modifier.weight(1f)) { aj = a.copy(k = kPrev.coerceIn(1, 10)) }
                                    Chip(t("mf_ign"), a.k == 0, Modifier.weight(1f)) { if (fwSens) { if (a.k > 0) kPrev = a.k; aj = a.copy(k = 0) } }
                                }
                                if (!fwSens) Nota(t("mf_old"))
                                Spacer(Modifier.height(10.dp))
                                Text(t("mf_br"), color = Txt, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Nota(t("mf_br_d"))
                                Chip(t("mf_br_b"), a.d == 650, Modifier.fillMaxWidth()) { aj = a.copy(d = 650) }
                                Spacer(Modifier.height(10.dp))
                                Text(t("mf_prog"), color = Txt, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Nota(t("mf_prog_d"))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Chip(t("mf_auto"), prog.activa, Modifier.weight(1f)) { cambiarProg(prog.copy(activa = true)) }
                                    Chip(t("mf_off"), !prog.activa, Modifier.weight(1f)) { cambiarProg(prog.copy(activa = false)) }
                                }
                                Nota(t("mf_note"))
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        Tarjeta {
                            Etiq(t("se_title"))
                            AvisoAmarillo(t("se_warn"))
                            Nota(t("se_desc"))
                            if (a == null) {
                                Nota(t("s_nocon"))
                            } else {
                                Spacer(Modifier.height(8.dp))
                                FilaValor(t("se_base"), "${(a.d / 10.0).roundToInt()}")
                                SliderPM(a.d / 10f, 3f..100f, 1f) { v, _ -> aj = a.copy(d = (v.roundToInt() * 10).coerceAtLeast(30)) }
                                Nota(t("se_base_h"))
                                FilaValor(t("se_wait"), t("se_sec", "${a.e}"))
                                SliderPM(a.e.toFloat(), 5f..300f, 1f) { v, _ -> aj = a.copy(e = v.roundToInt()) }
                                FilaValor(t("se_breath"), if (a.r == 0) "0" else t("se_sec", "${a.r}"))
                                SliderPM(a.r.toFloat(), 0f..600f, 5f) { x, _ ->
                                    val q = (x / 5f).roundToInt() * 5
                                    aj = a.copy(r = if (q in 1..9) (if (x < a.r) 0 else 10) else q)
                                }
                                Nota(t("se_breath0"))
                                Text(t("rs_title"), color = Txt, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(t("rs_min"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        CampoNumero(a.rn) { aj = a.copy(rn = it) }
                                        Boton(t("b_use"), Modifier.fillMaxWidth(), pequeno = true) { aj = a.copy(rn = brillo.coerceIn(0, 1000)) }
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(t("rs_max"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        CampoNumero(a.rx) { aj = a.copy(rx = it) }
                                        Boton(t("b_use"), Modifier.fillMaxWidth(), pequeno = true) { aj = a.copy(rx = brillo.coerceIn(0, 1000)) }
                                    }
                                }
                                Nota(t("rs_hint"))
                                Text(t("se_pp"), color = Txt, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                                Nota(t("se_pp_h"))
                                SelectorModoParado(a.p, Acento, true, incluirRot3 = (est?.v?.toFloatOrNull() ?: 0f) >= 2.9f, incluirLista = (est?.v?.toFloatOrNull() ?: 0f) >= 3.0f) { aj = a.copy(p = it, lista = if (it == 8 && a.lista.isEmpty()) listOf(6, 1, 4) else a.lista) }
                                if (a.p == 8) {
                                    ConstructorLista(a.lista) { aj = a.copy(lista = it) }
                                }
                                if (a.p >= 4) {
                                    FilaValor(t("se_alt"), t("se_sec", "${a.pa}"))
                                    SliderPM(a.pa.toFloat(), 5f..300f, 1f) { v, _ -> aj = a.copy(pa = v.roundToInt()) }
                                    Nota(t("se_alt_h"))
                                }
                                if (a.p != 0) {
                                    Text(t("pp_bri"), color = Txt, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(t("pp_min"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            CampoNumero(a.pn) { aj = a.copy(pn = it) }
                                            Boton(t("b_use"), Modifier.fillMaxWidth(), pequeno = true) { aj = a.copy(pn = brillo.coerceIn(0, 1000)) }
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(t("pp_max"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            CampoNumero(a.px) { aj = a.copy(px = it) }
                                            Boton(t("b_use"), Modifier.fillMaxWidth(), pequeno = true) { aj = a.copy(px = brillo.coerceIn(0, 1000)) }
                                        }
                                    }
                                    Nota(t("pp_bri_h"))
                                }
                                FilaValor(t("se_sens"), if (a.k == 0) t("se_sens_off") else "${a.k}")
                                SliderPM(a.k.coerceAtLeast(1).toFloat(), 1f..10f, 1f, steps = 8) { v, _ -> aj = a.copy(k = v.roundToInt()) }
                                Nota(t("se_sens_hint"))
                                Text(t("se_meter"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                MedidorSensor(ble.medidor)
                                Nota(t("se_meter_hint") + "  (100 = ${t("te_meter").substringAfter("= ").trimEnd(')')})")
                                Text(t("se_welcome"), color = Txt, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                                for (i in 1..5) {
                                    OpcionFila(t("w$i"), t("wd$i"), a.w == i, Acento) { aj = a.copy(w = i); ble.enviar("WSEL:$i") }
                                }
                                Spacer(Modifier.height(10.dp))
                                Boton(t("se_preview"), Modifier.fillMaxWidth(), enabled = conectado, principal = true) { ble.enviar("WPLAY:${a.w}"); avisoAj = t("se_preview_on"); verifFallo = false }
                                Spacer(Modifier.height(14.dp))
                                AvisoAmarillo(t("se_warn_save"))
                                Spacer(Modifier.height(8.dp))
                                fun cmdSet() = "SET:${a.e},${a.r},${a.p},${a.k},${a.d},${a.rn.coerceIn(10, 980)},${a.rx.coerceIn(a.rn.coerceIn(10, 980) + 10, 1000)},${a.pa},${a.pn.coerceIn(10, 980)},${a.px.coerceIn(a.pn.coerceIn(10, 980) + 10, 1000)}"
                                fun cmdLst() = "LST:${a.lista.size}" + a.lista.joinToString("") { ",$it" }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Boton(t("se_test"), Modifier.weight(1f), enabled = conectado) { ble.enviar(cmdSet()); ble.enviar(cmdLst()); ble.enviar("RESTART"); avisoAj = t("se_tried"); verifFallo = false }
                                    Boton(t("se_save"), Modifier.weight(1f), enabled = conectado, principal = true) {
                                        confirmarGuardar = true
                                    }
                                }
                                if (avisoAj.isNotEmpty()) { if (verifFallo) Aviso(avisoAj) else Nota(avisoAj) }
                                if (ultimoGuardado.isNotEmpty()) Nota(t("se_last", ultimoGuardado))
                                Spacer(Modifier.height(10.dp))
                                Boton(t("se_reset"), Modifier.fillMaxWidth(), enabled = conectado, peligro = true) { confirmarReset = true }
                            }
                        }
                    }
                    // =============================== SISTEMA ===============================
                    else -> {
                        Tarjeta {
                            Etiq(t("sy_info"))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column { Text(t("sy_version"), color = Muted, fontSize = 11.sp); Text(est?.v?.ifEmpty { "-" } ?: "-", color = Txt, fontWeight = FontWeight.ExtraBold) }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(t("sy_sensor"), color = Muted, fontSize = 11.sp)
                                    Text(if (est == null) "-" else if (est.m) t("sy_sens_ok") else t("sy_sens_no"), color = Txt, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Tarjeta {
                            Etiq("C10 LED · CRÉDITOS")
                            Text("Diseñado para Leapmotor C10", color = Txt, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Creado por Juanlu", color = Muted, fontSize = 12.sp)
                            Text("#juanludetoledo", color = Acento, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("Versión 18", color = Txt, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Proyecto independiente", color = Muted, fontSize = 11.sp)
                            Spacer(Modifier.height(10.dp))
                            Nota(t("kofi_d"))
                            Spacer(Modifier.height(6.dp))
                            Boton(t("kofi_b"), Modifier.fillMaxWidth(), principal = true) {
                                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/juanludetoledo")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Tarjeta {
                            Etiq(t("sy_fw"))
                            Nota(t("sy_wifi"))
                            Spacer(Modifier.height(10.dp))
                            Boton(t("sy_wifi_b"), Modifier.fillMaxWidth(), principal = true) {
                                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://192.168.4.1")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                            }
                            Nota(t("sy_orig_d"))
                        }
                        if (Build.VERSION.SDK_INT >= 31 && !Auto.puedeAlarmaExacta(ctx)) {
                            Spacer(Modifier.height(12.dp))
                            Tarjeta {
                                Etiq(t("sc_title"))
                                Nota(t("sc_bg"))
                                Spacer(Modifier.height(8.dp))
                                Boton(t("sc_perm"), Modifier.fillMaxWidth()) {
                                    try { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                                }
                            }
                        }
                        if (conectado) {
                            Spacer(Modifier.height(12.dp))
                            Boton(t("disconnect"), Modifier.fillMaxWidth()) { ble.desconectar() }
                        }
                    }
                }

                // =============================== TECNICA (oculta) ===============================
                if (tecnica) {
                    Spacer(Modifier.height(14.dp))
                    Tarjeta(borde = Acento) {
                        Etiq(t("te_title"))
                        Nota(t("te_hint"))
                        Text(t("te_scale"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CampoNumero(brillo, Modifier.weight(1f)) { brillo = it }
                            Boton(t("te_send"), Modifier.width(110.dp), enabled = conectado, principal = true) { ble.enviar("BR:$brillo") }
                        }
                        val p = pwmDe(brillo)
                        Text("PWM $p · 0x" + p.toString(16).uppercase().padStart(4, '0'), color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(8.dp))
                        var crudo by remember { mutableStateOf("") }
                        Text(t("te_cmd"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = crudo, onValueChange = { crudo = it }, singleLine = true, modifier = Modifier.weight(1f))
                            Boton(t("te_send"), Modifier.width(110.dp), enabled = conectado) { if (crudo.isNotBlank()) ble.enviar(crudo.trim()) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("${t("te_meter")}: ${ble.medidor}", color = Txt, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(t("te_state"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(est?.toString() ?: "-", color = Color(0xFF9FE3B0), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(6.dp))
                        Text(t("te_conn"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(ble.registro, color = Color(0xFF9FE3B0), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("C10 LED CONTROL · BLUETOOTH", color = Color(0xFF64748B), fontSize = 10.sp, letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            if (confirmarGuardar) {
                val aa = aj
                AlertDialog(
                    onDismissRequest = { confirmarGuardar = false },
                    title = { Text(t("se_conf_t")) },
                    text = { Text(t("se_conf_m")) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmarGuardar = false
                            if (aa != null) {
                                val rn = aa.rn.coerceIn(10, 980)
                                val rx = aa.rx.coerceIn(rn + 10, 1000)
                                val pn = aa.pn.coerceIn(10, 980)
                                val px = aa.px.coerceIn(pn + 10, 1000)
                                ble.enviar("SET:${aa.e},${aa.r},${aa.p},${aa.k},${aa.d},$rn,$rx,${aa.pa},$pn,$px")
                                ble.enviar("LST:${aa.lista.size}" + aa.lista.joinToString("") { ",$it" })
                                ble.enviar("SAVE")
                                ble.enviar("RESTART")
                                enviadoAj = listOf(aa.e, aa.r, aa.p, aa.k, aa.d, rn, rx, aa.pa, pn, px)
                                guardadoTick++
                            }
                        }) { Text(t("se_save")) }
                    },
                    dismissButton = { TextButton(onClick = { confirmarGuardar = false }) { Text(t("back")) } }
                )
            }
            if (confirmarReset) {
                AlertDialog(
                    onDismissRequest = { confirmarReset = false },
                    title = { Text(t("se_reset")) },
                    text = { Text(t("se_reset_c")) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmarReset = false
                            ble.enviar("RESET"); avisoAj = t("se_reseted"); aj = null
                        }) { Text(t("ok")) }
                    },
                    dismissButton = { TextButton(onClick = { confirmarReset = false }) { Text(t("back")) } }
                )
            }
        }
      }
    }
}

// ---------------------------------------------------------------------------------------------
// Componentes
// ---------------------------------------------------------------------------------------------
@Composable
internal fun Tarjeta(borde: Color = Line, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF7182742), Color(0xFA0D1524), Color(0xFC0A0F1A))))
            .border(1.dp, borde, RoundedCornerShape(24.dp)).padding(18.dp),
        content = content
    )
}

@Composable internal fun Etiq(x: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
        Box(Modifier.size(width = 4.dp, height = 14.dp).clip(RoundedCornerShape(50)).background(Acento))
        Spacer(Modifier.width(9.dp))
        Text(x, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.4.sp)
    }
}

@Composable internal fun AvisoAmarillo(x: String) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF33250A)).border(1.dp, Color(0xFFFBBF24), RoundedCornerShape(12.dp)).padding(11.dp)
    ) { Text(x, color = Color(0xFFFDE68A), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp) }
}

// --- Colores oficiales del C10 (recoloreado de la carroceria en la foto) ---
private data class ColorCoche(val key: String, val res: Int, val sw: Color, val nombre: String, val cod: String)
private val COLORES_C10 = listOf(
    ColorCoche("verde",    R.drawable.c10_verde,    Color(0xFF2F6B52), "Verde Boreal",    "LP9600"),
    ColorCoche("blanco",   R.drawable.c10_blanco,   Color(0xFFD8DDE0), "Blanco Amanecer", "LP9400"),
    ColorCoche("tundra",   R.drawable.c10_tundra,   Color(0xFF8A8070), "Gris Tundra",     "LP7000"),
    ColorCoche("nocturno", R.drawable.c10_nocturno, Color(0xFF4A4E52), "Gris Nocturno",   "LP9702"),
    ColorCoche("negro",    R.drawable.c10_negro,    Color(0xFF1C1F22), "Negro Eclipse",   "LP9401"),
)
// Coordenadas de la barra en la foto, en fracciones (base 1024x446) -> escalan a cualquier tamaño
private const val BAR_X0 = 0.2559f
private const val BAR_X1 = 0.7246f
private const val BAR_YC = 0.4238f
private const val BAR_ARC = 0.0112f
private const val BAR_SEGH = 0.0336f
private const val BAR_SEGW_F = 0.74f
private const val BAR_AR = 1024f / 446f
/** Color de un segmento segun su brillo real (0..1): cian tenue -> blanco brillante (mi tono). */
private fun colSeg(bf: Float): Color {
    val b = bf.coerceIn(0f, 1f)
    val lum = 0.22f + 0.78f * b                 // luminancia global: los tenues se ven de verdad tenues
    return Color((0.12f + 0.88f * b) * lum, (0.72f + 0.28f * b) * lum, (0.88f + 0.12f * b) * lum)
}

// Color elegido, compartido por los dos monitores (Control y Efectos) y guardado en prefs.
private val colorCocheKey = mutableStateOf("")

@Composable private fun Divisor() {
    Box(Modifier.width(1.dp).height(20.dp).background(Acento.copy(alpha = 0.5f)))
}

/** Fila de datos del monitor: canales activos, PWM (hex + decimal) y modo.
 *  FlowRow: si no cabe en el ancho (movil), cada bloque entero salta de linea; nunca se parte un texto. */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun FilaDatosBarra(activos: Int, pwm: Int, modo: String) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(if (activos > 0) Color(0xFF22C55E) else Color(0xFF64748B)))
            Text("  ${t("lv_act")}: ", color = Muted, fontSize = 10.5.sp, maxLines = 1, softWrap = false)
            Text("$activos / 34", color = Txt, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WbSunny, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(13.dp))
            Text(" PWM: 0x%04X (%d)".format(pwm, pwm), color = Muted, fontSize = 10.5.sp, maxLines = 1, softWrap = false)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Wifi, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(13.dp))
            Text(" ${t("lv_modo")}: ", color = Muted, fontSize = 10.5.sp, maxLines = 1, softWrap = false)
            Text(modo, color = Acento, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
    }
}

/** Panel HUD: titulo + medidor de 34 segmentos redondeados (brillo real) + datos. */
@Composable private fun PanelHud(v: IntArray, activos: Int, pwm: Int, modo: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF05080F)).border(1.5.dp, Acento.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Titulo enmarcado con lineas a los lados (diseno de la referencia)
        Row(Modifier.fillMaxWidth().padding(bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(Acento.copy(alpha = 0.35f)))
            Text(t("lv_hud"), color = Color(0xFFDFF2FF), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp))
            Box(Modifier.weight(1f).height(1.dp).background(Acento.copy(alpha = 0.35f)))
        }
        // Medidor de 34 segmentos CUADRADOS, con brillo real (mi tono) y reflejo glossy
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(42.dp)) {
            val w = size.width; val h = size.height; val gap = 2.2.dp.toPx()
            val sw = (w - gap * 33f) / 34f
            val rad = 1.5.dp.toPx()
            for (i in 0 until 34) {
                val x = i * (sw + gap)
                val vi = v.getOrElse(i) { 0 }; val lit = vi > 8; val bf = vi.coerceIn(0, 255) / 255f
                if (lit) drawRoundRect(colSeg(bf).copy(alpha = 0.30f * bf),
                    Offset(x - gap, 0f), Size(sw + 2f * gap, h), CornerRadius(rad))
                drawRoundRect(if (lit) colSeg(bf) else Color(0xFF1C2433),
                    Offset(x, h * 0.04f), Size(sw, h * 0.92f), CornerRadius(rad))
                if (lit) drawRoundRect(Color.White.copy(alpha = 0.18f * bf),
                    Offset(x, h * 0.04f), Size(sw, h * 0.30f), CornerRadius(rad))
            }
        }
        Spacer(Modifier.height(10.dp))
        FilaDatosBarra(activos, pwm, modo)
    }
}

/** Tarjeta de shows (efectos): rejilla 3x3 + duracion + parar. Extraida para colocarla al lado del monitor. */
@Composable private fun CartaShows(est: EstadoC?, conectado: Boolean, ble: BleController, duracionShow: Int, setDur: (Int) -> Unit, showOn: Boolean) {
    Tarjeta {
        Etiq(t("fx_title"))
        Nota(t("fx_desc"))
        Spacer(Modifier.height(8.dp))
        for (fila in 0 until 3) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 3) {
                    val i = fila * 3 + col + 1
                    Chip(t("th$i"), est?.h == i, Modifier.weight(1f), alto = 46) {
                        if (conectado) ble.enviar("SHOW:$i,$duracionShow")
                    }
                }
            }
        }
        Text(t("fx_dur"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 60, 0).forEach { m ->
                Chip(if (m == 0) t("fx_inf") else t("fx_min", "$m"), duracionShow == m, Modifier.weight(1f)) { setDur(m) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Boton(t("fx_stop"), Modifier.fillMaxWidth(), enabled = conectado, peligro = true) { ble.enviar("SHOW:STOP") }
        if (showOn) Nota(t("fx_running", t("th${est?.h ?: 1}")))
        Nota(t("fx_note"))
    }
}

/** Monitor en vivo: foto del coche (color elegido) con los 34 segmentos superpuestos + panel HUD. */
@Composable internal fun TarjetaVivo(ble: BleController) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("c10ui", android.content.Context.MODE_PRIVATE) }
    val colorKey = colorCocheKey.value.ifEmpty { prefs.getString("color", "verde") ?: "verde" }
    val color = COLORES_C10.firstOrNull { it.key == colorKey } ?: COLORES_C10[0]
    val v = ble.vivo
    val est = ble.estado
    val activos = v.count { it > 6 }
    val n = (est?.n ?: 0).coerceIn(0, 1000)
    val pwm = pwmDe(n)
    val modo = when {
        est == null -> "—"
        est.off -> t("car_st_off")
        (est.h) > 0 -> t("th${est.h}")
        else -> t("p_normal")
    }
    Tarjeta(borde = Acento.copy(alpha = 0.45f)) {
        // Foto del color elegido + barra viva superpuesta (brillo real por segmento)
        Box(Modifier.fillMaxWidth().aspectRatio(BAR_AR).clip(RoundedCornerShape(14.dp))) {
            Image(
                painter = painterResource(color.res),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                for (i in 0 until 34) {
                    val vi = v.getOrElse(i) { 0 }
                    if (vi < 8) continue
                    val bf = vi.coerceIn(0, 255) / 255f
                    val tt = i / 33f
                    val cx = (BAR_X0 + (BAR_X1 - BAR_X0) * tt) * w
                    val cy = (BAR_YC + BAR_ARC * (2 * tt - 1) * (2 * tt - 1)) * h
                    val sw = (BAR_X1 - BAR_X0) / 34f * BAR_SEGW_F * w
                    val sh = BAR_SEGH * h
                    // halo proporcional al brillo (los tenues casi no brillan)
                    drawRoundRect(colSeg(bf).copy(alpha = 0.35f * bf),
                        Offset(cx - sw * 0.9f, cy - sh * 1.3f), Size(sw * 1.8f, sh * 2.6f), CornerRadius(sh))
                    // segmento con el brillo real (sin nucleo blanco fijo)
                    drawRoundRect(colSeg(bf), Offset(cx - sw / 2f, cy - sh / 2f), Size(sw, sh), CornerRadius(sh * 0.4f))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Selector de color (compartido por los dos monitores, recuerda el elegido)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            COLORES_C10.forEach { c ->
                val sel = c.key == colorKey
                Box(
                    Modifier.padding(horizontal = 5.dp).size(if (sel) 32.dp else 26.dp)
                        .clip(RoundedCornerShape(50)).background(c.sw)
                        .border(if (sel) 2.dp else 1.dp, if (sel) Acento else Color(0xFF2A3A4D), RoundedCornerShape(50))
                        .clickable {
                            colorCocheKey.value = c.key
                            prefs.edit().putString("color", c.key).apply()
                        }
                )
            }
        }
        Text("${color.nombre} · ${color.cod}", color = Muted, fontSize = 11.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp))
        PanelHud(v, activos, pwm, modo)
        Nota(t("lv_note"))
    }
}

@Composable internal fun Nota(x: String) {
    Text(x, color = Muted, fontSize = 11.5.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable internal fun Aviso(x: String) {
    Text(x, color = Color(0xFFFDE68A), fontSize = 12.sp, lineHeight = 17.sp,
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2A1A06)).border(1.dp, Color(0xFF92400E), RoundedCornerShape(12.dp)).padding(12.dp))
}

@Composable internal fun FilaValor(titulo: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(titulo, color = Muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        Text(valor, color = Txt, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
internal fun Chip(texto: String, activo: Boolean, modifier: Modifier = Modifier, alto: Int = 0, unaLinea: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .then(if (alto > 0) Modifier.heightIn(min = alto.dp) else Modifier)
            .clip(RoundedCornerShape(50))
            .background(if (activo) Color(0xFF0E5A86) else Color(0xFF111A2B))
            .border(if (activo) 1.5.dp else 1.dp, if (activo) Acento else Line, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = if (unaLinea) 3.dp else 11.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(texto, color = if (activo) Color(0xFFE0F2FE) else Muted, fontSize = if (unaLinea) 10.5.sp else 11.5.sp,
            fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
            maxLines = if (unaLinea) 1 else 2, softWrap = !unaLinea)
    }
}

@Composable
internal fun Boton(
    texto: String, modifier: Modifier = Modifier, enabled: Boolean = true, principal: Boolean = false,
    peligro: Boolean = false, pequeno: Boolean = false, onClick: () -> Unit
) {
    val forma = RoundedCornerShape(50)
    val fondo: Brush = when {
        !enabled -> Brush.linearGradient(listOf(Color(0xFF0D1421), Color(0xFF0D1421)))
        peligro -> Brush.linearGradient(listOf(Color(0xFF3B0D18), Color(0xFF3B0D18)))
        principal -> Brush.horizontalGradient(listOf(Color(0xFF22B3F3), AcentoB))
        else -> Brush.verticalGradient(listOf(Color(0xFF1C2B48), Color(0xFF131F36)))
    }
    val borde = when { !enabled -> Line; peligro -> Color(0xFF9F1239); principal -> Color.Transparent; else -> Color(0x4D9FC2FF) }
    val colorTxt = when { !enabled -> Muted; peligro -> Color(0xFFFECDD3); principal -> TxtAcento; else -> Color(0xFFE5EDF8) }
    Box(
        modifier.heightIn(min = if (pequeno) 34.dp else 44.dp).clip(forma)
            .background(fondo)
            .border(1.dp, borde, forma)
            .clickable(enabled = enabled) { onClick() }.padding(horizontal = 12.dp, vertical = if (pequeno) 5.dp else 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(texto, color = colorTxt, fontSize = if (pequeno) 11.sp else 12.5.sp,
            fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Deslizador con botones - y + para mover de uno en uno. onCambio(valor, final): final=true al soltar o al pulsar - / +. */
@Composable
internal fun SliderPM(
    valor: Float, rango: ClosedFloatingPointRange<Float>, paso: Float = 1f, enabled: Boolean = true, steps: Int = 0,
    onCambio: (Float, Boolean) -> Unit
) {
    val actual by rememberUpdatedState(valor)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BotonPM("\u2212", enabled) { onCambio((actual - paso).coerceIn(rango.start, rango.endInclusive), true) }
        Slider(
            value = valor.coerceIn(rango.start, rango.endInclusive), onValueChange = { onCambio(it, false) },
            onValueChangeFinished = { onCambio(actual, true) },
            valueRange = rango, steps = steps, enabled = enabled, modifier = Modifier.weight(1f)
        )
        BotonPM("+", enabled) { onCambio((actual + paso).coerceIn(rango.start, rango.endInclusive), true) }
    }
}

@Composable
internal fun BotonPM(texto: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(50))
            .background(if (enabled) Color(0xFF15213A) else Color(0xFF0D1421))
            .border(1.dp, Line, RoundedCornerShape(50))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(texto, color = if (enabled) Color(0xFFE5EDF8) else Muted, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
}

/** Fila de opcion tipo "radio": titulo, explicacion corta y color. Se ve claro cual esta elegida. */
@Composable
internal fun OpcionFila(titulo: String, desc: String, activo: Boolean, color: Color = Acento, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp))
            .background(if (activo) color.copy(alpha = 0.16f) else Color(0xFF0D1421))
            .border(1.5.dp, if (activo) color else Line, RoundedCornerShape(14.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(20.dp).border(2.dp, if (activo) color else Muted, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) { if (activo) Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, color = Txt, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            if (desc.isNotEmpty()) Text(desc, color = Muted, fontSize = 11.5.sp, lineHeight = 15.sp)
        }
    }
}

/** Constructor de la playlist de parado (modo 8): añadir, ordenar con flechas ↑↓ y quitar con ✕. */
@Composable
internal fun ConstructorLista(lista: List<Int>, onChange: (List<Int>) -> Unit) {
    var anadiendo by rememberSaveable { mutableStateOf(false) }
    Text(t("lp_title"), color = Txt, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    Nota(t("lp_help"))
    Spacer(Modifier.height(8.dp))
    if (lista.isEmpty()) {
        Aviso(t("lp_empty"))
    } else {
        lista.forEachIndexed { i, id ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Insignia(i + 1, Acento)
                Text(t("th$id"), color = Txt, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 4.dp))
                BotonPM("↑", i > 0) { val l = lista.toMutableList(); val x = l[i]; l[i] = l[i - 1]; l[i - 1] = x; onChange(l) }
                BotonPM("↓", i < lista.size - 1) { val l = lista.toMutableList(); val x = l[i]; l[i] = l[i + 1]; l[i + 1] = x; onChange(l) }
                BotonPM("✕", true) { val l = lista.toMutableList(); l.removeAt(i); onChange(l) }
            }
        }
    }
    if (lista.size >= 8) {
        Nota(t("lp_full"))
    } else if (!anadiendo) {
        Boton(t("lp_add"), Modifier.fillMaxWidth(), principal = true) { anadiendo = true }
    } else {
        Text(t("lp_pick"), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
        for (fila in listOf(1, 3, 7, 4, 2, 5, 6, 8).chunked(2)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (id in fila) Chip(t("th$id"), false, Modifier.weight(1f)) { onChange(lista + id); anadiendo = false }
            }
        }
        Boton(t("back"), Modifier.fillMaxWidth(), pequeno = true) { anadiendo = false }
    }
}

/** Efecto de la 2.a parte parada: una fila por opcion, con su explicacion. */
@Composable
internal fun SelectorModoParado(p: Int, color: Color, incluirApagado: Boolean, incluirRot3: Boolean = false, incluirLista: Boolean = false, onSel: (Int) -> Unit) {
    var modos = if (incluirApagado) listOf(0, 1, 3, 4, 5, 6) else listOf(1, 3, 4, 5, 6)
    // El modo 7 (Olas·Cometa·Fiesta) necesita el firmware 2.9+; el modo 8 (Lista) el 3.0+. Se incluyen si ya estan elegidos.
    if (incluirRot3 || p == 7) modos = modos + 7
    if (incluirLista || p == 8) modos = modos + 8
    for (m in modos) {
        val clave = when (m) { 0 -> "se_pp_off"; 1 -> "se_pp_co"; 3 -> "se_pp_ol"; 4 -> "se_pp_ro"; 5 -> "se_pp_or"; 7 -> "se_pp_rot3"; 8 -> "se_pp_lista"; else -> "se_pp_cl2" }
        OpcionFila(t(clave), t("pm_d$m"), p == m, color) { onSel(m) }
    }
}

/** Numero dentro de un circulo de color (para numerar secciones y partes). */
@Composable
internal fun Insignia(num: Int, color: Color) {
    Box(Modifier.size(26.dp).clip(RoundedCornerShape(50)).background(color), contentAlignment = Alignment.Center) {
        Text("$num", color = Color(0xFF05080F), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Campo numerico 0..1000 (cualquier valor). */
@Composable
internal fun CampoNumero(valor: Int, modifier: Modifier = Modifier.fillMaxWidth(), onCambio: (Int) -> Unit) {
    var texto by remember(valor) { mutableStateOf(valor.toString()) }
    OutlinedTextField(
        value = texto,
        onValueChange = { nuevo ->
            val limpio = nuevo.filter { it.isDigit() }.take(4)
            texto = limpio
            limpio.toIntOrNull()?.let { onCambio(it.coerceIn(0, 1000)) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun BigButton(
    modifier: Modifier, titulo: String, sub: String, encendido: Boolean,
    icono: ImageVector, enabled: Boolean, seleccion: Int = 0, onClick: () -> Unit   // seleccion: 1 = puesto ahora, -1 = no puesto, 0 = sin dato
) {
    val forma = RoundedCornerShape(28.dp)
    val fondo = if (encendido) Brush.verticalGradient(listOf(Color(0xFF22C55E), Color(0xFF15803D)))
                else Brush.verticalGradient(listOf(Color(0xFFF43F5E), Color(0xFF9F1239)))
    val colorIc = Color.White
    val colorTxt = Color.White
    Column(
        modifier.height(96.dp).clip(forma)
            .alpha(if (seleccion < 0) 0.45f else 1f)
            .background(fondo)
            .border(if (seleccion > 0) 3.dp else 1.dp, if (seleccion > 0) Color.White else Color.Transparent, forma)
            .clickable(enabled = enabled) { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icono, null, tint = colorIc, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(5.dp))
        Text(titulo, color = colorTxt, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(if (seleccion > 0) "✓  " + sub else sub, color = colorTxt.copy(alpha = if (seleccion > 0) 1f else 0.75f), fontSize = 10.sp, fontWeight = if (seleccion > 0) FontWeight.ExtraBold else FontWeight.Normal, textAlign = TextAlign.Center)
    }
}

/** Tira de 9 leds que se ilumina hacia el centro (la marca de la barra). */
@Composable private fun TiraLeds() {
    androidx.compose.foundation.Canvas(Modifier.padding(top = 5.dp).width(104.dp).height(7.dp)) {
        val al = floatArrayOf(.25f, .4f, .6f, .8f, 1f, .8f, .6f, .4f, .25f)
        val gap = 3.dp.toPx()
        val w = (size.width - gap * 8) / 9
        for (i in 0 until 9) {
            drawRoundRect(Acento.copy(alpha = al[i]), androidx.compose.ui.geometry.Offset(i * (w + gap), 0f),
                androidx.compose.ui.geometry.Size(w, size.height), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        }
    }
}


/** Medidor del sensor de movimiento: verde, naranja y rojo como en la pagina Wi-Fi; la linea blanca marca el umbral (100). */
@Composable
internal fun MedidorSensor(valor: Int) {
    val frac = (valor / 200f).coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp).height(14.dp)
            .clip(RoundedCornerShape(50)).background(Color(0xFF0A111D)).border(1.dp, Color(0xFF2C3D57), RoundedCornerShape(50))
    ) {
        if (frac > 0.005f) Box(
            Modifier.fillMaxWidth(frac).fillMaxHeight().background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(0f to Color(0xFF22C55E), 0.6f to Color(0xFFFBBF24), 1f to Color(0xFFFB4655))
            )
        )
        Box(Modifier.align(Alignment.Center).width(2.dp).fillMaxHeight().background(Color.White))
    }
}
