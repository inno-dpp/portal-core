# Database Setup Guide

## Development Database Configuration

The application uses an **H2 in-memory database** for development by default. A fresh database is created on every start and dropped on shutdown, and demo data is reseeded automatically (when `app.demo-data.enabled=true`, the dev-profile default).

## Current Configuration (In-Memory)

**Location**: `src/main/resources/application-dev.yml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:d4c_portal_dev
  jpa:
    hibernate:
      ddl-auto: create-drop
```

### Pros:
- ✅ Always starts fresh
- ✅ No schema drift
- ✅ Demo data reloads automatically
- ✅ No cleanup needed

### Cons:
- ❌ Lose all data on restart
- ❌ Need to re-onboard organizations every time
- ❌ Slower testing of multi-step workflows

---

## Option 1: In-Memory (Current Default)

**Use when:** You want a fresh database every time you restart — this is the configuration shipped in `application-dev.yml`; no changes needed.

**H2 Console:**
```
URL: http://localhost:8085/h2-console
JDBC URL: jdbc:h2:mem:d4c_portal_dev
Username: sa
Password: (empty)
```

### Reset Database (Fresh Start):

Just restart the application — the database is recreated from the entities and demo data reloads.

---

## Option 2: File-Based (Alternative - Persistent Data Between Restarts)

**Use when:** Working on features that need persistent data between restarts (e.g. multi-session onboarding workflows)

### Switch to File-Based Mode:

Edit `src/main/resources/application-dev.yml`:

```yaml
spring:
  datasource:
    # File-based H2 database (persists between restarts)
    url: jdbc:h2:file:./data/d4c_portal_dev

  jpa:
    hibernate:
      ddl-auto: update

  sql:
    init:
      mode: never
```

**Database location:** `./data/d4c_portal_dev.mv.db`

**H2 Console:**
```
URL: http://localhost:8085/h2-console
JDBC URL: jdbc:h2:file:./data/d4c_portal_dev
Username: sa
Password: (empty)
```

### Pros:
- ✅ Data persists between application restarts
- ✅ No need to re-onboard organizations every time
- ✅ Faster development iteration (keep test data)
- ✅ Can test workflows that span multiple sessions

### Cons:
- ❌ Schema drift if entities change
- ❌ No automatic sample data reload
- ❌ Database files not version controlled

### Reset Database (Fresh Start):

**Option A: Delete database files**
```bash
# Windows
rmdir /s /q data

# Linux/Mac
rm -rf data/
```

**Option B: Use the reset scripts**
```bash
# Windows
reset-dev-database.bat

# Linux/Mac
./reset-dev-database.sh
```

After deletion, restart the app — the schema is recreated from the entities.

---

## Common Development Scenarios

### Scenario 1: Testing Onboarding Workflow
**Recommended:** File-based
- Onboard organization once
- Test SPIP/CKAN sync
- Data persists for further testing

### Scenario 2: Testing Database Schema Changes
**Recommended:** In-memory (default)
- Just restart the app
- Schema recreated from entities

### Scenario 3: Adding New Entity Fields
**In-memory approach (default):**
- Just restart (already fresh)

**File-based approach:**
```bash
# 1. Stop the application
# 2. Delete database
rm -rf data/
# 3. Restart - schema recreated with new fields
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## Troubleshooting

### Issue: "Table X not found"
**Cause:** Database schema out of sync with entities (file-based mode)
**Solution:** Delete `data/` directory and restart; in-memory mode just restart

### Issue: "Column X not found"
**Cause:** Added new field to entity, but `ddl-auto: update` didn't apply it (file-based mode)
**Solution:** Delete `data/` directory for clean schema

### Issue: "Demo data not loading"
**Cause:** `app.demo-data.enabled` is false, or file-based mode retained an old database
**Solution:** Check `DEMO_DATA_ENABLED`; in file-based mode delete `data/` and restart

### Issue: "Cannot access H2 Console"
**Check:**
1. H2 console enabled: `spring.h2.console.enabled=true`
2. URL: http://localhost:8085/h2-console (dev profile runs on port 8085)
3. JDBC URL matches your config

---

## Best Practices

### For Active Feature Development:
1. ✅ Use the **in-memory** configuration (current default)
2. ✅ If you switch to file-based, reset the database regularly to avoid schema drift
3. ✅ Keep demo/seed data reproducible

### For Testing Schema Changes:
1. ✅ Verify schema creation from scratch (fresh in-memory start)
2. ✅ Test with both fresh and existing database if using file-based mode

### For Production Deployment:
1. ✅ Use PostgreSQL (already configured in `application-prod.yml`)
2. ✅ Use proper database migrations (Flyway/Liquibase recommended)
3. ❌ Never use H2 in production

---

## Quick Reference Commands

```bash
# Start application (dev profile)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Access H2 Console
# Open: http://localhost:8085/h2-console
# JDBC URL: jdbc:h2:mem:d4c_portal_dev

# File-based mode only: reset database completely
rm -rf data/ && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# File-based mode only: check database files
ls -lh data/
```

---

## Need Help?

- Database schema issues? → Restart (in-memory) or delete `data/` and restart (file-based)
- Lost demo data? → Restart with `DEMO_DATA_ENABLED=true`
- Want data to survive restarts? → Switch to file-based mode (Option 2)
- Schema drift problems (file-based)? → Reset database regularly during development

## Configuration Files

- Development: `src/main/resources/application-dev.yml`
- Test: `src/main/resources/application-test.yml`
- Production: `src/main/resources/application-prod.yml`
