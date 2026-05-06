# RENTGENIUS AI — MASTER ARCHITECTURE DOCUMENT
### India's First Autonomous AI Home Rental Platform
### Version 3.0 | Unified Design | Built on existing codebase

---

## QUICK REFERENCE: SERVICE REGISTRY

| Service | Port | DB | Status | Priority |
|---|---|---|---|---|
| Config Server | 8888 | Git | DONE | - |
| Service Registry (Eureka) | 8761 | In-memory | DONE | - |
| API Gateway | 8080 | None | DONE + add routes | - |
| Auth Service | 8081 | PostgreSQL | DONE | - |
| Property Service | 8082 | PostgreSQL | DONE + 2 events | Week 1 |
| User Service | 8083 | PostgreSQL | DONE + 3 columns | Week 1 |
| Payment Service | 8084 | PostgreSQL | DONE + UPI webhook | Week 1 |
| Maintenance Service | 8085 | MongoDB | DONE + ai_predicted field | Week 1 |
| Notification Service | 8086 | MongoDB | DONE + WhatsApp + AI events | Week 2 |
| Analytics Service | 8087 | PostgreSQL + Redis | DONE + AI insight APIs | Week 2 |
| AI Decision Engine | 8088 | PostgreSQL + Redis | BUILD | Week 3-4 |
| AI Python FastAPI | 8098 | Redis feature store | BUILD | Week 3-4 |
| Lease Service | 8090 | PostgreSQL | BUILD | Week 5 |
| Document Service | 8091 | S3/GCS | BUILD | Week 5 |
| KYC Service | 8092 | PostgreSQL | BUILD | Week 6 |
| Compliance Service | 8093 | PostgreSQL | BUILD | Week 6 |
| Review Service | 8094 | MongoDB | BUILD | Week 8 |
| AI Gateway (LLM Proxy) | 8089 | None | BUILD | Week 9 |

---

## SYSTEM ARCHITECTURE OVERVIEW

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                           FRONTEND LAYER                                    ║
║  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   ║
║  │  Android App │  │   iOS App    │  │Owner Dashboard│  │Tenant Portal │   ║
║  │ React Native │  │ React Native │  │  Next.js Web  │  │ WhatsApp Bot │   ║
║  │  Play Store  │  │  App Store   │  │  AI Insights  │  │  In-App Chat │   ║
║  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘   ║
╚═══════════════════════════════════╤════════════════════════════════════════╝
                                    │ HTTPS / WSS
                                    ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║              API GATEWAY  (Port: 8080) — Spring Cloud Gateway               ║
