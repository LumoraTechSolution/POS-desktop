"use client";

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { authService } from '@/services/authService';
import { useAuthStore } from '@/stores/authStore';
import { Button } from "@/components/ui/button";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";

export function PinPad() {
  const router = useRouter();
  const setAuth = useAuthStore((state) => state.setAuth);
  const setPendingPasswordChange = useAuthStore((state) => state.setPendingPasswordChange);
  const [pin, setPin] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  const handleNumberClick = (num: number) => {
    if (pin.length < 4) {
      setPin(prev => prev + num);
    }
  };

  const handleDelete = () => {
    setPin(prev => prev.slice(0, -1));
  };

  const handleSubmit = async () => {
    if (pin.length !== 4) {
      toast.error("Please enter a 4-digit PIN");
      return;
    }

    setIsLoading(true);
    try {
      const response = await authService.pinLogin({ pin });

      if (response.passwordChangeRequired) {
        setPendingPasswordChange(response.user, response.accessToken);
        toast.info("Please set a new password to continue.");
        router.push('/change-password');
        return;
      }

      setAuth(response.user, response.accessToken, response.refreshToken, 'PIN');
      toast.success(`Welcome ${response.user.firstName}!`);
      // PIN login is the at-the-register login: go straight to the POS terminal.
      router.push('/terminal');
    } catch (error: unknown) {
      toast.error((error as { response?: { data?: { message?: string } } })?.response?.data?.message || "Invalid PIN");
      setPin(""); 
    } finally {
      setIsLoading(false);
    }
  };

  // Auto-submit when 4 digits are entered? 
  // Let's keep it manual for now to avoid accidental logins but make it easy to hit Enter.

  return (
    <div className="flex flex-col items-center space-y-6 max-w-[280px] mx-auto">
      <div className="flex gap-4 mb-4">
        {[1, 2, 3, 4].map((i) => (
          <div 
            key={i}
            className={cn(
              "w-4 h-4 rounded-full border-2 border-primary transition-all duration-200",
              pin.length >= i ? "bg-primary scale-110" : "bg-transparent"
            )}
          />
        ))}
      </div>

      <div className="grid grid-cols-3 gap-3 w-full">
        {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((num) => (
          <Button
            key={num}
            variant="outline"
            className="h-16 text-xl font-semibold hover:bg-primary hover:text-primary-foreground transform active:scale-95"
            onClick={() => handleNumberClick(num)}
            disabled={isLoading}
          >
            {num}
          </Button>
        ))}
        <Button
          variant="ghost"
          className="h-16"
          onClick={handleDelete}
          disabled={isLoading || pin.length === 0}
        >
          <BackspaceIconProxy />
        </Button>
        <Button
          variant="outline"
          className="h-16 text-xl font-semibold hover:bg-primary hover:text-primary-foreground transform active:scale-95"
          onClick={() => handleNumberClick(0)}
          disabled={isLoading}
        >
          0
        </Button>
        <Button
          variant="default"
          className="h-16 font-bold"
          onClick={handleSubmit}
          disabled={isLoading || pin.length !== 4}
        >
          {isLoading ? <Loader2 className="animate-spin" /> : "ENTER"}
        </Button>
      </div>
    </div>
  );
}

// Fixed BackspaceIcon mapping since lucide name is just Backspace or Delete
function BackspaceIconProxy() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 4H8l-7 8 7 8h13a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z"/><line x1="18" y1="9" x2="12" y2="15"/><line x1="12" y1="9" x2="18" y2="15"/></svg>
  );
}
