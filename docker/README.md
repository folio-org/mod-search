# 🐳 Docker Compose Setup for mod-search

Local development environment for mod-search using Docker Compose.

## 📋 Prerequisites

- Docker and Docker Compose V2+
- Java 21+ (for local development mode)
- Maven 3.8+ (for building the module)

## 🏗️ Architecture

Two compose files provide flexible development workflows:

- **`infra-docker-compose.yml`**: Infrastructure services only (PostgreSQL, OpenSearch, Kafka, etc.)
- **`app-docker-compose.yml`**: Full stack including the module (uses `include` to incorporate infra services)

## ⚙️ Configuration

Configuration is managed via the `.env` file in this directory.

### Key Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `ENV` | FOLIO environment name | `folio` |
| `MODULE_REPLICAS` | Number of module instances | `1` |
| `DB_HOST` | PostgreSQL hostname | `postgres` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_DATABASE` | Database name | `okapi_modules` |
| `DB_USERNAME` | Database user | `folio_admin` |
| `DB_PASSWORD` | Database password | `folio_admin` |
| `KAFKA_HOST` | Kafka hostname | `kafka` |
| `KAFKA_PORT` | Kafka port (Docker network) | `9093` |
| `KAFKA_TOPIC_PARTITIONS` | Default topic partitions | `2` |
| `ELASTICSEARCH_URL` | OpenSearch URL | `http://opensearch:9200` |
| `ELASTICSEARCH_PORT` | OpenSearch port | `9200` |

## 🚀 Services

### PostgreSQL
- **Purpose**: Primary database for module data
- **Version**: PostgreSQL 16 Alpine
- **Access**: localhost:5432 (configurable via `DB_PORT`)
- **Credentials**: See `DB_USERNAME` and `DB_PASSWORD` in `.env`
- **Database**: See `DB_DATABASE` in `.env`

### pgAdmin
- **Purpose**: Database administration interface
- **Access**: http://localhost:5050 (configurable via `PGADMIN_PORT`)
- **Login**: Use `PGADMIN_DEFAULT_EMAIL` and `PGADMIN_DEFAULT_PASSWORD` from `.env`

### OpenSearch
- **Purpose**: Search engine for module data
- **Access**: http://localhost:9200 (configurable via `ELASTICSEARCH_PORT`)
- **Mode**: Single-node cluster for development

### OpenSearch Dashboards
- **Purpose**: Web interface for OpenSearch
- **Access**: http://localhost:5601 (configurable via `OPENSEARCH_DASHBOARDS_PORT`)

### Apache Kafka
- **Purpose**: Message broker for event-driven architecture
- **Mode**: KRaft (no Zookeeper required)
- **Listeners**:
  - Docker internal: `kafka:9093`
  - Host: `localhost:29092`

### Kafka UI
- **Purpose**: Web interface for Kafka management
- **Access**: http://localhost:8090 (configurable via `KAFKA_UI_PORT`)
- **Features**: Topic browsing, message viewing/producing, consumer group monitoring

### Kafka Topic Initializer
- **Purpose**: Automatically creates required Kafka topics on startup
- **Topics Created**:
  - `{ENV}.Default.inventory.*`
  - `{ENV}.Default.authorities.*`
  - `{ENV}.Default.search.*`
  - And more (see `kafka-init.sh`)

### WireMock
- **Purpose**: Mock Okapi and other FOLIO modules for testing
- **Access**: http://localhost:9130 (configurable via `WIREMOCK_PORT`)

## 📖 Usage

> **Note**: All commands should be run from the `docker/` directory.

### Starting the Environment

```bash
# Start all services (infrastructure + module)
docker compose -f app-docker-compose.yml up -d
```

```bash
# Start only infrastructure services (for local development)
docker compose -f infra-docker-compose.yml up -d
```

```bash
# Start with build (if module code changed)
docker compose -f app-docker-compose.yml up -d --build
```

```bash
# Start specific service
docker compose -f app-docker-compose.yml up -d mod-search
```

### Stopping the Environment

```bash
# Stop all services
docker compose -f app-docker-compose.yml down
```

```bash
# Stop infra services only
docker compose -f infra-docker-compose.yml down
```

```bash
# Stop and remove volumes (clean slate)
docker compose -f app-docker-compose.yml down -v
```

### Viewing Logs

```bash
# All services
docker compose -f app-docker-compose.yml logs
```

```bash
# Specific service
docker compose -f app-docker-compose.yml logs mod-search
```

```bash
# Follow logs in real-time
docker compose -f app-docker-compose.yml logs -f mod-search
```

```bash
# Last 100 lines
docker compose -f app-docker-compose.yml logs --tail=100 mod-search
```

### Scaling the Module

```bash
# Scale to 3 instances
docker compose -f app-docker-compose.yml up -d --scale mod-search=3
```

```bash
# Or modify MODULE_REPLICAS in .env and restart
echo "MODULE_REPLICAS=3" >> .env
docker compose -f app-docker-compose.yml up -d
```

### Cleanup and Reset

```bash
# Complete cleanup (stops containers, removes volumes)
docker compose -f app-docker-compose.yml down -v
```

