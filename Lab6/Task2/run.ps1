param([string[]]$ModelArgs = @())
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Path build -Force | Out-Null
    $sources = @(Get-ChildItem vendor/ua/stetsenkoinna/petriobj -Filter *.java | ForEach-Object { 'vendor/ua/stetsenkoinna/petriobj/' + $_.Name })
    $sources += @(Get-ChildItem src -Filter *.java | ForEach-Object { 'src/' + $_.Name })
    & javac -encoding UTF-8 -cp 'lib/*' -d build @sources
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
    & java -cp 'build;lib/*' Main @ModelArgs
    if ($LASTEXITCODE -ne 0) { throw 'Simulation or verification failed' }
} finally { Pop-Location }
