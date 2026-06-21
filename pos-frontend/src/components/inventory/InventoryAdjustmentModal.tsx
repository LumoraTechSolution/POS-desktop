"use client";

import { useState, useEffect } from "react";
import { 
  Dialog, 
  DialogContent, 
  DialogHeader, 
  DialogTitle,
  DialogDescription
} from "@/components/ui/dialog";
import { 
  Form, 
  FormControl, 
  FormField, 
  FormItem, 
  FormLabel, 
  FormMessage 
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useForm, Resolver } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { inventoryAdjustmentService } from "@/services/inventoryAdjustmentService";
import { branchService, Branch } from "@/services/branchService";
import { Product } from "@/types/inventory";
import { toast } from "sonner";
import { 
  History, 
  ArrowLeftRight, 
  PackagePlus, 
  Loader2, 
  ArrowUpRight, 
  ArrowDownRight,
  RefreshCw,
  Info
} from "lucide-react";
import { format } from "date-fns";
import { cn } from "@/lib/utils";

const adjustmentSchema = z.object({
  branchId: z.string().uuid("Please select a branch"),
  type: z.enum(['STOCK_IN', 'STOCK_OUT', 'RECONCILIATION', 'DAMAGE', 'RETURN'] as const),
  quantity: z.coerce.number().min(0.01, "Quantity must be greater than 0"),
  reason: z.string().optional(),
});

const transferSchema = z.object({
  sourceBranchId: z.string().uuid("Please select source branch"),
  destinationBranchId: z.string().uuid("Please select destination branch"),
  quantity: z.coerce.number().min(0.01, "Quantity must be greater than 0"),
  reason: z.string().optional(),
}).refine(data => data.sourceBranchId !== data.destinationBranchId, {
  message: "Source and destination branches must be different",
  path: ["destinationBranchId"]
});

type AdjustmentType = 'STOCK_IN' | 'STOCK_OUT' | 'RECONCILIATION' | 'DAMAGE' | 'RETURN';

interface InventoryAdjustmentModalProps {
  product: Product | null;
  isOpen: boolean;
  onClose: () => void;
  /** Pre-fill the branch selector. Used when opening from a checkout-time
   *  "Fix stock" action so the manager doesn't have to re-pick the branch. */
  defaultBranchId?: string;
  /** Pre-fill the adjustment type (defaults to RECONCILIATION). */
  defaultType?: AdjustmentType;
}

