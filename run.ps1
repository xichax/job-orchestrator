#requires -version 5
<#
  run.ps1 — build & run the Job Orchestration Platform.

  The orchestrator itself runs on the HOST JVM (it needs to shell out to `podman`
  to provision a fresh container per job). The jar is compiled inside a Gradle
  container so you don't need Gradle installed — only a JDK and Podman on PATH.

  Usage:
    ./run.ps1 build   compile the jar (inside a gradle container)
    ./run.ps1 up      build (if needed) + run the app on the host JVM
    ./run.ps1 run     run the already-built jar
#>

param(
  [Parameter(Position = 0)]
  [ValidateSet('build', 'up', 'run')]
  [string]$Command = 'up'
)

$ErrorActionPreference = 'Stop'
$Root  = $PSScriptRoot
$Jar   = Join-Path $Root 'build\libs\job-orchestrator-0.1.0.jar'

function Build {
  Write-Host "Building jar inside a gradle:8.10-jdk21 container..." -ForegroundColor Cyan
  # Mount the project into the container and run bootJar. The host home is
  # auto-mounted by the podman machine, so this path is visible inside.
  podman run --rm -v "${Root}:/work" -w /work docker.io/library/gradle:8.10-jdk21 `
    gradle --no-daemon bootJar
  if ($LASTEXITCODE -ne 0) { throw "Gradle build failed." }
  Write-Host "Built $Jar" -ForegroundColor Green
}

function Run {
  if (-not (Test-Path $Jar)) { throw "Jar not found. Run './run.ps1 build' first." }
  Write-Host "Starting orchestrator on the host JVM (http://localhost:8090)..." -ForegroundColor Green
  Write-Host "Jobs will be launched as podman containers. Ctrl+C to stop." -ForegroundColor Green
  java -jar $Jar
}

switch ($Command) {
  'build' { Build }
  'run'   { Run }
  'up'    { if (-not (Test-Path $Jar)) { Build }; Run }
}
