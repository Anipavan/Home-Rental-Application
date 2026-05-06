#!/usr/bin/env node
/**
 * Seeds the demo: buildings + flats + tenant assignments + payments + maintenance.
 *
 * Logs in as the auto-seeded users (created by auth-service's DemoDataSeeder),
 * then creates everything those services don't auto-seed for the actual
 * authUserIds the users were issued. Idempotent on owners — if Alice already
 * has buildings, none are created again.
 *
 * Requires Node 18+ (uses global fetch).
 *
 *   cd frontend
 *   npm run seed
 *
 * Override the gateway URL with API_BASE_URL env var.
 */

const BASE = process.env.API_BASE_URL || "http://localhost:8080/rentals/v1";
const PASSWORD = "Password1";

// ── tiny utils ─────────────────────────────────────────────────────────────

const C = {
  reset: "\x1b[0m", dim: "\x1b[2m", green: "\x1b[32m", red: "\x1b[31m",
  yellow: "\x1b[33m", cyan: "\x1b[36m", bold: "\x1b[1m",
};
const info = (m) => console.log(`${C.cyan}›${C.reset} ${m}`);
const ok   = (m) => console.log(`${C.green}✓${C.reset} ${m}`);
const warn = (m) => console.log(`${C.yellow}!${C.reset} ${m}`);

async function call(method, path, body, token) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json;
  try { json = text ? JSON.parse(text) : null; } catch { json = text; }
  if (!res.ok) {
    const e = new Error(`${method} ${path} → ${res.status}`);
    e.status = res.status; e.body = json;
    throw e;
  }
  return json;
}

const isoDate = (d) => d.toISOString().slice(0, 10);
const monthsAgo = (n) => { const d = new Date(); d.setMonth(d.getMonth() - n); d.setDate(5); return d; };
const monthsAhead = (n) => { const d = new Date(); d.setMonth(d.getMonth() + n); d.setDate(5); return d; };

// ── plan ───────────────────────────────────────────────────────────────────

const ALL_USERS = [
  "admin",
  "owner_alice", "owner_bob",
  "tenant_chris", "tenant_dana",
  "tenant_eli", "tenant_fran", "tenant_gabe",
];

