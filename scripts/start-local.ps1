[CmdletBinding()]
param(
	[Alias('Keep', 'NoStop', 'KeepPreviousProcesses')]
	[switch]$KeepPrevious,

	[Alias('NoPackage', 'Dev', 'SkipPackage')]
	[switch]$BackendNoPackage,

	[ValidateSet('auto', 'wt', 'powershell', 'cmd')]
	[string]$Terminal = 'auto',

	[switch]$Help,

	[ValidateSet('frontend', 'backend')]
	[string]$Service,

	[string]$StateDir
)

$ErrorActionPreference = 'Stop'
$scriptPath = $MyInvocation.MyCommand.Path
$scriptDirectory = Split-Path -Parent $scriptPath
$projectRoot = Split-Path -Parent $scriptDirectory
if (-not $StateDir) {
	$StateDir = Join-Path $scriptDirectory '.start-local'
}

function Show-Usage {
	@'
DataAgent local development launcher

Usage:
  .\scripts\start-local.ps1 [-KeepPrevious] [-BackendNoPackage]

Options:
  -KeepPrevious       Do not stop services started by the previous invocation.
  -BackendNoPackage   Run the backend with spring-boot:run instead of packaging a jar.
  -Terminal <mode>    auto (default), wt, powershell, or cmd.
  -Help               Show this help.

Examples:
  .\scripts\start-local.ps1
  .\scripts\start-local.ps1 -BackendNoPackage
  .\scripts\start-local.ps1 -KeepPrevious -Terminal powershell
'@ | Write-Output
}

function Ensure-StateDirectory {
	if (-not (Test-Path -LiteralPath $StateDir)) {
		New-Item -ItemType Directory -Path $StateDir -Force | Out-Null
	}
}

function Get-PidFile([string]$serviceName) {
	return Join-Path $StateDir "$serviceName.pid"
}

function Read-TrackedPids([string]$pidFile) {
	if (-not (Test-Path -LiteralPath $pidFile)) {
		return @()
	}
	return @(Get-Content -LiteralPath $pidFile | Where-Object { $_ -match '^\d+$' } | ForEach-Object {
		[int]$_
	})
}

function Add-TrackedPid([string]$serviceName, [int]$processId) {
	$pidFile = Get-PidFile $serviceName
	Add-Content -LiteralPath $pidFile -Value $processId -Encoding ascii
}

function Remove-TrackedPid([string]$serviceName, [int]$processId) {
	$pidFile = Get-PidFile $serviceName
	if (-not (Test-Path -LiteralPath $pidFile)) {
		return
	}
	$remaining = @(Read-TrackedPids $pidFile | Where-Object { $_ -ne $processId })
	if ($remaining.Count -eq 0) {
		Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
	}
	else {
		$remaining | Set-Content -LiteralPath $pidFile -Encoding ascii
	}
}

function Stop-TrackedServices {
	Ensure-StateDirectory
	foreach ($serviceName in @('frontend', 'backend')) {
		$pidFile = Get-PidFile $serviceName
		foreach ($processId in @(Read-TrackedPids $pidFile)) {
			$process = Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction SilentlyContinue
			if ($null -ne $process) {
				try {
					& taskkill.exe /PID $processId /T /F | Out-Null
					Write-Host "Stopped previous $serviceName process tree (PID $processId)."
				}
				catch {
					Write-Warning "Could not stop previous $serviceName process tree (PID $processId): $($_.Exception.Message)"
				}
			}
		}
		Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
	}
}

function Invoke-Service([string]$serviceName) {
	Ensure-StateDirectory
	$processId = $PID
	Add-TrackedPid $serviceName $processId
	try {
		if ($serviceName -eq 'frontend') {
			$frontendDirectory = Join-Path $projectRoot 'data-agent-frontend-nuxt'
			Set-Location -LiteralPath $frontendDirectory
			Write-Host 'Starting DataAgent frontend (pnpm dev)...'
			& pnpm.cmd dev
			return $LASTEXITCODE
		}

		Set-Location -LiteralPath $projectRoot
		if ($BackendNoPackage) {
			Write-Host 'Starting DataAgent backend with spring-boot:run...'
			& (Join-Path $projectRoot 'mvnw.cmd') -pl data-agent-management spring-boot:run
			return $LASTEXITCODE
		}

		Write-Host 'Packaging DataAgent backend (tests are skipped)...'
		& (Join-Path $projectRoot 'mvnw.cmd') -pl data-agent-management -am package -DskipTests
		if ($LASTEXITCODE -ne 0) {
			Write-Error "Backend packaging failed with exit code $LASTEXITCODE."
			return $LASTEXITCODE
		}

		$jar = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'data-agent-management\target') -Filter '*.jar' -File |
			Where-Object { $_.Name -notlike '*.original' } |
			Sort-Object LastWriteTime -Descending |
			Select-Object -First 1
		if ($null -eq $jar) {
			Write-Error 'Backend packaging succeeded, but no executable jar was found.'
			return 1
		}

		Write-Host "Starting DataAgent backend from $($jar.Name)..."
		& java.exe -jar $jar.FullName
		return $LASTEXITCODE
	}
	finally {
		Remove-TrackedPid $serviceName $processId
	}
}

