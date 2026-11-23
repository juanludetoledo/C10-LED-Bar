# C10 LED Control · Manual do utilizador

**Idiomas:** [Español](MANUAL.md) · [English](MANUAL.en.md) · **Português** · [中文](MANUAL.zh.md)

Guia do **C10 LED Control**, a app que controla a barra LED frontal do **Leapmotor C10**, desenvolvida por **[juanludetoledo](https://github.com/juanludetoledo)** ([juanludetoledo/C10-LED-Bar](https://github.com/juanludetoledo/C10-LED-Bar)).

> [!IMPORTANT]
> **Tudo é gratuito**: a app, o firmware e o manual de montagem do hardware. Se gostar do projeto e quiser apoiá-lo, pode pagar-me um café no **[Ko-fi](https://ko-fi.com/juanludetoledo)**.

> [!WARNING]
> Faça toda a configuração **com o carro estacionado**. Os shows e efeitos são para o carro parado: param sozinhos se o carro se mover.

---

## Índice

1. [O que é e o que precisa](#1-o-que-é-e-o-que-precisa)
2. [Instalar a app](#2-instalar-a-app)
3. [Ligar à barra](#3-ligar-à-barra)
4. [Separador Controle](#4-separador-controle)
5. [Separador Efeitos](#5-separador-efeitos)
6. [Separador Ajustes](#6-separador-ajustes)
7. [Construtor de animações](#7-construtor-de-animações)
8. [Separador Sistema e atualizações](#8-separador-sistema-e-atualizações)
9. [Android Auto e widget](#9-android-auto-e-widget)
10. [iPhone: página Wi-Fi](#10-iphone-página-wi-fi)
11. [Resolução de problemas](#11-resolução-de-problemas)
12. [Suporte e apoiar o projeto](#12-suporte-e-apoiar-o-projeto)

---

## 1. O que é e o que precisa

O **C10 LED Control** comunica com a central da barra por **Bluetooth (BLE)** e permite ligá-la e desligá-la, ajustar o brilho, lançar shows e desenhar as suas próprias animações canal a canal.

| Característica | Detalhe |
|---|---|
| **Ligação** | Bluetooth BLE com a central `JUANLU_C10LED_BLE` |
| **Onde funciona** | Android 8 ou superior · Android Auto · iPhone pela página Wi-Fi |
| **Barra** | 34 canais em três zonas: esquerda (1–11), centro (12–23) e direita (24–34) |
| **Idiomas** | Español, English, Português e 中文 (mudança dentro da app) |

**O que precisa:**
- A barra montada com a central **ESP32-S3 de 16 MB** (ver o [manual de montagem do hardware](docs/hardware/C10_LED_BAR_Manual_montagem_PT.pdf)).
- O firmware da barra gravado na central com o **instalador para Windows** (`.exe`, em [Releases](https://github.com/juanludetoledo/C10-LED-Bar/releases)). Peça a chave do instalador em privado no Telegram a **[@juanludetoledo](https://t.me/juanludetoledo)**.
- Um telemóvel **Android 8+** com Bluetooth, ou um iPhone para a página Wi-Fi.

---

## 2. Instalar a app

1. Descarregue o último `C10-LED-Control-…-release.apk` em **[Releases](https://github.com/juanludetoledo/C10-LED-Bar/releases)**.
2. Abra-o no telemóvel e permita **instalar apps desconhecidas** se o Android pedir.
3. Abra o **C10 LED Control** e conceda as permissões de **Dispositivos próximos / Bluetooth**.
4. Para o modo **Pelo sol** (dia/noite segundo o nascer e o pôr do sol), permita também a **localização aproximada**.

As atualizações instalam-se por cima da versão anterior sem perder os seus ajustes.

---

## 3. Ligar à barra

1. Ligue o carro e espere que termine a **animação de boas-vindas** da barra.
2. Ative o Bluetooth e abra a app: procura e liga-se sozinha. Se não, toque em **CONECTAR**.
3. No topo vê o estado: **MODO NORMAL**, **BARRA DESLIGADA** ou **SHOW EM CURSO**.

> [!NOTE]
> A central aceita **um só telemóvel de cada vez**. Se abrir a app no Android Auto ou tocar no widget, a app do telemóvel fecha-se e liberta a ligação.

---

## 4. Separador Controle

- **OFF · Desligar barra:** bloqueio manual; a barra fica desligada até tocar em **ON**.
- **Lembrar desligado:** com a caixa marcada, a barra continua desligada em cada arranque (sem boas-vindas) até tocar em ON.
- **ON · Modo normal:** reinício limpo com as boas-vindas.
- **SENSOR OFF / SENSOR ON:** OFF deixa a barra fixa no brilho marcado, sem respiração nem sensor. Com **Lembrar** mantém-se nos arranques seguintes.
- **Brilho:** cursor e **ajuste fino de 0 a 1000**, em tempo real. Ao desligar e ligar o carro volta ao brilho de arranque.
- **Programação dia / noite:** brilho de DIA e de NOITE automático, **por hora** ou **pelo sol** (nascer e pôr do sol onde estiver o telemóvel). Ao mudar de período a barra passa ao novo brilho com uma expansão a partir do centro.
- **Vista em direto da barra:** mostra no ecrã o que a barra está a fazer nesse momento.

---

## 5. Separador Efeitos

**Show / Festa** com 9 temas: Ondas, Snake, Estrelas, Festa, Pacífico, Respiração, Cometa, Cinema e Aleatório, além de **Minha animação**.

- Escolha a **duração** (minutos ou *Até parar*) e toque no tema.
- **PARAR SHOW** termina-o a qualquer momento.
- Enquanto o show dura, a programação dia/noite fica em pausa.

---

## 6. Separador Ajustes

Aqui decide como a barra se comporta **com o carro parado**.

| Ajuste | O que faz |
|---|---|
| **Espera antes de respirar** | Segundos de luz fixa antes de começar a respiração |
| **Duração da respiração** | 0 = sem respiração |
| **Brilho da respiração** | Mínimo e máximo (escala 0–1000) |
| **Ping-pong parado** | Efeito depois da respiração: Clássico, Cometa, Ondas, combinações ou **a sua lista** (até 8 efeitos) |
| **Trocar de efeito a cada** | Tempo de cada efeito antes de passar ao seguinte |
| **Sensibilidade do sensor** | 5 é o normal; mais alto deteta movimentos mais suaves. Com **Medidor ao vivo** |
| **Brilho fixo de arranque** | Brilho com que a barra arranca sempre (0–100; de fábrica 65) |
| **Animação de boas-vindas** | Clássica, Filme, Aurora, Cascata ou Raio |

> [!IMPORTANT]
> **TESTAR** aplica os valores de forma temporária. **GUARDAR** grava-os na central para cada arranque e verifica-os. **Repor ajustes** volta aos valores de fábrica.

---

## 7. Construtor de animações

Crie animações próprias e coloque-as na sequência da barra.

1. **Suas animações:** 4 ranhuras, as mesmas 4 que a barra tem.
2. **Movimentos básicos:** Cometa, Vai e vem, Pontas → centro, Ondas, Cintilações… Ajuste velocidade, cauda e brilho e toque em **Gerar**.
3. **Editor canal por canal:** cada passo fixa o brilho dos 34 canais. Desenhe, apague, inverta, espelhe ou desloque.
4. **Emulador:** veja a animação sozinha ou a **sequência inteira** antes de a enviar.
5. **Testar na barra:** envia-a e reprodu-la sem a guardar.
6. **Sequência definitiva:** 1 boas-vindas → luz fixa → parte 2 → parte 3 até se mover → 4 luz fixa em movimento.
7. **Guardar na barra:** grava-a na memória permanente. Guarde só quando estiver definitiva.

---

## 8. Separador Sistema e atualizações

- **Informação:** versão do sketch e se o **sensor de movimento** está detetado.
- **Atualizar firmware** e **Voltar ao firmware original:** só por Wi-Fi. Ligue o telemóvel à rede **`JUANLU_C10LED_WiFi`** (senha `juanludetoledo`) e toque em **ABRIR PÁGINA WI-FI (192.168.4.1)**. Não corte a alimentação durante a atualização.
- **Ecrã técnico:** toque 5 vezes no título para o abrir ou fechar.

---

## 9. Android Auto e widget

- **Android Auto:** o ícone C10 LED aparece na fila de apps. Ao tocar vê uma grelha com Controle (Ligar / Desligar), Brilho, Shows e **Estado ao vivo**. É preciso o **modo programador** do Android Auto com **Fontes desconhecidas** ativado.
- **Widget:** mantenha premido o ecrã inicial do telemóvel → **Widgets** → **C10LED**, com botões ON e OFF.

---

## 10. iPhone: página Wi-Fi

A app é para Android. Num **iPhone**, ligue-se à rede **`JUANLU_C10LED_WiFi`** (senha `juanludetoledo`) e abra **`http://192.168.4.1`** no Safari para controlar a barra a partir do navegador.

---

## 11. Resolução de problemas

| Sintoma | O que verificar |
|---|---|
| Não encontra `JUANLU_C10LED_BLE` | Carro ligado e boas-vindas terminadas; Bluetooth e permissões ativos |
| Liga-se e desliga-se | Feche o Android Auto ou o widget: a central aceita um só telemóvel |
| Os ajustes perdem-se ao desligar | Tocou em TESTAR em vez de **GUARDAR** |
| O sensor não reage | Veja em Sistema se está *Detetado*; suba a **sensibilidade** em Ajustes |
| Uma atualização falha | Use **Voltar ao firmware original** na página Wi-Fi |

---

## 12. Suporte e apoiar o projeto

- 🐞 **Erros e sugestões:** abra um pedido em **[GitHub Issues](https://github.com/juanludetoledo/C10-LED-Bar/issues)** com o modelo do telemóvel, a versão da app e a do sketch.
- 💬 **Suporte:** Telegram **[@juanludetoledo](https://t.me/juanludetoledo)**.
- ☕ **Ko-fi:** [https://ko-fi.com/juanludetoledo](https://ko-fi.com/juanludetoledo)

Projeto independente, sem relação com a Leapmotor.