const BUILDINGS = [
  {
    ownerKey: "owner_alice",
    body: {
      buildingName: "Sunrise Residency",
      buildingAddress: "12 MG Road, Sector 9",
      buildingCity: "Bangalore",
      buildingState: "Karnataka",
      buildingTotalFloors: 8,
      buildingTotalFlats: 8, // backend requires 6–20
      amenities: "Gym, Swimming Pool, Parking, Security, Garden",
    },
    flats: [
      { flatNumber: "A-101", floor: 1, bedrooms: 2, bathrooms: 2, areaSqft: 1100, rentAmount: 22000, tenantKey: null },
      { flatNumber: "A-102", floor: 1, bedrooms: 3, bathrooms: 2, areaSqft: 1450, rentAmount: 32000, tenantKey: "tenant_chris" },
      { flatNumber: "B-201", floor: 2, bedrooms: 2, bathrooms: 2, areaSqft: 1100, rentAmount: 24000, tenantKey: "tenant_dana" },
      { flatNumber: "B-202", floor: 2, bedrooms: 1, bathrooms: 1, areaSqft: 650,  rentAmount: 16000, tenantKey: null },
      { flatNumber: "C-301", floor: 3, bedrooms: 3, bathrooms: 3, areaSqft: 1500, rentAmount: 36000, tenantKey: null },
      { flatNumber: "C-302", floor: 3, bedrooms: 2, bathrooms: 2, areaSqft: 1200, rentAmount: 28000, tenantKey: null },
    ],
  },
  {
    ownerKey: "owner_alice",
    body: {
      buildingName: "Maple Heights",
      buildingAddress: "78 Brigade Road",
      buildingCity: "Bangalore",
      buildingState: "Karnataka",
      buildingTotalFloors: 12,
      buildingTotalFlats: 6,
      amenities: "Gym, Concierge, Parking, Power Backup, EV Charging",
    },
    flats: [
      { flatNumber: "M-301", floor: 3, bedrooms: 3, bathrooms: 3, areaSqft: 1850, rentAmount: 45000, tenantKey: null },
      { flatNumber: "M-302", floor: 3, bedrooms: 2, bathrooms: 2, areaSqft: 1200, rentAmount: 30000, tenantKey: null },
      { flatNumber: "M-401", floor: 4, bedrooms: 3, bathrooms: 2, areaSqft: 1500, rentAmount: 36000, tenantKey: null },
      { flatNumber: "M-501", floor: 5, bedrooms: 4, bathrooms: 3, areaSqft: 2400, rentAmount: 60000, tenantKey: null },
    ],
  },
  {
    ownerKey: "owner_bob",
    body: {
      buildingName: "Lakeview Towers",
      buildingAddress: "5 Lake Side Drive",
      buildingCity: "Mumbai",
      buildingState: "Maharashtra",
      buildingTotalFloors: 15,
      buildingTotalFlats: 8,
      amenities: "Pool, Gym, Clubhouse, Parking, Garden, Children Play Area",
    },
    flats: [
      { flatNumber: "L-101", floor: 1, bedrooms: 2, bathrooms: 2, areaSqft: 1300, rentAmount: 38000, tenantKey: "tenant_eli" },
      { flatNumber: "L-202", floor: 2, bedrooms: 3, bathrooms: 3, areaSqft: 1700, rentAmount: 52000, tenantKey: "tenant_fran" },
      { flatNumber: "L-305", floor: 3, bedrooms: 3, bathrooms: 2, areaSqft: 1500, rentAmount: 46000, tenantKey: "tenant_gabe" },
      { flatNumber: "L-410", floor: 4, bedrooms: 4, bathrooms: 4, areaSqft: 2100, rentAmount: 75000, tenantKey: null },
      { flatNumber: "L-501", floor: 5, bedrooms: 2, bathrooms: 2, areaSqft: 1250, rentAmount: 40000, tenantKey: null },
      { flatNumber: "L-601", floor: 6, bedrooms: 3, bathrooms: 3, areaSqft: 1650, rentAmount: 50000, tenantKey: null },
    ],
  },
];

// 6-row payment plan applied to every tenant
const PAYMENT_SCHEDULE = [
  { monthsAgo: 4, status: "PAID" },
  { monthsAgo: 3, status: "PAID" },
  { monthsAgo: 2, status: "PAID" },
  { monthsAgo: 1, status: "OVERDUE" },
  { monthsAgo: 0, status: "PENDING" },
  { monthsAhead: 1, status: "PENDING" },
];

const MAINTENANCE = {
  tenant_chris: [
    { title: "Kitchen tap drips overnight", description: "Cold water tap leaks ~2L/day. Sink drains slowly.",
      category: "PLUMBING", priority: "MEDIUM", finalStatus: "RESOLVED" },
    { title: "Bedroom AC not cooling", description: "AC switches on but blows warm air. Filters clean.",
      category: "APPLIANCE", priority: "HIGH", finalStatus: "IN_PROGRESS" },
    { title: "Power socket loose near bed", description: "Socket wobbles when plug inserted; mild sparks once.",
      category: "ELECTRICAL", priority: "HIGH", finalStatus: "OPEN" },
  ],
  tenant_dana: [
    { title: "Repaint living room walls", description: "Walls scuffed from previous tenant. Requesting fresh coat.",
      category: "PAINTING", priority: "LOW", finalStatus: "OPEN" },
  ],
  tenant_eli: [
    { title: "Geyser stopped working", description: "No hot water since this morning. Pilot light won't relight.",
      category: "PLUMBING", priority: "CRITICAL", finalStatus: "OPEN" },
    { title: "Pest control needed", description: "Cockroach issue in kitchen. Need fumigation.",
      category: "PEST_CONTROL", priority: "MEDIUM", finalStatus: "IN_PROGRESS" },
  ],
  tenant_fran: [
    { title: "Wardrobe door off its hinge", description: "Master bedroom wardrobe door fell off; needs reattachment.",
      category: "CARPENTRY", priority: "MEDIUM", finalStatus: "RESOLVED" },
  ],
  tenant_gabe: [
    { title: "Internet router not powering on", description: "Router blinking red, no Wi-Fi for 2 days.",
      category: "ELECTRICAL", priority: "HIGH", finalStatus: "IN_PROGRESS" },
    { title: "Common-area corridor light out", description: "5th-floor corridor light hasn't worked since last week.",
      category: "ELECTRICAL", priority: "LOW", finalStatus: "OPEN" },
  ],
};

