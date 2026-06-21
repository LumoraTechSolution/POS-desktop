"use client";

import { useQuery } from "@tanstack/react-query";
import { inventoryService } from "@/services/inventoryService";
import ProductForm from "@/components/inventory/ProductForm";
import { useParams } from "next/navigation";
import { Suspense } from "react";
import { Breadcrumbs } from "@/components/ui/breadcrumbs";
import { DashboardHeaderSlot } from "@/components/layout/DashboardHeaderSlot";

export default function EditProductPage() {
  const { id } = useParams();

  const { data: productData, isLoading } = useQuery({
    queryKey: ['product', id],
    queryFn: () => inventoryService.getProduct(id as string),
    enabled: !!id
  });

  if (isLoading) {
    return <div className="p-8 text-center text-muted-foreground">Loading product details...</div>;
  }

  return (
    <div className="bg-background min-h-screen">
      <DashboardHeaderSlot>
        <Breadcrumbs
          items={[
            { label: 'Products', href: '/inventory/products' },
            { label: productData?.name ?? 'Edit product' },
          ]}
        />
      </DashboardHeaderSlot>
      <Suspense fallback={<div className="p-8 text-center text-muted-foreground">Loading form...</div>}>
        <ProductForm initialData={productData} />
      </Suspense>
    </div>
  );
}
