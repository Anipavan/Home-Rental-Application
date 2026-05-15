# DNS Setup — `anirudhhomes.in`

This document lists the exact DNS records to add at the registrar
(GoDaddy / Namecheap / wherever `anirudhhomes.in` was purchased)
to make the production deployment reachable AND let
`support@anirudhhomes.in` send + receive transactional email.

Skip nothing. SPF/DKIM/DMARC are the difference between "email
lands in inbox" and "email lands in spam (or bounces)".

> **Quick path for the current deploy (Resend chosen):** §1
> (A records) + §3 Option B (Resend) + §4 (SPF, the Resend line) +
> §5 (DMARC). The other options in §2/§3 are kept as reference for
> if you ever swap providers. The mapping is also summarized at the
> top of `deploy/DEPLOY_RUNBOOK.md` Phase 0.4.

---

## 1. Web traffic — point the domain at the droplet

You need TWO records: the apex (`anirudhhomes.in`) and the
`www.` subdomain. Replace `<DROPLET_IP>` with the IPv4 address
of your DigitalOcean droplet.

| Type    | Host / Name | Value                        | TTL    |
|---------|-------------|------------------------------|--------|
| `A`     | `@`         | `<DROPLET_IP>`               | 3600   |
| `A`     | `www`       | `<DROPLET_IP>`               | 3600   |

> Some registrars don't accept `@` as the apex host — use blank,
> `anirudhhomes.in`, or whatever the UI labels as "root". Same
> meaning.

If you prefer `www` as a CNAME (cleaner, only one A record to
update on IP change), use:

| Type    | Host / Name | Value               | TTL    |
|---------|-------------|---------------------|--------|
| `A`     | `@`         | `<DROPLET_IP>`      | 3600   |
| `CNAME` | `www`       | `anirudhhomes.in.`  | 3600   |

> Some registrars block CNAME at the apex. Don't try that — use
> an A record at `@` like above.

After this resolves (give it 5–15 min, sometimes up to an hour),
`http://anirudhhomes.in` should reach the nginx container in
`docker-compose.prod.yml`. TLS via Let's Encrypt is a separate
step — see the deployment runbook.

---

## 2. Email — receiving on `support@anirudhhomes.in`

Pick **one** mail-receiving provider. Easiest options:

### Option A — Zoho Mail Free (recommended for low volume)

Free for up to 5 mailboxes on a custom domain. Add these MX
records:

| Type | Host / Name | Value               | Priority | TTL  |
|------|-------------|---------------------|----------|------|
| `MX` | `@`         | `mx.zoho.in`        | 10       | 3600 |
| `MX` | `@`         | `mx2.zoho.in`       | 20       | 3600 |
| `MX` | `@`         | `mx3.zoho.in`       | 50       | 3600 |

Then verify the domain in the Zoho admin console (they'll give
you a `zb*` TXT record to add temporarily).

### Option B — Google Workspace (paid, ~₹136/user/month)

| Type | Host / Name | Value                       | Priority |
|------|-------------|-----------------------------|----------|
| `MX` | `@`         | `smtp.google.com.`          | 1        |

Google now uses a single MX. Older "ASPMX.L.GOOGLE.COM" records
still work but the single-host setup is preferred.

---

## 3. Email — sending transactional email

This is the path used by `notification-service`. Pick **one**
sending provider — same three good options on the cost/setup
trade-off:

### Option A — AWS SES (cheapest at scale)

- ₹0.10 per 1000 emails. Cheapest for >5k emails/month.
- Region: `ap-south-1` (Mumbai).
- Setup: verify the domain in SES console; AWS gives you 3
  CNAME records to add for DKIM:

| Type    | Host / Name                  | Value                                       |
|---------|------------------------------|---------------------------------------------|
| `CNAME` | `<token1>._domainkey`        | `<token1>.dkim.amazonses.com`               |
| `CNAME` | `<token2>._domainkey`        | `<token2>.dkim.amazonses.com`               |
| `CNAME` | `<token3>._domainkey`        | `<token3>.dkim.amazonses.com`               |

- AWS will also ask for a TXT verification record. Add whichever
  one the console shows.

`.env` values:
```
SPRING_MAIL_HOST=email-smtp.ap-south-1.amazonaws.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<SES SMTP username>
SPRING_MAIL_PASSWORD=<SES SMTP password>
```

### Option B — Resend (easiest setup, generous free tier)

- 100 emails/day free, ₹3500/month for 50k. Best DX.
- Setup: add domain in Resend console; it gives you 2 records:

| Type    | Host / Name           | Value                                     |
|---------|-----------------------|-------------------------------------------|
| `MX`    | `send`                | `feedback-smtp.us-east-1.amazonses.com.`  |
| `TXT`   | `resend._domainkey`   | `p=<long key from Resend console>`        |

`.env` values:
```
SPRING_MAIL_HOST=smtp.resend.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=resend
SPRING_MAIL_PASSWORD=<API key>
```