// ── steps ──────────────────────────────────────────────────────────────────

const sessions = {}; // userKey → { authUserId, accessToken, role }

async function loginAll() {
  info("Logging in auto-seeded demo users…");
  for (const u of ALL_USERS) {
    try {
      const auth = await call("POST", "/auth/login", { userName: u, password: PASSWORD });
      sessions[u] = { authUserId: auth.authUserId, accessToken: auth.accessToken, role: auth.role };
      ok(`${u} → authUserId=${auth.authUserId} role=${auth.role}`);
    } catch (e) {
      throw new Error(
        `Login failed for ${u}. Make sure auth-service is up and DemoDataSeeder ran. ` +
        `(${e.status} ${JSON.stringify(e.body)})`,
      );
    }
  }
}

async function seedBuildingsAndFlats() {
  info("Seeding buildings and flats (idempotent per owner)…");

  // Track flat IDs by tenantKey so we can use them for payments + maintenance.
  const tenantFlatIds = {};

  for (const spec of BUILDINGS) {
    const owner = sessions[spec.ownerKey];

    // Skip if owner already has a building with the same name (idempotent)
    let existing = [];
    try {
      existing = await call("GET", `/properties/buildings/owner/${owner.authUserId}`, null, owner.accessToken);
    } catch (e) {
      if (e.status !== 404) warn(`byOwner lookup for ${spec.ownerKey} returned ${e.status}`);
    }
    const match = (existing ?? []).find((b) => b.buildingName === spec.body.buildingName);
    let buildingId;
    if (match) {
      buildingId = match.buildingId;
      warn(`  building "${spec.body.buildingName}" already exists for ${spec.ownerKey} → ${buildingId}`);
    } else {
      const created = await call("POST", "/properties/buildings/create/building",
        { ...spec.body, ownerId: owner.authUserId },
        owner.accessToken);
      buildingId = created.buildingId;
      ok(`  + building ${buildingId}: ${created.buildingName} (${created.buildingCity})`);
    }

    // Discover existing flats so we don't duplicate
    let flatsInBuilding = [];
    try {
      flatsInBuilding = await call("GET", `/properties/flats/building/${buildingId}`, null, owner.accessToken);
    } catch (e) {
      if (e.status !== 404) warn(`flats/building lookup returned ${e.status}`);
    }
    const existingNumbers = new Set((flatsInBuilding ?? []).map((f) => f.flatNumber));

    for (const f of spec.flats) {
      let flatId;
      const existingFlat = (flatsInBuilding ?? []).find((x) => x.flatNumber === f.flatNumber);
      if (existingFlat) {
        flatId = existingFlat.id;
        warn(`    flat ${f.flatNumber} already exists → ${flatId}`);
      } else {
        try {
          const cFlat = await call("POST", "/properties/flats/create/flat", {
            buildingId,
            flatNumber: f.flatNumber,
            floor: f.floor,
            bedrooms: f.bedrooms,
            bathrooms: f.bathrooms,
            areaSqft: f.areaSqft,
            rentAmount: f.rentAmount,
          }, owner.accessToken);
          flatId = cFlat.id;
          ok(`    + flat ${f.flatNumber} → ${flatId}`);
        } catch (e) {
          warn(`    flat ${f.flatNumber} create failed: ${e.status} ${JSON.stringify(e.body)}`);
          continue;
        }
      }

      if (f.tenantKey) {
        tenantFlatIds[f.tenantKey] = flatId;
        const tenant = sessions[f.tenantKey];
        // Only assign if not already assigned to this tenant
        const liveFlat = existingFlat ?? (await call("GET", `/properties/flats/${flatId}`, null, owner.accessToken).catch(() => null));
        if (liveFlat?.tenantId === tenant.authUserId) {
          warn(`    flat ${f.flatNumber} already assigned to ${f.tenantKey}`);
        } else {
          try {
            await call("POST", `/properties/flats/${flatId}/assign`, {
              tenantId: tenant.authUserId,
              leaseStartDate: isoDate(monthsAgo(3)),
              leaseEndDate: isoDate(monthsAhead(9)),
            }, owner.accessToken);
            ok(`    → assigned to ${f.tenantKey}`);
          } catch (e) {
            warn(`    assign to ${f.tenantKey} failed: ${e.status} ${JSON.stringify(e.body)}`);
          }
        }
      }

      // keep set in case we want it later
      existingNumbers.add(f.flatNumber);
    }
  }

  return tenantFlatIds;
}

