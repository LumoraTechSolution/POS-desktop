'use client';

import { useEffect, useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { toast } from 'sonner';

interface CustomItemModalProps {
  open: boolean;
  onClose: () => void;
  onAdd: (name: string, price: number, quantity: number) => void;
}

/**
 * Adds an open/custom line item — for a product that isn't in the catalog.
 * Sells and prints, but is not stock-tracked (handled server-side).
 */
export function CustomItemModal({ open, onClose, onAdd }: CustomItemModalProps) {
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [qty, setQty] = useState('1');

  useEffect(() => {
    if (open) {
      setName('');
      setPrice('');
      setQty('1');
    }
  }, [open]);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const priceNum = parseFloat(price);
    const qtyNum = parseInt(qty, 10);
    if (!name.trim()) {
      toast.error('Enter an item name');
      return;
    }
    if (!Number.isFinite(priceNum) || priceNum < 0) {
      toast.error('Enter a valid price');
      return;
    }
    if (!Number.isInteger(qtyNum) || qtyNum < 1) {
      toast.error('Quantity must be at least 1');
      return;
    }
    onAdd(name.trim(), priceNum, qtyNum);
    toast.success(`Added: ${name.trim()}`);
    onClose();
  };

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-[400px] bg-background border-border">
        <DialogHeader>
          <DialogTitle>Add Custom Item</DialogTitle>
          <DialogDescription className="text-muted-foreground">
            Bill an item that isn&apos;t in the catalog. It won&apos;t be stock-tracked.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} className="space-y-4 pt-2">
          <div className="space-y-2">
            <label className="text-sm font-medium">Item name <span className="text-destructive">*</span></label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Loose screws"
              maxLength={255}
              autoFocus
              className="bg-card border-border"
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">Unit price <span className="text-destructive">*</span></label>
              <Input
                type="number"
                step="0.01"
                min="0"
                value={price}
                onChange={(e) => setPrice(e.target.value)}
                placeholder="0.00"
                className="bg-card border-border"
              />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">Quantity</label>
              <Input
                type="number"
                min="1"
                step="1"
                value={qty}
                onChange={(e) => setQty(e.target.value)}
                className="bg-card border-border"
              />
            </div>
          </div>
          <DialogFooter className="pt-2">
            <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
            <Button type="submit" className="bg-primary hover:bg-primary/90 min-w-[110px]">Add to cart</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
