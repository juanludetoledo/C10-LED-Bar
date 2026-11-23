# C10 LED Control · User manual

**Languages:** [Español](MANUAL.md) · **English** · [Português](MANUAL.pt.md) · [中文](MANUAL.zh.md)

Guide to **C10 LED Control**, the app that drives the front LED bar of the **Leapmotor C10**, developed by **[juanludetoledo](https://github.com/juanludetoledo)** ([juanludetoledo/C10-LED-Bar](https://github.com/juanludetoledo/C10-LED-Bar)).

> [!IMPORTANT]
> **Everything is free**: the app, the firmware and the hardware assembly manual. If you like the project and want to support it, you can buy me a coffee on **[Ko-fi](https://ko-fi.com/juanludetoledo)**.

> [!WARNING]
> Do all the setup **with the car parked**. Shows and effects are for a stationary car: they stop by themselves when the car moves.

---

## Contents

1. [What it is and what you need](#1-what-it-is-and-what-you-need)
2. [Install the app](#2-install-the-app)
3. [Connect to the bar](#3-connect-to-the-bar)
4. [Control tab](#4-control-tab)
5. [Effects tab](#5-effects-tab)
6. [Settings tab](#6-settings-tab)
7. [Animation builder](#7-animation-builder)
8. [System tab and updates](#8-system-tab-and-updates)
9. [Android Auto and widget](#9-android-auto-and-widget)
10. [iPhone: Wi-Fi page](#10-iphone-wi-fi-page)
11. [Troubleshooting](#11-troubleshooting)
12. [Support and backing the project](#12-support-and-backing-the-project)

---

## 1. What it is and what you need

**C10 LED Control** talks to the bar's controller over **Bluetooth (BLE)** and lets you switch it on and off, set the brightness, run shows and design your own animations channel by channel.

| Feature | Detail |
|---|---|
| **Connection** | Bluetooth BLE to the `JUANLU_C10LED_BLE` controller |
| **Runs on** | Android 8 or later · Android Auto · iPhone through the Wi-Fi page |
| **Bar** | 34 channels in three zones: left (1–11), centre (12–23) and right (24–34) |
| **Languages** | Español, English, Português and 中文 (switch inside the app) |

**What you need:**
- The **LED bar** with its **6-wire connector**: buy it [here, on Alibaba](https://www.alibaba.com/x/1lBZ7u1?ck=pdp) and **ask the seller to send the connector too**. 4 wires are used: red (+12 V) and black (GND) to the fuse tap and ground; brown (CANH) and blue (CANL) to the SN65HVD230. The new bar replaces the original one: remove the 4 upper screws, take out the bar and fit the new one.
- The bar fitted with the **16 MB ESP32-S3** controller (see the [hardware assembly manual](docs/hardware/C10_LED_BAR_Assembly_manual_EN.pdf)).
- The bar firmware flashed onto the controller with the **Windows installer** (`.exe`, in [Releases](https://github.com/juanludetoledo/C10-LED-Bar/releases)). Ask for the installer key in a private message on Telegram to **[@juanludetoledo](https://t.me/juanludetoledo)**.
- An **Android 8+** phone with Bluetooth, or an iPhone for the Wi-Fi page.

---

## 2. Install the app

1. Download the latest `C10-LED-Control-…-release.apk` from **[Releases](https://github.com/juanludetoledo/C10-LED-Bar/releases)**.
2. Open it on the phone and allow **installing unknown apps** if Android asks.
3. Open **C10 LED Control** and grant the **Nearby devices / Bluetooth** permissions.
4. For **By sun** mode (day/night from sunrise and sunset), also allow **approximate location**.

Updates install over the previous version without losing your settings.

---

## 3. Connect to the bar

1. Switch the car on and wait for the bar's **welcome** animation to finish.
2. Turn on Bluetooth and open the app: it searches and connects by itself. If not, tap **CONNECT**.
3. The status at the top shows **NORMAL MODE**, **BAR OFF** or **SHOW RUNNING**.

> [!NOTE]
> The controller accepts **one phone at a time**. Opening the app in Android Auto or tapping the widget closes the phone app and releases the connection.

---

## 4. Control tab

- **OFF · Turn bar off:** manual lock; the bar stays off until you tap **ON**.
- **Remember off state:** with this box ticked, the bar stays off at every start-up (no welcome) until you tap ON.
- **ON · Normal mode:** clean restart with the welcome.
- **SENSOR OFF / SENSOR ON:** OFF keeps the bar steady at the set brightness, with no breathing and no sensor. With **Remember** it stays that way on later start-ups.
- **Brightness:** slider and **fine adjustment from 0 to 1000**, in real time. After switching the car off and on it returns to the start-up brightness.
- **Day / night schedule:** automatic DAY and NIGHT brightness, **by time** or **by sun** (sunrise and sunset where the phone is). When the period changes the bar moves to the new brightness with an expansion from the centre.
- **Live view:** shows on screen what the bar is doing right now.

---

## 5. Effects tab

**Show / Party** with 9 themes: Waves, Snake, Stars, Party, Pacific, Breathing, Comet, Cinema and Random tour, plus **My animation**.

- Pick the **duration** (minutes or *Until stopped*) and tap the theme.
- **STOP SHOW** ends it at any time.
- While a show runs, the day/night schedule is paused.

---

## 6. Settings tab

This is where you decide how the bar behaves **while the car is stationary**.

| Setting | What it does |
|---|---|
| **Wait before breathing** | Seconds of steady light before breathing starts |
| **Breathing duration** | 0 = no breathing |
| **Breathing brightness** | Minimum and maximum (0–1000 scale) |
| **Ping-pong when parked** | Effect after breathing: Classic, Comet, Waves, combinations or **your list** (up to 8 effects) |
| **Switch effect every** | How long each effect runs before the next one |
| **Sensor sensitivity** | 5 is the default; higher picks up gentler movements. With a **Live meter** |
| **Start-up fixed brightness** | Brightness the bar always starts with (0–100; factory 65) |
| **Welcome animation** | Classic, Film, Aurora, Cascade or Lightning |

> [!IMPORTANT]
> **TRY** applies the values temporarily. **SAVE** writes them to the controller for every start-up and checks them. **Reset settings** goes back to factory values.

---

## 7. Animation builder

Create your own animations and place them in the bar's sequence.

1. **Your animations:** 4 slots, the same 4 the bar has.
2. **Basic movements:** Comet, Bounce, Ends → centre, Waves, Sparkles… Set speed, tail and brightness and tap **Generate**.
3. **Channel-by-channel editor:** each step sets the brightness of all 34 channels. Draw, erase, invert, mirror or shift.
4. **Emulator:** watch the animation alone or the **whole sequence** before sending it.
5. **Try on the bar:** uploads and plays it without saving.
6. **Final sequence:** 1 welcome → steady light → part 2 → part 3 until you move → 4 steady light while moving.
7. **Save to the bar:** writes it to permanent memory. Only save once it is final.

---

## 8. System tab and updates

- **Information:** sketch version and whether the **motion sensor** is detected.
- **Update firmware** and **Back to original firmware:** Wi-Fi only. Connect the phone to the **`JUANLU_C10LED_WiFi`** network (password `juanludetoledo`) and tap **OPEN WI-FI PAGE (192.168.4.1)**. Do not cut the power during an update.
- **Technical screen:** tap the title 5 times to open or close it.

---

## 9. Android Auto and widget

- **Android Auto:** the C10 LED icon appears in the app row. Tapping it shows a grid with Control (On / Off), Brightness, Shows and **Live status**. Android Auto **developer mode** with **Unknown sources** enabled is required.
- **Widget:** long-press the phone's home screen → **Widgets** → **C10LED**, with ON and OFF buttons.

---

## 10. iPhone: Wi-Fi page

The app is for Android. From an **iPhone**, join the **`JUANLU_C10LED_WiFi`** network (password `juanludetoledo`) and open **`http://192.168.4.1`** in Safari to control the bar from the browser.

---

## 11. Troubleshooting

| Symptom | What to check |
|---|---|
| `JUANLU_C10LED_BLE` not found | Car on and welcome finished; Bluetooth and permissions enabled |
| Connects and drops | Close Android Auto or the widget: the controller accepts one phone only |
| Settings lost after switching off | You tapped TRY instead of **SAVE** |
| Sensor does not react | Check in System that it is *Detected*; raise the **sensitivity** in Settings |
| An update fails | Use **Back to original firmware** from the Wi-Fi page |

---

## 12. Support and backing the project

- 🐞 **Bugs and suggestions:** open an issue in **[GitHub Issues](https://github.com/juanludetoledo/C10-LED-Bar/issues)** with your phone model, app version and sketch version.
- 💬 **Support:** Telegram **[@juanludetoledo](https://t.me/juanludetoledo)**.
- ☕ **Ko-fi:** [https://ko-fi.com/juanludetoledo](https://ko-fi.com/juanludetoledo)

Independent project, not affiliated with Leapmotor.