async function seedPayments(tenantFlatIds) {
  info("Seeding payment history…");
  // Tenant → owner map matches the BUILDINGS data above
  const ownerOf = { tenant_chris: "owner_alice", tenant_dana: "owner_alice",
                    tenant_eli: "owner_bob", tenant_fran: "owner_bob", tenant_gabe: "owner_bob" };

  for (const [tenantKey, flatId] of Object.entries(tenantFlatIds)) {
    const tenant = sessions[tenantKey];
    const owner  = sessions[ownerOf[tenantKey]];
    // Find the rent amount from BUILDINGS plan
    let rent = 25000;
    for (const b of BUILDINGS) {
      const flat = b.flats.find((f) => f.tenantKey === tenantKey);
      if (flat) { rent = flat.rentAmount; break; }
    }
    info(`  ${tenantKey} on flat ${flatId} (₹${rent}/mo)`);

    // Idempotency: if this tenant already has any payments, skip the whole tenant.
    let existing = [];
    try { existing = await call("GET", `/payments/tenant/${tenant.authUserId}`, null, tenant.accessToken); }
    catch (e) { if (e.status !== 404) warn(`    history lookup → ${e.status}`); }
    if ((existing ?? []).length > 0) {
      warn(`    ${tenantKey} already has ${existing.length} payment(s) — skipping`);
      continue;
    }

    for (const slot of PAYMENT_SCHEDULE) {
      const due = slot.monthsAgo != null ? monthsAgo(slot.monthsAgo) : monthsAhead(slot.monthsAhead);
      const body = {
        tenantId: tenant.authUserId,
        flatId: String(flatId),
        ownerId: owner.authUserId,
        amount: rent,
        dueDate: isoDate(due),
      };
      let payment;
      try {
        payment = await call("POST", "/payments", body, owner.accessToken);
      } catch (e) {
        warn(`    + payment(${body.dueDate}) failed: ${e.status} ${JSON.stringify(e.body)}`);
        continue;
      }
      ok(`    + payment ${payment.id} due ${body.dueDate} (target ${slot.status})`);

      if (slot.status === "PAID") {
        try {
          await call("POST", `/payments/${payment.id}/pay-cash`,
            { paidDate: isoDate(due), notes: "Seed: marked paid" },
            owner.accessToken);
        } catch (e) {
          warn(`      pay-cash → ${e.status} ${JSON.stringify(e.body)}`);
        }
      }
    }
  }
}

