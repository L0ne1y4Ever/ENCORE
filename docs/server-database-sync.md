# ENCORE 服务器数据库同步步骤

本文用于把云服务器上的旧 MySQL 数据库同步到当前代码所需结构。当前项目以 Flyway 为默认迁移方式，部署时不需要手动逐条执行业务 SQL。

## 当前 SQL 文件职责

- `encore-backend/src/main/resources/db/migration/V1__baseline_schema_and_seed.sql`
  - 全量基线库结构与种子数据。
  - 适用于全新空库。
- `encore-backend/src/main/resources/db/migration/V2__venue_layout_schedule_and_default_showtimes.sql`
  - 旧库增量升级脚本。
  - 创建场馆、厅、布局、布局座位、区域库存等新结构，并补齐默认排片。
  - 使用幂等写法，支持重复启动检查。
- `encore-backend/src/main/resources/db/init/01-schema.sql`
  - 历史 Docker MySQL 首次初始化参考脚本。
  - 当前 Compose 不再挂载到 `/docker-entrypoint-initdb.d`，避免绕过 Flyway。
- `encore-backend/src/main/resources/db/init/02-data.sql`
  - 历史 Docker MySQL 首次初始化参考种子数据。
  - 当前 Compose 不再自动执行；全新空库由 Flyway `V1` 初始化。
- `encore-backend/src/main/resources/db/manual/2026-05-26-core-experience.sql`
  - 历史手动补丁归档。
  - 不作为当前自动迁移入口。

## 推荐同步流程

在服务器仓库目录执行：

```bash
cd /opt/ENCORE
git pull origin main
docker compose -f docker-compose.full.yml up -d --build
```

后端容器启动时会自动执行 Flyway：

- 如果是全新空库：执行 `V1`，再执行 `V2`。
- 如果是已有旧库且没有 `flyway_schema_history`：因为启用了 `baseline-on-migrate=true`，Flyway 会把当前库标记为基线版本 1，再执行 `V2`。
- 如果已经执行过：Flyway 会根据 `flyway_schema_history` 跳过已完成迁移。

Flyway 配置在 `application.yml` 中作为所有 profile 的默认运行配置；即使服务器使用
`SPRING_PROFILES_ACTIVE=prod`，只要设置好 `ENCORE_DB_*` 环境变量，后端启动时仍会自动迁移。
不要在生产库上手动执行 `db/init` 或 `db/manual` 目录下的旧脚本。

## 部署前备份

升级已有数据库前，先备份：

```bash
docker exec encore-mysql mysqldump -uroot -p"$ENCORE_MYSQL_ROOT_PASSWORD" encore > encore-backup-$(date +%F-%H%M).sql
```

如果服务器没有设置 `ENCORE_MYSQL_ROOT_PASSWORD` 环境变量，使用 compose 默认 root 密码：

```bash
docker exec encore-mysql mysqldump -uroot -proot123 encore > encore-backup-$(date +%F-%H%M).sql
```

## 验证迁移结果

查看 Flyway 执行记录：

```bash
docker exec -it encore-mysql mysql -uroot -proot123 encore -e "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

检查关键新表：

```bash
docker exec -it encore-mysql mysql -uroot -proot123 encore -e "SHOW TABLES LIKE 'venue'; SHOW TABLES LIKE 'seat_layout'; SHOW TABLES LIKE 'seat_layout_seat';"
```

检查 `show_schedule` 新字段：

```bash
docker exec -it encore-mysql mysql -uroot -proot123 encore -e "SHOW COLUMNS FROM show_schedule LIKE 'hall_id'; SHOW COLUMNS FROM show_schedule LIKE 'layout_id'; SHOW COLUMNS FROM show_schedule LIKE 'publish_status';"
```

检查默认账号和五类排片：

```bash
docker exec -it encore-mysql mysql -uroot -proot123 encore -e "SELECT username, role, status FROM user_account WHERE username IN ('user','friend','checker','admin','sysadmin');"
docker exec -it encore-mysql mysql -uroot -proot123 encore -e "SELECT s.category, COUNT(DISTINCT s.id) shows, COUNT(sc.id) schedules FROM encore_show s LEFT JOIN show_schedule sc ON sc.show_id = s.id AND sc.status = 'ON_SALE' WHERE s.status = 'PUBLISHED' GROUP BY s.category;"
```

验证后端和前端：

```bash
curl http://127.0.0.1:8080/api/health
curl http://127.0.0.1:8080/api/shows
docker compose -f docker-compose.full.yml ps
```

## 常见问题

如果 `git pull` 报 `RPC failed` 或 `early EOF`：

```bash
git config --global http.version HTTP/1.1
git config --global http.postBuffer 524288000
git pull origin main
```

如果端口被占用：

```bash
docker ps
docker stop encore-backend encore-frontend
docker compose -f docker-compose.full.yml up -d --build
```

如果改用了域名或公网 IP，并且前后端不是同源部署，设置允许来源：

```bash
export ENCORE_ALLOWED_ORIGIN_PATTERNS="https://your-domain.com,http://your-server-ip:*"
docker compose -f docker-compose.full.yml up -d --build backend
```
