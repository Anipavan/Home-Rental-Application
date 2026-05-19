# Local Development

Quickstart for running the full Anirudh Homes stack on your laptop.

## TL;DR

```bash
# Terminal 1 — backend services + infra
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build

# Terminal 2 — frontend (Vite dev server)
cd frontend
npm install      # first time only
npm run dev

# Open
open http://localhost:4200
```

That's it. No env files, no extra setup. Defaults baked into
`docker-compose.yml` create a working Oracle user (`siva`/`Pavan@123`)
and the gateway already accepts CORS from `localhost:4200`.

---

## What runs where

| Component | Where | Port (host) | Access |
|---|---|---|---|
| Frontend (Vite) | Host machine | 4200 | http://localhost:4200 |
| API gateway | Docker | 8080 | http://localhost:8080 (Vite proxies /api → here) |
| Service registry (Eureka) | Docker | 8761 | http://localhost:8761 |
| Config server | Docker | 8888 | internal only |
| Auth service | Docker | 9090 | via gateway |
| User service | Docker | 8089 | via gateway |
| Property service | Docker | 8088 | via gateway |
| (other backend services) | Docker | various | via gateway |
| Oracle DB | Docker | **1522** | jdbc:oracle:thin:@localhost:**1522**/FREEPDB1 |
| MongoDB | Docker | **27018** | mongodb://localhost:**27018** |
| Kafka | Docker | **9093** | localhost:**9093** (PLAINTEXT) |
| Zookeeper | Docker | 2181 | internal only |

> **Note:** infrastructure host-ports are shifted by +1 in dev to avoid
> clashing with any locally-installed Oracle / MongoDB / Kafka.
> **Inside the docker network, services still talk to
> `oracle-db:1521`, `mongodb:27017`, and `kafka:9092`** — only the
> host-side mapping changes. SQL clients / Mongo Compass / kcat
> running on your host need to use the +1 ports.

## How the request flow works in dev

```
Browser → http://localhost:4200/api/rentals/v1/auth/login
              ↓
       Vite dev server (port 4200)
              ↓  proxies /api/* (strips /api prefix)
       http://localhost:8080/rentals/v1/auth/login
              ↓
       API gateway (Docker container)
              ↓  service discovery via Eureka
       lb://HRA-auth-service → auth-service container
              ↓
       Oracle (oracle-db:1521/FREEPDB1, user=siva)
```

The Vite proxy forges the `Origin` header to `http://localhost:4200`
so the gateway's CORS filter accepts every request — see
`frontend/vite.config.ts` for the exact `configure` hook.

## Database credentials (dev)

| User | Password | Purpose |
|---|---|---|
| `sys` | `Pavan@123` | DBA — connect with `AS SYSDBA` for schema work |
| `siva` | `Pavan@123` | Application user — what the Spring services use |

Pluggable DB name: **`FREEPDB1`** (Oracle Free 23.5 default — **not** `XEPDB1`).

Connect from a SQL client (DBeaver / SQL Developer):
- Host: `localhost`
- Port: **`1522`** (the container's exposed host port — see infra note above)
- **Service Name** (not SID): `FREEPDB1`
- User: `siva`
- Password: `Pavan@123`

## Common tasks

### Reset everything (DB included)

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml down -v
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
```

The `-v` flag deletes named volumes — wipes Oracle, Mongo, Kafka. Fresh start.

### Restart one service after a code change

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build user-service
```

### Tail logs

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml logs -f user-service
```

### Set up a shell alias (optional)

The double-compose flag is verbose. Save it once:

```bash
# bash / zsh on Linux/macOS / WSL
alias dc='docker compose -f docker-compose.yml -f docker-compose.dev.yml'
```

```powershell
# PowerShell
function dc { docker compose -f docker-compose.yml -f docker-compose.dev.yml @Args }
```

Then: `dc up -d --build`, `dc ps`, `dc logs -f api-gateway`.

## Troubleshooting

### `frontend can't reach backend` / 502 / network errors

1. Check the gateway is up:
   ```bash
   curl -i http://localhost:8080/actuator/health
   ```
   Expect `{"status":"UP"}`. If you get `connection refused`, the
   gateway container isn't running — `docker compose ps` and check.

2. Check Eureka shows all services registered:
   ```
   open http://localhost:8761
   ```
   You should see ~14 services listed under "Instances currently
   registered with Eureka". If only Eureka itself is there, the
   backend services haven't started — wait 60s and refresh, or
   `docker compose logs` to see what crashed.

3. Check Vite is actually proxying:
   ```bash
   curl -i http://localhost:4200/api/rentals/v1/auth/health
   ```
   Should return the same as `curl http://localhost:8080/rentals/v1/auth/health`.

### `ORA-12541: TNS:no listener` from a backend service

Oracle hasn't finished booting yet. The container reports healthy
when the listener is ready, but Spring services have `depends_on:
{ condition: service_healthy }` so they wait. If you still see this,
`docker compose logs oracle-db` to confirm `DATABASE IS READY TO USE!`
appeared. First boot takes 60-120 seconds.

### `ORA-01017: invalid username/password`

The Oracle container was previously created with a different password
(because volumes persist). Wipe and recreate:

```bash
docker compose down
docker volume rm anirudhhomes_oracle-data
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
```

### Custom credentials / non-default ports

Copy `.env.dev` to `.env` and edit. Docker Compose auto-loads `.env`
when no `--env-file` is passed. See comments inside `.env.dev` for
what each variable controls.

### Alternative: backend-in-Docker, frontend-on-Docker too

If you want EVERYTHING in Docker (no host-side `npm run dev`), use
the prod overlay — same compose layering as production:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

This builds the frontend into an nginx image and serves it through
Caddy at https://anirudhhomes.in (in prod). Locally, the Caddy step
expects a real domain + Let's Encrypt; for pure local you generally
want the dev flow above instead.

### Alternative: only-infra-in-Docker (run services in IntelliJ)

For breakpoint-driven debugging of a Spring service, use the
`docker-compose.dev.local.yml` standalone compose file — it spins up
only the infra (Eureka, config-server, Oracle, Kafka) so you can
launch services via IntelliJ run configs against `localhost`.
