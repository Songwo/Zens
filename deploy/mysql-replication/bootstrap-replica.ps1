$ErrorActionPreference = "Stop"

$composeFile = Join-Path $PSScriptRoot "docker-compose.yml"
$envFile = Join-Path $PSScriptRoot ".env"
$containerSeedFile = "/replication-seed/campus-pulse-replication-seed.sql"
$composePrefix = @(
    "compose",
    "--project-directory", $PSScriptRoot,
    "--env-file", $envFile,
    "--file", $composeFile
)

function Invoke-Compose {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $CommandArgs
    )

    & docker @composePrefix @CommandArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed with exit code $LASTEXITCODE."
    }
}

function Invoke-ComposeCleanup {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $CommandArgs
    )

    try {
        & docker @composePrefix @CommandArgs *> $null
    }
    catch {
        # Cleanup is best-effort and must not hide the original bootstrap error.
    }
}

if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw "Missing $envFile. Copy .env.example to .env and replace every placeholder."
}

try {
    Write-Host "1/7 Validate the local demonstration configuration..." -ForegroundColor Cyan
    Invoke-Compose -CommandArgs @("config", "--quiet")
    Invoke-Compose -CommandArgs @(
        "run", "--rm", "--no-deps", "--entrypoint", "bash", "mysql-source",
        "/opt/campus-replication/source/configure-replication-user.sh", "--validate-only"
    )
    Invoke-Compose -CommandArgs @(
        "run", "--rm", "--no-deps", "--entrypoint", "bash", "mysql-replica",
        "/opt/campus-replication/replica/manage-replica.sh", "--validate-only"
    )

    Write-Host "2/7 Start the source and replica containers..." -ForegroundColor Cyan
    Invoke-Compose -CommandArgs @("up", "--detach", "--wait", "--wait-timeout", "180")

    Write-Host "3/7 Create or rotate the dedicated replication user..." -ForegroundColor Cyan
    Invoke-Compose -CommandArgs @(
        "exec", "-T", "mysql-source", "bash",
        "/opt/campus-replication/source/configure-replication-user.sh"
    )

    Write-Host "4/7 Create a consistent GTID seed from campus_pulse..." -ForegroundColor Cyan
    Invoke-Compose -CommandArgs @(
        "exec", "-T", "mysql-source", "bash",
        "/opt/campus-replication/source/create-seed.sh"
    )

    Write-Host "5/7 Re-seed the local replica..." -ForegroundColor Cyan
    Invoke-Compose -CommandArgs @(
        "exec", "-T", "mysql-replica", "bash",
        "/opt/campus-replication/replica/manage-replica.sh", "prepare"
    )
    Invoke-Compose -CommandArgs @(
        "exec", "-T", "mysql-replica", "bash",
        "/opt/campus-replication/replica/manage-replica.sh", "import"
    )

    Write-Host "6/7 Configure GTID replication and restore strict read-only mode..." -ForegroundColor Cyan
    Invoke-Compose -CommandArgs @(
        "exec", "-T", "mysql-replica", "bash",
        "/opt/campus-replication/replica/manage-replica.sh", "configure"
    )

    Write-Host "7/7 Verify replication, read-only flags, and the database filter..." -ForegroundColor Cyan
    Invoke-Compose -CommandArgs @(
        "exec", "-T", "mysql-replica", "bash",
        "/opt/campus-replication/replica/manage-replica.sh", "verify"
    )
    Invoke-Compose -CommandArgs @(
        "exec", "-T", "mysql-replica", "bash",
        "/opt/campus-replication/replica/manage-replica.sh", "status"
    )

    Write-Host ""
    Write-Host "Local demonstration is ready. Connection ports are defined in $envFile." -ForegroundColor Green
    Write-Host "The replica accepts campus_pulse reads only; keep all writes on the source." -ForegroundColor Yellow
}
finally {
    if (Test-Path -LiteralPath $envFile -PathType Leaf) {
        Invoke-ComposeCleanup -CommandArgs @(
            "exec", "-T", "mysql-replica", "bash",
            "/opt/campus-replication/replica/manage-replica.sh", "read-only"
        )
        Invoke-ComposeCleanup -CommandArgs @(
            "exec", "-T", "mysql-source", "rm", "-f", "--", $containerSeedFile
        )
        Invoke-ComposeCleanup -CommandArgs @(
            "exec", "-T", "mysql-replica", "rm", "-f", "--", $containerSeedFile
        )
    }
}