function Get-ExecutablePath([string]$name) {
	$command = Get-Command $name -ErrorAction SilentlyContinue | Select-Object -First 1
	if ($null -eq $command) {
		return $null
	}
	return $command.Source
}

function Get-CmdServiceArguments([string]$serviceName) {
	if ($serviceName -eq 'frontend') {
		return @('/k', "cd /d `"$(Join-Path $projectRoot 'data-agent-frontend-nuxt')`" && pnpm.cmd dev")
	}
	if ($BackendNoPackage) {
		return @('/k', "cd /d `"$projectRoot`" && mvnw.cmd -pl data-agent-management spring-boot:run")
	}
	$jarPath = Join-Path $projectRoot 'data-agent-management\target\spring-ai-alibaba-data-agent-management-1.0.0-SNAPSHOT.jar'
	return @('/k', "cd /d `"$projectRoot`" && mvnw.cmd -pl data-agent-management -am package -DskipTests && java -jar `"$jarPath`"")
}

function Get-ServiceArguments([string]$serviceName, [string]$shellType) {
	if ($shellType -eq 'cmd') {
		return Get-CmdServiceArguments $serviceName
	}
	$arguments = @('-NoExit', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $scriptPath, '-Service', $serviceName, '-StateDir', $StateDir)
	if ($serviceName -eq 'backend' -and $BackendNoPackage) {
		$arguments += '-BackendNoPackage'
	}
	return $arguments
}

function Start-WithWindowsTerminal([string]$wtPath, [string]$shellPath, [string]$shellType) {
	$arguments = @(
		'-w', '0',
		'new-tab', '--title', 'DataAgent Frontend',
		$shellPath
	) + (Get-ServiceArguments 'frontend' $shellType) + @(
		';',
		'new-tab', '--title', 'DataAgent Backend',
		$shellPath
	) + (Get-ServiceArguments 'backend' $shellType)

	& $wtPath @arguments
	if ($LASTEXITCODE -ne 0) {
		throw "Windows Terminal returned exit code $LASTEXITCODE."
	}
}

function Start-WithSeparateWindows([string]$shellType, [string]$shellPath) {
	foreach ($serviceName in @('frontend', 'backend')) {
		$arguments = Get-ServiceArguments $serviceName $shellType
		Start-Process -FilePath $shellPath -WorkingDirectory $projectRoot -ArgumentList $arguments | Out-Null
	}
}

if ($Help) {
	Show-Usage
	exit 0
}

if ($Service) {
	exit (Invoke-Service $Service)
}

Ensure-StateDirectory
if (-not $KeepPrevious) {
	Stop-TrackedServices
}

$powershellPath = Get-ExecutablePath 'powershell.exe'
$wtPath = Get-ExecutablePath 'wt.exe'
$cmdPath = Get-ExecutablePath 'cmd.exe'
if ($Terminal -eq 'cmd') {
	$wtPath = $null
	$powershellPath = $null
}
elseif ($Terminal -eq 'powershell') {
	$wtPath = $null
}
elseif ($Terminal -eq 'wt' -and $null -eq $wtPath) {
	throw 'Windows Terminal (wt.exe) is not available.'
}

if ($null -ne $wtPath -and ($null -ne $powershellPath -or $null -ne $cmdPath)) {
	Write-Host 'Opening DataAgent frontend and backend in Windows Terminal tabs.'
	if ($null -ne $powershellPath) {
		Start-WithWindowsTerminal $wtPath $powershellPath 'powershell'
	}
	else {
		Start-WithWindowsTerminal $wtPath $cmdPath 'cmd'
	}
}
elseif ($null -ne $powershellPath) {
	Write-Host 'Windows Terminal is unavailable; opening separate PowerShell windows.'
	Start-WithSeparateWindows 'powershell' $powershellPath
}
else {
	if ($null -eq $cmdPath) {
		throw 'Neither Windows Terminal, PowerShell, nor cmd.exe is available.'
	}
	Write-Host 'PowerShell is unavailable; opening separate cmd windows.'
	Start-WithSeparateWindows 'cmd' $cmdPath
}

