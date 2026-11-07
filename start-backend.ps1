# Starts the AppBazaar backend (Java 21 + Maven from F:\tools). Run from any folder.
$jdk = (Get-ChildItem "F:\tools" -Directory -Filter "jdk-21*" | Select-Object -First 1).FullName
$mvn = "F:\tools\apache-maven-3.9.9\bin"
if (-not $jdk) { Write-Host "JDK 21 not found in F:\tools" -ForegroundColor Red; exit 1 }
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$mvn;" + $env:Path
if (-not $env:DB_PASS) { $env:DB_PASS = Read-Host "PostgreSQL password" }
if (-not $env:PAYPAL_CLIENT_ID) {
    $id = Read-Host "PayPal sandbox Client ID (press Enter to skip and use demo payments)"
    if ($id) {
        $env:PAYPAL_CLIENT_ID = $id
        $env:PAYPAL_SECRET = Read-Host "PayPal sandbox Secret"
    }
}
if (-not $env:SPRING_MAIL_HOST) {
    $mail = Read-Host "Gmail address that sends login codes (press Enter to skip: codes will print in this window)"
    if ($mail) {
        $env:SPRING_MAIL_HOST = "smtp.gmail.com"
        $env:SPRING_MAIL_PORT = "587"
        $env:SPRING_MAIL_USERNAME = $mail
        $env:MAIL_FROM = $mail
        $env:SPRING_MAIL_PASSWORD = Read-Host "Gmail app password (16 letters, not your normal password)"
    }
}
Set-Location (Join-Path $PSScriptRoot "backend")
mvn spring-boot:run
