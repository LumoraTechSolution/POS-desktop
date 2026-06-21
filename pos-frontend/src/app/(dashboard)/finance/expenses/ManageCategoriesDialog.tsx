"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { expenseService, type ExpenseCategory } from "@/services/expenseService";
import { QK } from "@/lib/queryKeys";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Plus, Trash2, Loader2, Check, X } from "lucide-react";
import { toast } from "sonner";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ManageCategoriesDialog({ open, onOpenChange }: Props) {
  const queryClient = useQueryClient();
  const [newName, setNewName] = useState("");

  const { data: categories, isLoading } = useQuery({
    queryKey: QK.expenseCategories,
    queryFn: () => expenseService.listCategories(),
    enabled: open,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: QK.expenseCategories });

  const createMutation = useMutation({
    mutationFn: (name: string) => expenseService.createCategory({ name, isActive: true }),
    onSuccess: () => {
      invalidate();
      setNewName("");
      toast.success("Category added");
    },
    onError: (e: unknown) =>
      toast.error((e as { response?: { data?: { message?: string } } })?.response?.data?.message || "Failed to add category"),
  });

  const toggleMutation = useMutation({
    mutationFn: (c: ExpenseCategory) =>
      expenseService.updateCategory(c.id, { name: c.name, isActive: !c.isActive }),
    onSuccess: invalidate,
    onError: () => toast.error("Failed to update category"),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => expenseService.removeCategory(id),
    onSuccess: () => {
      invalidate();
      toast.success("Category deleted");
    },
    onError: (e: unknown) =>
      toast.error((e as { response?: { data?: { message?: string } } })?.response?.data?.message || "Cannot delete category"),
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[440px] bg-background border-border">
        <DialogHeader>
          <DialogTitle>Expense Categories</DialogTitle>
          <DialogDescription className="text-muted-foreground">
            Organize spending. Deactivate a category to hide it from new expenses without losing history.
          </DialogDescription>
        </DialogHeader>

        <form
          className="flex gap-2 pt-2"
          onSubmit={(e) => {
            e.preventDefault();
            if (newName.trim()) createMutation.mutate(newName.trim());
          }}
        >
          <Input
            className="bg-card border-border"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            placeholder="New category name"
            maxLength={100}
          />
          <Button type="submit" disabled={!newName.trim() || createMutation.isPending} className="gap-1 shrink-0">
            {createMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus size={16} />}
            Add
          </Button>
        </form>

        <div className="max-h-72 overflow-y-auto space-y-1 mt-2">
          {isLoading ? (
            <div className="flex justify-center py-6">
              <Loader2 className="animate-spin text-primary" size={24} />
            </div>
          ) : (
            (categories ?? []).map((c) => (
              <div key={c.id} className="flex items-center justify-between rounded-lg border border-border px-3 py-2">
                <div className="flex items-center gap-2 min-w-0">
                  <span className="font-medium text-foreground truncate">{c.name}</span>
                  {!c.isActive && <Badge variant="outline" className="text-muted-foreground">Inactive</Badge>}
                </div>
                <div className="flex items-center gap-1 shrink-0">
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8"
                    title={c.isActive ? "Deactivate" : "Activate"}
                    onClick={() => toggleMutation.mutate(c)}
                    disabled={toggleMutation.isPending}
                  >
                    {c.isActive ? <X size={15} /> : <Check size={15} className="text-success" />}
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8 text-muted-foreground hover:text-destructive hover:bg-destructive/10"
                    title="Delete"
                    onClick={() => deleteMutation.mutate(c.id)}
                    disabled={deleteMutation.isPending}
                  >
                    <Trash2 size={15} />
                  </Button>
                </div>
              </div>
            ))
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