```bash
# Remove all Docker resources
docker compose -f app-docker-compose.yml down -v
docker volume prune -f
docker network prune -f
```

## 🔧 Development Workflows

### Workflow 1: Full Docker Stack
Run everything in Docker, including the module.

```bash
# Build and start
mvn -f ../pom.xml clean package -DskipTests
docker compose -f app-docker-compose.yml up -d
```

### Workflow 2: Infrastructure Only + IDE
Run infrastructure in Docker, develop the module in your IDE.

```bash
# Start infrastructure
docker compose -f infra-docker-compose.yml up -d

# Run module from IDE or command line
mvn -f ../pom.xml spring-boot:run
```

### Workflow 3: Spring Boot Docker Compose Integration
Let Spring Boot manage Docker Compose automatically.

```bash
# Run with dev profile (starts infrastructure automatically)
mvn -f ../pom.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile is configured to:
- Start services from `docker/infra-docker-compose.yml`
- Connect to services via localhost ports (Kafka: 29092, PostgreSQL: 5432, OpenSearch: 9200)
- Keep containers running after the application stops

### Workflow 4: Spring Boot DevTools
For rapid development with automatic restart.

```bash
# Start infrastructure
docker compose -f infra-docker-compose.yml up -d

# Run with devtools
mvn -f ../pom.xml spring-boot:run

# Make code changes - application will automatically restart
```

## 🛠️ Common Tasks

### Building the Module

```bash
# Clean build (skip tests)
mvn -f ../pom.xml clean package -DskipTests
```

```bash
# Build with tests
mvn -f ../pom.xml clean package
```

### Accessing Services

```bash
# PostgreSQL CLI
docker compose -f app-docker-compose.yml exec postgres psql -U folio_admin -d okapi_modules
```

```bash
# View database tables
docker compose -f app-docker-compose.yml exec postgres psql -U folio_admin -d okapi_modules -c "\dt"
```

```bash
# Check PostgreSQL health
docker compose -f app-docker-compose.yml exec postgres pg_isready -U folio_admin
```

```bash
# List Kafka topics
docker compose -f app-docker-compose.yml exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9093 --list
```

### Adding New Kafka Topics

Edit `kafka-init.sh` and add topics to the `TOPICS` array:

```bash
TOPICS=(
  "${ENV}.Default.inventory.instance"
  "${ENV}.Default.your-new-topic"  # Add your new topic here
)
```

After editing, restart the kafka-topic-init service:
```bash
docker compose -f infra-docker-compose.yml up -d kafka-topic-init
```

## 🐛 Troubleshooting

### Module won't start
- Check if the JAR is built: `ls -lh ../target/*.jar`
- Check module logs: `docker compose -f app-docker-compose.yml logs mod-search`
- Verify database is ready: `docker compose -f app-docker-compose.yml exec postgres pg_isready`

### Database connection issues
- Verify PostgreSQL is running: `docker compose -f app-docker-compose.yml ps postgres`
- Check database credentials in `.env`
- Test connection: `docker compose -f app-docker-compose.yml exec postgres psql -U folio_admin -d okapi_modules -c "SELECT 1"`

### Kafka issues
- Check Kafka logs: `docker compose -f app-docker-compose.yml logs kafka`
- Verify topics were created: Use Kafka UI at http://localhost:8090
- List topics manually:
  ```bash
  docker compose -f app-docker-compose.yml exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9093 --list
  ```

### OpenSearch issues
- Check OpenSearch health: `curl http://localhost:9200/_cluster/health`
- View OpenSearch logs: `docker compose -f app-docker-compose.yml logs opensearch`

### Port conflicts
- Check if ports are already in use: `lsof -i :5432 -i :8081 -i :8090 -i :9093 -i :5050 -i :29092`
- Modify ports in `.env` file if needed

### Container keeps restarting
- Check container logs: `docker compose -f app-docker-compose.yml logs <service-name>`
- Check health status: `docker compose -f app-docker-compose.yml ps`
- Verify dependencies are healthy before starting dependent services

### Clean slate restart
If things are broken, reset everything:

```bash
# Stop and remove everything
docker compose -f app-docker-compose.yml down -v
docker compose -f infra-docker-compose.yml down -v

# Clean up Docker resources
docker volume prune -f
docker network prune -f

# Start fresh
docker compose -f infra-docker-compose.yml up -d
```

## 🎯 Performance Tuning

### Database Optimization
Adjust PostgreSQL settings in `infra-docker-compose.yml`:
```yaml
environment:
  POSTGRES_SHARED_BUFFERS: 256MB
  POSTGRES_WORK_MEM: 10MB
  POSTGRES_MAX_CONNECTIONS: 100
```

### Module Resource Limits
Adjust resource limits in `app-docker-compose.yml` under `deploy.resources`:
```yaml
resources:
  limits:
    cpus: "1.0"
    memory: "1G"
  reservations:
    cpus: "0.5"
    memory: "512M"
```

## 📚 Additional Resources

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Spring Boot Docker Compose Support](https://docs.spring.io/spring-boot/reference/features/dev-services.html#features.dev-services.docker-compose)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [OpenSearch Documentation](https://opensearch.org/docs/latest/)

