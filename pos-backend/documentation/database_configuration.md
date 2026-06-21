# Database Configuration & Must-Know Commands

Direct and comprehensive guide for managing the Lumora POS database.

## 1. Connection Details (Local Development)

These settings are defined in `docker-compose.yml` and utilized by the `application-dev.yml` profile.

| Parameter           | Value                     |
| :------------------ | :------------------------ |
| **Database Engine** | PostgreSQL 15             |
| **Host**            | `localhost`               |
| **Port**            | `5434` (mapped from 5432) |
| **Database Name**   | `lumora_pos`              |
| **Username**        | `postgres`                |
| **Password**        | `postgres`                |

---

## 2. Accessing the Database via Terminal

Use this command to jump into the database console directly from your terminal:

```powershell
docker exec -it lumora-pos-db psql -U postgres -d lumora_pos
```

---

## 3. Must-Know `psql` Meta-Commands

Once you are inside the `psql` console, these commands help you navigate:

| Command         | Action                                             |
| :-------------- | :------------------------------------------------- |
| `\dt`           | **List all tables**                                |
| `\dt+`          | List tables with size and description              |
| `\d table_name` | **Describe table** (columns, types, indexes)       |
| `\x`            | Toggle **Expanded Display** (useful for wide rows) |
| `\l`            | List all databases                                 |
| `\! cls`        | Clear the screen (Windows)                         |
| `\q`            | **Exit** the console                               |

---

## 4. Common SQL Queries for POS

Always end your SQL queries with a semicolon `;`.

### Inventory Checks

```sql
-- View all categories
SELECT id, name, tenant_id FROM categories;

-- View all brands
SELECT id, name, website FROM brands;

-- View products and their stock levels
SELECT name, sku, stock_quantity, base_price FROM products;
```

### Sales & Transactions

```sql
-- View recent sales
SELECT invoice_number, total_amount, payment_status, created_at FROM sales ORDER BY created_at DESC;

-- View items in a specific sale
SELECT * FROM sale_items WHERE sale_id = 'YOUR_SALE_ID_HERE';
```

### Security & Multi-tenancy

```sql
-- List users and their status
SELECT email, first_name, last_name, is_active FROM users;

-- List all tenants
SELECT id, name, domain FROM tenants;
```

---

## 5. External Tools (GUI)

To use **pgAdmin**, **DBeaver**, or **DataGrip**, create a new PostgreSQL connection with these parameters:

- **Host**: `localhost`
- **Port**: `5434`
- **Database**: `lumora_pos`
- **User**: `postgres`
- **Password**: `postgres`

> **Note**: If you are using a tool like **pgAdmin** inside Docker, the host should be the service name `postgres` and the port should be `5432`.
