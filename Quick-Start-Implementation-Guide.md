# 🚀 HOME RENTAL MICROSERVICES - QUICK START GUIDE
### Get Started in 30 Minutes

---

## 📋 PREREQUISITES CHECKLIST

Before starting, ensure you have:

```
✅ JDK 17 or higher installed
✅ Maven 3.8+ or Gradle 7+
✅ Docker Desktop installed and running
✅ Git installed
✅ IDE (IntelliJ IDEA recommended)
✅ Postman or similar API testing tool
✅ 16GB RAM minimum (for running all services + Docker)
```

**Verify installations:**
```bash
java -version          # Should show version 17+
mvn -version           # Should show Maven 3.8+
docker --version       # Should show Docker version
git --version          # Should show Git version
```

---

## 🎯 PHASE 1: INFRASTRUCTURE SETUP (Day 1-3)

---

### STEP 1: Create Project Structure

```bash
# Create main project directory
mkdir home-rental-microservices
cd home-rental-microservices

# Create directory for each service
mkdir config-server
mkdir eureka-server
mkdir api-gateway
mkdir auth-service
mkdir property-service
mkdir user-service
mkdir payment-service
mkdir maintenance-service
mkdir notification-service
mkdir analytics-service
```

---

### STEP 2: Setup Docker Infrastructure

Create `docker-compose.yml` in the root directory:

```yaml
version: '3.8'

services:
  # PostgreSQL Database
  postgres:
    image: postgres:15-alpine
    container_name: rental-postgres
    environment:
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: admin123
      POSTGRES_DB: rental_db
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - rental-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U admin"]
      interval: 10s
      timeout: 5s
      retries: 5

  # MongoDB Database
  mongodb:
    image: mongo:7-jammy
    container_name: rental-mongo
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: admin123
    ports:
      - "27017:27017"
    volumes:
      - mongo-data:/data/db
    networks:
      - rental-network
    healthcheck:
      test: echo 'db.runCommand("ping").ok' | mongosh localhost:27017/test --quiet
      interval: 10s
      timeout: 5s
      retries: 5

  # Redis Cache
  redis:
    image: redis:7-alpine
    container_name: rental-redis
    ports:
      - "6379:6379"
    networks:
      - rental-network
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Zookeeper (required for Kafka)
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: rental-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"
    networks:
      - rental-network

  # Kafka Message Broker
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: rental-kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    networks:
      - rental-network

volumes:
  postgres-data:
  mongo-data:

networks:
  rental-network:
    driver: bridge
```

**Start all infrastructure:**
```bash
docker-compose up -d

# Verify all containers are running
docker-compose ps

# Check logs if any issues
docker-compose logs -f
```

---

### STEP 3: Create Config Server

**Navigate to config-server directory:**
```bash
cd config-server
```

**Create pom.xml:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.rental</groupId>
    <artifactId>config-server</artifactId>
    <version>1.0.0</version>
    <name>Config Server</name>

    <properties>
        <java.version>17</java.version>
        <spring-cloud.version>2023.0.0</spring-cloud.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-config-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Create src/main/java/com/rental/configserver/ConfigServerApplication.java:**
```java
package com.rental.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

**Create src/main/resources/application.yml:**
```yaml
server:
  port: 8888

spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        native:
          search-locations: classpath:/config
  profiles:
    active: native

management:
  endpoints:
    web:
      exposure:
        include: '*'
```

**Create config directory and common configuration:**

Create `src/main/resources/config/application.yml`:
```yaml
# Common configuration for all services

eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/
    register-with-eureka: true
    fetch-registry: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

logging:
  level:
    root: INFO
    com.rental: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"

spring:
  jackson:
    serialization:
      write-dates-as-timestamps: false
    time-zone: Asia/Kolkata
```

**Build and run:**
```bash
mvn clean package
mvn spring-boot:run
```

**Verify:** Visit http://localhost:8888/actuator/health

---

### STEP 4: Create Eureka Server

**Navigate to eureka-server directory:**
```bash
cd ../eureka-server
```

**Create pom.xml:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.rental</groupId>
    <artifactId>eureka-server</artifactId>
    <version>1.0.0</version>
    <name>Eureka Server</name>

    <properties>
        <java.version>17</java.version>
        <spring-cloud.version>2023.0.0</spring-cloud.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Create src/main/java/com/rental/eurekaserver/EurekaServerApplication.java:**
```java
package com.rental.eurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

**Create src/main/resources/application.yml:**
```yaml
server:
  port: 8761

spring:
  application:
    name: eureka-server

eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/
  server:
    enable-self-preservation: false
    eviction-interval-timer-in-ms: 4000

management:
  endpoints:
    web:
      exposure:
        include: '*'
```

**Build and run:**
```bash
mvn clean package
mvn spring-boot:run
```

**Verify:** Visit http://localhost:8761 - You should see Eureka Dashboard

---

### STEP 5: Create API Gateway

**Navigate to api-gateway directory:**
```bash
cd ../api-gateway
```

