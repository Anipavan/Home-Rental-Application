# Flyway migrations — Auth Service

Empty by design: this service currently uses Hibernate `ddl-auto: update` for
schema management.

## Why this folder exists

In production you should swap `ddl-auto: update` for Flyway-managed
migrations:

1. Add the dependency to `pom.xml`:
   ```xml
   <dependency>
     <groupId>org.flywaydb</groupId>
     <artifactId>flyway-core</artifactId>
   </dependency>
   <!-- Oracle XE / Free needs the optional database module -->
   <dependency>
     <groupId>org.flywaydb</groupId>
     <artifactId>flyway-database-oracle</artifactId>
   </dependency>
   ```

2. Set `spring.jpa.hibernate.ddl-auto: validate` in
   `config-server/src/main/resources/config/HRA-auth-service.yml`.

3. Create a baseline migration here named `V1__init_auth_schema.sql` containing
   the current DDL (you can dump it via `mvn hibernate:hbm2ddl` or by reading
   the `CREATE TABLE` log lines from a fresh `update` run).

4. From then on every schema change is a new `V<n>__<name>.sql` file in this
   folder.

The same pattern applies to `property-service`, `user-service`,
`payment-service`, and `analytics-service` (all Oracle).
`maintenance-service` and `notification-service` use MongoDB which is
schemaless and doesn't need migrations.