export default function InventoryAdjustmentModal({ product, isOpen, onClose, defaultBranchId, defaultType }: InventoryAdjustmentModalProps) {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState("adjust");

  const { data: branches } = useQuery({
    queryKey: ["branches", "me"],
    queryFn: branchService.getMyBranches
  });

  const { data: history, isLoading: isHistoryLoading } = useQuery({
    queryKey: ['inventory-adjustments', product?.id],
    queryFn: () => inventoryAdjustmentService.getAdjustments(product!.id),
    enabled: !!product && isOpen && activeTab === "history"
  });

  const adjForm = useForm<z.infer<typeof adjustmentSchema>>({
    resolver: zodResolver(adjustmentSchema) as Resolver<z.infer<typeof adjustmentSchema>>,
    defaultValues: {
      type: defaultType ?? 'RECONCILIATION',
      branchId: defaultBranchId,
      quantity: 0,
      reason: "",
    }
  });

  // Re-apply pre-fill defaults each time the modal opens for a new product /
  // branch context (e.g. checkout "Fix stock" action targeting a specific row).
  useEffect(() => {
    if (isOpen) {
      adjForm.reset({
        type: defaultType ?? 'RECONCILIATION',
        branchId: defaultBranchId,
        quantity: 0,
        reason: "",
      });
    }
  }, [isOpen, defaultBranchId, defaultType, adjForm]);

  const transferForm = useForm<z.infer<typeof transferSchema>>({
    resolver: zodResolver(transferSchema) as Resolver<z.infer<typeof transferSchema>>,
    defaultValues: {
      quantity: 0,
      reason: "",
    }
  });

  const adjustMutation = useMutation({
    mutationFn: (data: z.infer<typeof adjustmentSchema>) => 
      inventoryAdjustmentService.adjustStock({
        ...data,
        productId: product!.id
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      queryClient.invalidateQueries({ queryKey: ['product', product?.id] });
      queryClient.invalidateQueries({ queryKey: ['inventory-adjustments', product?.id] });
      toast.success("Inventory adjusted successfully");
      adjForm.reset();
      onClose();
    },
    onError: (error: unknown) => {
      toast.error((error as { response?: { data?: { message?: string } } })?.response?.data?.message || "Adjustment failed");
    }
  });

  const transferMutation = useMutation({
    mutationFn: (data: z.infer<typeof transferSchema>) => 
      inventoryAdjustmentService.transferStock({
        ...data,
        productId: product!.id
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      queryClient.invalidateQueries({ queryKey: ['product', product?.id] });
      queryClient.invalidateQueries({ queryKey: ['inventory-adjustments', product?.id] });
      toast.success("Stock transferred successfully");
      transferForm.reset();
      onClose();
    },
    onError: (error: unknown) => {
      toast.error((error as { response?: { data?: { message?: string } } })?.response?.data?.message || "Transfer failed");
    }
  });

  if (!product) return null;

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="sm:max-w-[600px] bg-background border-border text-foreground">
        <DialogHeader>
          <div className="flex items-center gap-3 mb-2">
            <div className="w-10 h-10 rounded bg-primary/10 flex items-center justify-center text-primary">
              <PackagePlus size={20} />
            </div>
            <div>
              <DialogTitle className="text-xl">Inventory Management</DialogTitle>
              <DialogDescription className="text-muted-foreground">
                Adjust stock levels or transfer items for <span className="text-primary font-semibold">{product.name}</span>
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
          <TabsList className="grid w-full grid-cols-3 bg-card border border-border p-1 mb-6">
            <TabsTrigger value="adjust" className="gap-2 data-[state=active]:bg-muted data-[state=active]:text-primary">
              <PackagePlus size={16} /> Adjust
            </TabsTrigger>
            <TabsTrigger value="transfer" className="gap-2 data-[state=active]:bg-muted data-[state=active]:text-primary">
              <ArrowLeftRight size={16} /> Transfer
            </TabsTrigger>
            <TabsTrigger value="history" className="gap-2 data-[state=active]:bg-muted data-[state=active]:text-primary">
              <History size={16} /> History
            </TabsTrigger>
          </TabsList>

          <TabsContent value="adjust" className="space-y-4">
            <Form {...adjForm}>
              <form onSubmit={adjForm.handleSubmit(data => adjustMutation.mutate(data))} className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <FormField
                    control={adjForm.control}
                    name="branchId"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Target Branch</FormLabel>
                        <FormControl>
                          <select 
                            className="w-full h-10 px-3 bg-card border border-border rounded-lg text-sm focus:ring-2 focus:ring-primary outline-none"
                            {...field}
                          >
                            <option value="">Select Branch</option>
                            {branches?.map((b: Branch) => (
                              <option key={b.id} value={b.id}>{b.name}</option>
                            ))}
                          </select>
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={adjForm.control}
                    name="type"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Adjustment Type</FormLabel>
                        <FormControl>
                          <select 
                            className="w-full h-10 px-3 bg-card border border-border rounded-lg text-sm focus:ring-2 focus:ring-primary outline-none"
                            {...field}
                          >
                            <option value="RECONCILIATION">Reconciliation (Set Value)</option>
                            <option value="STOCK_IN">Stock In (Add)</option>
                            <option value="STOCK_OUT">Stock Out (Deduct)</option>
                            <option value="DAMAGE">Damaged/Expired</option>
                            <option value="RETURN">Customer Return</option>
                          </select>
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>

                <FormField
                  control={adjForm.control}
                  name="quantity"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Quantity</FormLabel>
                      <FormControl>
                        <Input type="number" placeholder="Enter amount" className="bg-card border-border" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={adjForm.control}
                  name="reason"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Reason (Optional)</FormLabel>
                      <FormControl>
                        <textarea 
                          className="w-full min-h-[80px] px-3 py-2 bg-card border border-border rounded-lg text-sm focus:ring-2 focus:ring-primary outline-none"
                          placeholder="Why is this adjustment being made?"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <div className="flex justify-end gap-3 pt-4">
                  <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
                  <Button type="submit" className="bg-primary hover:bg-primary/90 min-w-[120px]" disabled={adjustMutation.isPending}>
                    {adjustMutation.isPending ? <Loader2 className="animate-spin mr-2" size={16} /> : null}
                    Confirm Adjustment
                  </Button>
                </div>
              </form>
            </Form>
          </TabsContent>

          <TabsContent value="transfer" className="space-y-4">
            <Form {...transferForm}>
              <form onSubmit={transferForm.handleSubmit(data => transferMutation.mutate(data))} className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <FormField
                    control={transferForm.control}
                    name="sourceBranchId"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>From Branch</FormLabel>
                        <FormControl>
                          <select 
                            className="w-full h-10 px-3 bg-card border border-border rounded-lg text-sm focus:ring-2 focus:ring-primary outline-none"
                            {...field}
                          >
                            <option value="">Select Source</option>
                            {branches?.map((b: Branch) => (
                              <option key={b.id} value={b.id}>{b.name}</option>
                            ))}
                          </select>
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={transferForm.control}
                    name="destinationBranchId"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>To Branch</FormLabel>
                        <FormControl>
                          <select 
                            className="w-full h-10 px-3 bg-card border border-border rounded-lg text-sm focus:ring-2 focus:ring-primary outline-none"
                            {...field}
                          >
                            <option value="">Select Destination</option>
                            {branches?.map((b: Branch) => (
                              <option key={b.id} value={b.id}>{b.name}</option>
                            ))}
                          </select>
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>

                <FormField
                  control={transferForm.control}
                  name="quantity"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Transfer Quantity</FormLabel>
                      <FormControl>
                        <Input type="number" placeholder="Enter amount" className="bg-card border-border" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={transferForm.control}
                  name="reason"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Note</FormLabel>
                      <FormControl>
                        <textarea 
                          className="w-full min-h-[80px] px-3 py-2 bg-card border border-border rounded-lg text-sm focus:ring-2 focus:ring-primary outline-none"
                          placeholder="Describe the transfer..."
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <div className="bg-primary/5 border border-primary/20 rounded-lg p-3 flex gap-3 text-xs text-indigo-300">
                  <Info className="shrink-0" size={14} />
                  <p>Stock will be deducted from the source and added to the destination branch atomically.</p>
                </div>

                <div className="flex justify-end gap-3 pt-4">
                  <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
                  <Button type="submit" className="bg-primary hover:bg-primary/90 min-w-[120px]" disabled={transferMutation.isPending}>
                    {transferMutation.isPending ? <Loader2 className="animate-spin mr-2" size={16} /> : null}
                    Transfer Stock
                  </Button>
                </div>
              </form>
            </Form>
          </TabsContent>

          <TabsContent value="history" className="h-[400px] overflow-y-auto pr-2 scrollbar-thin scrollbar-thumb-gray-800">
            {isHistoryLoading ? (
              <div className="flex h-full items-center justify-center text-muted-foreground flex-col gap-2">
                <Loader2 className="animate-spin" />
                <p className="text-sm">Fetching audit logs...</p>
              </div>
            ) : history && history.length > 0 ? (
              <div className="space-y-4 pb-4">
                {history.map((log) => (
                  <div key={log.id} className="p-4 bg-card/50 border border-border rounded-xl flex items-start justify-between gap-4">
                    <div className="flex gap-3">
                      <div className={cn(
                        "w-8 h-8 rounded-lg flex items-center justify-center",
                        log.type.includes('IN') || log.type === 'RETURN' ? "bg-success/10 text-success" :
                        log.type.includes('OUT') || log.type === 'DAMAGE' || log.type === 'SALE' ? "bg-destructive/10 text-destructive" :
                        "bg-primary/10 text-primary"
                      )}>
                        {log.type.includes('IN') || log.type === 'RETURN' ? <ArrowUpRight size={16} /> :
                         log.type.includes('OUT') || log.type === 'DAMAGE' || log.type === 'SALE' ? <ArrowDownRight size={16} /> :
                         <RefreshCw size={16} />}
                      </div>
                      <div>
                        <div className="text-sm font-semibold text-foreground">
                          {log.type.replace(/_/g, ' ')}
                        </div>
                        <div className="text-[10px] text-muted-foreground font-mono uppercase">
                          {log.branchName} • {format(new Date(log.createdAt), 'MMM dd, yyyy HH:mm')}
                        </div>
                        {log.reason && (
                          <p className="text-xs text-muted-foreground mt-1 italic">&quot;{log.reason}&quot;</p>
                        )}
                      </div>
                    </div>
                    <div className="text-right">
                      <div className={cn(
                        "text-sm font-bold",
                        log.type.includes('IN') || log.type === 'RETURN' ? "text-success" :
                        log.type.includes('OUT') || log.type === 'DAMAGE' || log.type === 'SALE' ? "text-destructive" :
                        "text-primary"
                      )}>
                        {log.type.includes('IN') || log.type === 'RETURN' ? '+' :
                         log.type.includes('OUT') || log.type === 'DAMAGE' || log.type === 'SALE' ? '-' : ''}
                        {log.quantity}
                      </div>
                      <div className="text-[10px] text-muted-foreground">
                         {log.previousQuantity} → {log.newQuantity}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="flex h-full items-center justify-center text-muted-foreground flex-col gap-2">
                <History className="opacity-20" size={48} />
                <p className="text-sm font-medium">No adjustment history found.</p>
              </div>
            )}
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  );
}
