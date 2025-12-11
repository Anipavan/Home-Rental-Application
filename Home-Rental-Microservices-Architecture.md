# 🏠 HOME RENTAL APPLICATION - MICROSERVICES ARCHITECTURE
### Complete System Design & Implementation Guide

---

## 📊 SYSTEM ARCHITECTURE OVERVIEW

```
                                    ┌─────────────────────────────────────┐
                                    │         FRONTEND LAYER              │
                                    │   (React / Angular / Mobile App)    │
                                    └──────────────┬──────────────────────┘
                                                   │
                                                   │ HTTPS
                                                   ↓
                        ┌──────────────────────────────────────────────────┐
                        │         API GATEWAY (Port: 8080)                 │
                        │      Spring Cloud Gateway + Load Balancer        │
                        │   - Routing  - Rate Limiting  - JWT Validation   │
                        └──────────────┬───────────────────────────────────┘
                                       │
                ┌──────────────────────┼──────────────────────┐
                ↓                      ↓                      ↓
        ┌───────────────┐      ┌───────────────┐     ┌───────────────┐
        │ AUTH SERVICE  │      │SERVICE REGISTRY│     │CONFIG SERVER  │
        │  (Port: 8081) │      │  EUREKA SERVER │     │  (Port: 8888) │
        │  PostgreSQL   │      │  (Port: 8761)  │     │  Git/Local    │
        └───────────────┘      └───────────────┘     └───────────────┘
                                       ↑
                ┌──────────────────────┼──────────────────────────┐
                ↓                      ↓                           ↓
        ┌───────────────┐      ┌───────────────┐         ┌───────────────┐
        │ PROPERTY SVC  │      │   USER SVC    │         │  PAYMENT SVC  │
        │ (Port: 8082)  │      │ (Port: 8083)  │         │ (Port: 8084)  │
        │  PostgreSQL   │      │  PostgreSQL   │         │  PostgreSQL   │
        └───────┬───────┘      └───────┬───────┘         └───────┬───────┘
                │                      │                         │
                └──────────────────────┼─────────────────────────┘
                                       ↓
                        ┌──────────────────────────┐
                        │     KAFKA CLUSTER        │
                        │    (Message Bus)         │
                        │   Topics: payment-events │
                        │   property-events, etc.  │
                        └──────────┬───────────────┘
                                   │
                ┌──────────────────┼────────────────────────┐
                ↓                  ↓                        ↓
        ┌───────────────┐  ┌───────────────┐      ┌───────────────┐
        │MAINTENANCE SVC│  │NOTIFICATION SVC│      │ ANALYTICS SVC │
        │ (Port: 8085)  │  │ (Port: 8086)  │      │ (Port: 8087)  │
        │   MongoDB     │  │   MongoDB     │      │ PostgreSQL +  │
        └───────────────┘  └───────────────┘      │    Redis      │
                                                    └───────────────┘
                                   ↓
                        ┌──────────────────────────┐
                        │   MONITORING & LOGGING   │
                        │  Prometheus + Grafana    │
                        │  ELK Stack (Optional)    │
                        └──────────────────────────┘
```

---

## 🔧 MICROSERVICES DETAILED BREAKDOWN

---

### 1️⃣ API GATEWAY SERVICE
**Port:** 8080  
**Technology:** Spring Cloud Gateway  
**Database:** None (stateless)

#### **What it does:**
- Single entry point for all client requests
- Routes requests to appropriate microservices
- Load balancing across service instances
- JWT token validation
- Rate limiting and throttling
- Request/Response logging
- Circuit breaker pattern

#### **Key Features:**
```yaml
Routes:
  - /api/auth/**        → Auth Service (8081)
  - /api/properties/**  → Property Service (8082)
  - /api/users/**       → User Service (8083)
  - /api/payments/**    → Payment Service (8084)
  - /api/maintenance/** → Maintenance Service (8085)
  - /api/analytics/**   → Analytics Service (8087)
```

#### **APIs:**
```
No direct APIs - Acts as reverse proxy
All requests pass through gateway
Example: GET /api/properties/buildings → routed to Property Service
```

---

### 2️⃣ AUTHENTICATION SERVICE
**Port:** 8081  
**Technology:** Spring Boot + Spring Security + JWT  
**Database:** PostgreSQL (users, roles, tokens)

#### **What it does:**
- User authentication (login/logout)
- JWT token generation and validation
- Role-based access control (RBAC)
- Password encryption and management
- Refresh token mechanism
- Session management

