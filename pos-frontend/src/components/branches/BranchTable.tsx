"use client";

import { Branch } from "@/services/branchService";
import { 
  Table, 
  TableBody, 
  TableCell, 
  TableHead, 
  TableHeader, 
  TableRow 
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Edit2, MapPin, Phone } from "lucide-react";
import { Badge } from "@/components/ui/badge";

interface BranchTableProps {
  data: Branch[];
  isLoading: boolean;
  onEdit: (branch: Branch) => void;
}

export default function BranchTable({ data, isLoading, onEdit }: BranchTableProps) {
  if (isLoading) {
    return (
      <div className="w-full h-64 bg-card/20 rounded-xl border border-border animate-pulse flex items-center justify-center">
        <span className="text-muted-foreground">Loading branches...</span>
      </div>
    );
  }

  return (
    <div className="bg-background border border-border rounded-xl overflow-hidden">
      <Table>
        <TableHeader className="bg-card/50">
          <TableRow className="border-border">
            <TableHead className="text-muted-foreground">Branch Name</TableHead>
            <TableHead className="text-muted-foreground">Address</TableHead>
            <TableHead className="text-muted-foreground">Contact</TableHead>
            <TableHead className="text-muted-foreground">Status</TableHead>
            <TableHead className="text-muted-foreground">Type</TableHead>
            <TableHead className="text-right text-muted-foreground">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.length === 0 ? (
            <TableRow>
              <TableCell colSpan={6} className="h-32 text-center text-muted-foreground">
                No branches found.
              </TableCell>
            </TableRow>
          ) : (
            data.map((branch) => (
              <TableRow key={branch.id} className="border-border hover:bg-card/30 transition-colors">
                <TableCell className="font-medium">
                  <div className="flex flex-col">
                    <span className="text-foreground">{branch.name}</span>
                    <span className="text-xs text-muted-foreground font-mono">{branch.id.substring(0, 8)}...</span>
                  </div>
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-2 text-muted-foreground">
                    <MapPin size={14} className="shrink-0" />
                    <span className="text-sm truncate max-w-[200px]">{branch.address || 'No address provided'}</span>
                  </div>
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-2 text-muted-foreground">
                    <Phone size={14} className="shrink-0" />
                    <span className="text-sm">{branch.phoneNumber || 'N/A'}</span>
                  </div>
                </TableCell>
                <TableCell>
                  <Badge 
                    variant={branch.isActive ? "default" : "secondary"}
                    className={branch.isActive ? "bg-success/10 text-success border-success/20" : "bg-muted text-muted-foreground"}
                  >
                    {branch.isActive ? "Active" : "Inactive"}
                  </Badge>
                </TableCell>
                <TableCell>
                  {branch.isDefault ? (
                    <Badge className="bg-primary/10 text-primary border-primary/20">
                      Primary
                    </Badge>
                  ) : (
                    <span className="text-muted-foreground text-xs text-center block">Standard</span>
                  )}
                </TableCell>
                <TableCell className="text-right">
                  <Button 
                    variant="ghost" 
                    size="sm" 
                    className="h-8 w-8 p-0 text-muted-foreground hover:text-foreground"
                    onClick={() => onEdit(branch)}
                  >
                    <Edit2 size={16} />
                  </Button>
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </div>
  );
}