async function seedMaintenance(tenantFlatIds) {
  info("Seeding maintenance tickets…");
  for (const [tenantKey, tickets] of Object.entries(MAINTENANCE)) {
    const tenant = sessions[tenantKey];
    const flatId = tenantFlatIds[tenantKey];
    if (!tenant || !flatId) { warn(`  no tenant/flat for ${tenantKey}, skipping`); continue; }

    // Idempotency check
    let existing = [];
    try { existing = await call("GET", `/maintenance/requests/tenant/${tenant.authUserId}`, null, tenant.accessToken); }
    catch (e) { if (e.status !== 404) warn(`    history lookup → ${e.status}`); }
    if ((existing ?? []).length > 0) {
      warn(`  ${tenantKey} already has ${existing.length} ticket(s) — skipping`);
      continue;
    }

    for (const t of tickets) {
      let request;
      try {
        request = await call("POST", "/maintenance/requests", {
          flatId: String(flatId),
          tenantId: tenant.authUserId,
          category: t.category,
          priority: t.priority,
          title: t.title,
          description: t.description,
        }, tenant.accessToken);
      } catch (e) {
        warn(`    "${t.title}" → ${e.status} ${JSON.stringify(e.body)}`);
        continue;
      }
      ok(`    + ${request.id} "${t.title}" (target ${t.finalStatus})`);

      // OPEN → IN_PROGRESS → RESOLVED → CLOSED state-machine transitions
      const transitions =
        t.finalStatus === "IN_PROGRESS" ? ["IN_PROGRESS"]
        : t.finalStatus === "RESOLVED"  ? ["IN_PROGRESS", "RESOLVED"]
        : t.finalStatus === "CLOSED"    ? ["IN_PROGRESS", "RESOLVED", "CLOSED"]
        : [];
      for (const s of transitions) {
        try {
          await call("POST", `/maintenance/requests/${request.id}/status`,
            { newStatus: s, changedBy: tenant.authUserId },
            tenant.accessToken);
        } catch (e) {
          warn(`      → ${s} failed: ${e.status} ${JSON.stringify(e.body)}`);
          break;
        }
      }
    }
  }
}

// ── main ───────────────────────────────────────────────────────────────────

async function main() {
  console.log(`${C.bold}Hearth · seed${C.reset} ${C.dim}(${BASE})${C.reset}\n`);
  try {
    await loginAll();
    const tenantFlatIds = await seedBuildingsAndFlats();
    await seedPayments(tenantFlatIds);
    await seedMaintenance(tenantFlatIds);
  } catch (e) {
    console.error(`\n${C.red}Seeding failed${C.reset}: ${e.message}`);
    if (e.body) console.error("Response body:", JSON.stringify(e.body, null, 2));
    process.exit(1);
  }
  console.log(`\n${C.bold}${C.green}✓ Seed complete${C.reset}\n`);
  printCredentials();
}

function printCredentials() {
  console.log(`${C.bold}Sign-in credentials${C.reset} ${C.dim}(http://localhost:4200/login)${C.reset}`);
  console.table([
    { Username: "admin",         Password: "Password1", Role: "ADMIN"  },
    { Username: "owner_alice",   Password: "Password1", Role: "OWNER"  },
    { Username: "owner_bob",     Password: "Password1", Role: "OWNER"  },
    { Username: "tenant_chris",  Password: "Password1", Role: "TENANT" },
    { Username: "tenant_dana",   Password: "Password1", Role: "TENANT" },
    { Username: "tenant_eli",    Password: "Password1", Role: "TENANT" },
    { Username: "tenant_fran",   Password: "Password1", Role: "TENANT" },
    { Username: "tenant_gabe",   Password: "Password1", Role: "TENANT" },
  ]);
  console.log(`\n${C.bold}What each user shows${C.reset}`);
  console.log(`  ${C.cyan}admin${C.reset}        → /admin   platform overview`);
  console.log(`  ${C.cyan}owner_alice${C.reset}  → /owner   2 buildings (Sunrise + Maple), 10 flats, tenants Chris + Dana, payment history`);
  console.log(`  ${C.cyan}owner_bob${C.reset}    → /owner   1 building (Lakeview), 6 flats, tenants Eli + Fran + Gabe`);
  console.log(`  ${C.cyan}tenant_chris${C.reset} → /app     A-102, ₹32K/mo, 1 OVERDUE rent + 3 maintenance tickets`);
  console.log(`  ${C.cyan}tenant_dana${C.reset}  → /app     B-201, ₹24K/mo, painting request`);
  console.log(`  ${C.cyan}tenant_eli${C.reset}   → /app     L-101, ₹38K/mo, geyser CRITICAL + pest control`);
  console.log(`  ${C.cyan}tenant_fran${C.reset}  → /app     L-202, ₹52K/mo, wardrobe RESOLVED`);
  console.log(`  ${C.cyan}tenant_gabe${C.reset}  → /app     L-305, ₹46K/mo, router + corridor light\n`);
}

main();
