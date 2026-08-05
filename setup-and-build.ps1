$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

Write-Host "=== 디온 GPS 빌드 환경 설정 ===" -ForegroundColor Cyan

# 1) Java 확인
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    $fallbackJava = "C:\Program Files\Common Files\Oracle\Java\javapath\java.exe"
    if (Test-Path $fallbackJava) {
        $env:Path = "$(Split-Path $fallbackJava);$env:Path"
        $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    }
}
if (-not $javaCmd) {
    Write-Host "[ERROR] Java가 설치되어 있지 않습니다. JDK 17+를 설치해 주세요." -ForegroundColor Red
    exit 1
}
$javaVersion = (cmd /c "java -version 2>&1") | Select-Object -First 1
Write-Host "[OK] Java: $javaVersion"

# 2) Gradle Wrapper JAR
$wrapperJar = Join-Path $ProjectRoot "gradle\wrapper\gradle-wrapper.jar"
if (-not (Test-Path $wrapperJar)) {
    Write-Host "[..] Gradle Wrapper 다운로드 중..."
    New-Item -ItemType Directory -Force -Path (Split-Path $wrapperJar) | Out-Null
    Invoke-WebRequest -Uri "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar" -OutFile $wrapperJar
    Write-Host "[OK] Gradle Wrapper 준비 완료"
} else {
    Write-Host "[OK] Gradle Wrapper 이미 존재"
}

# 3) Android SDK (명령줄 도구)
$SdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$CmdlineToolsDir = Join-Path $SdkRoot "cmdline-tools\latest"
$SdkManager = Join-Path $CmdlineToolsDir "bin\sdkmanager.bat"

if (-not (Test-Path $SdkManager)) {
    Write-Host "[..] Android SDK Command-line Tools 다운로드 중 (약 150MB)..."
    New-Item -ItemType Directory -Force -Path $SdkRoot | Out-Null
    $zipPath = Join-Path $env:TEMP "cmdline-tools.zip"
    $toolsUrl = "https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip"
    Invoke-WebRequest -Uri $toolsUrl -OutFile $zipPath

    $extractDir = Join-Path $env:TEMP "android-cmdline-tools"
    if (Test-Path $extractDir) { Remove-Item $extractDir -Recurse -Force }
    Expand-Archive -Path $zipPath -DestinationPath $extractDir -Force

    New-Item -ItemType Directory -Force -Path (Join-Path $SdkRoot "cmdline-tools") | Out-Null
    if (Test-Path $CmdlineToolsDir) { Remove-Item $CmdlineToolsDir -Recurse -Force }
    Move-Item (Join-Path $extractDir "cmdline-tools") $CmdlineToolsDir
    Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
    Write-Host "[OK] Command-line Tools 설치 완료"
} else {
    Write-Host "[OK] Android SDK Command-line Tools 이미 존재"
}

# 4) SDK 패키지 설치
Write-Host "[..] Android SDK 패키지 설치 중 (platform-tools, build-tools, android-34)..."
$yes = ("y`n" * 20)
$yes | & $SdkManager --sdk_root=$SdkRoot "platform-tools" "platforms;android-34" "build-tools;34.0.0" | Out-Host
Write-Host "[OK] SDK 패키지 설치 완료"

# 5) local.properties
$localProps = Join-Path $ProjectRoot "local.properties"
$sdkPath = $SdkRoot -replace '\\', '/'
"sdk.dir=$sdkPath" | Set-Content -Path $localProps -Encoding UTF8
Write-Host "[OK] local.properties 생성: $localProps"

# 6) APK 빌드
Write-Host "[..] Debug APK 빌드 중 (첫 실행 시 Gradle 다운로드로 시간이 걸릴 수 있습니다)..."
& (Join-Path $ProjectRoot "gradlew.bat") assembleDebug --no-daemon

if ($LASTEXITCODE -eq 0) {
    $apk = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
    Write-Host ""
    Write-Host "=== 빌드 성공 ===" -ForegroundColor Green
    Write-Host "APK: $apk"
    Write-Host ""
    Write-Host "휴대폰 설치 방법:"
    Write-Host "1. USB 디버깅 ON 후 휴대폰 연결"
    Write-Host "2. adb install `"$apk`""
    Write-Host "3. 개발자 옵션 -> 모의 위치 앱 -> 디온 GPS 선택"
} else {
    Write-Host "[ERROR] 빌드 실패. 위 로그를 확인해 주세요." -ForegroundColor Red
    exit 1
}