### Option C — Zoho Mail's outbound SMTP

If you already chose Zoho for receiving, you can send through
it too — no extra DNS setup beyond #2 above (Zoho's DKIM is
already signed under their MX).

`.env` values:
```
SPRING_MAIL_HOST=smtp.zoho.in
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=support@anirudhhomes.in
SPRING_MAIL_PASSWORD=<Zoho app password — not your login password>
```

---

## 4. SPF — declare which servers are allowed to send

This is a TXT record at the apex. It tells receiving servers
which hosts may legitimately send mail "from" `@anirudhhomes.in`.

Pick the line that matches your sender(s). If you're using
multiple providers (e.g. Zoho for inbox + SES for transactional),
include both.

| Type | Host / Name | Value                                                         |
|------|-------------|---------------------------------------------------------------|
| `TXT`| `@`         | `v=spf1 include:zoho.in ~all`                                 |
| `TXT`| `@`         | `v=spf1 include:amazonses.com ~all`                           |
| `TXT`| `@`         | `v=spf1 include:_spf.resend.com ~all`                         |
| `TXT`| `@`         | `v=spf1 include:zoho.in include:amazonses.com ~all`           |

> **Only one SPF record per domain.** Adding two is a hard fail
> per RFC 7208 — receivers will reject all mail. Merge providers
> into a single record as shown in the last row.

The `~all` (soft-fail) is correct during rollout. Switch to
`-all` (hard-fail) once you've verified delivery is clean.

---

## 5. DMARC — tell receivers what to do with failures

DMARC builds on SPF + DKIM. Set it to `quarantine` (spam folder
on fail) on rollout, then upgrade to `reject` (drop entirely)
once you've confirmed legitimate mail isn't being caught.

| Type  | Host / Name | Value                                                                                          |
|-------|-------------|------------------------------------------------------------------------------------------------|
| `TXT` | `_dmarc`    | `v=DMARC1; p=quarantine; rua=mailto:support@anirudhhomes.in; ruf=mailto:support@anirudhhomes.in; adkim=s; aspf=s` |

- `p=quarantine` — failing mail → spam folder. Use `p=none` for
  the first week if you want to monitor without affecting
  delivery, then ratchet up.
- `rua` / `ruf` — aggregate + forensic reports come to your
  inbox. Free DMARC parsers (dmarcian, postmarkapp) ingest these
  reports and visualise failures.
- `adkim=s; aspf=s` — strict alignment. The signing domain and
  return-path domain must match `anirudhhomes.in` exactly, not
  just be in the same org. Stricter = better for protection
  against lookalike-domain spoofing.

---

## 6. Optional — MTA-STS + TLS-RPT (modern email hardening)

Once email is flowing cleanly, add MTA-STS to enforce TLS on
inbound mail. This is a TXT record + a hosted policy file at
`mta-sts.anirudhhomes.in/.well-known/mta-sts.txt`. Skip on the
first deploy; add later when you have time.

---

## 7. Verifying the whole chain

After all records propagate, run these checks. Each one should
PASS before you start sending real transactional email at
volume.

```bash
# 1. Apex resolves to the droplet
dig +short anirudhhomes.in
# → <DROPLET_IP>

# 2. www resolves too
dig +short www.anirudhhomes.in
# → <DROPLET_IP>

# 3. MX is set
dig +short MX anirudhhomes.in
# → 10 mx.zoho.in. (or wherever)

# 4. SPF is one record only
dig +short TXT anirudhhomes.in | grep -i spf
# → "v=spf1 include:... ~all"   (exactly one line)

# 5. DMARC is in place
dig +short TXT _dmarc.anirudhhomes.in
# → "v=DMARC1; p=quarantine; ..."

# 6. DKIM signs (test by sending mail and inspecting headers
#    — look for "dkim=pass" in Authentication-Results)
```

Or use the web tools:
- https://mxtoolbox.com/SuperTool.aspx — paste `anirudhhomes.in`
- https://www.mail-tester.com/ — sends a real test, scores 0–10

A 9+/10 on mail-tester is the bar before going live.

---

## 8. Mapping to `.env`

These are the values to plug into the `.env` file (template at
`.env.example`):

```env
# Web
CORS_ALLOWED_ORIGINS=https://anirudhhomes.in,https://www.anirudhhomes.in
FRONTEND_URL=https://anirudhhomes.in
PUBLIC_BASE_URL=https://anirudhhomes.in/rentals/v1

# Email sender
APP_NOTIFICATION_FROM_EMAIL=support@anirudhhomes.in
APP_NOTIFICATION_FROM_NAME=Anirudh Homes

# SMTP (pick the provider you set up in §3)
SPRING_MAIL_HOST=email-smtp.ap-south-1.amazonaws.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<from provider>
SPRING_MAIL_PASSWORD=<from provider>
```

The `notification-service` will refuse to send (and silently
fall back to log-only) if SMTP credentials are blank, so once
the records propagate and you fill these in, transactional
email starts flowing.