║     JWT Validation | Rate Limiting | Routing | Circuit Breaker | Logging    ║
║                                                                              ║
║  Routes:                                                                     ║
║  /api/auth/**        → 8081   /api/ai/**          → 8088                   ║
║  /api/properties/**  → 8082   /api/lease/**        → 8090                  ║
║  /api/users/**       → 8083   /api/documents/**    → 8091                  ║
║  /api/payments/**    → 8084   /api/kyc/**          → 8092                  ║
║  /api/maintenance/** → 8085   /api/compliance/**   → 8093                  ║
║  /api/analytics/**   → 8087   /api/reviews/**      → 8094                  ║
║  /api/llm/**         → 8089                                                 ║
╚══════════╤═══════════════════════════════════════════════════════════════════╝
           │
     ┌─────┴──────┬────────────────┐
     ▼            ▼                ▼
┌─────────┐ ┌──────────┐ ┌──────────────┐
│ AUTH    │ │ SERVICE  │ │ CONFIG       │
│ SERVICE │ │ REGISTRY │ │ SERVER       │
│  8081   │ │  8761    │ │  8888        │
│PostgreSQL│ │  Eureka  │ │  Git/Local   │
└─────────┘ └──────────┘ └──────────────┘

════════════════════ BUSINESS MICROSERVICES ═════════════════════

┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ PROPERTY    │  │ USER SVC    │  │ PAYMENT SVC │  │ MAINTENANCE │
│ SVC  8082   │  │  8083       │  │  8084       │  │ SVC  8085   │
│ PostgreSQL  │  │ PostgreSQL  │  │ PostgreSQL  │  │  MongoDB    │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │                │                │                 │
       └────────────────┴────────────────┴─────────────────┘
                                  │
                                  ▼
════════════════ INDIA COMPLIANCE LAYER (NEW) ═══════════════════

┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  KYC SVC    │  │ COMPLIANCE  │  │  LEASE SVC  │  │ DOCUMENT    │
│   8092      │  │ SVC  8093   │  │  8090       │  │ SVC  8091   │
│ Aadhaar/PAN │  │ RERA + GST  │  │ PostgreSQL  │  │  S3 + OCR   │
│ PostgreSQL  │  │ PostgreSQL  │  │             │  │             │
└─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘
                                  │
                                  ▼
╔══════════════════════════════════════════════════════════════════╗
║                    KAFKA EVENT BUS                               ║
║                                                                  ║
║  EXISTING TOPICS:        NEW TOPICS:          AI TOPICS:        ║
║  user-events             kyc-events           ai-events          ║
║  property-events         lease-events         ai.risk.detected   ║
║  payment-events          compliance-events    ai.maint.predicted ║
║  maintenance-events      document-events      ai.rent.suggested  ║
║  notification-events     upi-events           ai.lease.strategy  ║
║  audit-events                                 ai.churn.predicted ║
╚═══════════════════════════════════┬══════════════════════════════╝
                                    │
          ┌─────────────────────────┼──────────────────────┐
          ▼                         ▼                      ▼
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│ NOTIFICATION SVC │    │  ANALYTICS SVC   │    │   REVIEW SVC     │
│      8086        │    │     8087         │    │     8094         │
│ Email|SMS|Push   │    │  PostgreSQL      │    │    MongoDB       │
│ WhatsApp (NEW)   │    │  Redis Cache     │    │  AI Sentiment    │
└──────────────────┘    │  ClickHouse(NEW) │    └──────────────────┘
                        └──────────────────┘
                                    │
                                    ▼
╔══════════════════════════════════════════════════════════════════╗
║              AI DECISION ENGINE  (Port: 8088)                    ║
║           Spring Boot Shell + Python FastAPI (8098)              ║
║                                                                  ║
║  ┌──────────────────┐   ┌──────────────────┐                    ║
║  │ Tenant Risk      │   │ Predictive       │                    ║
║  │ Engine           │   │ Maintenance      │                    ║
║  │ XGBoost          │   │ LSTM + RF        │                    ║
║  └──────────────────┘   └──────────────────┘                    ║
║  ┌──────────────────┐   ┌──────────────────┐                    ║
║  │ Rent Optimizer   │   │ Lease Renewal    │                    ║
║  │ Gradient Boost   │   │ + Churn Engine   │                    ║
║  └──────────────────┘   └──────────────────┘                    ║
║  ┌──────────────────┐   ┌──────────────────┐                    ║
║  │ Document AI      │   │ Multilingual     │                    ║
║  │ OCR + NLP        │   │ Assistant (LLM)  │                    ║
║  └──────────────────┘   └──────────────────┘                    ║
║                                                                  ║
║  Database: PostgreSQL (ai_db) + Redis (Feature Store)           ║
╚══════════════════════════════════════════════════════════════════╝
                                    │
                                    ▼
╔══════════════════════════════════════════════════════════════════╗
║                   AI GATEWAY  (Port: 8089)                       ║
║           LLM Proxy | Claude API | WhatsApp Business API        ║
╚══════════════════════════════════════════════════════════════════╝
```

---

## SECTION 1: EXISTING SERVICES — WHAT TO ADD (NON-BREAKING)

### Rule: Never modify existing APIs. Only add new endpoints, columns, and Kafka consumers.

---

### 1.1 API GATEWAY (8080) — Add 5 New Routes

Add to your existing gateway route config:

```yaml
# ADD these routes to your existing api-gateway configuration
spring:
  cloud:
    gateway:
      routes:
        # --- EXISTING ROUTES (DO NOT TOUCH) ---
        # ... your existing routes stay exactly as they are ...

        # --- NEW ROUTES TO ADD ---
        - id: ai-service
          uri: lb://AI-DECISION-ENGINE
          predicates:
            - Path=/api/ai/**
          filters:
            - StripPrefix=1

        - id: lease-service
          uri: lb://LEASE-SERVICE
          predicates:
            - Path=/api/lease/**
          filters:
            - StripPrefix=1

        - id: document-service
          uri: lb://DOCUMENT-SERVICE
          predicates:
            - Path=/api/documents/**
          filters:
            - StripPrefix=1

        - id: kyc-service
          uri: lb://KYC-SERVICE
          predicates:
            - Path=/api/kyc/**
          filters:
            - StripPrefix=1

        - id: compliance-service
          uri: lb://COMPLIANCE-SERVICE
          predicates:
            - Path=/api/compliance/**
          filters:
            - StripPrefix=1

        - id: review-service
          uri: lb://REVIEW-SERVICE
          predicates:
            - Path=/api/reviews/**
          filters:
            - StripPrefix=1
```

---

### 1.2 KafkaEvents Module — Add New Event Classes

Add these 8 new classes to your existing KafkaEvents module. Do NOT modify existing classes.

```java
// NEW: Add to KafkaEvents module

// AI Output Events
public record AiRiskEvent(
    Long tenantId, Double riskScore, Double defaultProbability,
    Double churnProbability, String riskLevel, LocalDateTime detectedAt) {}

public record AiMaintenanceEvent(
    Long propertyId, String issueType, LocalDate predictedDate,
    Double confidenceScore, String severity) {}

public record AiRentEvent(
    Long propertyId, BigDecimal currentRent, BigDecimal suggestedRent,
    Double confidenceScore, String justification) {}

public record AiLeaseEvent(
    Long tenantId, Double renewalProbability, Double churnRisk,
    String recommendedAction, LocalDate leaseEndDate) {}

public record AiChurnEvent(
    Long tenantId, Double churnProbability, Integer daysUntilExpiry,
    String recommendation) {}

// India-Specific Events
public record UpiPaymentEvent(
    Long paymentId, Long tenantId, String upiTransactionId,
    BigDecimal amount, String mandateId, LocalDateTime settledAt) {}

public record KycVerifiedEvent(
    Long userId, String kycProvider, String aadhaarHash,
    String panNumber, Boolean verified, LocalDateTime verifiedAt) {}

public record LeaseEvent(
    Long leaseId, Long tenantId, Long flatId,
    String eventType, // SIGNED | EXPIRING | RENEWED | TERMINATED
    LocalDate startDate, LocalDate endDate, BigDecimal rentAmount) {}
```

---

### 1.3 Property Service (8082) — Complete Missing Events

These two events are referenced in your architecture but not yet published. Add them to the existing service methods:

```java
// In FlatService.java — add Kafka publish to EXISTING assignTenant() method
// DO NOT change method signature or return type

@Autowired
private KafkaTemplate<String, Object> kafkaTemplate;

// ADD inside your existing assignTenant() method, after saving the flat:
kafkaTemplate.send("property-events", FlatOccupiedEvent.builder()
    .flatId(flat.getId())
    .tenantId(tenantId)
    .rentAmount(flat.getRentAmount())
    .startDate(flat.getLeaseStartDate())
    .buildingId(flat.getBuildingId())
    .timestamp(LocalDateTime.now())
    .build());

// ADD inside your existing vacateFlat() method, after saving:
kafkaTemplate.send("property-events", FlatVacatedEvent.builder()
    .flatId(flat.getId())
    .tenantId(flat.getTenantId())
    .endDate(flat.getLeaseEndDate())
    .timestamp(LocalDateTime.now())
    .build());
```

Database addition (Flyway migration — backward compatible):
```sql
-- V2__add_ai_fields_to_flats.sql
ALTER TABLE flats ADD COLUMN IF NOT EXISTS ai_rent_suggestion DECIMAL(10,2);
ALTER TABLE flats ADD COLUMN IF NOT EXISTS ai_suggestion_confidence DECIMAL(5,4);
ALTER TABLE flats ADD COLUMN IF NOT EXISTS ai_suggestion_date TIMESTAMP;
-- All columns are nullable — zero impact on existing code
```

New read-only endpoint to add:
```java
// ADD to FlatController.java (new endpoint only)
@GetMapping("/flats/{id}/ai-suggestion")
public ResponseEntity<AiRentSuggestionDTO> getAiRentSuggestion(@PathVariable Long id) {
    return flatService.getAiRentSuggestion(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
}
```

---

### 1.4 User Service (8083) — Add AI & KYC Fields

```sql
-- V2__add_ai_kyc_fields_to_users.sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS kyc_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE users ADD COLUMN IF NOT EXISTS ai_risk_score DECIMAL(5,2);
ALTER TABLE users ADD COLUMN IF NOT EXISTS ai_risk_level VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(10) DEFAULT 'en';
ALTER TABLE users ADD COLUMN IF NOT EXISTS whatsapp_number VARCHAR(15);
-- All nullable / have defaults — zero impact on existing code
```

```java
// ADD new Kafka consumers to UserService — do not modify existing consumers

@KafkaListener(topics = "kyc-events", groupId = "user-service")
public void handleKycVerified(KycVerifiedEvent event) {
    userRepository.updateKycStatus(event.getUserId(),
        event.isVerified() ? "VERIFIED" : "FAILED");
}

@KafkaListener(topics = "ai-events", groupId = "user-service-ai")
public void handleAiRiskUpdate(AiRiskEvent event) {
    userRepository.updateAiRiskScore(
        event.getTenantId(), event.getRiskScore(), event.getRiskLevel());
}

// ADD new read endpoint
@GetMapping("/users/{id}/risk-profile")
public ResponseEntity<UserRiskProfileDTO> getRiskProfile(@PathVariable Long id) { ... }
```

---

### 1.5 Payment Service (8084) — Add UPI Integration

```sql
-- V2__add_upi_fields_to_payments.sql
ALTER TABLE payments ADD COLUMN IF NOT EXISTS upi_mandate_id VARCHAR(100);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS upi_transaction_id VARCHAR(100);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payment_channel VARCHAR(20) DEFAULT 'MANUAL';
-- payment_channel: MANUAL | UPI_AUTOPAY | RAZORPAY | STRIPE
```

```java
// ADD new UPI webhook endpoint — existing endpoints unchanged

@PostMapping("/payments/upi/webhook")
public ResponseEntity<Void> handleUpiWebhook(
    @RequestBody RazorpayWebhookPayload payload,
    @RequestHeader("X-Razorpay-Signature") String signature) {

    // 1. Verify Razorpay signature
    razorpayService.verifyWebhook(payload, signature);

    // 2. Update payment record
    paymentService.markUpiPaymentComplete(payload);

    // 3. Publish UPI event to Kafka
    kafkaTemplate.send("upi-events", UpiPaymentEvent.builder()
        .paymentId(payload.getPaymentId())
        .tenantId(payload.getTenantId())
        .upiTransactionId(payload.getTransactionId())
        .amount(payload.getAmount())
        .mandateId(payload.getMandateId())
        .settledAt(LocalDateTime.now())
        .build());

    return ResponseEntity.ok().build();
}
```

---

### 1.6 Maintenance Service (8085) — Add AI Predicted Flag

```javascript
// ADD to maintenance_requests MongoDB schema (backward compatible)
// The new field is optional — existing documents are unaffected

maintenance_requests {
    // ... all EXISTING fields unchanged ...
    ai_predicted: { type: Boolean, default: false },     // NEW
    ai_confidence_score: { type: Number },               // NEW
    ai_predicted_date: { type: Date }                    // NEW
}
```

```java
// ADD new Kafka consumer to MaintenanceService

@KafkaListener(topics = "ai-events", groupId = "maintenance-service-ai")
public void handleAiMaintenancePrediction(AiMaintenanceEvent event) {
    // Auto-create maintenance request from AI prediction
    MaintenanceRequest request = MaintenanceRequest.builder()
        .propertyId(event.getPropertyId())
        .category(event.getIssueType())
        .title("AI Predicted: " + event.getIssueType())
        .priority(event.getSeverity())
        .status("OPEN")
        .aiPredicted(true)
        .aiConfidenceScore(event.getConfidenceScore())
        .aiPredictedDate(event.getPredictedDate())
        .build();
    maintenanceRepository.save(request);
}
```

---

### 1.7 Notification Service (8086) — Add WhatsApp + AI Events

```java
// ADD new channel: WhatsApp (existing Email/SMS/Push unchanged)

// New MongoDB collection:
whatsapp_logs {
    _id: ObjectId,
    user_id: Long,
    whatsapp_number: String,
    message: String,
    template_name: String,
    status: String,         // PENDING | SENT | DELIVERED | READ | FAILED
    message_id: String,     // WhatsApp message ID
    sent_at: Date,
    delivered_at: Date
}

// ADD new Kafka consumers (do not touch existing consumers)

@KafkaListener(topics = "ai-events", groupId = "notification-ai")
public void handleAiEvents(Object event) {
    if (event instanceof AiRiskEvent risk) {
        if (risk.getRiskLevel().equals("HIGH")) {
            sendWhatsApp(getOwnerNumber(risk.getTenantId()),
                "HIGH_RISK_TENANT_ALERT", buildRiskParams(risk));
        }
    } else if (event instanceof AiMaintenanceEvent maint) {
        sendWhatsApp(getTenantNumber(maint.getPropertyId()),
            "PREDICTED_MAINTENANCE_NOTICE", buildMaintenanceParams(maint));
    } else if (event instanceof AiRentEvent rent) {
        sendWhatsApp(getOwnerNumber(rent.getPropertyId()),
            "RENT_SUGGESTION_READY", buildRentParams(rent));
    } else if (event instanceof AiLeaseEvent lease) {
        sendWhatsApp(getTenantNumber(lease.getTenantId()),
            "LEASE_RENEWAL_OFFER", buildLeaseParams(lease));
    }
}

// New WhatsApp message templates to add:
// HIGH_RISK_TENANT_ALERT, PREDICTED_MAINTENANCE_NOTICE,
// RENT_SUGGESTION_READY, LEASE_RENEWAL_OFFER, UPI_PAYMENT_CONFIRMED
```

---

### 1.8 Analytics Service (8087) — Add AI Insight APIs

```java
// ADD new endpoints — existing endpoints unchanged

@GetMapping("/analytics/ai/risk-summary/owner/{ownerId}")
public ResponseEntity<AiRiskSummaryDTO> getAiRiskSummary(...) { ... }

@GetMapping("/analytics/ai/maintenance-forecast/building/{buildingId}")
public ResponseEntity<MaintenanceForecastDTO> getMaintenanceForecast(...) { ... }

@GetMapping("/analytics/ai/rent-optimization/owner/{ownerId}")
public ResponseEntity<RentOptimizationDTO> getRentOptimization(...) { ... }

@GetMapping("/analytics/ai/churn-risk/owner/{ownerId}")
public ResponseEntity<ChurnRiskDTO> getChurnRisk(...) { ... }

// ADD new Kafka consumer
@KafkaListener(topics = "ai-events", groupId = "analytics-ai")
public void consumeAiEvents(Object event) {
    // Aggregate AI decisions into analytics_db for reporting
    aiMetricsRepository.recordAiEvent(event);
}
```

---

## SECTION 2: NEW SERVICE DESIGNS

---

### 2.1 AI DECISION ENGINE (8088) — THE BRAIN

Port: 8088 (Spring Boot shell) + 8098 (Python FastAPI ML)
Database: PostgreSQL (ai_db) + Redis (feature store)

Architecture:
- Spring Boot shell handles: Kafka consumption, event routing, AI event publishing, REST API exposure
- Python FastAPI handles: ML model serving, feature computation, prediction scoring
- Spring calls Python via internal HTTP (localhost:8098)
- This design means ZERO changes to existing Java services

```
Spring Boot (8088)
    │
    ├── Kafka Consumers (all event topics)
    │       ↓ extract features
    ├── Feature Builder → Redis (real-time feature store)
    │       ↓ on threshold trigger
    ├── HTTP POST → Python FastAPI (8098)
    │       ↓ ML prediction result
    ├── AI Event Publisher → Kafka (ai-events topic)
    │       ↓
    └── REST APIs → /api/ai/** endpoints
```

#### Database Tables (ai_db — PostgreSQL)

```sql
CREATE TABLE tenant_risk_scores (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL UNIQUE,
    risk_score DECIMAL(5,2),
    default_probability DECIMAL(5,4),
    churn_probability DECIMAL(5,4),
    risk_level VARCHAR(10),  -- LOW | MEDIUM | HIGH | CRITICAL
    payment_delay_frequency DECIMAL(5,4),
    avg_payment_delay_days DECIMAL(5,2),
    complaint_frequency DECIMAL(5,4),
    maintenance_cost_per_tenant DECIMAL(10,2),
    lease_duration_pattern INTEGER,
    upi_mandate_compliance DECIMAL(5,4),
    kyc_confidence_score DECIMAL(5,4),
    last_updated TIMESTAMP DEFAULT NOW()
);

CREATE TABLE predicted_maintenance (
    id BIGSERIAL PRIMARY KEY,
    property_id BIGINT NOT NULL,
    issue_type VARCHAR(100),
    predicted_date DATE,
    confidence_score DECIMAL(5,4),
    severity VARCHAR(20),
    status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING | TRIGGERED | RESOLVED
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE rent_recommendations (
    id BIGSERIAL PRIMARY KEY,
    property_id BIGINT NOT NULL,
    current_rent DECIMAL(10,2),
    suggested_rent DECIMAL(10,2),
    confidence_score DECIMAL(5,4),
    market_rate_delta DECIMAL(10,2),
    justification TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING | ACCEPTED | REJECTED
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE lease_predictions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    lease_id BIGINT,
    renewal_probability DECIMAL(5,4),
    churn_risk DECIMAL(5,4),
    recommended_action VARCHAR(100),
    days_until_expiry INTEGER,
    discount_suggested DECIMAL(5,2),
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE ai_feature_log (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(20),  -- TENANT | PROPERTY
    entity_id BIGINT,
    feature_name VARCHAR(100),
    feature_value TEXT,
    recorded_at TIMESTAMP DEFAULT NOW()
);
```

#### Redis Feature Store (Key Patterns)

```
tenant:features:{tenantId}       → HashMap of all computed features
property:features:{propertyId}   → HashMap of property-level features
ai:model:version                 → Current model version metadata
ai:threshold:risk                → Configurable risk threshold (default: 70)
ai:threshold:churn               → Configurable churn threshold (default: 65)
```

#### Python FastAPI Structure (8098)

```
ai-engine-python/
├── main.py                      # FastAPI app entry point
├── models/
│   ├── tenant_risk_model.pkl    # XGBoost trained model
│   ├── maintenance_model.pkl    # LSTM + Random Forest
│   ├── rent_optimizer.pkl       # Gradient Boosting Regressor
│   └── churn_model.pkl          # Logistic Regression + Survival
├── endpoints/
│   ├── risk.py                  # POST /predict/risk
│   ├── maintenance.py           # POST /predict/maintenance
│   ├── rent.py                  # POST /predict/rent
│   └── churn.py                 # POST /predict/churn
├── feature_engineering/
│   ├── payment_features.py
│   ├── maintenance_features.py
│   └── property_features.py
└── requirements.txt
    # fastapi, uvicorn, scikit-learn, xgboost, torch, pandas, numpy, redis
```

#### AI Module Specifications

**Module 1: Tenant Risk Prediction**
```
Model:    XGBoost Classifier
Inputs:   payment_delay_frequency, avg_payment_delay_days,
          complaint_frequency, maintenance_cost_per_tenant,
          lease_duration_pattern, upi_mandate_compliance, kyc_confidence_score
Output:   {risk_score: 0-100, default_probability: 0-1, churn_probability: 0-1}
Trigger:  On every payment.completed, payment.overdue, maintenance.created event
Action:   If risk_score > 70 → publish ai.tenant.risk.detected
```

**Module 2: Predictive Maintenance**
```
Model:    LSTM (time-series) + Random Forest Classifier
Inputs:   maintenance_history (last 24 months), property_age_years,
          avg_resolution_time, seasonal_month, category_frequency_vector
Output:   {issue_type, predicted_date, confidence_score, severity}
Trigger:  Daily batch job + on maintenance.resolved event
Action:   If confidence_score > 0.75 → publish ai.maintenance.predicted
```

**Module 3: Dynamic Rent Optimizer**
```
Model:    Gradient Boosting Regressor
Inputs:   current_rent, occupancy_rate, local_market_rate,
          tenant_churn_risk, payment_consistency_score,
          comparable_properties_avg_rent, demand_index
Output:   {suggested_rent, confidence_score, justification}
Trigger:  Weekly batch + on flat.vacated event
Action:   Always publish ai.rent.adjustment.suggested (owner reviews)
```

**Module 4: Lease Renewal & Churn**
```
Model:    Logistic Regression + Cox Proportional Hazards (survival)
Inputs:   lease_tenure_months, payment_history_score, maintenance_complaint_count,
          days_until_expiry, rent_vs_market_ratio, satisfaction_score
Output:   {renewal_probability, churn_risk, recommended_action, discount_suggested}
Trigger:  Daily batch for leases expiring in 60 days
Action:   If churn_risk > 65 → publish ai.lease.renewal.strategy
```

**Module 5: Document AI**
```
Technology: Apache Tika (text extraction) + Tesseract OCR + custom NLP
Inputs:     Uploaded Aadhaar, PAN card, rent agreements, ID proofs (PDF/JPG/PNG)
Output:     {extracted_name, extracted_address, document_type,
             extracted_id_number, fraud_flag, confidence_score}
Trigger:    On document.uploaded event
Action:     Publish ai.document.extracted → auto-fill tenant profile
```

**Module 6: Multilingual AI Assistant**
```
Technology: Claude API (claude-sonnet-4-6) via AI Gateway (8089)
Languages:  Hindi, Tamil, Telugu, Kannada, Marathi, English
Channels:   WhatsApp Business API, in-app chat, SMS
Personas:   - Owner Assistant: AI insights, property analytics
             - Tenant Assistant: maintenance requests, payment queries
Actions:    Route to appropriate service based on intent detection
```

---

### 2.2 LEASE SERVICE (8090)

```sql
CREATE TABLE leases (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    flat_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    lease_number VARCHAR(50) UNIQUE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    rent_amount DECIMAL(10,2) NOT NULL,
    security_deposit DECIMAL(10,2),
    rent_increment_percent DECIMAL(5,2) DEFAULT 5.0,
    status VARCHAR(20) DEFAULT 'ACTIVE',   -- DRAFT | ACTIVE | EXPIRED | TERMINATED
    rera_agreement_number VARCHAR(100),     -- India compliance
    document_url VARCHAR(500),
    digital_signature_status VARCHAR(20),  -- PENDING | SIGNED | REJECTED
    ai_renewal_probability DECIMAL(5,4),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE lease_history (
    id BIGSERIAL PRIMARY KEY,
    lease_id BIGINT REFERENCES leases(id),
    event_type VARCHAR(50),    -- CREATED | RENEWED | AMENDED | TERMINATED
    previous_rent DECIMAL(10,2),
    new_rent DECIMAL(10,2),
    changed_by BIGINT,
    changed_at TIMESTAMP DEFAULT NOW(),
    notes TEXT
);
```

APIs:
```
POST   /api/lease/leases                    - Create new lease
GET    /api/lease/leases/{id}               - Get lease by ID
GET    /api/lease/leases/tenant/{tenantId}  - Get tenant's leases
GET    /api/lease/leases/flat/{flatId}      - Get flat's lease history
PUT    /api/lease/leases/{id}/renew         - Renew lease
PUT    /api/lease/leases/{id}/terminate     - Terminate lease
POST   /api/lease/leases/{id}/sign          - Digital signature trigger
GET    /api/lease/leases/{id}/document      - Download lease PDF
GET    /api/lease/expiring?days=60          - Leases expiring in N days
POST   /api/lease/leases/generate-rera      - Generate RERA compliant lease
```

Kafka Events Published:
- lease.signed, lease.expiring (60-day cron job), lease.renewed, lease.terminated

---

### 2.3 DOCUMENT SERVICE (8091)

```sql
CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    document_type VARCHAR(50),  -- AADHAAR | PAN | AGREEMENT | PHOTO | OTHER
    original_filename VARCHAR(255),
    storage_url VARCHAR(1000),     -- S3/GCS path (encrypted)
    content_type VARCHAR(100),
    file_size_bytes BIGINT,
    ocr_status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING | PROCESSING | DONE | FAILED
    extracted_data JSONB,          -- OCR/AI extracted key-value pairs
    fraud_flag BOOLEAN DEFAULT FALSE,
    confidence_score DECIMAL(5,4),
    verified_by VARCHAR(50),       -- SYSTEM | ADMIN | KYC_PROVIDER
    uploaded_at TIMESTAMP DEFAULT NOW(),
    verified_at TIMESTAMP
);
```

APIs:
```
POST   /api/documents/upload              - Upload document (multipart)
GET    /api/documents/{id}                - Get document metadata
GET    /api/documents/user/{userId}       - Get user's documents
GET    /api/documents/{id}/download       - Secure download URL (pre-signed)
POST   /api/documents/{id}/extract        - Trigger OCR extraction
GET    /api/documents/{id}/extracted-data - Get AI extracted data
DELETE /api/documents/{id}               - Soft delete document
```

---

### 2.4 KYC SERVICE (8092)

```sql
CREATE TABLE kyc_records (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    kyc_provider VARCHAR(50),   -- DIGIO | SIGNZY | MANUAL
    aadhaar_number_hash VARCHAR(255),  -- Hashed, never store plain
    pan_number VARCHAR(20),
    verification_status VARCHAR(20) DEFAULT 'PENDING',
    aadhaar_verified BOOLEAN DEFAULT FALSE,
    pan_verified BOOLEAN DEFAULT FALSE,
    face_match_score DECIMAL(5,4),
    digilocker_linked BOOLEAN DEFAULT FALSE,
    consent_recorded BOOLEAN DEFAULT FALSE,
    kyc_reference_id VARCHAR(100),
    verified_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);
```

APIs:
```
POST   /api/kyc/initiate/{userId}          - Start Aadhaar KYC flow
GET    /api/kyc/status/{userId}            - Check KYC status
POST   /api/kyc/verify-pan                 - Verify PAN number
POST   /api/kyc/digilocker/link            - Link DigiLocker
GET    /api/kyc/report/{userId}            - Full KYC report
POST   /api/kyc/webhook/digio              - Digio callback webhook
```

Kafka Events Published:
- kyc.verified, kyc.failed, kyc.pan.verified

Third-party Integrations: Digio (https://www.digio.in), Signzy

---

### 2.5 COMPLIANCE SERVICE (8093)

```sql
CREATE TABLE rera_registrations (
    id BIGSERIAL PRIMARY KEY,
    property_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    state VARCHAR(50),                    -- Karnataka, Maharashtra, etc.
    rera_registration_number VARCHAR(100),
    rera_portal_id VARCHAR(100),
    registration_status VARCHAR(20),      -- PENDING | REGISTERED | EXPIRED
    registered_at TIMESTAMP,
    expiry_date DATE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE gst_invoices (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL UNIQUE,
    tenant_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    invoice_number VARCHAR(50) UNIQUE,
    invoice_date DATE,
    rent_amount DECIMAL(10,2),
    gst_applicable BOOLEAN DEFAULT FALSE, -- True if monthly rent > 20L
    gst_amount DECIMAL(10,2),
    total_amount DECIMAL(10,2),
    pdf_url VARCHAR(500),
    sent_via_whatsapp BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);
```

APIs:
```
POST   /api/compliance/rera/register             - Register property on RERA
GET    /api/compliance/rera/status/{propertyId}  - Check RERA status
POST   /api/compliance/gst/generate/{paymentId}  - Generate GST invoice
GET    /api/compliance/gst/invoice/{id}/pdf      - Download GST invoice
POST   /api/compliance/lease/generate-rera/{leaseId} - Generate RERA lease
```

Kafka Events Published:
- rera.registered, gst.invoice.generated

---

### 2.6 REVIEW SERVICE (8094)

```javascript
// MongoDB Collection
reviews {
    _id: ObjectId,
    reviewer_id: Long,
    reviewer_type: String,    // TENANT | OWNER
    target_id: Long,
    target_type: String,      // PROPERTY | OWNER | TENANT
    rating: Number,           // 1-5
    title: String,
    body: String,
    tags: [String],
    ai_sentiment: String,     // POSITIVE | NEUTRAL | NEGATIVE
    ai_sentiment_score: Number,
    is_verified: Boolean,     // Verified tenancy
    is_moderated: Boolean,
    created_at: Date
}
```

APIs:
```
POST   /api/reviews                           - Submit review
GET    /api/reviews/property/{propertyId}     - Property reviews
GET    /api/reviews/owner/{ownerId}           - Owner reviews
GET    /api/reviews/tenant/{tenantId}         - Tenant reviews
PUT    /api/reviews/{id}/moderate             - Admin moderate
GET    /api/reviews/summary/{targetId}        - Rating summary + AI insights
```

---

### 2.7 AI GATEWAY (8089) — LLM Proxy

```java
// Spring Boot service acting as LLM proxy and WhatsApp integration hub

// Endpoints:
POST /api/llm/chat           - Tenant/Owner chat → Claude API
POST /api/llm/whatsapp/webhook - WhatsApp Business API webhook
POST /api/llm/analyze-document - Document AI analysis
POST /api/llm/generate-lease   - AI-assisted lease draft

// Claude API integration:
ClaudeClient configured with:
    model: claude-sonnet-4-6
    system_prompt: "You are RentGenius AI assistant. You help landlords 
                    and tenants in India manage rental properties.
                    Languages supported: Hindi, Tamil, Telugu, Kannada, English"
    
// WhatsApp Business API integration:
    Provider: Meta WhatsApp Business API (or Twilio)
    Templates: HIGH_RISK_ALERT, MAINTENANCE_NOTICE, RENT_SUGGESTION,
               LEASE_RENEWAL_OFFER, PAYMENT_CONFIRMED, WELCOME_MESSAGE
```

---

## SECTION 3: COMPLETE KAFKA EVENT CATALOG

### Existing Events (Working in your code)

| Topic | Event Key | Publisher | Consumers |
|---|---|---|---|
| user-events | user.registered | Auth | User, Notification |
| user-events | user.profile.created | User | Notification |
| user-events | user.login | Auth | Analytics |
| property-events | property.created | Property | Analytics |
| property-events | property.updated | Property | Analytics |
| payment-events | payment.created | Payment | Notification |
| payment-events | payment.completed | Payment | AI Engine, Notification, Analytics |
| payment-events | payment.overdue | Payment | Notification, AI Engine |
| payment-events | payment.failed | Payment | Notification |
| maintenance-events | maintenance.created | Maintenance | Notification, AI Engine |
| maintenance-events | maintenance.assigned | Maintenance | Notification |
| maintenance-events | maintenance.resolved | Maintenance | Analytics, AI Engine |
| notification-events | notification.sent | Notification | Analytics |

### Events to Complete (Referenced but Not Yet Published)

| Topic | Event Key | Where to Add | Consumers |
|---|---|---|---|
| property-events | flat.occupied | Property assignTenant() | Payment, Analytics, AI Engine |
| property-events | flat.vacated | Property vacateFlat() | Payment, Maintenance, AI Engine |
| user-events | owner.registered | User createOwner() | Notification |
| payment-events | payment.reminder | Payment scheduler | Notification |

### New Events (From New Services)

| Topic | Event Key | Publisher | Consumers |
|---|---|---|---|
| upi-events | upi.autopay.settled | Payment webhook | AI Engine, Notification, Analytics |
| kyc-events | kyc.verified | KYC Service | User, Notification |
| kyc-events | kyc.failed | KYC Service | Notification |
| kyc-events | kyc.pan.verified | KYC Service | User |
| lease-events | lease.signed | Lease Service | Property, Notification, Analytics |
| lease-events | lease.expiring | Lease cron job | AI Engine, Notification |
| lease-events | lease.renewed | Lease Service | Analytics, Notification |
| lease-events | lease.terminated | Lease Service | Property, Analytics |
| compliance-events | rera.registered | Compliance | Notification |
| compliance-events | gst.invoice.generated | Compliance | Notification |
| document-events | document.uploaded | Document Service | AI Engine |
| document-events | document.verified | Document Service | KYC, User |

### AI Output Events (Published by AI Decision Engine)

| Topic | Event Key | Trigger Condition | Consumers |
|---|---|---|---|
| ai-events | ai.tenant.risk.detected | risk_score > 70 | Notification, User, Analytics |
| ai-events | ai.maintenance.predicted | confidence > 0.75 | Maintenance, Notification |
| ai-events | ai.rent.adjustment.suggested | weekly batch | Notification, Analytics |
| ai-events | ai.lease.renewal.strategy | churn_risk > 65 | Notification, Analytics |
| ai-events | ai.churn.predicted | churn_probability > 0.6 | Notification, Analytics |
| ai-events | ai.document.extracted | on document upload | KYC, User |

---

## SECTION 4: DATABASE SUMMARY

### PostgreSQL Databases

| Database | Owner Service | Key Tables |
|---|---|---|
| auth_db | Auth Service | users, roles, refresh_tokens |
| property_db | Property Service | buildings, flats, property_images |
| user_db | User Service | users, owners, emergency_contacts |
| payment_db | Payment Service | payments, invoices, receipts, payment_reminders |
| analytics_db | Analytics Service | revenue_summary, occupancy_stats, payment_trends |
| ai_db | AI Decision Engine | tenant_risk_scores, predicted_maintenance, rent_recommendations, lease_predictions |
| lease_db | Lease Service | leases, lease_history |
| document_db | Document Service | documents |
| kyc_db | KYC Service | kyc_records |
| compliance_db | Compliance Service | rera_registrations, gst_invoices |

### MongoDB Databases

| Database | Owner Service | Collections |
|---|---|---|
| maintenance_db | Maintenance Service | maintenance_requests |
| notification_db | Notification Service | notifications, notification_templates, user_preferences, whatsapp_logs |
| review_db | Review Service | reviews |

### Redis (Shared)

```
# Feature Store (AI Engine)
tenant:features:{id}        → HashMap (payment_delay_frequency, avg_delay_days, ...)
property:features:{id}      → HashMap (occupancy_rate, maintenance_frequency, ...)

# Cache
analytics:revenue:{ownerId} → Cached revenue summary (TTL: 1h)
property:vacant:{city}      → Cached vacant listings (TTL: 15m)

# Session / Rate Limiting (existing)
session:{token}             → User session data
rate:{ip}                   → Request counter
```

---

## SECTION 5: COMPLETE API GATEWAY ROUTES

```yaml
routes:
  - id: auth-service
    uri: lb://AUTH-SERVICE
    predicates: [Path=/api/auth/**]

  - id: property-service
    uri: lb://PROPERTY-SERVICE
    predicates: [Path=/api/properties/**]

  - id: user-service
    uri: lb://USER-SERVICE
    predicates: [Path=/api/users/**]

  - id: payment-service
    uri: lb://PAYMENT-SERVICE
    predicates: [Path=/api/payments/**]

  - id: maintenance-service
    uri: lb://MAINTENANCE-SERVICE
    predicates: [Path=/api/maintenance/**]

  - id: analytics-service
    uri: lb://ANALYTICS-SERVICE
    predicates: [Path=/api/analytics/**]

  - id: ai-decision-engine         # NEW
    uri: lb://AI-DECISION-ENGINE
    predicates: [Path=/api/ai/**]

  - id: lease-service              # NEW
    uri: lb://LEASE-SERVICE
    predicates: [Path=/api/lease/**]

  - id: document-service           # NEW
    uri: lb://DOCUMENT-SERVICE
    predicates: [Path=/api/documents/**]

  - id: kyc-service                # NEW
    uri: lb://KYC-SERVICE
    predicates: [Path=/api/kyc/**]

  - id: compliance-service         # NEW
    uri: lb://COMPLIANCE-SERVICE
    predicates: [Path=/api/compliance/**]

  - id: review-service             # NEW
    uri: lb://REVIEW-SERVICE
    predicates: [Path=/api/reviews/**]

  - id: ai-gateway                 # NEW
    uri: lb://AI-GATEWAY
    predicates: [Path=/api/llm/**]
```

---

## SECTION 6: DOCKER-COMPOSE ADDITIONS

Add these to your existing docker-compose.yml. Do NOT modify existing service definitions.

```yaml
# ADD to existing docker-compose.yml

  # New Databases
  clickhouse:
    image: clickhouse/clickhouse-server:latest
    ports:
      - "8123:8123"
      - "9000:9000"
    volumes:
      - clickhouse_data:/var/lib/clickhouse

  # Python AI Service
  ai-engine-python:
    build: ./ai-engine-python
    ports:
      - "8098:8098"
    environment:
      - REDIS_HOST=redis
      - POSTGRES_HOST=postgres
    depends_on:
      - redis
      - postgres

  # New Spring Boot Services
  ai-decision-engine:
    build: ./ai-decision-engine
    ports:
      - "8088:8088"
    environment:
      - SPRING_APPLICATION_NAME=AI-DECISION-ENGINE
      - EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://eureka-server:8761/eureka/
      - AI_PYTHON_URL=http://ai-engine-python:8098
    depends_on:
      - eureka-server
      - kafka
      - redis
      - ai-engine-python

  lease-service:
    build: ./lease-service
    ports:
      - "8090:8090"
    environment:
      - SPRING_APPLICATION_NAME=LEASE-SERVICE
      - EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://eureka-server:8761/eureka/
    depends_on:
      - eureka-server
      - postgres
      - kafka

  document-service:
    build: ./document-service
    ports:
      - "8091:8091"
    environment:
      - SPRING_APPLICATION_NAME=DOCUMENT-SERVICE
      - AWS_S3_BUCKET=rentgenius-documents
    depends_on:
      - eureka-server
      - kafka

  kyc-service:
    build: ./kyc-service
    ports:
      - "8092:8092"
    environment:
      - SPRING_APPLICATION_NAME=KYC-SERVICE
      - DIGIO_API_KEY=${DIGIO_API_KEY}
    depends_on:
      - eureka-server
      - postgres
      - kafka

  compliance-service:
    build: ./compliance-service
    ports:
      - "8093:8093"
    environment:
      - SPRING_APPLICATION_NAME=COMPLIANCE-SERVICE
      - RAZORPAY_KEY_ID=${RAZORPAY_KEY_ID}
    depends_on:
      - eureka-server
      - postgres
      - kafka

  review-service:
    build: ./review-service
    ports:
      - "8094:8094"
    environment:
      - SPRING_APPLICATION_NAME=REVIEW-SERVICE
    depends_on:
      - eureka-server
      - mongodb
      - kafka

  ai-gateway:
    build: ./ai-gateway
    ports:
      - "8089:8089"
    environment:
      - SPRING_APPLICATION_NAME=AI-GATEWAY
      - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}
      - WHATSAPP_API_TOKEN=${WHATSAPP_API_TOKEN}
    depends_on:
      - eureka-server

volumes:
  clickhouse_data:
```

---

## SECTION 7: PROJECT FOLDER STRUCTURE

```
Home-Rental-Application/
├── .gitignore
├── docker-compose.yml                  (EXISTING — add new services)
├── docker-compose.ai.yml               (NEW — AI services only)
│
├── ARCHITECTURE.md                     (THIS FILE — master reference)
│
├── KafkaEvents/                        (EXISTING — add 8 new event classes)
├── Service-Registry/                   (EXISTING — no changes)
├── config-server/                      (EXISTING — add configs for new services)
├── api-gateway/                        (EXISTING — add routes)
│
├── auth-service/                       (EXISTING — no changes)
├── property-service/                   (EXISTING — add 2 events + 1 column)
├── user-service/                       (EXISTING — add 3 columns + 2 consumers)
├── payment-service/                    (EXISTING — add UPI webhook)
├── maintenance-service/                (EXISTING — add ai_predicted field)
├── notification-service/               (EXISTING — add WhatsApp + AI consumers)
├── analytics-service/                  (EXISTING — add AI insight APIs)
│
├── ai-decision-engine/                 (NEW — Spring Boot 8088)
│   ├── src/main/java/
│   │   └── com/rentgenius/ai/
│   │       ├── AiDecisionEngineApplication.java
│   │       ├── consumer/               (Kafka consumers)
│   │       ├── service/                (Feature builder, AI caller)
│   │       ├── publisher/              (Kafka AI event publishers)
│   │       ├── controller/             (REST /api/ai/**)
│   │       └── model/                  (DB entities)
│   └── pom.xml
│
├── ai-engine-python/                   (NEW — Python FastAPI 8098)
│   ├── main.py
│   ├── models/                         (Trained .pkl files)
│   ├── endpoints/
│   ├── feature_engineering/
│   └── requirements.txt
│
├── lease-service/                      (NEW — Spring Boot 8090)
├── document-service/                   (NEW — Spring Boot 8091)
├── kyc-service/                        (NEW — Spring Boot 8092)
├── compliance-service/                 (NEW — Spring Boot 8093)
├── review-service/                     (NEW — Spring Boot 8094)
├── ai-gateway/                         (NEW — Spring Boot 8089)
│
├── mobile-app/                         (NEW — React Native)
│   ├── owner-app/
│   └── tenant-app/
│
└── infrastructure/
    ├── kubernetes/                     (K8s manifests)
    ├── monitoring/                     (Prometheus + Grafana configs)
    └── scripts/                        (DB migration scripts)
```

---

## SECTION 8: IMPLEMENTATION PHASES

### Phase 0 — Complete Existing Gaps (Week 1, estimated 2-3 days)

DO THIS FIRST before building anything new.

- [ ] Add flat.occupied event to Property Service assignTenant()
- [ ] Add flat.vacated event to Property Service vacateFlat()
- [ ] Add owner.registered event to User Service createOwner()
- [ ] Add payment.reminder event to Payment Service scheduler
- [ ] Run DB migration: add nullable columns to flats, users, payments tables
- [ ] Add 8 new event classes to KafkaEvents module
- [ ] Add 6 new routes to API Gateway config

---

### Phase 1 — AI Decision Engine (Weeks 2-4, core AI)

This is the most critical new service. Build it next.

- [ ] Set up ai-decision-engine Spring Boot project
- [ ] Set up ai-engine-python FastAPI project
- [ ] Implement Kafka consumers for all topics
- [ ] Build Redis feature store pipeline
- [ ] Train Tenant Risk model (XGBoost) — use synthetic data initially
- [ ] Train Rent Optimizer (Gradient Boosting) — use NoBroker/99acres data
- [ ] Connect Spring Boot shell to Python via HTTP
- [ ] Add notification-service consumers for ai-events topic
- [ ] Wire analytics-service to consume ai-events

---

### Phase 2 — India Compliance Layer (Weeks 5-6)

- [ ] Build KYC Service (Aadhaar + PAN via Digio SDK)
- [ ] Build Lease Service (CRUD + RERA lease generation)
- [ ] Build Compliance Service (RERA registration + GST invoice)
- [ ] Build Document Service (upload + OCR extraction)
- [ ] Integrate Document AI into AI Engine (Module 5)

---

### Phase 3 — Advanced AI Modules (Weeks 7-8)

- [ ] Implement Predictive Maintenance (LSTM model)
- [ ] Implement Lease Renewal + Churn Engine
- [ ] Build Review Service (+ AI sentiment analysis)
- [ ] Add ClickHouse analytics pipeline
- [ ] Integrate UPI AutoPay (Razorpay mandate setup)

---

### Phase 4 — Mobile + AI Assistant (Weeks 9-12)

- [ ] Build AI Gateway (Claude API proxy + WhatsApp webhook)
- [ ] Build React Native mobile app (Owner + Tenant apps)
- [ ] Implement Multilingual AI Assistant
- [ ] WhatsApp Business API integration for all AI notifications
- [ ] End-to-end testing all AI flows
- [ ] Play Store + App Store submission
- [ ] Deploy to AWS Mumbai (production)

---

## SECTION 9: TECHNOLOGY STACK

```yaml
Backend:
  Framework:     Spring Boot 3.x (Java 21)
  Cloud:         Spring Cloud (Gateway, Config, Eureka, Feign)
  Messaging:     Apache Kafka + Kafka Streams
  Security:      Spring Security + JWT

AI/ML:
  Framework:     Python 3.11, FastAPI, Uvicorn
  Models:        scikit-learn, XGBoost, PyTorch (LSTM)
  LLM:           Claude API (claude-sonnet-4-6) via Anthropic SDK
  OCR:           Apache Tika + Tesseract OCR

Databases:
  RDBMS:         PostgreSQL 16
  Document:      MongoDB 7
  Cache:         Redis 7
  Analytics:     ClickHouse (OLAP, ML training data)
  Object Store:  AWS S3 / Google Cloud Storage

India Integrations:
  Payments:      Razorpay (UPI AutoPay + mandates)
  KYC:           Digio (Aadhaar eKYC + DigiLocker)
  WhatsApp:      Meta WhatsApp Business API
  SMS:           MSG91 or Twilio India
  Email:         SendGrid or AWS SES

Mobile:
  Framework:     React Native + Expo
  State:         Redux Toolkit
  Auth:          JWT stored in secure keychain

Infrastructure:
  Container:     Docker + Docker Compose (dev)
  Orchestration: Kubernetes (prod) — AWS EKS Mumbai region
  CI/CD:         GitHub Actions
  Monitoring:    Prometheus + Grafana
  Tracing:       Zipkin
  Logging:       ELK Stack (Elasticsearch + Logstash + Kibana)
```

---

## SECTION 10: SECURITY MEASURES

1. API Gateway: JWT validation on all routes except /api/auth/login and /api/auth/register
2. Service-to-service: Internal calls use service accounts, not JWT
3. Aadhaar data: Hash immediately, never store plain text, comply with DPDP Act 2023
4. PAN numbers: Store encrypted (AES-256)
5. Document storage: Encrypted at rest in S3, pre-signed URLs with 15-minute expiry
6. WhatsApp webhooks: Verify Meta signature on every request
7. Razorpay webhooks: Verify Razorpay signature on every request
8. Rate limiting: 100 req/min per IP on gateway, 20 req/min on AI endpoints
9. KYC consent: Record explicit consent before any UIDAI API call (DPDP compliance)

---

## SECTION 11: MONITORING & OBSERVABILITY

```yaml
Metrics (Prometheus + Grafana):
  - Request rate per service
  - AI model prediction latency (p50, p95, p99)
  - Kafka consumer lag per topic
  - AI event throughput (predictions/hour)
  - Feature store Redis hit rate
  - UPI webhook success rate

Alerts:
  - AI Engine Python pod down → PagerDuty
  - Kafka consumer lag > 1000 → Slack
  - Risk score > 90 for any tenant → Immediate notification
  - Payment failure rate > 5% → Alert

Tracing (Zipkin):
  - Trace full AI decision flow: event received → feature computed → prediction → action

Dashboards:
  - Owner Dashboard: AI insights, risk scores, rent suggestions
  - System Dashboard: Service health, Kafka lag, model metrics
  - AI Performance Dashboard: Model accuracy, prediction volume, action rates
```

---

*RentGenius AI — India's First Autonomous AI Rental Platform*
*Architecture Version 3.0 | April 2026*
*This document supersedes all previous architecture files.*

---
END OF DOCUMENT
