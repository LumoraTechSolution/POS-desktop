'use client';

import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  UserPlus,
  Edit2,
  Trash2,
  Mail,
  Phone,
  MapPin,
  Star,
  Loader2,
  X,
  Eye,
  Download
} from 'lucide-react';
import { useRouter } from 'next/navigation';
import { Customer, CustomerRequest, customerService } from '@/services/customerService';
import { Button } from '@/components/ui/button';
import { exportToCsv, type CsvColumn } from '@/lib/exportCsv';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { DataTableToolbar } from '@/components/ui/data-table-toolbar';
import { toast } from 'sonner';
import { CustomerForm } from '@/components/customers/CustomerForm';
import { useConfirmDialog } from '@/components/super-admin/ConfirmDialog';

const CUSTOMER_CSV_COLUMNS: CsvColumn<Customer>[] = [
  { header: 'First Name', value: (c) => c.firstName },
  { header: 'Last Name', value: (c) => c.lastName ?? '' },
  { header: 'Email', value: (c) => c.email ?? '' },
  { header: 'Phone', value: (c) => c.phone ?? '' },
  { header: 'Address', value: (c) => c.address ?? '' },
  { header: 'Loyalty Points', value: (c) => c.loyaltyPoints },
  { header: 'Member Since', value: (c) => new Date(c.createdAt).toLocaleDateString() },
];

