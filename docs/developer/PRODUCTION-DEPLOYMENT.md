# Production Deployment Guide

This guide covers deploying the Portal to a production server. TLS termination
and routing are handled by an **external reverse proxy** (nginx, Traefik, a
cloud load balancer, ...) that points at the app container on port 8080 — the
stack itself serves plain HTTP and ships no bundled proxy.

This guide assumes you build the image yourself. If you pull pre-built images
from GHCR, set `APP_IMAGE`/`PULL_POLICY` in `.env` and skip the build step.

For the compact run reference (all three run methods, env variable table),
see the main [DEPLOYMENT.md](../../DEPLOYMENT.md). This guide is the
server-provisioning companion to it.

## Prerequisites

- Linux server (Ubuntu 20.04+ recommended)
- Docker and Docker Compose installed
- An external reverse proxy terminating TLS and forwarding to the app
- Root or sudo access
- At least 4GB RAM, 20GB disk space

## Step-by-Step Production Setup

### 1. Server Preparation

```bash
# Update system
sudo apt-get update && sudo apt-get upgrade -y

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# Logout and login to apply docker group changes
exit
```

### 2. Application Setup

```bash
# Clone repository
git clone <your-repository-url>
cd portal-for-circularity

# Create environment file
cp .env.example .env
nano .env  # Edit with your values
```

### 3. Environment Configuration

Edit `.env` with production values (see the full variable reference in
[DEPLOYMENT.md](../../DEPLOYMENT.md)):

```bash
# Database Configuration
DB_HOST=postgres
DB_PORT=5432
DB_NAME=portal
DB_USERNAME=portal
DB_PASSWORD=your_very_secure_password_here

# Application Configuration
APP_IMAGE=d4c-portal:latest
APP_PORT=8080        # host port your reverse proxy forwards to

# Security (ENCRYPTION_KEY exactly 32 chars)
ENCRYPTION_KEY=change_this_32_char_key_in_prod!

# JVM Configuration
JAVA_OPTS=-Xmx2g -Xms1g -XX:+UseG1GC
```

### 4. Build and Deploy

```bash
# Build application image (skip if pulling from a registry)
docker build -t d4c-portal:latest .

# Deploy
docker-compose -f docker-compose.prod.yml --env-file .env up -d

# Check services status
docker-compose -f docker-compose.prod.yml ps

# View logs
docker-compose -f docker-compose.prod.yml logs -f
```

### 5. Verify Deployment

```bash
# Check application health (app listens on 8080)
curl http://localhost:8080/actuator/health

# Check database connection
docker-compose -f docker-compose.prod.yml exec postgres \
  psql -U portal -d portal -c "SELECT COUNT(*) FROM organizations;"
```

### 6. Front the App with Your Reverse Proxy

TLS and routing live outside this stack. Point your proxy at the app on
`${APP_PORT:-8080}` and terminate TLS there. A minimal nginx `server` block
on the proxy host looks like:

```nginx
server {
    listen 443 ssl;
    server_name your-domain.com;

    ssl_certificate     /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Manage certificates (e.g. Let's Encrypt / certbot, or a cloud certificate
manager) on the proxy — the portal stack is unaware of them. For a proxy on a
separate host, bind the app port to the proxy's network or publish it and
firewall it to the proxy's address.

## Firewall Configuration

```bash
# Ubuntu/Debian with ufw — expose 443 at the proxy, keep 8080 internal
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 443/tcp   # HTTPS (terminated at the reverse proxy)
sudo ufw --force enable
```

If the proxy runs on the same host, do not publish `APP_PORT` to the public
interface — let the proxy reach it over the Docker network or loopback only.

## Monitoring and Maintenance

### Log Management

```bash
# View application logs
docker-compose -f docker-compose.prod.yml logs -f app

# View database logs
docker-compose -f docker-compose.prod.yml logs -f postgres
```

### Database Backup

```bash
# Create backup
docker-compose -f docker-compose.prod.yml exec postgres \
  pg_dump -U portal portal > backup-$(date +%Y%m%d).sql

# Restore backup
docker-compose -f docker-compose.prod.yml exec -T postgres \
  psql -U portal portal < backup-20240101.sql
```

### Application Updates

```bash
# Pull latest code
git pull origin main

# Rebuild and deploy
docker build -t d4c-portal:latest .
docker-compose -f docker-compose.prod.yml --env-file .env up -d

# Remove old images
docker image prune -f
```

## Security Checklist

- [ ] Strong database password configured
- [ ] `ENCRYPTION_KEY` is exactly 32 characters and unique
- [ ] `DEMO_DATA_ENABLED` is not enabled; a real `ADMIN_PASSWORD` is set
- [ ] TLS is terminated at the external reverse proxy with valid certificates
- [ ] `APP_PORT` is not publicly exposed; only the proxy reaches it
- [ ] Firewall blocks unused ports
- [ ] Server is regularly updated
- [ ] Database backups are scheduled
- [ ] Log rotation is configured

## Performance Tuning

### Database Optimization

```bash
# Connect to database
docker-compose -f docker-compose.prod.yml exec postgres psql -U portal portal

# Monitor connections
SELECT count(*) FROM pg_stat_activity;
```

### Application Performance

```bash
# Monitor application metrics
curl http://localhost:8080/actuator/metrics

# Check memory usage
docker stats d4c-portal-app-prod

# Adjust JVM settings in .env
JAVA_OPTS=-Xmx4g -Xms2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

## Troubleshooting

### Common Issues

1. **Database Connection Issues**
   ```bash
   # Check database container
   docker-compose -f docker-compose.prod.yml ps postgres

   # Test database connection
   docker-compose -f docker-compose.prod.yml exec postgres pg_isready -U portal
   ```

2. **Application Not Starting**
   ```bash
   # Check application logs
   docker-compose -f docker-compose.prod.yml logs app

   # Check available resources
   free -h
   df -h
   ```

3. **502 / connection refused from the proxy**
   ```bash
   # Confirm the app is healthy and listening on 8080
   curl http://localhost:8080/actuator/health

   # Confirm the proxy targets the correct host:port and network
   ```

4. **High CPU/Memory Usage**
   ```bash
   # Monitor resource usage
   docker stats

   # Adjust resource limits in docker-compose.prod.yml
   # Optimize JVM settings in .env
   ```

For additional support, check the main [DEPLOYMENT.md](../../DEPLOYMENT.md) guide.
