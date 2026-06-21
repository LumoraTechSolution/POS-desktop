'use client';

import React, { useEffect, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Search, X, UserPlus, Phone, Star, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog';
import { customerService, Customer, CustomerRequest } from '@/services/customerService';
import { POS_ADD_CUSTOMER_BUTTON_ID } from '@/hooks/usePosKeyboard';
import { toast } from 'sonner';

interface CustomerSelectorProps {
  selectedCustomer: Customer | null;
  onSelect: (customer: Customer | null) => void;
}

/**
 * Splits the cashier's search term into name/phone defaults so the quick-add
 * dialog opens already half-filled. Digits → phone, words → name.
 */
function prefillFromSearch(search: string): { firstName: string; phone: string } {
  const trimmed = search.trim();
  if (!trimmed) return { firstName: '', phone: '' };
  const looksLikePhone = /^[+\d\s()-]+$/.test(trimmed) && /\d/.test(trimmed);
  return looksLikePhone
    ? { firstName: '', phone: trimmed }
    : { firstName: trimmed, phone: '' };
}

export const CustomerSelector: React.FC<CustomerSelectorProps> = ({ selectedCustomer, onSelect }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [results, setResults] = useState<Customer[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [isQuickAddOpen, setIsQuickAddOpen] = useState(false);
  const [quickFirstName, setQuickFirstName] = useState('');
  const [quickPhone, setQuickPhone] = useState('');

  const handleSearch = async (val: string) => {
    setSearch(val);
    if (val.length < 2) {
      setResults([]);
      return;
    }

    setIsSearching(true);
    try {
      const data = await customerService.getCustomers({ query: val });
      setResults(data?.content ?? []);
    } catch (error) {
      console.error(error);
    } finally {
      setIsSearching(false);
    }
  };

  // Pre-fill the quick-add fields from the search term whenever the dialog opens.
  useEffect(() => {
    if (isQuickAddOpen) {
      const pre = prefillFromSearch(search);
      setQuickFirstName(pre.firstName);
      setQuickPhone(pre.phone);
    }
  }, [isQuickAddOpen, search]);

  const registerMutation = useMutation({
    mutationFn: (data: CustomerRequest) => customerService.createCustomer(data),
    onSuccess: (created) => {
      toast.success(`${created.firstName} added and attached to this sale`);
      onSelect(created);
      setIsQuickAddOpen(false);
      setIsOpen(false);
      setSearch('');
      setResults([]);
      setQuickFirstName('');
      setQuickPhone('');
    },
    onError: (error: unknown) => {
      toast.error(
        (error as { response?: { data?: { message?: string } } })?.response?.data?.message ||
          'Failed to register customer'
      );
    },
  });

  const handleQuickSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const firstName = quickFirstName.trim();
    if (firstName.length < 2) {
      toast.error('Enter a name with at least 2 characters');
      return;
    }
    const phone = quickPhone.trim();
    registerMutation.mutate({
      firstName,
      phone: phone || undefined,
    });
  };

  if (selectedCustomer) {
    return (
      <div className="flex items-center justify-between p-3 bg-primary/10 border border-primary/20 rounded-xl animate-in slide-in-from-top-2">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-primary rounded-full flex items-center justify-center text-primary-foreground font-bold">
            {selectedCustomer.firstName[0]}
          </div>
          <div>
            <p className="text-sm font-bold text-white leading-tight">
              {selectedCustomer.firstName} {selectedCustomer.lastName}
            </p>
            <div className="flex items-center gap-2">
               <span className="text-[10px] text-gray-400 flex items-center gap-1">
                 <Phone size={10} /> {selectedCustomer.phone || 'No phone'}
               </span>
               <span className="text-[10px] text-primary font-bold flex items-center gap-1">
                 <Star size={10} fill="currentColor" /> {selectedCustomer.loyaltyPoints} pts
               </span>
            </div>
          </div>
        </div>
        <Button
          variant="ghost"
          size="icon"
          onClick={() => onSelect(null)}
          className="h-8 w-8 text-primary hover:text-indigo-300 hover:bg-primary/20"
        >
          <X size={16} />
        </Button>
      </div>
    );
  }

  return (
    <div className="relative">
      {!isOpen ? (
        <Button
          id={POS_ADD_CUSTOMER_BUTTON_ID}
          onClick={() => setIsOpen(true)}
          className="w-full h-12 bg-gray-900 border-gray-800 text-gray-400 justify-start gap-3 hover:bg-gray-800 rounded-xl"
        >
          <UserPlus size={18} />
          <span className="text-sm">Add Customer to Sale (F3)</span>
        </Button>
      ) : (
        <div className="space-y-2 animate-in fade-in duration-200">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" size={16} />
            <Input
              autoFocus
              placeholder="Search by name or phone..."
              value={search}
              onChange={(e) => handleSearch(e.target.value)}
              className="pl-10 h-11 bg-gray-900 border-gray-800 focus:ring-primary"
            />
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setIsOpen(false)}
              className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-500"
            >
              <X size={16} />
            </Button>
          </div>

          {search.length >= 2 && (
            <Card className="absolute z-50 w-full mt-1 bg-gray-900 border-gray-800 shadow-2xl max-h-60 overflow-y-auto">
              <div className="p-1">
                {isSearching ? (
                  <div className="p-4 text-center text-sm text-gray-500">Searching...</div>
                ) : results.length > 0 ? (
                  <>
                    {results.map(c => (
                      <button
                        key={c.id}
                        onClick={() => {
                          onSelect(c);
                          setIsOpen(false);
                        }}
                        className="w-full text-left p-3 hover:bg-gray-800 rounded-lg flex items-center justify-between group"
                      >
                        <div className="flex items-center gap-3">
                          <div className="w-8 h-8 bg-gray-800 group-hover:bg-primary/20 rounded-full flex items-center justify-center text-xs font-bold text-gray-400 group-hover:text-primary transition-colors">
                            {c.firstName[0]}
                          </div>
                          <div>
                            <p className="text-sm font-medium text-white">{c.firstName} {c.lastName}</p>
                            <p className="text-[10px] text-gray-500">{c.phone}</p>
                          </div>
                        </div>
                        <div className="text-right">
                          <p className="text-[10px] font-bold text-primary">{c.loyaltyPoints} pts</p>
                        </div>
                      </button>
                    ))}
                    <button
                      onClick={() => setIsQuickAddOpen(true)}
                      className="w-full text-left p-2 mt-1 text-xs text-primary hover:bg-primary/10 rounded-lg flex items-center gap-2 border-t border-gray-800"
                    >
                      <UserPlus size={12} /> Register a new customer instead
                    </button>
                  </>
                ) : (
                  <div className="p-4 text-center">
                    <p className="text-sm text-gray-500 mb-2">No customer found</p>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => setIsQuickAddOpen(true)}
                      className="text-primary border-primary/20 h-7 text-xs"
                    >
                      + Quick add
                    </Button>
                  </div>
                )}
              </div>
            </Card>
          )}
        </div>
      )}

      <Dialog open={isQuickAddOpen} onOpenChange={(o) => !registerMutation.isPending && setIsQuickAddOpen(o)}>
        <DialogContent className="sm:max-w-[420px] bg-gray-950 border-gray-800 text-white">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <UserPlus className="h-5 w-5 text-primary" />
              Quick add customer
            </DialogTitle>
            <DialogDescription className="text-gray-400">
              Just name and phone — finish their profile later from the Customers page.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleQuickSubmit} className="space-y-4 pt-2">
            <div className="space-y-2">
              <label htmlFor="qa-name" className="text-sm text-gray-300">Name *</label>
              <Input
                id="qa-name"
                autoFocus
                value={quickFirstName}
                onChange={(e) => setQuickFirstName(e.target.value)}
                placeholder="e.g. Sara"
                className="bg-gray-900 border-gray-800 focus-visible:ring-primary"
                required
                minLength={2}
              />
            </div>
            <div className="space-y-2">
              <label htmlFor="qa-phone" className="text-sm text-gray-300">Phone (optional)</label>
              <Input
                id="qa-phone"
                value={quickPhone}
                onChange={(e) => setQuickPhone(e.target.value)}
                placeholder="e.g. +94 77 123 4567"
                className="bg-gray-900 border-gray-800 focus-visible:ring-primary"
                inputMode="tel"
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => setIsQuickAddOpen(false)}
                disabled={registerMutation.isPending}
                className="border-gray-700 hover:bg-gray-800"
              >
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={registerMutation.isPending}
                className="bg-primary hover:bg-primary/90 text-primary-foreground min-w-[120px]"
              >
                {registerMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : 'Add & attach'}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
};
