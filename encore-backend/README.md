# ENCORE Backend

Spring Boot backend for the ENCORE ticketing management system.

## Local Startup

Start MySQL and Redis from the repository root:

```powershell
docker compose up -d mysql redis
```

The default host-side MySQL port is `3307` to avoid conflicts with a local MySQL installation. In a cloud/container deployment, set `ENCORE_DB_HOST=mysql` and `ENCORE_DB_PORT=3306` for container-to-container access.

Run the backend:

```powershell
cd D:\ENCORE\encore-backend
mvn spring-boot:run
```

Useful endpoints:

- `GET http://localhost:8080/api/health`
- `POST http://localhost:8080/api/auth/login`
- `GET http://localhost:8080/api/shows`
- `GET http://localhost:8080/api/shows/s-001/schedules`
- `GET http://localhost:8080/api/schedules/sch-101/seats`
- `POST http://localhost:8080/api/schedules/sch-101/seats/lock`
- `POST http://localhost:8080/api/orders`
- `GET http://localhost:8080/api/orders/{id}`
- `POST http://localhost:8080/api/orders/{id}/pay`
- `http://localhost:8080/doc.html`

Demo accounts:

- `user / 123`
- `friend / 123`
- `admin / 123`
- `checker / 123`
- `sysadmin / 123`

## Docker Preview

The backend Docker image is normally built through the root full compose file:

```powershell
cd D:\ENCORE
docker compose -f docker-compose.full.yml up --build backend
```

In container mode, the backend reads MySQL and Redis settings from environment variables and connects to `mysql:3306` and `redis:6379`.

See the root [README](../README.md) for full-stack startup and verification instructions.
