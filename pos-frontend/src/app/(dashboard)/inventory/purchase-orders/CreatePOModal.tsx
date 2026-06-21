import { useState, useEffect } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { purchaseOrderService, PurchaseOrderRequest } from "@/services/purchaseOrderService";
import { supplierService } from "@/services/supplierService";
import { branchService } from "@/services/branchService";
import { inventoryService } from "@/services/inventoryService";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ScrollArea } from "@/components/ui/scroll-area";
import { toast } from "sonner";
import { Truck, Trash2 } from "lucide-react";
import { Product } from "@/types/inventory";
import { CURRENCY } from '@/lib/utils';

interface CreatePOModalProps {
  isOpen: boolean;
  onClose: () => void;
}

interface SelectedProduct extends Partial<Product> {
  id: string;
  name: string;
  sku: string;
  quantity: number;
  unitCost: number;
}

export function CreatePOModal({ isOpen, onClose }: CreatePOModalProps) {
  const queryClient = useQueryClient();
  const [supplierId, setSupplierId] = useState("");
  const [branchId, setBranchId] = useState("");
  const [expectedDate, setExpectedDate] = useState("");
  const [notes, setNotes] = useState("");
  const [items, setItems] = useState<SelectedProduct[]>([]);

  // Fetch Suppliers
  const { data: suppliersData } = useQuery({
    queryKey: ["suppliers-all"],
    queryFn: () => supplierService.getSuppliers(0, 100),
    enabled: isOpen,
  });

  // Fetch the branches this user may order for (delivery branch is enforced server-side).
  const { data: branches } = useQuery({
    queryKey: ["branches", "me"],
    queryFn: branchService.getMyBranches,
    enabled: isOpen,
  });

  // Fetch Products
  const { data: productsData } = useQuery({
    queryKey: ["products-all"],
    queryFn: () => inventoryService.getProducts(0, 1000), // Get a decent chunk of products
    enabled: isOpen,
  });

  const suppliers = (suppliersData?.content || []).filter(s => s.isActive);
  const products = productsData?.content || [];

  // Reset form when opened Let's rely on standard resets.
  useEffect(() => {
    if (isOpen) {
      setSupplierId("");
      setBranchId("");
      setExpectedDate("");
      setNotes("");
      setItems([]);
    }
  }, [isOpen]);

  const addProduct = (productId: string) => {
    if (!productId) return;
    const product = products.find(p => p.id === productId);
    if (!product) return;

    if (items.some(i => i.id === productId)) {
      toast.error("Product already added to the purchase order.");
      return;
    }

    setItems([...items, {
      id: product.id,
      name: product.name,
      sku: product.sku,
      quantity: 1,
      unitCost: product.costPrice || 0,
    }]);
  };

  const removeProduct = (productId: string) => {
    setItems(items.filter(i => i.id !== productId));
  };

  const updateItemQty = (productId: string, qty: number) => {
    setItems(items.map(i => i.id === productId ? { ...i, quantity: qty } : i));
  };

  const updateItemCost = (productId: string, cost: number) => {
    setItems(items.map(i => i.id === productId ? { ...i, unitCost: cost } : i));
  };

  const createMutation = useMutation({
    mutationFn: (data: PurchaseOrderRequest) => purchaseOrderService.createPurchaseOrder(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["purchase-orders"] });
      toast.success("Purchase order created successfully and saved as DRAFT");
      onClose();
    },
    onError: (error: unknown) => {
      toast.error((error as { response?: { data?: { message?: string } } })?.response?.data?.message || "Failed to create purchase order");
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!supplierId || !branchId || items.length === 0) {
      toast.error("Please fill all required fields and add at least one item.");
      return;
    }

    const requestData: PurchaseOrderRequest = {
      supplierId,
      branchId,
      expectedDate: expectedDate ? `${expectedDate}T00:00:00` : undefined,
      notes,
      items: items.map(i => ({
        productId: i.id,
        quantity: i.quantity,
        unitCost: i.unitCost
      }))
    };

    createMutation.mutate(requestData);
  };

  const totalAmount = items.reduce((acc, current) => acc + (current.quantity * current.unitCost), 0);

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-[700px] h-[90vh] flex flex-col bg-card border-border text-foreground p-0">
        <DialogHeader className="p-6 pb-2 border-b border-border">
          <DialogTitle className="flex items-center gap-2 text-xl">
            <Truck className="h-5 w-5 text-primary" />
            Create Purchase Order
          </DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="flex-1 flex flex-col overflow-hidden">
          <ScrollArea className="flex-1 p-6">
            <div className="space-y-6">
              {/* Header Info */}
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label>Supplier *</Label>
                  <Select value={supplierId} onValueChange={setSupplierId}>
                    <SelectTrigger className="bg-background border-border">
                      <SelectValue placeholder="Select Supplier" />
                    </SelectTrigger>
                    <SelectContent className="bg-card border-border text-foreground">
                      {suppliers.map(s => (
                        <SelectItem key={s.id} value={s.id}>{s.name}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-2">
                  <Label>Delivery Branch *</Label>
                  <Select value={branchId} onValueChange={setBranchId}>
                    <SelectTrigger className="bg-background border-border">
                      <SelectValue placeholder="Select Branch" />
                    </SelectTrigger>
                    <SelectContent className="bg-card border-border text-foreground">
                      {branches?.map(b => (
                        <SelectItem key={b.id} value={b.id}>{b.name}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                
                <div className="space-y-2">
                  <Label>Expected Date</Label>
                  <Input 
                    type="date"
                    value={expectedDate}
                    onChange={(e) => setExpectedDate(e.target.value)}
                    className="bg-background border-border [color-scheme:dark]"
                  />
                </div>
                
                <div className="space-y-2">
                  <Label>Notes</Label>
                  <Input 
                    placeholder="Optional remarks..."
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                    className="bg-background border-border"
                  />
                </div>
              </div>

              {/* Items Section */}
              <div className="space-y-4 pt-4 border-t border-border">
                <div className="flex justify-between items-center">
                  <Label className="text-lg font-semibold">Order Items</Label>
                  <div className="w-[300px]">
                    <Select onValueChange={addProduct} value="">
                      <SelectTrigger className="bg-background border-border h-8">
                        <SelectValue placeholder="+ Select product to add..." />
                      </SelectTrigger>
                      <SelectContent className="bg-card border-border text-foreground">
                        {products.map(p => (
                          <SelectItem key={p.id} value={p.id}>{p.name} ({p.sku})</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>

                {items.length === 0 ? (
                  <div className="text-center py-8 bg-background rounded border border-dashed border-border text-muted-foreground">
                    No products added yet. Select products from the dropdown above.
                  </div>
                ) : (
                  <div className="border border-border rounded-md overflow-hidden bg-background">
                    <table className="w-full text-sm">
                      <thead className="bg-card text-muted-foreground border-b border-border">
                        <tr>
                          <th className="text-left py-2 px-3 font-medium">Product</th>
                          <th className="text-left py-2 px-3 font-medium w-24">Unit Cost</th>
                          <th className="text-left py-2 px-3 font-medium w-24">Quantity</th>
                          <th className="text-right py-2 px-3 font-medium w-28">Total</th>
                          <th className="w-10"></th>
                        </tr>
                      </thead>
                      <tbody>
                        {items.map(item => (
                          <tr key={item.id} className="border-b border-border/50 last:border-0 hover:bg-card/50 transition-colors">
                            <td className="py-2 px-3">
                              <div className="font-medium">{item.name}</div>
                              <div className="text-xs text-muted-foreground">{item.sku}</div>
                            </td>
                            <td className="py-2 px-3">
                              <Input 
                                type="number" 
                                min="0" 
                                step="0.01"
                                className="h-8 bg-card border-border px-2"
                                value={item.unitCost}
                                onChange={(e) => updateItemCost(item.id, Number(e.target.value))}
                              />
                            </td>
                            <td className="py-2 px-3">
                              <Input 
                                type="number" 
                                min="1" 
                                className="h-8 bg-card border-border px-2"
                                value={item.quantity}
                                onChange={(e) => updateItemQty(item.id, Number(e.target.value))}
                              />
                            </td>
                            <td className="py-2 px-3 text-right font-medium text-success">
                              {CURRENCY.symbol} {(item.quantity * item.unitCost).toFixed(2)}
                            </td>
                            <td className="py-2 px-2 text-center">
                              <Button
                                type="button"
                                variant="ghost"
                                size="icon"
                                aria-label="Remove item"
                                title="Remove item"
                                className="h-8 w-8 text-muted-foreground hover:text-destructive hover:bg-destructive/10"
                                onClick={() => removeProduct(item.id)}
                              >
                                <Trash2 className="h-4 w-4" />
                              </Button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          </ScrollArea>

          <div className="p-4 border-t border-border bg-background flex items-center justify-between">
            <div className="flex flex-col">
              <span className="text-sm text-muted-foreground">Total Order Amount</span>
              <span className="text-xl font-bold text-success">{CURRENCY.symbol} {totalAmount.toFixed(2)}</span>
            </div>
            <div className="flex gap-3">
              <Button type="button" variant="outline" onClick={onClose} className="border-border hover:bg-muted text-foreground">
                Cancel
              </Button>
              <Button type="submit" disabled={createMutation.isPending} className="bg-primary hover:bg-primary/90 text-primary-foreground min-w-[120px]">
                {createMutation.isPending ? "Saving..." : "Create Draft"}
              </Button>
            </div>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