#### **Database Tables:**
```sql
users (id, username, email, password_hash, role, is_active, created_at)
roles (id, role_name, permissions)
user_roles (user_id, role_id)
refresh_tokens (id, user_id, token, expiry_date)
```

#### **APIs:**
```
POST   /api/auth/register          - Register new user
POST   /api/auth/login             - Login and get JWT token
POST   /api/auth/refresh           - Refresh JWT token
POST   /api/auth/logout            - Logout user
POST   /api/auth/forgot-password   - Request password reset
POST   /api/auth/reset-password    - Reset password with token
GET    /api/auth/validate          - Validate JWT token
```

#### **Roles:**
- **ADMIN**: Full system access
- **OWNER**: Manage properties, view payments, tenants
- **TENANT**: View own details, make payments, raise requests

#### **Kafka Events Published:**
```
- user.registered (userId, email, role, timestamp)
- user.login (userId, loginTime)
- user.logout (userId, logoutTime)
```

---

### 3️⃣ PROPERTY SERVICE
**Port:** 8082  
**Technology:** Spring Boot + JPA  
**Database:** PostgreSQL

#### **What it does:**
- Manage buildings (add, update, delete, view)
- Manage flats/apartments within buildings
- Track occupancy status
- Link properties to owners
- Assign tenants to flats
- Track property history

#### **Database Tables:**
```sql
buildings (
    id, name, address, city, state, pincode, 
    total_floors, total_flats, owner_id, 
    amenities, created_at, updated_at
)

flats (
    id, building_id, flat_number, floor, 
    bedrooms, bathrooms, area_sqft, 
    rent_amount, is_occupied, tenant_id,
    lease_start_date, lease_end_date,
    created_at, updated_at
)

property_images (
    id, property_id, image_url, type
)
```

#### **APIs:**

**Building Management:**
```
GET    /api/properties/buildings              - Get all buildings - done
GET    /api/properties/buildings/{id}         - Get building by ID - done
POST   /api/properties/buildings              - Create new building - done
PUT    /api/properties/buildings/{id}         - Update building 
DELETE /api/properties/buildings/{id}         - Delete building - done
GET    /api/properties/buildings/owner/{id}   - Get buildings by owner
```

**Flat Management:**
```
GET    /api/properties/flats                  - Get all flats - done
GET    /api/properties/flats/{id}             - Get flat by ID - done
POST   /api/properties/flats                  - Create new flat - done
PUT    /api/properties/flats/{id}             - Update flat
DELETE /api/properties/flats/{id}             - Delete flat - done
GET    /api/properties/flats/building/{id}    - Get flats by building -done
GET    /api/properties/flats/vacant           - Get all vacant flats -  done
POST   /api/properties/flats/{id}/assign      - Assign tenant to flat
POST   /api/properties/flats/{id}/vacate      - Mark flat as vacant - done
```

#### **Kafka Events Published:**
```
- property.created (propertyId, ownerId, timestamp)
- property.updated (propertyId, changes, timestamp)
- flat.occupied (flatId, tenantId, rentAmount, startDate)
- flat.vacated (flatId, tenantId, endDate)
```

#### **Kafka Events Consumed:**
```
- None (producer only)
```

---

### 4️⃣ USER SERVICE
**Port:** 8083  
**Technology:** Spring Boot + JPA  
**Database:** PostgreSQL

#### **What it does:**
- Manage user profiles (tenants and owners)
- Store personal information
- Track user history
- Manage owner details and business info
- Document storage references (ID proofs, etc.)

#### **Database Tables:**
```sql
users (
    id, auth_user_id (FK to auth service), 
    first_name, last_name, email, phone, 
    date_of_birth, gender, address,
    profile_picture_url, id_proof_url,
    created_at, updated_at
)

owners (
    id, user_id (FK), business_name, 
    gst_number, pan_number, 
    bank_account_number, ifsc_code, 
    total_properties, created_at
)

emergency_contacts (
    id, user_id, name, relation, phone
)
```

#### **APIs:**

**User Management:**
```
GET    /api/users                    - Get all users
GET    /api/users/{id}               - Get user by ID
POST   /api/users                    - Create new user
PUT    /api/users/{id}               - Update user
DELETE /api/users/{id}               - Delete user
GET    /api/users/email/{email}      - Get user by email
GET    /api/users/role/{role}        - Get users by role (TENANT/OWNER)
```

