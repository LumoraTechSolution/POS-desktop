"use client";

import { useEffect, useMemo, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  expenseService,
  type Expense,
  type ExpenseRequest,
} from "@/services/expenseService";
import { QK } from "@/lib/queryKeys";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { Wallet, Plus, Edit2, Trash2, Loader2, Settings2 } from "lucide-react";
import { toast } from "sonner";
import { ManageCategoriesDialog } from "./ManageCategoriesDialog";
import { useAuthStore } from "@/stores/authStore";
import { branchService } from "@/services/branchService";

const PAYMENT_METHODS = ["CASH", "CARD", "BANK_TRANSFER", "CHEQUE", "OTHER"];

const money = (n: number) =>
  (Number.isFinite(n) ? n : 0).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

function monthRange() {
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), 1);
  const end = new Date(now.getFullYear(), now.getMonth() + 1, 0);
  const iso = (d: Date) => d.toISOString().slice(0, 10);
  return { start: iso(start), end: iso(end) };
}

export default function ExpensesPage() {
  const queryClient = useQueryClient();
  const hasFeature = useAuthStore((s) => s.hasFeature);
  const branchEnabled = hasFeature("BRANCH_RESTRICTIONS");
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Expense | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Expense | null>(null);
  const [manageOpen, setManageOpen] = useState(false);

  const { start, end } = useMemo(monthRange, []);

  const { data: expensesPage, isLoading } = useQuery({
    queryKey: [...QK.expenses, start, end],
    queryFn: () => expenseService.list({ start, end, size: 200 }),
  });

  const { data: categories } = useQuery({
    queryKey: QK.expenseCategories,
    queryFn: () => expenseService.listCategories(),
  });

  const expenses = expensesPage?.content ?? [];
  const monthTotal = expenses.reduce((sum, e) => sum + e.amount, 0);

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: QK.expenses });
    queryClient.invalidateQueries({ queryKey: QK.financePnl });
    queryClient.invalidateQueries({ queryKey: QK.financeCashFlow });
  };

  const saveMutation = useMutation({
    mutationFn: (payload: { id?: string; data: ExpenseRequest }) =>
      payload.id ? expenseService.update(payload.id, payload.data) : expenseService.create(payload.data),
    onSuccess: () => {
      invalidate();
      toast.success(editing ? "Expense updated" : "Expense recorded");
      closeModal();
    },
    onError: (error: unknown) =>
      toast.error(
        (error as { response?: { data?: { message?: string } } })?.response?.data?.message ||
          "Failed to save expense"
      ),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => expenseService.remove(id),
    onSuccess: () => {
      invalidate();
      toast.success("Expense deleted");
      setDeleteTarget(null);
    },
    onError: () => toast.error("Failed to delete expense"),
  });

  const openCreate = () => {
    setEditing(null);
    setModalOpen(true);
  };
  const openEdit = (e: Expense) => {
    setEditing(e);
    setModalOpen(true);
  };
  const closeModal = () => {
    setModalOpen(false);
    setEditing(null);
  };

  return (
    <div className="p-8 space-y-6 animate-in fade-in duration-500">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <Wallet className="text-primary" size={24} />
            <h1 className="text-3xl font-bold tracking-tight">Expenses</h1>
          </div>
          <p className="text-muted-foreground">
            Track operating costs — rent, payroll, utilities, and more. These feed your P&amp;L and cash flow.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={() => setManageOpen(true)} className="gap-2">
            <Settings2 size={16} /> Categories
          </Button>
          <Button onClick={openCreate} className="gap-2 bg-primary hover:bg-primary/90">
            <Plus size={18} /> Add Expense
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card className="bg-background border-border">
          <CardContent className="p-5">
            <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">This Month</p>
            <p className="text-2xl font-bold text-foreground">{money(monthTotal)}</p>
          </CardContent>
        </Card>
        <Card className="bg-background border-border">
          <CardContent className="p-5">
            <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Entries</p>
            <p className="text-2xl font-bold text-foreground">{expenses.length}</p>
          </CardContent>
        </Card>
        <Card className="bg-background border-border">
          <CardContent className="p-5">
            <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Categories</p>
            <p className="text-2xl font-bold text-foreground">{categories?.length ?? 0}</p>
          </CardContent>
        </Card>
      </div>

      <Card className="bg-background border-border overflow-hidden">
        <CardContent className="p-0">
          <Table>
            <TableHeader className="bg-muted/40">
              <TableRow className="border-border hover:bg-transparent">
                <TableHead className="pl-6">Date</TableHead>
                <TableHead>Category</TableHead>
                {branchEnabled && <TableHead>Branch</TableHead>}
                <TableHead>Payee</TableHead>
                <TableHead>Method</TableHead>
                <TableHead className="text-right">Amount</TableHead>
                <TableHead className="text-right pr-6">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell colSpan={branchEnabled ? 7 : 6} className="h-40 text-center">
                    <Loader2 className="animate-spin text-primary mx-auto" size={28} />
                  </TableCell>
                </TableRow>
              ) : expenses.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={branchEnabled ? 7 : 6} className="h-40 text-center">
                    <div className="flex flex-col items-center gap-2 text-muted-foreground">
                      <Wallet size={40} className="opacity-10" />
                      <p className="text-sm font-medium">No expenses this month</p>
                      <Button variant="link" className="text-primary" onClick={openCreate}>
                        Record your first expense
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ) : (
                expenses.map((e) => (
                  <TableRow key={e.id} className="border-border hover:bg-foreground/5 group">
                    <TableCell className="pl-6">{e.expenseDate}</TableCell>
                    <TableCell className="font-medium text-foreground">{e.categoryName}</TableCell>
                    {branchEnabled && (
                      <TableCell className="text-muted-foreground">
                        {e.branchName || <span className="italic opacity-70">Company-wide</span>}
                      </TableCell>
                    )}
                    <TableCell className="text-muted-foreground">{e.payee || "—"}</TableCell>
                    <TableCell className="text-muted-foreground">{e.paymentMethod || "—"}</TableCell>
                    <TableCell className="text-right font-semibold tabular-nums">{money(e.amount)}</TableCell>
                    <TableCell className="text-right pr-6">
                      <div className="flex justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                        <Button variant="ghost" size="icon" className="h-8 w-8 text-primary hover:bg-primary/10"
                          aria-label="Edit expense" onClick={() => openEdit(e)}>
                          <Edit2 size={15} />
                        </Button>
                        <Button variant="ghost" size="icon"
                          className="h-8 w-8 text-muted-foreground hover:text-destructive hover:bg-destructive/10"
                          aria-label="Delete expense" onClick={() => setDeleteTarget(e)}>
                          <Trash2 size={15} />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <ExpenseModal
        open={modalOpen}
        editing={editing}
        categories={categories ?? []}
        branchEnabled={branchEnabled}
        submitting={saveMutation.isPending}
        onClose={closeModal}
        onSubmit={(data) => saveMutation.mutate({ id: editing?.id, data })}
      />

      <ManageCategoriesDialog open={manageOpen} onOpenChange={setManageOpen} />

      <Dialog open={!!deleteTarget} onOpenChange={() => setDeleteTarget(null)}>
        <DialogContent className="sm:max-w-[400px] bg-background border-border">
          <DialogHeader>
            <DialogTitle className="text-destructive">Delete expense</DialogTitle>
            <DialogDescription className="text-muted-foreground pt-2">
              Delete this {deleteTarget ? money(deleteTarget.amount) : ""} expense? This cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className="pt-4">
            <Button variant="ghost" onClick={() => setDeleteTarget(null)} disabled={deleteMutation.isPending}>
              Cancel
            </Button>
            <Button variant="destructive" disabled={deleteMutation.isPending}
              onClick={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)}>
              {deleteMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

interface ExpenseModalProps {
  open: boolean;
  editing: Expense | null;
  categories: { id: string; name: string; isActive: boolean }[];
  branchEnabled: boolean;
  submitting: boolean;
  onClose: () => void;
  onSubmit: (data: ExpenseRequest) => void;
}

function ExpenseModal({ open, editing, categories, branchEnabled, submitting, onClose, onSubmit }: ExpenseModalProps) {
  const today = new Date().toISOString().slice(0, 10);
  const [categoryId, setCategoryId] = useState("");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(today);
  const [payee, setPayee] = useState("");
  const [method, setMethod] = useState("CASH");
  const [reference, setReference] = useState("");
  const [notes, setNotes] = useState("");
  const [branchId, setBranchId] = useState("");

  const { data: branches = [] } = useQuery({
    queryKey: ["myBranches"],
    queryFn: branchService.getMyBranches,
    enabled: branchEnabled && open,
  });

  // Reset form whenever the modal opens (for create) or target changes (for edit).
  useEffect(() => {
    if (!open) return;
    setCategoryId(editing?.categoryId ?? categories[0]?.id ?? "");
    setAmount(editing ? String(editing.amount) : "");
    setDate(editing?.expenseDate ?? today);
    setPayee(editing?.payee ?? "");
    setMethod(editing?.paymentMethod ?? "CASH");
    setReference(editing?.reference ?? "");
    setNotes(editing?.notes ?? "");
    setBranchId(editing?.branchId ?? "");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, editing]);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const amt = parseFloat(amount);
    if (!categoryId || !Number.isFinite(amt) || amt <= 0) {
      toast.error("Pick a category and enter an amount greater than zero");
      return;
    }
    onSubmit({
      categoryId,
      amount: amt,
      expenseDate: date,
      payee: payee.trim() || null,
      paymentMethod: method || null,
      reference: reference.trim() || null,
      notes: notes.trim() || null,
      branchId: branchId || null,
    });
  };

  const inputCls = "bg-card border-border";
  const selectCls =
    "w-full rounded-md bg-card border border-border text-sm text-foreground px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/50";

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="sm:max-w-[460px] bg-background border-border">
        <DialogHeader>
          <DialogTitle>{editing ? "Edit Expense" : "Add Expense"}</DialogTitle>
          <DialogDescription className="text-muted-foreground">
            Record a non-inventory business cost.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} className="space-y-4 pt-2">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">Category <span className="text-destructive">*</span></label>
              <select className={selectCls} value={categoryId} onChange={(ev) => setCategoryId(ev.target.value)}>
                <option value="" disabled>Select…</option>
                {categories.filter((c) => c.isActive || c.id === editing?.categoryId).map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">Amount <span className="text-destructive">*</span></label>
              <Input className={inputCls} type="number" step="0.01" min="0" value={amount}
                onChange={(e) => setAmount(e.target.value)} placeholder="0.00" required />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">Date <span className="text-destructive">*</span></label>
              <Input className={inputCls} type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">Payment method</label>
              <select className={selectCls} value={method} onChange={(ev) => setMethod(ev.target.value)}>
                {PAYMENT_METHODS.map((m) => (
                  <option key={m} value={m}>{m.replace("_", " ")}</option>
                ))}
              </select>
            </div>
          </div>
          {branchEnabled && (
            <div className="space-y-2">
              <label className="text-sm font-medium">Branch</label>
              <select className={selectCls} value={branchId} onChange={(ev) => setBranchId(ev.target.value)}>
                <option value="">Company-wide (no branch)</option>
                {branches.map((b) => (
                  <option key={b.id} value={b.id}>{b.name}</option>
                ))}
              </select>
              <p className="text-xs text-muted-foreground">
                Tag this cost to a branch for per-branch P&amp;L, or leave company-wide for shared overhead.
              </p>
            </div>
          )}
          <div className="space-y-2">
            <label className="text-sm font-medium">Payee</label>
            <Input className={inputCls} value={payee} onChange={(e) => setPayee(e.target.value)}
              placeholder="e.g. City Power Co." maxLength={255} />
          </div>
          <div className="space-y-2">
            <label className="text-sm font-medium">Reference / Notes</label>
            <Input className={inputCls} value={reference} onChange={(e) => setReference(e.target.value)}
              placeholder="Invoice # or reference" maxLength={100} />
            <textarea
              className="w-full rounded-md bg-card border border-border text-sm px-3 py-2 resize-none focus:outline-none focus:ring-2 focus:ring-primary/50"
              rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Notes (optional)" maxLength={2000} />
          </div>
          <DialogFooter className="pt-2">
            <Button type="button" variant="ghost" onClick={onClose} disabled={submitting}>Cancel</Button>
            <Button type="submit" disabled={submitting} className="bg-primary hover:bg-primary/90 min-w-[120px]">
              {submitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {editing ? "Save" : "Add Expense"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
