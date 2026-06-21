"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { branchService, Branch, BranchRequest } from "@/services/branchService";
import { Button } from "@/components/ui/button";
import { Plus, Store, Info, Shield } from "lucide-react";
import BranchTable from "@/components/branches/BranchTable";
import BranchFormModal from "@/components/branches/BranchForm";
import { toast } from "sonner";
import { useAuthStore } from "@/stores/authStore";
import { QK } from "@/lib/queryKeys";

export default function BranchesPage() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedBranch, setSelectedBranch] = useState<Branch | null>(null);
  
  const queryClient = useQueryClient();

  // Fetch branches
  const { data: branches, isLoading } = useQuery({
    queryKey: QK.branches,
    queryFn: () => branchService.getAllBranches(),
  });

  // Mutations
  const createMutation = useMutation({
    mutationFn: (data: BranchRequest) => branchService.createBranch(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QK.branches });
      toast.success("Branch created successfully");
      closeModal();
    },
    onError: (error: unknown) => {
      toast.error((error as { response?: { data?: { message?: string } } })?.response?.data?.message || "Failed to create branch");
    }
  });

  const updateMutation = useMutation({
    mutationFn: (data: { id: string; request: BranchRequest }) => 
      branchService.updateBranch(data.id, data.request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QK.branches });
      toast.success("Branch updated successfully");
      closeModal();
    },
    onError: (error: unknown) => {
      toast.error((error as { response?: { data?: { message?: string } } })?.response?.data?.message || "Failed to update branch");
    }
  });

  const { user: currentUser } = useAuthStore();
  const maxLocations = currentUser?.maxLocations || 1;
  const isLimitReached = (branches?.length || 0) >= maxLocations;

  const handleSubmit = (data: BranchRequest) => {
    if (selectedBranch) {
      updateMutation.mutate({ id: selectedBranch.id, request: data });
    } else {
      createMutation.mutate(data);
    }
  };

  const handleEdit = (branch: Branch) => {
    setSelectedBranch(branch);
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setSelectedBranch(null);
  };

  return (
    <div className="p-8 space-y-6">
      <div className="flex justify-between items-end">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <Store className="text-primary" size={24} />
            <h1 className="text-3xl font-bold tracking-tight">Branches</h1>
          </div>
          <p className="text-muted-foreground">Manage your physical store locations and warehouses.</p>
        </div>
        <div className="flex flex-col items-end gap-1">
          <Button 
            disabled={isLimitReached}
            onClick={() => setIsModalOpen(true)} 
            className="gap-2 bg-primary hover:bg-primary/90"
          >
            {isLimitReached ? <Shield size={18} className="text-warning" /> : <Plus size={18} />}
            Add Branch
          </Button>
          {isLimitReached && (
            <span className="text-[10px] text-warning font-bold bg-warning/10 px-2 py-0.5 rounded border border-warning/20">
              Plan Limit: {maxLocations} Location{maxLocations > 1 ? 's' : ''}
            </span>
          )}
        </div>
      </div>

      <div className={`rounded-xl p-4 flex gap-3 items-start transition-colors ${isLimitReached ? 'bg-warning/5 border border-warning/10' : 'bg-primary/5 border border-primary/10'}`}>
        <Info className={`${isLimitReached ? 'text-warning' : 'text-primary'} shrink-0 mt-0.5`} size={18} />
        <div className="space-y-1">
          <p className={`text-sm leading-relaxed ${isLimitReached ? 'text-amber-200/80' : 'text-indigo-300/80'}`}>
            Branches allow you to track inventory across different physical sites. Each transaction at the POS 
            terminal should be associated with a branch to ensure stock is deducted from the correct location.
          </p>
          {isLimitReached && (
            <p className="text-xs text-warning/60 font-medium">
              You have reached your current plan limit. Upgrade to <strong>Medium Business</strong> to manage up to 3 locations.
            </p>
          )}
        </div>
      </div>

      <BranchTable 
        data={branches || []} 
        isLoading={isLoading} 
        onEdit={handleEdit}
      />

      <BranchFormModal 
        isOpen={isModalOpen}
        onClose={closeModal}
        onSubmit={handleSubmit}
        initialData={selectedBranch}
        isLoading={createMutation.isPending || updateMutation.isPending}
      />
    </div>
  );
}
