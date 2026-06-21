# Database Management

This document provides commands for managing the PostgreSQL database for the backend.

## 1. Local Database Connection

- **Host**: `localhost`
- **Port**: `5434`
- **Database**: `lumora_pos`
- **User**: `postgres`
- **Password**: `postgres`

## 2. Docker CLI Access

```powershell
docker exec -it lumora-pos-db psql -U postgres -d lumora_pos
```

## 3. Common Commands

- `\dt`: List tables
- `\d table_name`: Describe table
- `\q`: Exit
- `SELECT * FROM sales;`: View sales (new)
- `SELECT * FROM products;`: View inventory
