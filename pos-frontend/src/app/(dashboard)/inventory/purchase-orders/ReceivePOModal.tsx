import { useState, useEffect } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { purchaseOrderService, PurchaseOrder, ReceivePoItemRequest } from "@/services/purchaseOrderService";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { toast } from "sonner";
import { PackageCheck } from "lucide-react";

interface ReceivePOModalProps {
  isOpen: boolean;
  onClose: () => void;
  purchaseOrder: PurchaseOrder | null;
}

interface ItemToReceive {
  id: string; // The PO Item ID
  productId: string;
  productName: string;
  sku: string;
  ordered: number;
  previouslyReceived: number;
  qtyToReceiveNow: number;
}

export function ReceivePOModal({ isOpen, onClose, purchaseOrder }: ReceivePOModalProps) {
  const queryClient = useQueryClient();
  const [items, setItems] = useState<ItemToReceive[]>([]);

  useEffect(() => {
    if (isOpen && purchaseOrder) {
      // Map items to default 'qtyToReceiveNow' = remaining amount
      const mapped = purchaseOrder.items.map(i => ({
        id: i.id,
        productId: i.productId,
        productName: i.productName,
        sku: i.sku,
        ordered: i.orderedQuantity,
        previouslyReceived: i.receivedQuantity,
        qtyToReceiveNow: Math.max(0, i.orderedQuantity - i.receivedQuantity) // Suggested auto-fill
      }));
      setItems(mapped);
    } else {
      setItems([]);
    }
  }, [isOpen, purchaseOrder]);

  const updateQty = (id: string, qty: number) => {
    setItems(items.map(i => i.id === id ? { ...i, qtyToReceiveNow: qty } : i));
  };

  const receiveMutation = useMutation({
    mutationFn: (payload: ReceivePoItemRequest[]) => {
       if(!purchaseOrder) throw new Error("No PO Selected");
       return purchaseOrderService.receivePurchaseOrder(purchaseOrder.id, payload);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["purchase-orders"] });
      toast.success("Products received into branch stock successfully!");
      onClose();
    },
    onError: (error: unknown) => {
      toast.error((error as { response?: { data?: { message?: string } } })?.response?.data?.message || "Failed to receive products");
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    const payload: ReceivePoItemRequest[] = items
      .filter(i => i.qtyToReceiveNow > 0)
      .map(i => ({
        poItemId: i.id,
        receivedQuantity: i.qtyToReceiveNow
      }));

    if (payload.length === 0) {
      toast.error("Please enter quantities greater than 0 to receive items.");
      return;
    }

    receiveMutation.mutate(payload);
  };

  if (!purchaseOrder) return null;

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-[700px] bg-card border-border text-foreground p-0 overflow-hidden flex flex-col">
        <DialogHeader className="p-6 pb-4 border-b border-border">
          <DialogTitle className="flex flex-col gap-1">
            <div className="flex items-center gap-2 text-xl">
              <PackageCheck className="h-5 w-5 text-primary" />
              Receive Purchase Order
            </div>
            <span className="text-sm font-normal text-muted-foreground">
              Receiving stock for {purchaseOrder.poNumber} at {purchaseOrder.branchName}
            </span>
          </DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="flex flex-col">
          <ScrollArea className="max-h-[60vh] p-6">
            <div className="border border-border rounded-lg overflow-hidden bg-background">
              <table className="w-full text-sm">
                <thead className="bg-card text-muted-foreground border-b border-border">
                  <tr>
                    <th className="text-left py-3 px-4 font-medium">Product</th>
                    <th className="text-center py-3 px-2 font-medium w-20">Ordered</th>
                    <th className="text-center py-3 px-2 font-medium w-24">Received</th>
                    <th className="text-center py-3 px-4 font-medium w-36">Receive Now</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {items.map(item => {
                    const remaining = item.ordered - item.previouslyReceived;
                    const isFullyReceived = remaining <= 0;

                    return (
                      <tr key={item.id} className="hover:bg-card/50 transition-colors">
                        <td className="py-3 px-4">
                          <div className="font-medium text-foreground">{item.productName}</div>
                          <div className="text-xs text-muted-foreground">{item.sku}</div>
                        </td>
                        <td className="py-3 px-2 text-center text-foreground font-medium">
                          {item.ordered}
                        </td>
                        <td className="py-3 px-2 text-center">
                          <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${isFullyReceived ? 'bg-success/10 text-success' : 'bg-muted text-muted-foreground'}`}>
                            {item.previouslyReceived}
                          </span>
                        </td>
                        <td className="py-2 px-4">
                          {isFullyReceived ? (
                            <div className="text-center text-xs font-semibold text-success bg-success/10 py-1.5 rounded w-full">
                              Completed
                            </div>
                          ) : (
                            <Input 
                              type="number" 
                              min="0" 
                              max={remaining + 10} // allow slight over-receiving handling by backend
                              className="h-9 bg-card border-border focus-visible:ring-primary text-center font-bold"
                              value={item.qtyToReceiveNow}
                              onChange={(e) => updateQty(item.id, Number(e.target.value))}
                            />
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            
            <div className="mt-4 text-sm text-warning/80 bg-warning/10 p-3 rounded-md border border-warning/20">
              <strong>Note:</strong> Receiving items here will directly update the physical stock count for <strong>{purchaseOrder.branchName}</strong> immediately upon submission.
            </div>
          </ScrollArea>

          <div className="p-4 border-t border-border bg-background flex justify-end gap-3">
            <Button type="button" variant="outline" onClick={onClose} className="border-border hover:bg-muted text-foreground">
              Cancel
            </Button>
            <Button 
                type="submit" 
                disabled={receiveMutation.isPending || items.every(i => i.ordered - i.previouslyReceived <= 0)} 
                className="bg-primary hover:bg-primary/90 text-primary-foreground min-w-[120px]"
            >
              {receiveMutation.isPending ? "Processing..." : "Receive Stock"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
