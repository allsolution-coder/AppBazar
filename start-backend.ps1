$jdk = (Get-ChildItem "F:\tools" -Directory -Filter "jdk-21*" | Select-Object -First 1).FullName
$mvn = "F:\tools\apache-maven-3.9.9\bin"
if (-not $jdk) { Write-Host "JDK 21 not found in F:\tools" -ForegroundColor Red; exit 1 }
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$mvn;" + $env:Path
if (-not $env:DB_PASS) { $env:DB_PASS = Read-Host "PostgreSQL password" }
Set-Location (Join-Path $PSScriptRoot "backend")
mvn spring-boot:run
