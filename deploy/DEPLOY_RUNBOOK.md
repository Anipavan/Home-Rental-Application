# Deployment Runbook — Anirudh Homes

End-to-end runbook for taking a fresh DigitalOcean droplet to a
running `https://anirudhhomes.in`. Designed to be **followed top to
bottom in one sitting**. Each block is a single paste into your SSH
session.

**Target architecture** (decided in the rebrand chat):
- DO droplet — Ubuntu 24.04, 8 GB / 4 vCPU
- Caddy reverse-proxy (TLS via Let's Encrypt)
- Oracle XE + Kafka on droplet
- MongoDB Atlas (managed, free tier)
- Resend (transactional email)
- Manual deploy: `git pull && docker compose up -d`

---

## Phase 0 — Pre-flight (do this BEFORE touching the droplet)

These take 15-30 min total. None of them require the droplet.

### 0.1 Resize the droplet to 8 GB
DigitalOcean control panel → your droplet → **Resize** → 8 GB / 4 vCPU
($48/mo). Choose "CPU and RAM only" (data preserved). Takes ~2 min,
needs a power-off. Power back on after.

### 0.2 Sign up for Resend
1. https://resend.com → **Sign up**.
2. **Domains → Add Domain** → `anirudhhomes.in`.
3. Resend will show you 2 DNS records (`MX send` + `TXT resend._domainkey`).
   Note them — you'll add them in step 0.4.
4. **API Keys → Create API Key** with `sending_access` scope. Copy the
   `re_...` key. You'll paste it into `.env` later.

### 0.3 Sign up for MongoDB Atlas
1. https://cloud.mongodb.com → **Sign up**.
2. **Build a Database → Free / M0** → AWS, region `ap-south-1`
   (Mumbai, lowest latency to a Bangalore-region droplet).
3. **Database Access → Add user**:
   - Username: `anirudhhomes`
   - Password: generate one (Atlas's button is fine). Copy it.
4. **Network Access → Add IP Address** → paste your droplet's public
   IPv4. Don't use `0.0.0.0/0` — the connection string is the only
   thing standing between Atlas and the internet.
5. **Database → Connect → Drivers → Java** → copy the
   `mongodb+srv://...` URL. Replace `<password>` with the real
   password. Replace the database name at the end (`/test`?) with
   `hra_notifications`. Final form:
   ```
   mongodb+srv://anirudhhomes:<password>@cluster0.xxxx.mongodb.net/hra_notifications?retryWrites=true&w=majority
   ```

### 0.4 Add DNS records at your registrar
The registrar for `anirudhhomes.in`. Use the table in
[`../DNS_SETUP.md`](../DNS_SETUP.md). At minimum:

| Type | Host | Value | Purpose |
|------|------|-------|---------|
| A | `@` | `<droplet IP>` | apex → droplet |
| A | `www` | `<droplet IP>` | www → droplet |
| MX | `send` | `feedback-smtp.us-east-1.amazonses.com.` (priority 10) | Resend outbound |
| TXT | `send` | (Resend SPF — copy from Resend console) | Resend SPF |
| TXT | `resend._domainkey` | (Resend DKIM — copy from Resend console) | Resend DKIM |
| TXT | `@` | `v=spf1 include:_spf.resend.com ~all` | SPF |
| TXT | `_dmarc` | `v=DMARC1; p=quarantine; rua=mailto:support@anirudhhomes.in; adkim=s; aspf=s` | DMARC |

> Propagation is usually 5-15 min but can take up to an hour. You
> can start the rest of the deploy while DNS settles — TLS issuance
> in Phase 3 is the only step that hard-requires DNS to resolve.

### 0.5 Confirm SSH-key login works
DO droplets ship with SSH-key auth by default. From your laptop:
```bash
ssh root@<droplet-ip> "echo ok"
# → ok
```
If that prints `ok`, the runbook can harden SSH later. If it prompts
for a password, set up keys first (see DO docs) — otherwise the
bootstrap script will skip SSH hardening to avoid locking you out.

---

## Phase 1 — Bootstrap the droplet (5 min)

SSH in as root and run the bootstrap script. It:
- checks RAM is ≥ 6 GB
- disables the host's pre-installed nginx (Caddy owns 80/443)
- creates 4 GB swap (Oracle XE refuses to boot without it)
- installs git + ufw + fail2ban + unattended-upgrades
- opens firewall ports 22 / 80 / 443
- hardens SSH

```bash
ssh root@<droplet-ip>

# Pull just the bootstrap script (repo not cloned yet)
curl -O https://raw.githubusercontent.com/Anipavan/Home-Rental-Application/master/deploy/bootstrap-droplet.sh
chmod +x bootstrap-droplet.sh
./bootstrap-droplet.sh
```

Expected output ends with a green "Done. Next steps:" block.

---

## Phase 2 — Clone repo, generate secrets, fill `.env` (10 min)

Still on the droplet, as root:

### 2.1 Clone
```bash
cd /opt
git clone https://github.com/Anipavan/Home-Rental-Application.git anirudhhomes
cd anirudhhomes
```

### 2.2 Generate cryptographic secrets
```bash
./deploy/generate-secrets.sh > .env.secrets
cat .env.secrets   # review
```

Output has 6 generated values: `JWT_SECRET`, `INTERNAL_AUTH_SECRET`,
`PII_ENCRYPTION_KEY`, `DOWNLOAD_URL_SECRET`, `EUREKA_PASSWORD`,
`DB_PASSWORD`. Keep this file secure — losing it means rotating
every secret.

### 2.3 Build `.env`
```bash
cp .env.example .env
nano .env
```

In `nano`, set these values (paste from `.env.secrets`, Resend,
Atlas, Razorpay):

```env
SPRING_PROFILES_ACTIVE=prod

# ── From .env.secrets ────────────────────────────────────────
JWT_SECRET=<paste from .env.secrets>
INTERNAL_AUTH_SECRET=<paste from .env.secrets>
PII_ENCRYPTION_KEY=<paste from .env.secrets>
DOWNLOAD_URL_SECRET=<paste from .env.secrets>
EUREKA_PASSWORD=<paste from .env.secrets>
DB_PASSWORD=<paste from .env.secrets>

# ── Oracle (running on droplet via compose) ───────────────────
DB_URL=jdbc:oracle:thin:@//oracle-db:1521/XEPDB1
DB_USERNAME=hra_app

# ── MongoDB Atlas (Phase 0.3) ─────────────────────────────────
SPRING_DATA_MONGODB_URI=<paste Atlas connection string>

# ── Kafka (running on droplet via compose) ────────────────────
KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# ── Eureka (running on droplet) ───────────────────────────────
EUREKA_URL=http://eureka:${EUREKA_PASSWORD}@service-registry:8761/eureka
EUREKA_USERNAME=eureka

# ── Email via Resend (Phase 0.2) ──────────────────────────────
SPRING_MAIL_HOST=smtp.resend.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=resend
SPRING_MAIL_PASSWORD=<paste Resend re_... API key>
APP_NOTIFICATION_FROM_EMAIL=support@anirudhhomes.in
APP_NOTIFICATION_FROM_NAME=Anirudh Homes

# ── CORS / Frontend URLs ──────────────────────────────────────
CORS_ALLOWED_ORIGINS=https://anirudhhomes.in,https://www.anirudhhomes.in
FRONTEND_URL=https://anirudhhomes.in
PUBLIC_BASE_URL=https://anirudhhomes.in/rentals/v1

# ── Razorpay (start with TEST keys, switch to live after smoke-test) ─
RAZORPAY_KEY_ID=rzp_test_<from razorpay dashboard>
RAZORPAY_KEY_SECRET=<from razorpay dashboard>
RAZORPAY_WEBHOOK_SECRET=<from razorpay dashboard>

# ── Twilio / Stripe — leave blank until you actually need SMS / intl payments ──
STRIPE_SECRET_KEY=
STRIPE_WEBHOOK_SECRET=
APP_TWILIO_ACCOUNT_SID=
APP_TWILIO_AUTH_TOKEN=
APP_TWILIO_SMS_FROM=
APP_TWILIO_WHATSAPP_FROM=

# ── Tracing / retention / auth-lockout — defaults are fine ───
OTEL_EXPORTER_OTLP_ENDPOINT=
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0.1
APP_RETENTION_SOFT_DELETE_DAYS=30
APP_RETENTION_ENABLED=true
APP_AUTH_LOCKOUT_ENABLED=true
INTERNAL_AUTH_ENABLED=true
```

Save (Ctrl+O, Enter, Ctrl+X). Verify no `CHANGE_ME_*` placeholders
remain:
```bash
grep CHANGE_ME .env || echo "all secrets set"
```

### 2.4 Lock down `.env`
```bash
chmod 600 .env .env.secrets
```

---

## Phase 3 — First boot (15-20 min cold start)

The first start builds 15 Docker images. Bake the kettle.

### 3.1 Build + start everything
```bash
cd /opt/anirudhhomes
docker compose --env-file .env \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  up -d --build
```

### 3.2 Watch the boot sequence
```bash
docker compose ps
```

Healthy sequence (roughly):
1. `service-registry` (Eureka) — ~30s
2. `config-server` — ~45s, depends on Eureka
3. `oracle-db` — 2-3 min, ALL JPA services wait
4. `kafka` — ~45s
5. `api-gateway` + 12 domain services — ~3-5 min total
6. `frontend` — instant once api-gateway is up
7. `caddy` — instant, then fetches TLS cert (~30s)

Tail logs for the slow ones if a service stays `Restarting`:
```bash
docker compose logs -f --tail=100 oracle-db
docker compose logs -f --tail=100 auth-service
```

### 3.3 Caddy fetched the certs?
```bash
docker compose logs caddy | grep -i "certificate"
# Expected: "certificate obtained successfully for anirudhhomes.in"
```

If you see "no such host" errors here, DNS isn't resolved yet. Wait,
then `docker compose restart caddy`.

### 3.4 Smoke-test
From your laptop:
```bash
# Gateway alive
curl -sf https://anirudhhomes.in/api/actuator/health/readiness
# → {"status":"UP"}

# SPA loads
curl -sI https://anirudhhomes.in | head -1
# → HTTP/2 200

# CORS rejects bad origins
curl -sI -H "Origin: https://evil.example" \
  https://anirudhhomes.in/api/rentals/v1/properties/flats \
  | grep -i "access-control-allow-origin"
# → (empty — no header returned for unallowed origins)
```

### 3.5 Send a test email
From the droplet:
```bash
docker compose exec auth-service curl -sf \
  http://localhost:8080/actuator/health
# → UP
```

Then trigger a registration in the SPA at `https://anirudhhomes.in/register`.
Check the inbox of the email you used — welcome email should land
within 30s. If it lands in spam, DMARC needs a couple of days to
warm up; switch DMARC `p=quarantine` → `p=none` for the first week
if you must.

---

## Phase 4 — Smoke-test on the live URL (10 min)

Open `https://anirudhhomes.in` in a fresh browser:

- [ ] SPA loads, logo says "Anirudh Homes"
- [ ] Sign-in page reachable at `/login`
- [ ] Register a tenant + an owner → both get welcome emails
- [ ] Owner adds a property → tenant sees it on `/browse`
- [ ] Tenant pays rent via Razorpay test card `4111 1111 1111 1111`
- [ ] Tenant downloads a receipt PDF — has "Anirudh Homes" footer
- [ ] Tenant uses Contact Support → opens to `support@anirudhhomes.in`

---

## Phase 5 — Future updates (the steady-state)

Every time you ship code from your laptop:

```bash
# laptop:
git push origin master                       # CI builds + runs tests

# droplet (ssh root@<droplet-ip>):
cd /opt/anirudhhomes
git pull
docker compose --env-file .env \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  up -d --build
```

Compose only rebuilds + restarts services whose image changed. A
typical pull-and-restart hits 1-3 services and takes 30-90s.

---

## Phase 6 — Operations runbook

### Tail logs across all services
```bash
docker compose logs -f --tail=50
```

### A single service
```bash
docker compose logs -f --tail=200 payment-service
```

### Restart one service
```bash
docker compose restart notification-service
```

### Get a shell in a container
```bash
docker compose exec auth-service bash
```

### Backup Oracle (run nightly via cron)
```bash
docker compose exec -T oracle-db sqlplus \
  hra_app/${DB_PASSWORD}@localhost:1521/XEPDB1 \
  @/opt/scripts/dump.sql > /opt/backups/hra-$(date +%Y%m%d).sql
```
(There's no `dump.sql` shipped yet — set this up when you have real
data to lose.)

### Check disk usage
Oracle's data file + Kafka logs grow without bound.
```bash
df -h /var/lib/docker
docker system df
docker system prune -af --volumes   # ONLY when you don't care about
                                     # the named volumes — destructive!
```

### Rotate JWT_SECRET (forces logout of every user)
```bash
NEW=$(openssl rand -base64 32)
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$NEW|" .env
docker compose --env-file .env up -d auth-service api-gateway
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `oracle-db` stuck `Restarting` | Out of memory / no swap | Re-run `bootstrap-droplet.sh`; check `free -h` shows swap |
| `caddy` log: "no such host" | DNS not propagated yet | Wait 15 min, `docker compose restart caddy` |
| `caddy` log: "rate limit exceeded" | Too many cert re-issues this week | Wait until the 7-day window resets; do NOT delete `caddy_data` |
| Welcome email never arrives | Resend domain not verified | Check Resend console → Domains → status should be "Verified" |
| `502 Bad Gateway` on `/api/` | api-gateway crashed or not yet healthy | `docker compose logs api-gateway` — usually waiting on oracle-db |
| Browser sees `Mixed Content` errors | Some component is hardcoded `http://` | Grep the frontend bundle: `docker compose exec frontend grep -ri "http://" /usr/share/nginx/html` |

---

## Acceptance criteria (Phase 4 + 6 done means…)

- [ ] `https://anirudhhomes.in` loads the SPA, browser shows the padlock
- [ ] `https://anirudhhomes.in/api/actuator/health/readiness` → `{"status":"UP"}`
- [ ] Registration sends a welcome email that lands in inbox (not spam)
- [ ] A test rent payment with `4111 1111 1111 1111` settles
- [ ] DMARC report (after 24h) shows `dkim=pass` + `spf=pass`
- [ ] `docker compose ps` shows 17 services all `Up`
- [ ] `free -h` shows used RAM < 7 GB (some headroom for spikes)
- [ ] `df -h` shows root disk < 50% full

If all eight check, you're shipped. Move to https://www.mail-tester.com
to verify the email score is 9/10 or higher before flipping DMARC
from `p=quarantine` to `p=reject`.
