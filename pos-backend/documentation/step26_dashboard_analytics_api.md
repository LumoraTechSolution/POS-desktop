# Step 26: Dashboard Analytics Backend APIs

**Date:** 2026-02-25  
**Phase:** Phase 1, Feature #1 — Dashboard Analytics  
**Scope:** Backend

## Summary

Created a new `dashboard` module providing a single API endpoint that returns all analytics data required for the overview dashboard page. This follows the "Backend for Frontend" (BFF) pattern — one API call returns everything the dashboard needs.

## API Endpoint

```
GET /api/v1/dashboard
Authorization: ADMIN, MANAGER only
```

### Response Structure

```json
{
  "todaySales": 1250.00,
  "yesterdaySales": 980.50,
  "todayTransactions": 15,
  "yesterdayTransactions": 12,
  "avgOrderValue": 83.33,
  "yesterdayAvgOrderValue": 81.71,
  "activeProducts": 45,
  "totalProducts": 50,
  "salesTrend": [
    { "date": "2026-02-19", "dayLabel": "Thu", "revenue": 800.00, "orders": 10 },
    ...7 days
  ],
  "topProducts": [
    { "productId": "...", "productName": "Widget A", "quantitySold": 25, "revenue": 500.00 },
    ...top 5
  ],
  "paymentBreakdown": [
    { "method": "CASH", "amount": 750.00, "count": 8 },
    { "method": "CARD", "amount": 500.00, "count": 7 }
  ],
  "lowStockAlerts": [
    { "productId": "...", "productName": "...", "sku": "...", "currentStock": 2, "threshold": 5 }
  ],
  "recentTransactions": [
    { "saleId": "...", "invoiceNumber": "INV-001", "netAmount": 125.00, "paymentMethod": "CASH", "paymentStatus": "PAID", "customerName": "Walk-in", "createdAt": "2026-02-25 10:30", "itemCount": 3 }
  ]
}
```

## Files Created/Modified

| File                                            | Action   | Description                                                                          |
| ----------------------------------------------- | -------- | ------------------------------------------------------------------------------------ |
| `dashboard/dto/DashboardResponse.java`          | NEW      | Response DTO with nested static classes for each data section                        |
| `dashboard/service/DashboardService.java`       | NEW      | Service with analytics logic, builds KPI comparisons, trends, alerts                 |
| `dashboard/controller/DashboardController.java` | NEW      | REST endpoint, ADMIN/MANAGER only                                                    |
| `sales/repository/SaleRepository.java`          | MODIFIED | Added 5 aggregate JPQL queries (count, sum, top products, payment breakdown, recent) |
| `inventory/repository/ProductRepository.java`   | MODIFIED | Added low stock query, active count, total count                                     |

## Design Decisions

- **Single API call**: Dashboard loads all data in one request to minimize latency
- **Today vs Yesterday**: KPI cards show comparison data for trend indicators
- **7-day trend**: Sales chart covers the last 7 days with daily granularity
- **30-day top products**: Top sellers are calculated over a 30-day window for meaningful data
- **Role restriction**: Only ADMIN and MANAGER can view analytics (cashiers use POS terminal)
- **Tenant isolation**: All queries are scoped by tenantId