**Create pom.xml:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.rental</groupId>
    <artifactId>api-gateway</artifactId>
    <version>1.0.0</version>
    <name>API Gateway</name>

    <properties>
        <java.version>17</java.version>
        <spring-cloud.version>2023.0.0</spring-cloud.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Create src/main/java/com/rental/gateway/ApiGatewayApplication.java:**
```java
package com.rental.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

**Create src/main/resources/application.yml:**
```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
      routes:
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/api/auth/**
          filters:
            - StripPrefix=1

        - id: property-service
          uri: lb://property-service
          predicates:
            - Path=/api/properties/**
          filters:
            - StripPrefix=1

        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/users/**
          filters:
            - StripPrefix=1

        - id: payment-service
          uri: lb://payment-service
          predicates:
            - Path=/api/payments/**
          filters:
            - StripPrefix=1

        - id: maintenance-service
          uri: lb://maintenance-service
          predicates:
            - Path=/api/maintenance/**
          filters:
            - StripPrefix=1

        - id: notification-service
          uri: lb://notification-service
          predicates:
            - Path=/api/notifications/**
          filters:
            - StripPrefix=1

        - id: analytics-service
          uri: lb://analytics-service
          predicates:
            - Path=/api/analytics/**
          filters:
            - StripPrefix=1

eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/

management:
  endpoints:
    web:
      exposure:
        include: '*'

logging:
  level:
    org.springframework.cloud.gateway: DEBUG
```

**Build and run:**
```bash
mvn clean package
mvn spring-boot:run
```

**Verify:** 
- Check Eureka Dashboard - API Gateway should be registered
- Visit http://localhost:8080/actuator/gateway/routes

---

## ✅ INFRASTRUCTURE VERIFICATION

**At this point, you should have:**

1. ✅ Docker containers running (PostgreSQL, MongoDB, Redis, Kafka)
2. ✅ Config Server running on port 8888
3. ✅ Eureka Server running on port 8761
4. ✅ API Gateway running on port 8080

**Quick verification commands:**
```bash
# Check Docker containers
docker ps

# Check if services are registered
curl http://localhost:8761/eureka/apps

# Check API Gateway routes
curl http://localhost:8080/actuator/gateway/routes
```

---

## 🎯 NEXT STEPS

Now that infrastructure is ready, proceed to:

### **Phase 2: Build Auth Service (Days 4-5)**
- JWT token generation
- User registration/login
- Role-based access control

### **Phase 3: Build Business Services (Days 6-14)**
- Property Service
- User Service
- Payment Service
- Maintenance Service
- Notification Service
- Analytics Service

---

## 📝 DEVELOPMENT BEST PRACTICES

1. **Git Workflow:**
```bash
# Create feature branch
git checkout -b feature/service-name

# Commit regularly
git add .
git commit -m "feat: Add service-name with basic CRUD"

# Push to remote
git push origin feature/service-name
```

2. **Testing:**
- Write unit tests for each service
- Use TestContainers for integration tests
- Test APIs using Postman

3. **Documentation:**
- Add README.md to each service
- Document all API endpoints
- Create architecture diagrams

---

## 🚨 TROUBLESHOOTING

### Issue: Docker containers not starting
```bash
# Check Docker logs
docker-compose logs -f

# Restart specific service
docker-compose restart postgres

# Clean and restart all
docker-compose down -v
docker-compose up -d
```

### Issue: Service not registering with Eureka
- Check if Eureka Server is running
- Verify eureka.client.serviceUrl in application.yml
- Check network connectivity

### Issue: Port already in use
```bash
# Find process using port (Windows)
netstat -ano | findstr :8080

# Kill process
taskkill /PID <process_id> /F

# Find process using port (Linux/Mac)
lsof -i :8080

# Kill process
kill -9 <process_id>
```

---

## 📚 USEFUL COMMANDS

```bash
# Maven commands
mvn clean install          # Build all modules
mvn spring-boot:run        # Run single service
mvn test                   # Run tests
mvn package                # Create JAR file

# Docker commands
docker-compose up -d       # Start all containers
docker-compose down        # Stop all containers
docker-compose ps          # List running containers
docker-compose logs -f     # View logs

# Git commands
git status                 # Check status
git add .                  # Stage all changes
git commit -m "message"    # Commit changes
git push                   # Push to remote
```

---

## 🎉 CONGRATULATIONS!

You've successfully set up the **infrastructure foundation** for your Home Rental Microservices Application!

**You now have:**
- ✅ All databases running
- ✅ Kafka message broker ready
- ✅ Config Server managing configurations
- ✅ Service Registry (Eureka) for service discovery
- ✅ API Gateway for routing

---

## 📞 NEED HELP?

**Want detailed code for:**
1. Auth Service with JWT security?
2. Property Service with full CRUD?
3. Payment Service with Razorpay integration?
4. Kafka producer/consumer setup?
5. Docker configuration for services?

**Just ask, and I'll provide complete, production-ready code!** 🚀

---

**READY TO BUILD AUTH SERVICE?** Let me know, and I'll give you the complete implementation with JWT security! 💪
