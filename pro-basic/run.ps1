param(
    [Parameter(Position = 0)]
    [ValidateSet('build', 'test', 'server', 'client')]
    [string]$Mode = 'build',
    [string]$ZkAddress = '127.0.0.1:2181',
    [int]$Port = 8888,
    [string]$AdvertiseHost = '127.0.0.1'
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$workspace = $PSScriptRoot
$repo = Join-Path $workspace '.build/m2'

if ($Mode -in 'build', 'test') {
    $goal = if ($Mode -eq 'build') { 'verify' } else { 'test' }
    # All Maven clean targets are the target directory of these five projects.
    foreach ($project in 'pro-basic','pro-netty-consumer','pro-netty-rpc','pro-user-api','pro-user-core') {
        $root = [IO.Path]::GetFullPath((Join-Path $workspace "../$project"))
        $target = [IO.Path]::GetFullPath((Join-Path $root 'target'))
        if (-not $target.StartsWith($root + [IO.Path]::DirectorySeparatorChar)) { throw 'Invalid build target' }
    }
    & mvn -B -f (Join-Path $workspace 'build/pom.xml') "-Dmaven.repo.local=$repo" clean $goal
    if ($LASTEXITCODE -ne 0) { throw 'Build failed. See the Maven output above.' }
    exit 0
}

$project = if ($Mode -eq 'server') { 'pro-user-core' } else { 'pro-basic' }
$root = [IO.Path]::GetFullPath((Join-Path $workspace "../$project"))
$jar = Join-Path $root "target/$project-1.0-SNAPSHOT.jar"
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build first: .\run.ps1 build' }
$classpath = $jar + ';' + (Join-Path $root 'target/dependency/*')
$main = if ($Mode -eq 'server') { 'com.xtwy.server.SpringServer' } else { 'com.xtwy.basic.controller.BasicController' }
& java "-Dfile.encoding=UTF-8" "-Drpc.zk.address=$ZkAddress" "-Drpc.server.port=$Port" "-Drpc.server.host=$AdvertiseHost" -cp $classpath $main
if ($LASTEXITCODE -ne 0) { throw "$Mode exited with code $LASTEXITCODE" }
