# Step 38: Expandable Product Details in Report Tables

## Overview

Enhanced both the **Sales History** and **Returns History** tables in the Reports page to support expandable rows. Users can now click on any transaction row to reveal a detailed sub-table showing all individual products within that transaction.

## Changes Made

### Backend Changes

#### 1. ReportDtos.java — New `SalesReportItemRecord` DTO

- Added a new inner class `SalesReportItemRecord` containing:
  - `productId`, `productName`, `sku`, `description`
  - `quantity`, `unitPrice`, `taxAmount`, `discountAmount`, `totalAmount`
- Added `List<SalesReportItemRecord> items` field to `SalesReportRecord`

#### 2. ReportService.java — Batch Product Fetching

- Enhanced `getSalesReport()` to batch-fetch all product details (name, SKU, description) for every sale item across the page
- Uses efficient `findAllById()` to avoid N+1 queries
- Maps each `SaleItemEntity` to `SalesReportItemRecord` with full product details

#### 3. ReturnService.java — Product Name for Return Items

- Added `ProductRepository` injection
- Updated `mapItem()` to look up product names from the database
- `ReturnItemResponse.productName` is now correctly populated instead of being null

### Frontend Changes

#### 4. report.ts — Updated TypeScript Types

- Added `SalesReportItemRecord` interface matching the backend DTO
- Added `items: SalesReportItemRecord[]` to `SalesReportRecord`

#### 5. reports/page.tsx — Sales History Expandable Rows

- Added `expandedSales` state to track which rows are open
- Each row now has a chevron toggle (▶/▼) as the first column
- Clicking a row expands a nested sub-table showing:
  - Product Name (with description below if available)
  - SKU
  - Quantity
  - Unit Price
  - Tax Amount
  - Discount Amount
  - Total Amount
- Return button uses `stopPropagation` to avoid triggering expand

#### 6. reports/page.tsx — Returns History Expandable Rows

- Added `expandedReturns` state
- Same chevron toggle pattern
- Expanded view shows:
  - Product Name
  - Quantity Returned
  - Unit Price
  - Refund Amount
- Approve/Reject buttons use `stopPropagation` to avoid triggering expand

## Technical Details

- **No additional API calls**: Item data is included in the existing paginated response
- **Batch loading**: Product details are fetched in bulk per page, not per sale
- **REFUNDED badge**: Sales with REFUNDED status now show an amber badge instead of default
