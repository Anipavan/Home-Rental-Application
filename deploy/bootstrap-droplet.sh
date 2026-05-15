#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════
#  One-shot droplet bootstrap for Anirudh Homes.
#
#  Assumes a fresh DigitalOcean Ubuntu 24.04 droplet (8 GB / 4 vCPU)
#  with the "Docker Compose" + "nginx" marketplace add-ons installed.
#
#  What this script does (in order):
#    1. Tells you upfront if RAM is below the safe floor.
#    2. Disables the pre-installed host nginx — Caddy will own 80/443.
#    3. Adds 4 GB of swap. Oracle XE refuses to boot without it, and
#       it cushions JVM headroom during compose's parallel boot.
#    4. Installs git + ufw + fail2ban + unattended-upgrades.
#    5. Configures UFW to only allow 22 / 80 / 443 (the SSH port is
#       kept open by default; if you changed it, edit ALLOWED_SSH).
#    6. Hardens SSH (no root login, no password auth — only if you
#       already have key-based access set up).
#    7. Schedules unattended security upgrades.
#    8. Prints a "next steps" cheatsheet.
#
#  Run as root (DO droplets give you a root login by default):
#    curl -O https://raw.githubusercontent.com/<your-fork>/Home-Rental-Application/master/deploy/bootstrap-droplet.sh
#    chmod +x bootstrap-droplet.sh
#    ./bootstrap-droplet.sh
#
#  The script is IDEMPOTENT — re-running it is safe. Each step
#  checks state before mutating.
# ════════════════════════════════════════════════════════════════════

set -euo pipefail

ALLOWED_SSH=22
SWAP_SIZE_MB=4096
SWAP_FILE=/swapfile

