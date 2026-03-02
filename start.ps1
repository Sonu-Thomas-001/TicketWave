# TicketWave — One-Command Full-Stack Launcher
# Usage:  .\start.ps1
# Stop:   .\start.ps1 -Stop

param(
    [switch]$Stop
)

$ErrorActionPreference = 'SilentlyContinue'

# ---------- Load environment ----------
. "$PSScriptRoot\env.ps1"

$BACKEND_PORT  = 8080
$FRONTEND_PORT = 3000
$BACKEND_DIR   = $PSScriptRoot
$FRONTEND_DIR  = Join-Path $PSScriptRoot 'frontend'

function Write-Status($msg, $color = 'Cyan') {
    Write-Host "[TicketWave] $msg" -ForegroundColor $color
}

function Stop-PortProcess($port) {
    $conns = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($conns) {
        $conns | ForEach-Object {
            if ($_.OwningProcess -gt 0) {
                Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
            }
        }
        Write-Status "Stopped process on port $port" 'Yellow'
        Start-Sleep -Seconds 2
    }
}

# ---------- Stop mode ----------
if ($Stop) {
    Write-Status 'Shutting down TicketWave...' 'Yellow'
    Stop-PortProcess $BACKEND_PORT
    Stop-PortProcess $FRONTEND_PORT
    Write-Status 'All services stopped.' 'Green'
    exit 0
}

# ---------- Pre-flight checks ----------
Write-Host ''
Write-Host '  ========================================' -ForegroundColor Magenta
Write-Host '    TicketWave Full-Stack Launcher v1.0' -ForegroundColor Magenta
Write-Host '  ========================================' -ForegroundColor Magenta
Write-Host ''

# Check Java
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Status 'Java not found. Check JAVA_HOME in env.ps1' 'Red'; exit 1
}

# Check Maven
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Status 'Maven not found. Check Path in env.ps1' 'Red'; exit 1
}

# Check Node
if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Status 'Node.js not found. Add it to Path in env.ps1' 'Red'; exit 1
}

# Check PostgreSQL is reachable
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect('localhost', 5432)
    $tcp.Close()
    Write-Status 'PostgreSQL .... OK' 'Green'
} catch {
    Write-Status 'PostgreSQL not reachable on port 5432. Start it first.' 'Red'; exit 1
}

# Check Redis is reachable
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect($env:REDIS_HOST, [int]$env:REDIS_PORT)
    $tcp.Close()
    Write-Status 'Redis ......... OK' 'Green'
} catch {
    Write-Status 'Redis not reachable on port 6379. Start it first.' 'Red'; exit 1
}

# ---------- Free ports if occupied ----------
Stop-PortProcess $BACKEND_PORT
Stop-PortProcess $FRONTEND_PORT

# ---------- Install frontend deps if needed ----------
if (-not (Test-Path (Join-Path $FRONTEND_DIR 'node_modules'))) {
    Write-Status 'Installing frontend dependencies...'
    Push-Location $FRONTEND_DIR
    npm install
    Pop-Location
}

# ---------- Start Backend (background job) ----------
Write-Status 'Starting Spring Boot backend on port 8080...'
$backendJob = Start-Job -ScriptBlock {
    param($dir, $javaHome, $mavenPath, $dbPass)
    $env:JAVA_HOME = $javaHome
    $env:Path = "$javaHome\bin;$mavenPath;$env:Path"
    $env:DB_PASSWORD = $dbPass
    $env:SPRING_PROFILES_ACTIVE = 'dev'
    Set-Location $dir
    & mvn spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dmaven.test.skip=true" 2>&1
} -ArgumentList $BACKEND_DIR, $env:JAVA_HOME, `
    'C:\Users\sonu_thomas\Documents\Dependecy for application\apache-maven-3.9.12-bin\apache-maven-3.9.12\bin', `
    $env:DB_PASSWORD

# ---------- Wait for backend to be ready ----------
Write-Status 'Waiting for backend to start (this may take ~20s)...'
$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $tcp.Connect('localhost', $BACKEND_PORT)
        $tcp.Close()
        $ready = $true
        break
    } catch { }

    # Check if job failed
    if ($backendJob.State -eq 'Completed' -or $backendJob.State -eq 'Failed') {
        Write-Status 'Backend failed to start. Logs:' 'Red'
        Receive-Job $backendJob | Select-Object -Last 30
        exit 1
    }
}

if (-not $ready) {
    Write-Status 'Backend did not start within 120s. Logs:' 'Red'
    Receive-Job $backendJob | Select-Object -Last 30
    exit 1
}
Write-Status 'Backend ........ RUNNING (port 8080)' 'Green'

# ---------- Start Frontend (background job) ----------
Write-Status 'Starting Vite frontend on port 3000...'
$frontendJob = Start-Job -ScriptBlock {
    param($dir)
    $env:Path = "C:\Program Files\nodejs;$env:Path"
    Set-Location $dir
    & npm run dev 2>&1
} -ArgumentList $FRONTEND_DIR

# Wait briefly for frontend
Start-Sleep -Seconds 5
Write-Status 'Frontend ....... RUNNING (port 3000)' 'Green'

# ---------- Done ----------
Write-Host ''
Write-Host '  ========================================' -ForegroundColor Green
Write-Host '    All services are running!' -ForegroundColor Green
Write-Host '' -ForegroundColor Green
Write-Host '    Frontend:  http://localhost:3000' -ForegroundColor Green
Write-Host '    Backend:   http://localhost:8080' -ForegroundColor Green
Write-Host '    Actuator:  http://localhost:8080/actuator' -ForegroundColor Green
Write-Host '' -ForegroundColor Green
Write-Host '    Press Ctrl+C to stop, or run:' -ForegroundColor Green
Write-Host '      .\start.ps1 -Stop' -ForegroundColor Green
Write-Host '  ========================================' -ForegroundColor Green
Write-Host ''

# Open browser
Start-Process 'http://localhost:3000'

# ---------- Keep alive & stream backend logs ----------
Write-Status 'Streaming backend logs (Ctrl+C to stop all)...' 'Cyan'
try {
    while ($true) {
        # Stream new backend output
        Receive-Job $backendJob 2>&1 | ForEach-Object { Write-Host $_ }
        
        # If backend died, report and exit
        if ($backendJob.State -ne 'Running') {
            Write-Status 'Backend process exited!' 'Red'
            Receive-Job $backendJob | Select-Object -Last 20
            break
        }
        Start-Sleep -Seconds 1
    }
} finally {
    Write-Status 'Cleaning up...' 'Yellow'
    Stop-Job $backendJob  -ErrorAction SilentlyContinue
    Stop-Job $frontendJob -ErrorAction SilentlyContinue
    Remove-Job $backendJob  -ErrorAction SilentlyContinue
    Remove-Job $frontendJob -ErrorAction SilentlyContinue
    Stop-PortProcess $BACKEND_PORT
    Stop-PortProcess $FRONTEND_PORT
    Write-Status 'TicketWave stopped.' 'Green'
}