**Owner Management:**
```
GET    /api/users/owners             - Get all owners
GET    /api/users/owners/{id}        - Get owner by ID
POST   /api/users/owners             - Create owner profile
PUT    /api/users/owners/{id}        - Update owner
GET    /api/users/owners/{id}/tenants - Get all tenants for an owner
```

#### **Kafka Events Published:**
```
- user.profile.created (userId, role, timestamp)
- user.profile.updated (userId, changes, timestamp)
- owner.registered (ownerId, businessName, timestamp)
```

#### **Kafka Events Consumed:**
```
- user.registered (from Auth Service) → Create user profile
```

---

### 5️⃣ PAYMENT SERVICE ⭐
**Port:** 8084  
**Technology:** Spring Boot + JPA + Payment Gateway Integration  
**Database:** PostgreSQL

#### **What it does:**
- Generate monthly rent invoices
- Track payment status (pending, paid, overdue)
- Process payments via payment gateway
- Generate receipts
- Send payment reminders
- Calculate late fees
- Payment history tracking

#### **Database Tables:**
```sql
payments (
    id, tenant_id, flat_id, owner_id,
    amount, due_date, payment_date,
    status (PENDING/PAID/OVERDUE/CANCELLED),
    transaction_id, payment_method,
    late_fee, total_amount,
    created_at, updated_at
)

invoices (
    id, payment_id, invoice_number,
    generated_date, pdf_url
)

receipts (
    id, payment_id, receipt_number,
    generated_date, pdf_url
)

payment_reminders (
    id, payment_id, reminder_date,
    type (EMAIL/SMS), status (SENT/FAILED)
)
```

#### **APIs:**

**Payment Management:**
```
GET    /api/payments                        - Get all payments
GET    /api/payments/{id}                   - Get payment by ID
POST   /api/payments                        - Create payment record
PUT    /api/payments/{id}                   - Update payment
GET    /api/payments/tenant/{id}            - Get payments by tenant
GET    /api/payments/owner/{id}             - Get payments by owner
GET    /api/payments/overdue                - Get all overdue payments
POST   /api/payments/{id}/pay               - Process payment
GET    /api/payments/{id}/receipt           - Download receipt
GET    /api/payments/{id}/invoice           - Download invoice
```

**Payment Gateway Integration:**
```
POST   /api/payments/initiate               - Initiate payment
POST   /api/payments/verify                 - Verify payment status
POST   /api/payments/webhook                - Payment gateway callback
```

**Analytics:**
```
GET    /api/payments/stats/tenant/{id}      - Tenant payment stats
GET    /api/payments/stats/owner/{id}       - Owner revenue stats
GET    /api/payments/history/tenant/{id}    - Tenant payment history
```

#### **Kafka Events Published:**
```
- payment.created (paymentId, tenantId, amount, dueDate)
- payment.completed (paymentId, tenantId, amount, paidDate, transactionId)
- payment.failed (paymentId, reason, timestamp)
- payment.overdue (paymentId, tenantId, daysOverdue, amount)
- payment.reminder (paymentId, tenantId, reminderType, timestamp)
```

#### **Kafka Events Consumed:**
```
- flat.occupied (from Property Service) → Create first payment record
- flat.vacated (from Property Service) → Cancel pending payments
```

#### **Payment Gateway Options:**
- Razorpay (India)
- Stripe (International)
- PayPal
- Paytm

---

### 6️⃣ MAINTENANCE SERVICE
**Port:** 8085  
**Technology:** Spring Boot + MongoDB  
**Database:** MongoDB (flexible schema for various complaint types)

#### **What it does:**
- Log maintenance/repair requests
- Categorize issues (plumbing, electrical, general)
- Priority assignment (low, medium, high, critical)
- Status tracking (open, in-progress, resolved, closed)
- Assign requests to technicians
- Upload issue images
- Comment/update thread
- Track resolution time

#### **MongoDB Collections:**
```javascript
maintenance_requests {
    _id: ObjectId,
    request_number: String,
    tenant_id: Long,
    flat_id: Long,
    category: String, // PLUMBING, ELECTRICAL, PAINTING, etc.
    title: String,
    description: String,
    priority: String, // LOW, MEDIUM, HIGH, CRITICAL
    status: String, // OPEN, IN_PROGRESS, RESOLVED, CLOSED
    images: [String], // URLs
    assigned_to: Long, // Technician/Owner ID
    created_at: Date,
    updated_at: Date,
    resolved_at: Date,
    comments: [
        {
            user_id: Long,
            comment: String,
            timestamp: Date
        }
    ]
}
```

#### **APIs:**