red()   { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
blue()  { printf '\033[34m%s\033[0m\n' "$*"; }

require_root() {
    if [[ $EUID -ne 0 ]]; then
        red "Must run as root. Try: sudo $0"
        exit 1
    fi
}

# ─── 1. RAM sanity check ────────────────────────────────────────────
check_ram() {
    blue "[1/8] Checking RAM…"
    local ram_kb
    ram_kb=$(awk '/MemTotal/ {print $2}' /proc/meminfo)
    local ram_gb=$((ram_kb / 1024 / 1024))
    if (( ram_gb < 6 )); then
        red "    ⚠ Droplet has ${ram_gb} GB RAM — Oracle + Kafka + 15 services need >= 8 GB."
        red "    Resize the droplet first, then re-run this script."
        red "    On DigitalOcean: Resources → Resize → 8 GB / 4 vCPU."
        exit 1
    fi
    green "    ✓ RAM: ${ram_gb} GB (>= 6 GB minimum)"
}

# ─── 2. Disable host nginx ──────────────────────────────────────────
# DO's "nginx" marketplace add-on installs nginx on the host. Caddy
# (in a container) is going to own 80/443 — we need to free those
# ports.
disable_host_nginx() {
    blue "[2/8] Disabling host nginx (Caddy will own 80/443)…"
    if systemctl is-active --quiet nginx; then
        systemctl stop nginx
        systemctl disable nginx
        green "    ✓ Stopped + disabled nginx service"
    else
        green "    ✓ nginx already inactive"
    fi
}

# ─── 3. Set up swap ─────────────────────────────────────────────────
# Oracle XE refuses to start without swap. Without it, the boot
# sequence hangs at "Starting Oracle Net Listener…" forever.
setup_swap() {
    blue "[3/8] Setting up ${SWAP_SIZE_MB} MB swap…"
    if swapon --show | grep -q "${SWAP_FILE}"; then
        green "    ✓ Swap already enabled at ${SWAP_FILE}"
        return
    fi
    if [[ -f ${SWAP_FILE} ]]; then
        rm -f "${SWAP_FILE}"
    fi
    fallocate -l "${SWAP_SIZE_MB}M" "${SWAP_FILE}"
    chmod 600 "${SWAP_FILE}"
    mkswap "${SWAP_FILE}" >/dev/null
    swapon "${SWAP_FILE}"
    if ! grep -q "${SWAP_FILE}" /etc/fstab; then
        echo "${SWAP_FILE} none swap sw 0 0" >> /etc/fstab
    fi
    # Lower swappiness — we want swap as a safety net, not a hot path.
    sysctl vm.swappiness=10 >/dev/null
    echo "vm.swappiness=10" > /etc/sysctl.d/99-swappiness.conf
    green "    ✓ ${SWAP_SIZE_MB} MB swap online, swappiness=10"
}

# ─── 4. Install OS packages ────────────────────────────────────────
install_packages() {
    blue "[4/8] Installing git + ufw + fail2ban + unattended-upgrades…"
    apt-get update -y >/dev/null
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
        git ufw fail2ban unattended-upgrades >/dev/null
    green "    ✓ Base packages installed"
}

# ─── 5. Configure firewall ─────────────────────────────────────────
configure_firewall() {
    blue "[5/8] Configuring UFW (only ${ALLOWED_SSH}/22 + 80 + 443 open)…"
    ufw --force reset >/dev/null
    ufw default deny incoming >/dev/null
    ufw default allow outgoing >/dev/null
    ufw allow "${ALLOWED_SSH}/tcp" >/dev/null
    ufw allow 80/tcp >/dev/null
    ufw allow 443/tcp >/dev/null
    ufw --force enable >/dev/null
    green "    ✓ UFW active: SSH + HTTP + HTTPS only"
}

# ─── 6. Harden SSH ──────────────────────────────────────────────────
# Only run this AFTER you've confirmed you can log in with a key
# (DO offers SSH-key login by default). If you're on password auth,
# skip this — it would lock you out.
harden_ssh() {
    blue "[6/8] Hardening SSH config…"
    local sshd=/etc/ssh/sshd_config

    if grep -q "^PasswordAuthentication yes" "${sshd}"; then
        red "    ⚠ Password auth is still on. Set up SSH keys FIRST,"
        red "      then re-run this script. Skipping SSH hardening."
        return
    fi

    # Idempotent edits: only flip a setting if it's not already set.
    grep -q "^PermitRootLogin"          "${sshd}" || echo "PermitRootLogin prohibit-password" >> "${sshd}"
    grep -q "^PasswordAuthentication"   "${sshd}" || echo "PasswordAuthentication no"         >> "${sshd}"
    grep -q "^X11Forwarding"            "${sshd}" || echo "X11Forwarding no"                  >> "${sshd}"
    grep -q "^MaxAuthTries"             "${sshd}" || echo "MaxAuthTries 3"                    >> "${sshd}"

    systemctl reload ssh
    green "    ✓ SSH hardened (root login: key-only, no passwords, max 3 tries)"
}

# ─── 7. Enable unattended security upgrades ─────────────────────────
enable_unattended_upgrades() {
    blue "[7/8] Enabling unattended security upgrades…"
    cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
APT::Periodic::AutocleanInterval "7";
EOF
    systemctl enable --now unattended-upgrades >/dev/null 2>&1 || true
    green "    ✓ Security patches will install daily without reboot"
}

# ─── 8. Print next steps ────────────────────────────────────────────
print_next_steps() {
    blue "[8/8] Done. Next steps:"
    cat <<'EOF'

  ┌─────────────────────────────────────────────────────────────┐
  │  Droplet base setup complete. Now deploy the app:           │
  │                                                             │
  │  1. Clone the repo:                                         │
  │       cd /opt                                               │
  │       git clone https://github.com/Anipavan/Home-Rental-Application.git anirudhhomes
  │       cd anirudhhomes                                       │
  │                                                             │
  │  2. Generate cryptographic secrets:                         │
  │       ./deploy/generate-secrets.sh > .env.secrets           │
  │                                                             │
  │  3. Build .env from .env.example + .env.secrets +           │
  │     your Resend / Atlas / Razorpay credentials:             │
  │       cp .env.example .env                                  │
  │       nano .env   # paste in real values                    │
  │                                                             │
  │  4. Bring up the stack:                                     │
  │       docker compose --env-file .env \                      │
  │         -f docker-compose.yml \                             │
  │         -f docker-compose.prod.yml \                        │
  │         up -d --build                                       │
  │                                                             │
  │  5. Watch the boot sequence (takes ~5-8 min cold):          │
  │       docker compose logs -f api-gateway auth-service       │
  │                                                             │
  │  See deploy/DEPLOY_RUNBOOK.md for the full sequence.        │
  └─────────────────────────────────────────────────────────────┘

EOF
}

# ─── main ───────────────────────────────────────────────────────────
main() {
    require_root
    check_ram
    disable_host_nginx
    setup_swap
    install_packages
    configure_firewall
    harden_ssh
    enable_unattended_upgrades
    print_next_steps
}

main "$@"
