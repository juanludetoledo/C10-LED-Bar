# C10 LED Control · Manual de usuario

**Idiomas:** **Español** · [English](MANUAL.en.md) · [Português](MANUAL.pt.md) · [中文](MANUAL.zh.md)

Guía de **C10 LED Control**, la app para manejar la barra LED frontal del **Leapmotor C10**, desarrollada por **[juanludetoledo](https://github.com/juanludetoledo)** ([juanludetoledo/C10-LED-Bar](https://github.com/juanludetoledo/C10-LED-Bar)).

> [!IMPORTANT]
> **Todo es gratis**: la app, el firmware y el manual de montaje del hardware. Si te gusta el proyecto y quieres apoyarlo, puedes invitarme a un café en **[Ko-fi](https://ko-fi.com/juanludetoledo)**.

> [!WARNING]
> Haz toda la configuración **con el coche aparcado**. Los shows y efectos son para el coche parado: se detienen solos si el coche se mueve.

---

## Índice

1. [Qué es y qué necesitas](#1-qué-es-y-qué-necesitas)
2. [Instalar la app](#2-instalar-la-app)
3. [Conectar con la barra](#3-conectar-con-la-barra)
4. [Pestaña Control](#4-pestaña-control)
5. [Pestaña Efectos](#5-pestaña-efectos)
6. [Pestaña Ajustes](#6-pestaña-ajustes)
7. [Constructor de animaciones](#7-constructor-de-animaciones)
8. [Pestaña Sistema y actualizaciones](#8-pestaña-sistema-y-actualizaciones)
9. [Android Auto y widget](#9-android-auto-y-widget)
10. [iPhone: página Wi-Fi](#10-iphone-página-wi-fi)
11. [Solución de problemas](#11-solución-de-problemas)
12. [Soporte y apoyar el proyecto](#12-soporte-y-apoyar-el-proyecto)

---

## 1. Qué es y qué necesitas

**C10 LED Control** se conecta por **Bluetooth (BLE)** con la centralita de la barra y te deja encenderla, apagarla, ajustar el brillo, lanzar shows y diseñar tus propias animaciones canal a canal.

| Característica | Detalle |
|---|---|
| **Conexión** | Bluetooth BLE con la centralita `JUANLU_C10LED_BLE` |
| **Dónde funciona** | Móvil Android 8 o superior · Android Auto · iPhone por la página Wi-Fi |
| **Barra** | 34 canales en tres zonas: izquierda (1–11), centro (12–23) y derecha (24–34) |
| **Idiomas** | Español, English, Português y 中文 (cambio en la propia app) |

**Qué necesitas:**
- La **barra LED** con su **conector de 6 cables**: cómprala [aquí, en Alibaba](https://www.alibaba.com/x/1lBZ7u1?ck=pdp) y **pide al vendedor que te envíe también el conector**. Se usan 4 cables: rojo (+12 V) y negro (GND) al portafusibles y a masa; marrón (CANH) y azul (CANL) al SN65HVD230. La barra nueva sustituye a la original: se quitan los 4 tornillos superiores, se saca la barra y se coloca la nueva.
- La barra montada con la centralita **ESP32-S3 de 16 MB** (ver el [manual de montaje del hardware](docs/hardware/C10_LED_BAR_Manual_montaje_ES.pdf)).
- El firmware de la barra grabado en la centralita con el **instalador para Windows** (`.exe`, en [Releases](https://github.com/juanludetoledo/C10-LED-Bar/releases)). La clave del instalador pídela por privado en Telegram a **[@juanludetoledo](https://t.me/juanludetoledo)**.
- Un móvil **Android 8+** con Bluetooth, o un iPhone para la página Wi-Fi.

---

## 2. Instalar la app

1. Descarga el último `C10-LED-Control-…-release.apk` desde **[Releases](https://github.com/juanludetoledo/C10-LED-Bar/releases)**.
2. Ábrelo en el móvil y permite **instalar apps de origen desconocido** si Android te lo pide.
3. Abre **C10 LED Control** y concede los permisos de **Dispositivos cercanos / Bluetooth**.
4. Para el modo **Por sol** (día/noche según amanecer y atardecer), permite también la **ubicación aproximada**.

Las actualizaciones se instalan encima de la versión anterior sin perder tus ajustes.

---

## 3. Conectar con la barra

1. Enciende el coche y espera a que termine la **bienvenida** de la barra.
2. Activa el Bluetooth del móvil y abre la app: busca y se conecta sola. Si no, pulsa **CONECTAR**.
3. Arriba verás el estado: **MODO NORMAL**, **BARRA APAGADA** o **SHOW EN MARCHA**.

> [!NOTE]
> La centralita admite **un solo móvil a la vez**. Si abres la app en Android Auto o pulsas el widget, la app del móvil se cierra sola y suelta la conexión.

---

## 4. Pestaña Control

- **OFF · Apagar barra:** bloqueo manual; la barra sigue apagada hasta que pulses **ON**.
- **Recordar apagado:** con la casilla marcada, la barra sigue apagada en cada arranque (sin bienvenida) hasta que pulses ON.
- **ON · Modo normal:** reinicio limpio con la bienvenida.
- **SENSOR OFF / SENSOR ON:** OFF deja la barra fija al brillo marcado, sin respiración ni sensor. Con **Recordar** se mantiene en los siguientes arranques.
- **Brillo:** deslizador y **ajuste fino de 0 a 1000**, en tiempo real. Al apagar y encender el coche vuelve al brillo de arranque.
- **Programación día / noche:** brillo de DÍA y de NOCHE automático, **por hora** o **por sol** (amanecer y atardecer según dónde esté el móvil). Al cambiar de periodo la barra pasa al nuevo brillo con una expansión desde el centro.
- **Vista en vivo:** muestra en la pantalla lo que está haciendo la barra en ese momento.

---

## 5. Pestaña Efectos

**Show / Fiesta** con 9 temas: Olas, Snake, Estrellas, Fiesta, Pacífico, Respiración, Cometa, Cine y Aleatorio, además de **Mi animación**.

- Elige la **duración** (minutos o *Hasta parar*) y pulsa el tema.
- **PARAR SHOW** lo detiene en cualquier momento.
- Mientras dura el show, la programación día/noche queda en pausa.

---

## 6. Pestaña Ajustes

Aquí decides cómo se comporta la barra **con el coche parado**.

| Ajuste | Qué hace |
|---|---|
| **Espera antes de respirar** | Segundos con luz fija antes de empezar la respiración |
| **Duración de la respiración** | 0 = sin respiración |
| **Brillo de la respiración** | Mínimo y máximo (escala 0–1000) |
| **Ping-pong en parado** | Efecto tras la respiración: Clásico, Cometa, Olas, combinaciones o **tu lista** (hasta 8 efectos) |
| **Cambiar de efecto cada** | Tiempo de cada efecto antes de pasar al siguiente |
| **Sensibilidad del sensor** | 5 es lo normal; más alto detecta movimientos más suaves. Con **Medidor en vivo** |
| **Brillo fijo de arranque** | Brillo con el que la barra arranca siempre (0–100; de fábrica 65) |
| **Bienvenida al encender** | Clásica, Película, Aurora, Cascada o Rayo |

> [!IMPORTANT]
> **PROBAR** aplica los valores de forma temporal. **GUARDAR** los graba en la centralita para cada arranque y los comprueba. **Restablecer ajustes** vuelve a los valores de fábrica.

---

## 7. Constructor de animaciones

Crea animaciones propias y colócalas en la secuencia de la barra.

1. **Tus animaciones:** 4 ranuras, las mismas que tiene la barra.
2. **Movimientos básicos:** Cometa, Rebote, Extremos → centro, Olas, Destellos… Ajusta velocidad, cola y brillo y pulsa **Generar**.
3. **Editor canal a canal:** cada paso fija el brillo de los 34 canales. Dibuja, borra, invierte, haz espejo o mueve.
4. **Emulador:** mira la animación sola o la **secuencia entera** antes de enviarla.
5. **Probar en la barra:** la sube y la reproduce sin guardarla.
6. **Secuencia definitiva:** 1 bienvenida → luz fija → parte 2 → parte 3 hasta que te muevas → 4 luz fija en movimiento.
7. **Guardar en la barra:** la graba en la memoria permanente. Guarda solo cuando esté definitivo.

---

## 8. Pestaña Sistema y actualizaciones

- **Información:** versión del sketch y si el **sensor de movimiento** está detectado.
- **Actualizar firmware** y **Volver al firmware original:** solo por Wi-Fi. Conecta el móvil a la red **`JUANLU_C10LED_WiFi`** (clave `juanludetoledo`) y pulsa **ABRIR PÁGINA WI-FI (192.168.4.1)**. No desconectes la alimentación durante la actualización.
- **Pantalla técnica:** pulsa 5 veces el título para abrirla o cerrarla.

---

## 9. Android Auto y widget

- **Android Auto:** el icono de C10 LED aparece en la fila de apps. Al tocarlo verás una rejilla con Control (Encender / Apagar), Brillo, Shows y **Estado en vivo**. Hace falta el **modo desarrollador** de Android Auto con **Fuentes desconocidas** activado.
- **Widget:** mantén pulsado el escritorio del móvil → **Widgets** → **C10LED**, con botones ON y OFF.

---

## 10. iPhone: página Wi-Fi

La app es para Android. Desde un **iPhone** conéctate a la red **`JUANLU_C10LED_WiFi`** (clave `juanludetoledo`) y abre **`http://192.168.4.1`** en Safari y tendrás los controles de la barra desde el navegador.

---

## 11. Solución de problemas

| Síntoma | Qué revisar |
|---|---|
| No encuentra `JUANLU_C10LED_BLE` | Coche encendido y bienvenida terminada; Bluetooth y permisos activados |
| Se conecta y se desconecta | Cierra Android Auto o el widget: la centralita admite un solo móvil |
| Los ajustes se pierden al apagar | Pulsaste PROBAR en vez de **GUARDAR** |
| El sensor no reacciona | Mira en Sistema si está *Detectado*; sube la **sensibilidad** en Ajustes |
| Una actualización falla | Usa **Volver al firmware original** desde la página Wi-Fi |

---

## 12. Soporte y apoyar el proyecto

- 🐞 **Fallos y sugerencias:** abre un aviso en **[GitHub Issues](https://github.com/juanludetoledo/C10-LED-Bar/issues)** con el modelo de tu móvil, la versión de la app y la del sketch.
- 💬 **Soporte:** Telegram **[@juanludetoledo](https://t.me/juanludetoledo)**.
- ☕ **Ko-fi:** [https://ko-fi.com/juanludetoledo](https://ko-fi.com/juanludetoledo)

Proyecto independiente, sin relación con Leapmotor.
