# HOME RENTAL APPLICATION - AUTONOMOUS AI MICROSERVICES ARCHITECTURE

### Complete System Design & Implementation Guide (With AI Decision Engine)

------------------------------------------------------------------------

## SYSTEM ARCHITECTURE OVERVIEW

                                        +-------------------------------------+
                                        |            FRONTEND LAYER           |
                                        |      (Web / Mobile / Admin App)     |
                                        +------------------+------------------+
                                                           |
                                                           | HTTPS
                                                           v
                            +--------------------------------------------------+
                            |             API GATEWAY (Port: 8080)             |
                            | Routing | JWT | Rate Limiting | Logging          |
                            +------------------+-------------------------------+
                                               |
            +----------------------------------+----------------------------------+
            v                                  v                                  v
    +---------------+                +----------------+                +----------------+
    | AUTH SERVICE  |                | SERVICE REGISTRY|               | CONFIG SERVER  |
    | (Port 8081)   |                | EUREKA (8761)  |               | (Port 8888)    |
    | PostgreSQL    |                +----------------+                | Git/Local      |
    +---------------+                                                +----------------+

    ----------------------------- BUSINESS MICROSERVICES --------------------------------

    +---------------+   +---------------+   +---------------+
    | PROPERTY SVC  |   | USER SVC      |   | PAYMENT SVC   |
    | (8082)        |   | (8083)        |   | (8084)        |
    | PostgreSQL    |   | PostgreSQL    |   | PostgreSQL    |
    +-------+-------+   +-------+-------+   +-------+-------+
            |                   |                   |
            +-------------------+-------------------+
                                v
                        +---------------------+
                        |     KAFKA BUS       |
                        |  (Event Streaming)  |
                        +----------+----------+
                                   |
             +---------------------+---------------------+
             v                     v                     v
    +---------------+     +----------------+     +----------------+
    | MAINTENANCE   |     | NOTIFICATION   |     | ANALYTICS SVC  |
    | (8085)        |     | (8086)         |     | (8087)         |
    | MongoDB       |     | MongoDB        |     | PostgreSQL     |
    +---------------+     +----------------+     +----------------+
                                   |
                                   v
                        +-----------------------------+
                        |   AI DECISION ENGINE (8088) |
                        |  Autonomous Intelligence    |
                        +-----------------------------+

------------------------------------------------------------------------

## AI DECISION ENGINE SERVICE

Port: 8088\
Technology: Spring Boot / FastAPI + Kafka Streams + ML Framework\
Database: PostgreSQL + Redis Feature Store

### What it does:

-   Consumes all lifecycle events from Kafka
-   Builds behavioral features in real time
-   Runs predictive ML models
-   Publishes AI decision events
-   Triggers automatic system actions

------------------------------------------------------------------------

## AI MODULES

### 1. Tenant Risk Prediction Engine

Purpose: Predicts probability of tenant default or churn.

Input Events: - payment.completed - payment.overdue -
maintenance.created - flat.occupied - flat.vacated

Generated Features: - payment_delay_frequency - avg_payment_delay_days -
complaint_frequency - maintenance_cost_per_tenant -
lease_duration_pattern

Output Table: tenant_risk_scores( tenant_id, risk_score,
default_probability, churn_probability, last_updated )

AI Events Published: - ai.tenant.risk.detected

Autonomous Actions: - Increase reminder frequency - Notify owner - Flag
high-risk tenant

------------------------------------------------------------------------

### 2. Predictive Maintenance Engine

Purpose: Predicts maintenance issues before complaint.

Input Data: - maintenance history - property age - resolution time -
seasonal trends

Output Table: predicted_maintenance( property_id, issue_type,
predicted_date, confidence_score )

AI Events Published: - ai.maintenance.predicted

Autonomous Actions: - Auto-create maintenance request - Assign
technician - Notify tenant and owner

------------------------------------------------------------------------

### 3. Dynamic Rent Optimization Engine

Purpose: Suggest optimal rent pricing.

Input Data: - occupancy rate - tenant churn - payment trends - demand
signals

Output Table: rent_recommendations( property_id, current_rent,
suggested_rent, confidence_score )

AI Events Published: - ai.rent.adjustment.suggested

Autonomous Actions: - Generate rent revision proposal - Notify owner -
Prepare lease update draft

------------------------------------------------------------------------

### 4. Lease Renewal and Churn Engine

Purpose: Predicts lease renewal probability.

Output Table: lease_predictions( tenant_id, renewal_probability,
churn_risk, recommended_action )

AI Events Published: - ai.lease.renewal.strategy

Autonomous Actions: - Send renewal offers - Suggest discount strategy -
Alert owner before vacancy

------------------------------------------------------------------------

## UPDATED KAFKA TOPICS

Existing Topics: - user-events - property-events - payment-events -
maintenance-events - notification-events

New AI Topic: - ai-events - ai.tenant.risk.detected -
ai.maintenance.predicted - ai.rent.adjustment.suggested -
ai.lease.renewal.strategy

------------------------------------------------------------------------

## END TO END AI FLOW

1.  Tenant pays rent
2.  payment.completed event published
3.  AI Engine updates risk model
4.  Risk threshold crossed
5.  ai.tenant.risk.detected published
6.  Notification & Payment services act automatically

------------------------------------------------------------------------

## DATABASE SUMMARY

PostgreSQL: - auth_db - property_db - user_db - payment_db -
analytics_db - ai_db

MongoDB: - maintenance_db - notification_db

Redis: - Feature store - Caching - Rate limiting

------------------------------------------------------------------------

## FINAL SYSTEM CHARACTERISTICS

-   Event-driven microservices architecture
-   Autonomous AI-driven decision making
-   Closed-loop lifecycle optimization
-   Scalable and cloud-ready design
-   Patent-ready AI integration model

------------------------------------------------------------------------

END OF DOCUMENT
