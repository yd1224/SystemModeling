param([string[]]$ModelArgs = @())
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    # Reuse the official PetriObj library and its JAR dependencies from Task2.
    if (!(Test-Path '../Task2/vendor/ua/stetsenkoinna/petriobj')) { throw 'Keep Task3 beside Task2: the shared PetriObj library is required.' }
    New-Item -ItemType Directory -Path build -Force | Out-Null
    $sources = @(Get-ChildItem ../Task2/vendor/ua/stetsenkoinna/petriobj -Filter *.java | ForEach-Object { '../Task2/vendor/ua/stetsenkoinna/petriobj/' + $_.Name })
    $sources += @(Get-ChildItem src/task3 -Filter *.java | ForEach-Object { 'src/task3/' + $_.Name })
    & javac -encoding UTF-8 -cp '../Task2/lib/*' -d build @sources
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
    & java -cp 'build;../Task2/lib/*' task3.Main @ModelArgs
    if ($LASTEXITCODE -ne 0) { throw 'Simulation or verification failed' }
} finally { Pop-Location }
