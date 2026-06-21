import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { salesService } from "@/services/salesService";
import { returnService, ReturnRequest, RefundMethod } from "@/services/returnService";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Loader2, ArrowRightLeft } from "lucide-react";
import { toast } from "sonner";
import { CURRENCY } from '@/lib/utils';

interface ReturnModalProps {
  saleId: string | null;
  onClose: () => void;
  onExchange?: (saleId: string, returnItems: { saleItemId: string; productId: string; quantity: number }[], returnCredit: number) => void;
}

export function ReturnModal({ saleId, onClose, onExchange }: ReturnModalProps) {
  const queryClient = useQueryClient();
  const [reason, setReason] = useState("");
  const [refundMethod, setRefundMethod] = useState<RefundMethod>('ORIGINAL');
  const [notes, setNotes] = useState("");
  const [quantities, setQuantities] = useState<Record<string, number>>({});

  const { data: sale, isLoading: saleLoading } = useQuery({
    queryKey: ['sales', saleId],
    queryFn: () => salesService.getSale(saleId!),
    enabled: !!saleId,
  });

  const { data: pastReturns, isLoading: returnsLoading } = useQuery({
    queryKey: ['returns', 'sale', saleId],
    queryFn: () => returnService.getReturnsBySale(saleId!),
    enabled: !!saleId,
  });

  const returnMutation = useMutation({
    mutationFn: (data: ReturnRequest) => returnService.createReturn(data),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['reports', 'sales'] });
      if (data.status === 'PENDING') {
        toast.success("Return submitted and is pending manager approval.");
      } else {
        toast.success("Return processed successfully.");
      }
      onClose();
    },
    onError: (error: Error | { response?: { data?: { message?: string } } }) => {
      const respError = error as { response?: { data?: { message?: string } } };
      toast.error(respError.response?.data?.message || "Failed to process return");
    }
  });

  const isLoading = saleLoading || returnsLoading;

  // Calculate remaining refundable quantities safely
  const getRemainingQty = (saleItemId: string, originalQty: number) => {
    if (!pastReturns) return originalQty;
    const returnedQty = pastReturns
      .filter(r => r.status !== 'REJECTED')
      .flatMap(r => r.items)
      .filter(i => i.saleItemId === saleItemId)
      .reduce((sum, item) => sum + item.quantityReturned, 0);
    return originalQty - returnedQty;
  };

  const totalReturnItems = Object.values(quantities).reduce((a, b) => a + b, 0);

  const totalReturnAmt = Object.entries(quantities).reduce((sum, [itemId, qty]) => {
    if (!sale) return sum;
    const item = sale.items.find((si: { id: string; totalAmount: number; quantity: number }) => si.id === itemId);
    if (!item) return sum;
    const unitPrice = item.totalAmount / item.quantity;
    return sum + (unitPrice * qty);
  }, 0);

  const handleReturn = () => {
    if (!sale) return;

    const items: ReturnRequest['items'] = Object.entries(quantities)
      .filter(([, qty]) => qty > 0)
      .map(([itemId, qty]) => {
        const saleItem = sale.items.find((si) => si.id === itemId);
        // Custom/open lines have no productId and cannot be returned — skip them.
        return saleItem && saleItem.productId
          ? { saleItemId: itemId, productId: saleItem.productId, quantity: qty }
          : null;
      })
      .filter((i): i is ReturnRequest['items'][number] => i !== null);

    if (items.length === 0) {
      toast.error("Please select at least one item to return");
      return;
    }

    if (!reason.trim()) {
      toast.error("Please provide a reason for the return");
      return;
    }

    if (reason === 'Exchange' && onExchange) {
        onExchange(sale.id, items, totalReturnAmt);
        onClose();
        return;
    }

    returnMutation.mutate({
      saleId: sale.id,
      reason,
      refundMethod,
      notes,
      items
    });
  };

  const canRefundSale = sale?.paymentStatus === 'PAID' || sale?.paymentStatus === 'PARTIAL';

  return (
    <Dialog open={!!saleId} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-[700px] bg-gray-900 border-gray-800 text-white">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-xl">
            <ArrowRightLeft className="text-primary" />
            Process Return {sale && `- Invoice #${sale.invoiceNumber}`}
          </DialogTitle>
        </DialogHeader>

        {isLoading ? (
          <div className="flex justify-center py-12">
            <Loader2 className="animate-spin text-primary" size={32} />
          </div>
        ) : !sale ? (
          <div className="py-8 text-center text-destructive">Sale not found.</div>
        ) : !canRefundSale ? (
          <div className="py-8 text-center text-warning space-y-2">
            <p>This sale cannot be refunded.</p>
            <p className="text-sm opacity-80">Current status: {sale.paymentStatus}</p>
          </div>
        ) : (
          <div className="space-y-6">
            <div className="space-y-3">
              <h3 className="font-medium text-gray-300">Select Items to Return</h3>
              <div className="border border-gray-800 rounded-lg overflow-hidden">
                <table className="w-full text-sm text-left">
                  <thead className="bg-gray-800/50 text-gray-400">
                    <tr>
                      <th className="px-4 py-2">Item</th>
                      <th className="px-4 py-2 text-right">Price</th>
                      <th className="px-4 py-2 text-center">Purchased</th>
                      <th className="px-4 py-2 text-center">Remaining</th>
                      <th className="px-4 py-2 text-center">Return Qty</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sale.items.map(item => {
                      const remaining = getRemainingQty(item.id, item.quantity);
                      const currentVal = quantities[item.id] || 0;
                      return (
                        <tr key={item.id} className="border-t border-gray-800/50">
                          <td className="px-4 py-3 text-white">{item.productName}</td>
                          <td className="px-4 py-3 text-right">{CURRENCY.symbol} {item.unitPrice.toFixed(2)}</td>
                          <td className="px-4 py-3 text-center">{item.quantity}</td>
                          <td className="px-4 py-3 text-center">
                            {remaining === 0 ? (
                              <Badge variant="outline" className="text-gray-500 border-gray-700">Returned</Badge>
                            ) : (
                              <span className="text-success font-medium">{remaining}</span>
                            )}
                          </td>
                          <td className="px-4 py-3 text-center">
                            <Input
                              type="number"
                              min="0"
                              max={remaining}
                              value={currentVal === 0 ? "" : currentVal}
                              disabled={remaining === 0}
                              onChange={(e) => {
                                const val = parseInt(e.target.value) || 0;
                                setQuantities(prev => ({
                                  ...prev,
                                  [item.id]: Math.min(Math.max(0, val), remaining)
                                }));
                              }}
                              className="w-20 mx-auto text-center h-8 bg-gray-950 border-gray-700"
                              placeholder="0"
                            />
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="text-sm text-gray-400">Refund Method</label>
                <select
                  value={refundMethod}
                  onChange={(e) => setRefundMethod(e.target.value as RefundMethod)}
                  className="w-full h-10 px-3 bg-gray-950 border border-gray-800 rounded-lg text-sm text-white"
                >
                  <option value="ORIGINAL">Original Method ({sale.paymentMethod})</option>
                  <option value="CASH">Cash</option>
                  <option value="STORE_CREDIT">Store Credit</option>
                </select>
              </div>
              <div className="space-y-2">
                <label className="text-sm text-gray-400">Return Reason <span className="text-destructive">*</span></label>
                <select
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  className="w-full h-10 px-3 bg-gray-950 border border-gray-800 rounded-lg text-sm text-white focus:ring-1 focus:ring-primary"
                >
                  <option value="" disabled>Select a reason...</option>
                  <option value="Defective / Damaged">Defective / Damaged</option>
                  <option value="Customer Changed Mind">Customer Changed Mind</option>
                  <option value="Wrong Item">Wrong Item</option>
                  <option value="Exchange">Exchange</option>
                  <option value="Other">Other</option>
                </select>
                {reason === "Defective / Damaged" && (
                    <p className="text-xs text-warning mt-2 bg-warning/10 p-2 rounded border border-warning/20">
                      ⚠ Items will not be restored to inventory (Written Off).
                    </p>
                )}
                {reason === "Exchange" && (
                    <p className="text-xs text-primary mt-2 bg-primary/10 p-2 rounded border border-primary/20">
                      ℹ You will select replacement products in the next step.
                    </p>
                )}
              </div>
            </div>
            
            <div className="space-y-2">
              <label className="text-sm text-gray-400">Manager Notes (Optional)</label>
              <Input
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder="Any additional context..."
                className="bg-gray-950 border-gray-800"
              />
            </div>

            <div className="flex justify-between items-center pt-4 border-t border-gray-800">
               <div className="text-sm">
                  {totalReturnItems > 0 && (
                      <span className="text-gray-300">
                        Total Return Value: <span className="font-bold text-white text-lg">{CURRENCY.symbol} {totalReturnAmt.toFixed(2)}</span>
                      </span>
                  )}
               </div>
              <div className="flex gap-3">
                <Button type="button" variant="ghost" onClick={onClose} className="hover:bg-gray-800 text-gray-300">
                  Cancel
                </Button>
                <Button 
                  onClick={handleReturn}
                  disabled={returnMutation.isPending || !reason || totalReturnItems === 0}
                  className={reason === 'Exchange' ? "bg-primary hover:bg-primary/90 text-primary-foreground" : "bg-destructive hover:bg-red-700 text-white"}
                >
                  {returnMutation.isPending ? "Processing..." : reason === 'Exchange' ? "Continue to Exchange" : "Process Return"}
                </Button>
              </div>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