**Request Management:**
```
GET    /api/maintenance/requests                     - Get all requests
GET    /api/maintenance/requests/{id}                - Get request by ID
POST   /api/maintenance/requests                     - Create new request
PUT    /api/maintenance/requests/{id}                - Update request
DELETE /api/maintenance/requests/{id}                - Delete request
GET    /api/maintenance/requests/tenant/{id}         - Get tenant's requests
GET    /api/maintenance/requests/owner/{id}          - Get owner's requests
GET    /api/maintenance/requests/status/{status}     - Get by status
GET    /api/maintenance/requests/priority/{priority} - Get by priority
```

**Request Actions:**
```
POST   /api/maintenance/requests/{id}/assign      - Assign to technician
POST   /api/maintenance/requests/{id}/comment     - Add comment
POST   /api/maintenance/requests/{id}/status      - Update status
POST   /api/maintenance/requests/{id}/images      - Upload images
GET    /api/maintenance/requests/{id}/history     - Get update history
```

**Analytics:**
```
GET    /api/maintenance/stats/category            - Requests by category
GET    /api/maintenance/stats/resolution-time     - Avg resolution time
GET    /api/maintenance/stats/pending             - Pending requests count
```

#### **Kafka Events Published:**
```
- maintenance.created (requestId, tenantId, category, priority, timestamp)
- maintenance.assigned (requestId, assignedTo, timestamp)
- maintenance.status.changed (requestId, oldStatus, newStatus, timestamp)
- maintenance.resolved (requestId, resolutionTime, timestamp)
- maintenance.comment.added (requestId, userId, comment, timestamp)
```

#### **Kafka Events Consumed:**
```
- flat.vacated (from Property Service) → Auto-close pending requests
```

---

### 7️⃣ NOTIFICATION SERVICE ⭐
**Port:** 8086  
**Technology:** Spring Boot + MongoDB  
**Database:** MongoDB (notification logs)

#### **What it does:**
- Send email notifications
- Send SMS notifications
- Send push notifications (mobile app)
- Template management
- Delivery status tracking
- Retry failed notifications
- Notification preferences

#### **MongoDB Collections:**
```javascript
notifications {
    _id: ObjectId,
    user_id: Long,
    type: String, // EMAIL, SMS, PUSH
    channel: String, // PAYMENT, MAINTENANCE, LEASE, etc.
    subject: String,
    message: String,
    status: String, // PENDING, SENT, FAILED, RETRY
    sent_at: Date,
    delivered_at: Date,
    error_message: String,
    retry_count: Number,
    metadata: Object
}

notification_templates {
    _id: ObjectId,
    name: String,
    type: String,
    subject: String,
    body_template: String,
    variables: [String]
}

user_preferences {
    user_id: Long,
    email_enabled: Boolean,
    sms_enabled: Boolean,
    push_enabled: Boolean,
    preferences: {
        payment_reminders: Boolean,
        maintenance_updates: Boolean,
        lease_expiry: Boolean
    }
}
```

#### **APIs:**

**Manual Notifications:**
```
POST   /api/notifications/send/email       - Send email
POST   /api/notifications/send/sms         - Send SMS
POST   /api/notifications/send/push        - Send push notification
GET    /api/notifications/user/{id}        - Get user notifications
GET    /api/notifications/{id}/status      - Check delivery status
```

**Preferences:**
```
GET    /api/notifications/preferences/{userId}  - Get user preferences
PUT    /api/notifications/preferences/{userId}  - Update preferences
```

**Templates:**
```
GET    /api/notifications/templates        - Get all templates
POST   /api/notifications/templates        - Create template
PUT    /api/notifications/templates/{id}   - Update template
```

#### **Kafka Events Consumed (Main Job):**
```
✅ payment.overdue           → Send payment reminder
✅ payment.completed         → Send payment confirmation
✅ maintenance.created       → Notify owner
✅ maintenance.assigned      → Notify tenant & technician
✅ maintenance.resolved      → Notify tenant
✅ flat.occupied            → Send welcome email
✅ user.registered          → Send verification email
✅ lease.expiring           → Send renewal reminder
```

#### **Third-Party Integrations:**
- **Email:** SendGrid, AWS SES, SMTP
- **SMS:** Twilio, MSG91, AWS SNS
- **Push:** Firebase Cloud Messaging (FCM)

---

### 8️⃣ ANALYTICS SERVICE
**Port:** 8087  
**Technology:** Spring Boot + JPA + Redis  
**Database:** PostgreSQL (aggregated data) + Redis (caching)

