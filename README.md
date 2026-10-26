# tv-tcl

Tela inicial e remapeamento de botões para uma TV TCL 2019 com Android TV 8 (1,5 GB de RAM), feitos depois de
limpar os apps de fábrica via ADB.

## O que tem aqui

### `inicio/`: tela inicial "Vice City"
Launcher leve, em Java puro (sem Gradle nem bibliotecas):

- Cenário de pôr do sol neon desenhado em código: sol com faixas, skyline art déco com luz de contorno e reflexo
  na água, palmeiras, estrelas, pássaros, jato e helicóptero passando.
- Relógio estilo VHS ("▶ PLAY", aberração cromática, linhas de varredura, contador de fita).
- Cinco cartões: TV aberta, PS4 (HDMI1), Notebook (HDMI2), YouTube e Ajustes.
- A parte estática é desenhada uma vez num bitmap; a animação roda a 20 fps e para fora da tela inicial.

`Botoes.java` é um serviço de acessibilidade que remapeia o controle:

| Botão     | Faz                | Como |
|-----------|--------------------|------|
| Input     | vai para TV aberta | filtro de teclas da acessibilidade (`KEYCODE_TV_INPUT`) |
| Netflix   | vai para HDMI1     | registro no serviço de teclas da TCL (`IKeyManagerlInterface`, keycode 4062) |
| Voz       | abre o YouTube     | idem (keycode 4048) |

Os botões Netflix e voz usam códigos próprios da TCL, que o Android não entrega à acessibilidade. O serviço
`com.tcl.keycustomfunctionservice` (processo `:remote`) aceita que um app registre um `KeyCallback` para uma
lista de teclas; com a flag `1` ele pula a ação padrão do botão e chama o callback. A interface foi lida do
vdex do próprio serviço (transação 2 = `register(String, KeyCallback, List<ListeningKey>)`).

### `ponte/`: botão Globoplay
O serviço da TCL abre um componente fixo, `com.globo.globotv/br.com.globo.globotv.activity.SplashActivity`.
Este app minúsculo tem esse nome e só troca a TV para o HDMI2. Ele substitui o Globoplay, que precisa estar
desinstalado.

## IDs das entradas desta TV

```
TV aberta  com.tcl.tvinput/.TDTVInputService/HW1413742848
HDMI1      com.tcl.tvpassthrough/.TvPassThroughService/HW1413744128
HDMI2      com.tcl.tvpassthrough/.TvPassThroughService/HW1413744384
```

Em outra TV, liste-as com `adb shell dumpsys tv_input`.

## Compilar e instalar

1. Baixe em `sdk/` (fica fora do git):
   - `build-tools_r34-windows.zip` → `sdk/bt/android-14/`
   - `platform-26_r02.zip` → `sdk/pf/android-8.0.0/`
2. Gere uma keystore em `ponte/ponte.keystore` (também fora do git) ou use a sua.
3. Com a Depuração USB ligada na TV e `adb connect <ip>:5555`, defina `$env:TV_KEYSTORE_PASS` com a senha da
   keystore e rode `.\build-inicio.ps1`.

Depois de instalar o `inicio.apk` pela primeira vez:

```
adb shell cmd package set-home-activity com.casa.inicio/.HomeActivity
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell settings put secure enabled_accessibility_services com.casa.inicio/com.casa.inicio.Botoes
adb shell settings put secure accessibility_enabled 1
adb shell pm grant com.casa.inicio android.permission.WRITE_SECURE_SETTINGS
```

O firmware da TCL zera os serviços de acessibilidade a cada boot. Com a permissão acima, o `Ativador` religa o
serviço dos botões no boot e sempre que a tela inicial abre.
