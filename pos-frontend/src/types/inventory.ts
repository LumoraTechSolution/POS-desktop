export interface Category {
  id: string;
  name: string;
  slug?: string;
  description?: string;
  parentId?: string;
  taxRateId?: string;
  taxRateName?: string;
  createdAt: string;
}

export interface Brand {
  id: string;
  name: string;
  description?: string;
  website?: string;
  createdAt: string;
}

export interface StockLevel {
  id: string;
  productId: string;
  branchId: string;
  branchName: string;
  quantity: number;
}

export interface Product {
  id: string;
  name: string;
  sku: string;
  barcode?: string;
  description?: string;
  basePrice: number;
  costPrice?: number;
  stockQuantity: number;
  lowStockThreshold: number;
  imageUrl?: string;
  isActive: boolean;
  categoryId?: string;
  categoryName?: string;
  brandId?: string;
  brandName?: string;
  primarySupplierId?: string;
  primarySupplierName?: string;
  stockLevels?: StockLevel[];
  createdAt: string;
  updatedAt: string;
}

export interface ProductRequest {
  name: string;
  sku?: string;
  barcode?: string;
  description?: string;
  basePrice: number;
  costPrice?: number;
  stockQuantity: number;
  lowStockThreshold: number;
  imageUrl?: string;
  categoryId?: string;
  brandId?: string;
  primarySupplierId?: string;
  isActive: boolean;
  branchStockLevels?: { branchId: string; quantity: number }[];
}

export interface LowStockResponse {
  productId: string;
  productName: string;
  productSku: string;
  branchId: string;
  branchName: string;
  currentQuantity: number;
  threshold: number;
}

export interface CategoryRequest {
  name: string;
  slug?: string;
  description?: string;
  parentId?: string;
  taxRateId?: string;
}

export interface BrandRequest {
  name: string;
  description?: string;
  website?: string;
}
