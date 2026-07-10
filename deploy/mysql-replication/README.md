# MySQL replication local demonstration

This directory starts two disposable MySQL 8.4 containers so you can verify
GTID replication locally:

- `mysql-source` accepts application writes.
- `mysql-replica` is restricted to `campus_pulse.%` and starts with both
  `read_only` and `super_read_only` enabled.
- Host ports bind to `127.0.0.1`; they are not exposed to the LAN.

> This is a **local demonstration**, not the production topology. The bootstrap
> scripts deliberately drop and re-seed `campus_pulse` on the demo replica and
> reset that replica's GTID history. Never point these scripts at a production
> database or reuse their Docker volumes as production storage.

## Start the demonstration

Docker Compose v2 is required. Create the ignored local environment file and
replace every placeholder with a unique value:

```powershell
Set-Location deploy/mysql-replication
Copy-Item .env.example .env
notepad .env
./bootstrap-replica.ps1
```

On Linux or macOS:

```bash
cd deploy/mysql-replication
cp .env.example .env
${EDITOR:-vi} .env
bash ./bootstrap-replica.sh
```

All four credential fields (two root passwords plus the replication username
and password) are mandatory. Use a dedicated non-root replication user and
passwords of at least 16 characters. `.env` is ignored by Git; do not commit
it. If a value contains characters interpreted by dotenv syntax, quote the
complete value in `.env`.

Both scripts perform the same sequence:

1. Validate Compose interpolation and credentials without printing secrets.
2. Wait for both MySQL health checks.
3. Create or rotate the dedicated replication account.
4. Take a consistent `campus_pulse` GTID snapshot.
5. Re-seed the demo replica and enable GTID auto-positioning.
6. Restore strict read-only mode and verify both replication threads.
7. Remove the snapshot from the private exchange volume even when a step fails.

Passwords are read from the containers' environment. The scripts do not put
password values in `docker`, `mysql`, or `mysqladmin` command-line arguments.

## Inspect and test

From this directory, open an interactive client. `-p` prompts for the matching
root password from your private `.env` file:

```bash
docker compose exec mysql-source mysql --user=root -p
docker compose exec mysql-replica mysql --user=root -p
```

Write a test row on the source, then read it from the replica. Check status on
the replica with:

```sql
SHOW REPLICA STATUS\G
SELECT @@GLOBAL.read_only, @@GLOBAL.super_read_only;
SELECT @@GLOBAL.replicate_wild_do_table;
```

Expected values include `Replica_IO_Running: Yes`,
`Replica_SQL_Running: Yes`, both read-only flags equal to `1`, and a filter of
`campus_pulse.%`.

Stop the demo while keeping its volumes:

```bash
docker compose down
```

Deleting volumes destroys both demo databases and must be an explicit choice:

```bash
docker compose down --volumes
```

## Production boundary

A production deployment uses an existing source and an independently managed
replica, normally on different hosts or failure domains over a private network:

```text
web/API writes -> production MySQL source
                         |
                         | GTID/binlog (private network)
                         v
Agent/search reads -> production MySQL replica
```

For production, take and test a backup first, restrict the source firewall to
the replica host, scope the replication account to that host, monitor lag and
both replication threads, and keep `read_only`, `super_read_only`, and
`relay_log_recovery` enabled on the replica. Give Agent a separate
`SELECT`/`SHOW VIEW` account; never let Agent use the root or replication
account. See [the production guide](../../docs/MYSQL_READ_REPLICA_GUIDE.md) for
the manual topology and operational checks.