export default function CustomersPage() {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [editingCustomer, setEditingCustomer] = useState<Customer | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const queryClient = useQueryClient();
  const router = useRouter();
  const { confirm, dialog: confirmDialog } = useConfirmDialog();

  const { data, isLoading } = useQuery({
    queryKey: ['customers', search, page],
    queryFn: () => customerService.getCustomers({ query: search, page, size: 10 }),
  });

  // Export every customer matching the current search (not just the page).
  const exportMutation = useMutation({
    mutationFn: async () => {
      const probe = await customerService.getCustomers({ query: search, page: 0, size: 1 });
      const total = probe.totalElements ?? 0;
      if (total === 0) return [] as Customer[];
      const all = await customerService.getCustomers({ query: search, page: 0, size: total });
      return all.content;
    },
    onSuccess: (rows) => {
      if (rows.length === 0) {
        toast.info('No customers to export');
        return;
      }
      exportToCsv('customers', CUSTOMER_CSV_COLUMNS, rows);
      toast.success(`Exported ${rows.length} customer${rows.length === 1 ? '' : 's'}`);
    },
    onError: (error: unknown) => {
      toast.error((error as { response?: { data?: { message?: string } } })?.response?.data?.message || 'Export failed');
    },
  });

  const createMutation = useMutation({
    mutationFn: (data: CustomerRequest) => customerService.createCustomer(data),
    onSuccess: () => {
      toast.success('Customer registered successfully');
      setShowCreate(false);
      queryClient.invalidateQueries({ queryKey: ['customers'] });
    },
    onError: (error: unknown) => {
      toast.error((error as { response?: { data?: { message?: string } } })?.response?.data?.message || 'Failed to register customer');
    }
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: CustomerRequest }) => customerService.updateCustomer(id, data),
    onSuccess: () => {
      toast.success('Customer updated successfully');
      setEditingCustomer(null);
      queryClient.invalidateQueries({ queryKey: ['customers'] });
    },
    onError: (error: unknown) => {
      toast.error((error as { response?: { data?: { message?: string } } })?.response?.data?.message || 'Failed to update customer');
    }
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => customerService.deleteCustomer(id),
    onSuccess: () => {
      toast.success('Customer deleted');
      queryClient.invalidateQueries({ queryKey: ['customers'] });
    },
    onError: (error: unknown) => {
      toast.error((error as { response?: { data?: { message?: string } } })?.response?.data?.message || 'Failed to delete customer');
    }
  });

  const bulkDeleteMutation = useMutation({
    mutationFn: (ids: string[]) => customerService.bulkDeleteCustomers(ids),
    onSuccess: (count) => {
      toast.success(`Deleted ${count} customer${count === 1 ? '' : 's'}`);
      setSelected(new Set());
      queryClient.invalidateQueries({ queryKey: ['customers'] });
    },
    onError: (error: unknown) => {
      toast.error((error as { response?: { data?: { message?: string } } })?.response?.data?.message || 'Failed to delete customers');
    },
  });

  const handleDelete = async (id: string) => {
    const ok = await confirm({
      title: 'Delete this customer?',
      description: 'This will permanently remove the customer profile. Historical sales remain intact.',
      confirmLabel: 'Delete customer',
      variant: 'destructive',
    });
    if (ok) deleteMutation.mutate(id);
  };

  const toggleRow = (id: string, checked: boolean) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  };

  const pageCustomers = data?.content ?? [];
  const allPageSelected = pageCustomers.length > 0 && pageCustomers.every((c) => selected.has(c.id));
  const somePageSelected = pageCustomers.some((c) => selected.has(c.id));

  const togglePage = (checked: boolean) => {
    setSelected((prev) => {
      const next = new Set(prev);
      for (const c of pageCustomers) {
        if (checked) next.add(c.id);
        else next.delete(c.id);
      }
      return next;
    });
  };

  const handleBulkDelete = async () => {
    const ids = Array.from(selected);
    if (ids.length === 0) return;
    const ok = await confirm({
      title: `Delete ${ids.length} customer${ids.length === 1 ? '' : 's'}?`,
      description: 'This permanently removes the selected customer profiles. Historical sales remain intact.',
      confirmLabel: `Delete ${ids.length}`,
      variant: 'destructive',
    });
    if (ok) bulkDeleteMutation.mutate(ids);
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-500 p-6">
      {confirmDialog}
      <div>
        <h1 className="text-3xl font-black tracking-tight text-foreground mb-1">Customer <span className="text-primary">Database</span></h1>
        <p className="text-muted-foreground text-sm">Manage client profiles and loyalty points.</p>
      </div>

      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-200">
          <Card className="w-full max-w-lg bg-card border-border shadow-2xl">
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="text-xl font-bold text-foreground">Register New Customer</CardTitle>
              <Button variant="ghost" size="icon" aria-label="Close" onClick={() => setShowCreate(false)} className="text-muted-foreground">
                <X size={20} />
              </Button>
            </CardHeader>
            <CardContent className="p-6">
              <CustomerForm
                onSubmit={(data) => createMutation.mutate(data)}
                isLoading={createMutation.isPending}
              />
            </CardContent>
          </Card>
        </div>
      )}

      {editingCustomer && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-200">
          <Card className="w-full max-w-lg bg-card border-border shadow-2xl">
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="text-xl font-bold text-foreground">Edit Customer Profile</CardTitle>
              <Button variant="ghost" size="icon" aria-label="Close" onClick={() => setEditingCustomer(null)} className="text-muted-foreground">
                <X size={20} />
              </Button>
            </CardHeader>
            <CardContent className="p-6">
              <CustomerForm 
                initialData={editingCustomer}
                onSubmit={(data) => updateMutation.mutate({ id: editingCustomer.id, data })} 
                isLoading={updateMutation.isPending} 
              />
            </CardContent>
          </Card>
        </div>
      )}

      <DataTableToolbar
        searchValue={search}
        onSearchChange={(v) => { setSearch(v); setPage(0); }}
        searchPlaceholder="Search by name, email or phone..."
        resultsCount={{ shown: data?.content.length ?? 0, total: data?.totalElements ?? 0, label: 'customers' }}
        actions={
          <>
            <Button
              variant="outline"
              onClick={() => exportMutation.mutate()}
              disabled={exportMutation.isPending}
              className="gap-2"
            >
              {exportMutation.isPending ? <Loader2 size={18} className="animate-spin" /> : <Download size={18} />}
              Export
            </Button>
            <Button onClick={() => setShowCreate(true)} className="bg-primary hover:bg-primary text-primary-foreground font-bold gap-2">
              <UserPlus size={18} /> Add New Customer
            </Button>
          </>
        }
        selectionBar={
          selected.size > 0 ? (
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-foreground">{selected.size} selected</span>
              <div className="flex items-center gap-2">
                <Button variant="ghost" size="sm" onClick={() => setSelected(new Set())}>
                  Clear
                </Button>
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={handleBulkDelete}
                  disabled={bulkDeleteMutation.isPending}
                  className="gap-2"
                >
                  {bulkDeleteMutation.isPending ? <Loader2 size={14} className="animate-spin" /> : <Trash2 size={14} />}
                  Delete {selected.size}
                </Button>
              </div>
            </div>
          ) : undefined
        }
      />

      <Card className="bg-card border-border shadow-xl overflow-hidden">
        <CardContent className="p-0">
          <Table>
            <TableHeader className="bg-muted/60">
              <TableRow className="border-border hover:bg-transparent">
                <TableHead className="w-12 pl-6">
                  <Checkbox
                    checked={allPageSelected}
                    indeterminate={somePageSelected && !allPageSelected}
                    onCheckedChange={togglePage}
                    aria-label="Select all customers on this page"
                  />
                </TableHead>
                <TableHead className="text-muted-foreground font-bold uppercase text-[10px] tracking-widest">Customer</TableHead>
                <TableHead className="text-muted-foreground font-bold uppercase text-[10px] tracking-widest">Contact Information</TableHead>
                <TableHead className="text-muted-foreground font-bold uppercase text-[10px] tracking-widest">Address</TableHead>
                <TableHead className="text-muted-foreground font-bold uppercase text-[10px] tracking-widest">Loyalty Points</TableHead>
                <TableHead className="text-right text-muted-foreground font-bold uppercase text-[10px] tracking-widest pr-6">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell colSpan={6} className="h-64 text-center">
                    <div className="flex flex-col items-center gap-2 text-muted-foreground">
                      <Loader2 className="animate-spin text-primary" size={32} />
                      <p className="font-medium">Loading customer directory...</p>
                    </div>
                  </TableCell>
                </TableRow>
              ) : (data?.content || []).length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="h-64 text-center">
                    <div className="flex flex-col items-center gap-2 text-muted-foreground">
                       <UserPlus size={48} className="opacity-10 mb-2" />
                       <p className="font-medium">No customers found</p>
                       <Button variant="link" className="text-primary" onClick={() => setSearch('')}>Clear Search</Button>
                    </div>
                  </TableCell>
                </TableRow>
              ) : (
                data?.content.map((customer: Customer) => (
                  <TableRow
                    key={customer.id}
                    data-state={selected.has(customer.id) ? 'selected' : undefined}
                    className="border-border hover:bg-foreground/5 data-[state=selected]:bg-primary/5 transition-colors group"
                  >
                    <TableCell className="pl-6">
                      <Checkbox
                        checked={selected.has(customer.id)}
                        onCheckedChange={(c) => toggleRow(customer.id, c)}
                        aria-label={`Select ${customer.firstName} ${customer.lastName ?? ''}`.trim()}
                      />
                    </TableCell>
                    <TableCell className="py-4">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-primary flex items-center justify-center text-primary-foreground font-bold shadow-lg text-xs">
                          {customer.firstName[0]}{customer.lastName?.[0] || ''}
                        </div>
                        <div>
                          <p className="font-bold text-foreground leading-tight">{customer.firstName} {customer.lastName}</p>
                          <p className="text-[10px] font-medium text-muted-foreground uppercase tracking-tighter">Member since {new Date(customer.createdAt).toLocaleDateString()}</p>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      <div className="space-y-1">
                        <div className="flex items-center gap-2 text-sm text-foreground">
                          <Mail size={14} className="text-primary" /> {customer.email || 'N/A'}
                        </div>
                        <div className="flex items-center gap-2 text-xs text-muted-foreground">
                          <Phone size={14} className="text-success" /> {customer.phone || 'N/A'}
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                       <div className="flex items-start gap-2 max-w-[200px]">
                          <MapPin size={14} className="text-destructive shrink-0 mt-0.5" />
                          <span className="text-xs text-muted-foreground line-clamp-2">{customer.address || 'No address provided'}</span>
                       </div>
                    </TableCell>
                    <TableCell>
                       <div className="flex items-center gap-2">
                          <div className="bg-primary/10 border border-primary/20 px-3 py-1 rounded-full flex items-center gap-2">
                             <Star size={14} className="text-primary" fill="currentColor" />
                             <span className="font-black text-primary">{customer.loyaltyPoints}</span>
                          </div>
                       </div>
                    </TableCell>
                    <TableCell className="text-right pr-6">
                      <div className="flex justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label={`View ${customer.firstName} ${customer.lastName}`}
                          title="View"
                          onClick={() => router.push(`/customers/${customer.id}`)}
                          className="h-8 w-8 text-info hover:bg-info/10"
                        >
                          <Eye size={16} />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label={`Edit ${customer.firstName} ${customer.lastName}`}
                          title="Edit"
                          onClick={() => setEditingCustomer(customer)}
                          className="h-8 w-8 text-primary hover:bg-primary/10"
                        >
                          <Edit2 size={16} />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label={`Delete ${customer.firstName} ${customer.lastName}`}
                          title="Delete"
                          onClick={() => handleDelete(customer.id)}
                          className="h-8 w-8 text-muted-foreground hover:text-destructive hover:bg-destructive/10"
                        >
                          <Trash2 size={16} />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
          
          <div className="p-4 border-t border-border flex items-center justify-end">
             <div className="flex gap-2">
                <Button 
                  disabled={page === 0} 
                  onClick={() => setPage(page - 1)}
                  variant="outline" 
                  size="sm" 
                  className="bg-background border-border text-muted-foreground h-8"
                >
                  Previous
                </Button>
                <Button 
                  disabled={data ? (page + 1) >= data.totalPages : true} 
                  onClick={() => setPage(page + 1)}
                  variant="outline" 
                  size="sm" 
                  className="bg-background border-border text-muted-foreground h-8"
                >
                  Next
                </Button>
             </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
