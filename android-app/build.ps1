$ErrorActionPreference = 'Stop'
$sourceRoot = $PSScriptRoot
# aapt2 on Windows cannot open this project's Chinese path. Stage inputs in TEMP.
$taskRoot = Join-Path $env:TEMP 'blackstone-android-build'
New-Item -ItemType Directory -Force -Path $taskRoot | Out-Null
Copy-Item -LiteralPath "$sourceRoot\src", "$sourceRoot\res", "$sourceRoot\AndroidManifest.xml" -Destination $taskRoot -Recurse -Force
# Use a fresh asset directory so removed private reference images cannot survive an earlier build.
$packageAssets=Join-Path $taskRoot ('assets-'+[guid]::NewGuid().ToString('N'))
Copy-Item -LiteralPath "$sourceRoot\assets" -Destination $packageAssets -Recurse
$javaBin = 'D:\JDK\openjdk-17\bin'
$sdkRoot = Join-Path (Split-Path $sourceRoot) 'android-tools\sdk'
$buildTools = Join-Path $sdkRoot 'android-15'
Copy-Item -LiteralPath "$sdkRoot\android-35-ext15\android.jar" -Destination "$taskRoot\android.jar" -Force
$androidJar = "$taskRoot\android.jar"
$buildDir = Join-Path $taskRoot 'build'
$env:JAVA_HOME = Split-Path $javaBin
$env:Path = "$javaBin;$env:Path"
New-Item -ItemType Directory -Force -Path "$buildDir\gen", "$buildDir\classes", "$buildDir\dex", "$taskRoot\dist" | Out-Null
function Run-Checked($exe, $arguments) { & $exe @arguments; if ($LASTEXITCODE -ne 0) { throw "$exe failed: $LASTEXITCODE" } }
Run-Checked "$buildTools\aapt2.exe" @('compile','--dir',"$taskRoot\res",'-o',"$buildDir\resources.zip")
Run-Checked "$buildTools\aapt2.exe" @('link','-o',"$buildDir\base.apk",'-I',$androidJar,'--manifest',"$taskRoot\AndroidManifest.xml",'--java',"$buildDir\gen",'-A',$packageAssets, "$buildDir\resources.zip")
$sources = @(Get-ChildItem "$taskRoot\src", "$buildDir\gen" -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
Run-Checked "$javaBin\javac.exe" (@('-encoding','UTF-8','-source','8','-target','8','-bootclasspath',"$androidJar;$buildTools\core-lambda-stubs.jar",'-d',"$buildDir\classes") + $sources)
Run-Checked "$javaBin\jar.exe" @('cf',"$buildDir\classes.jar",'-C',"$buildDir\classes",'.')
Run-Checked "$javaBin\java.exe" @('-cp',"$buildTools\lib\d8.jar",'com.android.tools.r8.D8','--lib',$androidJar,'--min-api','26','--output',"$buildDir\dex", "$buildDir\classes.jar")
Copy-Item -LiteralPath "$buildDir\base.apk" -Destination "$buildDir\unsigned.apk" -Force
Run-Checked "$javaBin\jar.exe" @('uf',"$buildDir\unsigned.apk",'-C',"$buildDir\dex",'classes.dex')
Run-Checked "$buildTools\zipalign.exe" @('-f','4',"$buildDir\unsigned.apk", "$buildDir\aligned.apk")
$keyPath = Join-Path $sourceRoot 'debug.keystore'
if (!(Test-Path -LiteralPath $keyPath)) { Run-Checked "$javaBin\keytool.exe" @('-genkeypair','-keystore',$keyPath,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Local Test','-keyalg','RSA','-keysize','2048','-validity','10000') }
$apk = Join-Path $taskRoot 'dist\blackstone-assistant-0.3.13.apk'
Run-Checked "$javaBin\java.exe" @('-jar',"$buildTools\lib\apksigner.jar",'sign','--ks',$keyPath,'--ks-pass','pass:android','--key-pass','pass:android','--out',$apk, "$buildDir\aligned.apk")
Run-Checked "$javaBin\java.exe" @('-jar',"$buildTools\lib\apksigner.jar",'verify','--verbose',$apk)
Write-Output "APK: $apk"
New-Item -ItemType Directory -Force -Path "$sourceRoot\dist" | Out-Null
Copy-Item -LiteralPath $apk -Destination "$sourceRoot\dist\blackstone-assistant-0.3.13.apk" -Force