#### **What it does:**
- Revenue reports (daily, monthly, yearly)
- Occupancy rate tracking
- Payment collection efficiency
- Maintenance request analytics
- Tenant retention metrics
- Property performance comparison
- Export reports (PDF, Excel)

#### **Database Tables:**
```sql
revenue_summary (
    id, owner_id, period (MONTH/YEAR),
    total_revenue, total_paid, total_pending,
    collection_rate, generated_at
)

occupancy_stats (
    id, building_id, date,
    total_flats, occupied_flats, 
    vacant_flats, occupancy_rate
)

payment_trends (
    id, owner_id, month, year,
    on_time_payments, late_payments,
    avg_delay_days
)
```

#### **APIs:**

**Revenue Reports:**
```
GET    /api/analytics/revenue/owner/{id}           - Owner revenue summary
GET    /api/analytics/revenue/monthly/{year}       - Monthly revenue
GET    /api/analytics/revenue/yearly/{year}        - Yearly revenue
GET    /api/analytics/revenue/comparison           - Period comparison
```

**Occupancy Analytics:**
```
GET    /api/analytics/occupancy/building/{id}      - Building occupancy
GET    /api/analytics/occupancy/overall            - Overall occupancy
GET    /api/analytics/occupancy/trend              - Occupancy trend
```

**Payment Analytics:**
```
GET    /api/analytics/payments/collection-rate     - Collection efficiency
GET    /api/analytics/payments/overdue             - Overdue analysis
GET    /api/analytics/payments/trends              - Payment trends
```

**Maintenance Analytics:**
```
GET    /api/analytics/maintenance/by-category      - Category breakdown
GET    /api/analytics/maintenance/resolution-time  - Avg resolution time
GET    /api/analytics/maintenance/pending          - Pending requests
```

**Export:**
```
GET    /api/analytics/export/revenue/pdf           - Download PDF report
GET    /api/analytics/export/revenue/excel         - Download Excel
```

#### **Kafka Events Consumed:**
```
- payment.completed    → Update revenue metrics
- payment.overdue      → Update collection rate
- flat.occupied       → Update occupancy stats
- flat.vacated        → Update occupancy stats
- maintenance.resolved → Update resolution metrics
```

---

### 9️⃣ CONFIG SERVER
**Port:** 8888  
**Technology:** Spring Cloud Config  
**Storage:** Git Repository / Local File System

#### **What it does:**
- Centralized configuration management
- Store application properties for all services
- Environment-specific configs (dev, test, prod)
- Dynamic config refresh without restart
- Encrypted sensitive properties

#### **Configuration Structure:**
```
config-repo/
├── api-gateway.yml
├── api-gateway-dev.yml
├── api-gateway-prod.yml
├── auth-service.yml
├── auth-service-dev.yml
├── auth-service-prod.yml
├── property-service.yml
├── payment-service.yml
└── application.yml (common configs)
```

#### **APIs:**
```
GET    /{application}/{profile}            - Get config
GET    /{application}/{profile}/{label}    - Get versioned config
POST   /actuator/refresh                   - Refresh configs
```

---

### 🔟 SERVICE REGISTRY (EUREKA)
**Port:** 8761  
**Technology:** Spring Cloud Netflix Eureka  
**Database:** None (in-memory)

#### **What it does:**
- Service discovery
- Register all microservices
- Health check monitoring
- Load balancing
- Failover support

#### **Registered Services:**
```
- auth-service (8081)
- property-service (8082)
- user-service (8083)
- payment-service (8084)
- maintenance-service (8085)
- notification-service (8086)
- analytics-service (8087)
```

#### **UI Dashboard:**
```
http://localhost:8761/
Shows all registered services and their status
```

---

## 📨 KAFKA TOPICS & EVENT FLOW

### **Kafka Topics:**
```
1. user-events
   - user.registered
   - user.profile.created
   - user.login

2. property-events
   - property.created
   - flat.occupied
   - flat.vacated

3. payment-events
   - payment.created
   - payment.completed
   - payment.overdue
   - payment.reminder

4. maintenance-events
   - maintenance.created
   - maintenance.assigned
   - maintenance.resolved

5. notification-events
   - notification.sent
   - notification.failed

6. audit-events
   - All system activities for logging
```

