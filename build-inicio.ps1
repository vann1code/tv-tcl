$ErrorActionPreference = 'Stop'
$sc  = $PSScriptRoot
$bt  = "$sc\sdk\bt\android-14"
$jar = "$sc\sdk\pf\android-8.0.0\android.jar"
$p   = "$sc\inicio"
$b   = "$p\build"
$adb = (Get-Command adb).Source
$senha = $env:TV_KEYSTORE_PASS
if (-not $senha) { throw "Defina a senha da keystore em `$env:TV_KEYSTORE_PASS" }

Remove-Item $b -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory "$b\obj", "$b\dex" | Out-Null
& javac --release 8 -encoding UTF-8 -nowarn -cp $jar -d "$b\obj" (Get-ChildItem "$p\src" -Recurse -Filter *.java | ForEach-Object FullName)
if ($LASTEXITCODE) { throw "javac" }
& "$bt\d8.bat" --release --min-api 21 --lib $jar --output "$b\dex" (Get-ChildItem "$b\obj" -Recurse -Filter *.class | ForEach-Object FullName)
if ($LASTEXITCODE) { throw "d8" }
& "$bt\aapt2.exe" compile --dir "$p\res" -o "$b\res.zip"
if ($LASTEXITCODE) { throw "aapt2 compile" }
& "$bt\aapt2.exe" link -o "$b\unsigned.apk" -I $jar --manifest "$p\AndroidManifest.xml" "$b\res.zip" --min-sdk-version 21 --target-sdk-version 26
if ($LASTEXITCODE) { throw "aapt2" }
Push-Location "$b\dex"; & jar uf "$b\unsigned.apk" classes.dex; Pop-Location
& "$bt\zipalign.exe" -f 4 "$b\unsigned.apk" "$b\aligned.apk"
& "$bt\apksigner.bat" sign --ks "$sc\ponte\ponte.keystore" --ks-pass env:TV_KEYSTORE_PASS --out "$p\inicio.apk" "$b\aligned.apk"
if ($LASTEXITCODE) { throw "apksigner" }

& $adb install -r "$p\inicio.apk"
& $adb shell "input keyevent KEYCODE_HOME"
Start-Sleep 3
& $adb shell "screencap -p /sdcard/tela.png"
& $adb pull /sdcard/tela.png "$sc\tela.png" | Out-Null
& $adb shell "rm /sdcard/tela.png"
