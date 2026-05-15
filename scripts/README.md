# scripts/

Convenience helpers for the dev + ops loop. Nothing in here is on the
runtime path — everything is operator-facing.

## Local stack

| Script | What it does |
|--------|--------------|
| `start-all.bat` | Bring the dev compose stack up in the right order: Eureka → config server → infra (Oracle, Mongo, Kafka, Zookeeper) → application services → frontend. |
| `stop-all.bat`  | Tear the same stack down cleanly (`docker compose down` with volumes left intact). |

Run from the repo root:

```powershell
.\scripts\start-all.bat       # boot
.\scripts\stop-all.bat        # shutdown
```

Both scripts are PowerShell / cmd-friendly so they work from the
default Windows shell that the project's been developed on. Linux /
WSL operators should `docker compose up -d` directly.

## Seed data

| Script | What it does |
|--------|--------------|
| `seed-demo-data.ps1` | Idempotent demo-data seeder. Hits the live API endpoints to register an admin + 2 owners + 5 tenants, creates 2 buildings with 10 flats, assigns some, generates rent invoices, leaves a mix of paid / pending / overdue so the analytics dashboard has real-looking data. Safe to re-run; existing rows are skipped via the same idempotency keys the production write paths use. |

```powershell
.\scripts\seed-demo-data.ps1
```

Requires the stack to already be running (`start-all.bat` first).

## Production deploys

For the prod deploy procedure see the **Production deployment**
section in the repo root `README.md`. Short version:

```bash
cp .env.example .env       # fill in real secrets
docker compose --env-file .env \
               -f docker-compose.yml \
               -f docker-compose.prod.yml up -d
```

The `docker-compose.prod.yml` overlay is what turns the dev compose
file into a production-shaped stack — see its header comment for
the diff it applies.