### **Event Flow Example: New Tenant Onboarding**
```
1. Auth Service: User registers
   ↓ Publish: user.registered
   
2. User Service: (Consumes) Creates user profile
   ↓ Publish: user.profile.created
   
3. Notification Service: (Consumes) Sends welcome email
   
4. Property Service: Owner assigns flat to tenant
   ↓ Publish: flat.occupied
   
5. Payment Service: (Consumes) Creates first payment record
   ↓ Publish: payment.created
   
6. Notification Service: (Consumes) Sends payment details
```

---

## 🗄️ DATABASE SCHEMA SUMMARY

### **PostgreSQL Databases:**
```
1. auth_db          - Users, roles, tokens
2. property_db      - Buildings, flats
3. user_db          - User profiles, owners
4. payment_db       - Payments, invoices, receipts
5. analytics_db     - Reports, aggregated data
```

### **MongoDB Databases:**
```
1. maintenance_db   - Maintenance requests
2. notification_db  - Notification logs, templates
```

### **Redis:**
```
- Session storage
- Cache for analytics
- Rate limiting data
```

---

## 🚀 DEPLOYMENT ARCHITECTURE

### **Docker Compose Structure:**
```yaml
services:
  # Infrastructure
  postgres:          # PostgreSQL container
  mongodb:           # MongoDB container
  redis:             # Redis container
  zookeeper:         # Kafka dependency
  kafka:             # Kafka broker
  
  # Spring Cloud Services
  config-server:     # Port 8888
  eureka-server:     # Port 8761
  api-gateway:       # Port 8080
  
  # Business Services
  auth-service:      # Port 8081
  property-service:  # Port 8082
  user-service:      # Port 8083
  payment-service:   # Port 8084
  maintenance-service: # Port 8085
  notification-service: # Port 8086
  analytics-service: # Port 8087
  
  # Monitoring
  prometheus:        # Port 9090
  grafana:          # Port 3000
```

---

## 📊 MONITORING & OBSERVABILITY

### **Tools:**
1. **Prometheus** - Metrics collection
2. **Grafana** - Visualization dashboards
3. **Spring Boot Actuator** - Health checks
4. **Zipkin/Jaeger** - Distributed tracing
5. **ELK Stack** - Log aggregation (optional)

### **Key Metrics to Monitor:**
- Request rate per service
- Response time (latency)
- Error rate
- Database connection pool
- Kafka lag
- JVM memory usage
- CPU utilization

---

## 🔐 SECURITY MEASURES

1. **API Gateway**: JWT validation
2. **Service-to-Service**: OAuth2 or mutual TLS
3. **Database**: Encrypted connections
4. **Secrets**: Environment variables, Vault
5. **Rate Limiting**: Prevent DDoS
6. **Input Validation**: Prevent injection attacks
7. **HTTPS**: All external communication

---

## 📦 TECHNOLOGY STACK SUMMARY

```yaml
Backend Framework: Spring Boot 3.x
Cloud: Spring Cloud (Gateway, Config, Eureka)
Messaging: Apache Kafka
Databases:
  - PostgreSQL (RDBMS)
  - MongoDB (NoSQL)
  - Redis (Cache)
Security: Spring Security + JWT
API Documentation: Swagger/OpenAPI
Containerization: Docker
Orchestration: Kubernetes (optional)
Monitoring: Prometheus + Grafana
CI/CD: Jenkins / GitHub Actions
```

---

## 🎯 IMPLEMENTATION STEPS

### **Week 1-2: Infrastructure Setup**
1. Setup project structure
2. Configure Eureka Server
3. Configure Config Server
4. Setup API Gateway
5. Setup Kafka cluster
6. Setup databases

### **Week 3-4: Core Services**
1. Auth Service
2. Property Service
3. User Service

### **Week 5-6: Payment & Notifications**
1. Payment Service
2. Notification Service
3. Kafka event integration

### **Week 7-8: Advanced Features**
1. Maintenance Service
2. Analytics Service
3. Testing & bug fixes

### **Week 9-10: Deployment**
1. Docker containerization
2. Kubernetes deployment (optional)
3. Monitoring setup
4. Production release

---

## 📞 SUPPORT & RESOURCES

- Spring Boot Docs: https://spring.io/projects/spring-boot
- Spring Cloud Docs: https://spring.io/projects/spring-cloud
- Kafka Docs: https://kafka.apache.org/documentation/
- Docker Docs: https://docs.docker.com/

---

**END OF ARCHITECTURE DOCUMENT**

This is your complete blueprint for building a production-ready Home Rental Application with Microservices Architecture. Each service is independent, scalable, and follows best practices.

Good luck with your implementation! 🚀
