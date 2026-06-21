"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { LoginForm } from "@/components/auth/LoginForm";
import { PinPad } from "@/components/auth/PinPad";
import { Logo } from "@/components/brand/Logo";

export default function LoginPage() {
  const router = useRouter();

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.ctrlKey && e.altKey && e.key === "F12") {
        e.preventDefault();
        router.push("/super-admin/login");
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [router]);

  return (
    <div className="min-h-screen w-full flex items-center justify-center bg-muted/40 p-4">
      <div className="w-full max-w-[450px] space-y-8">
        <div className="flex flex-col items-center text-center">
          {/* Mark + StoreX wordmark (no tagline). Vector, so it stays crisp and
              adapts to light/dark — "Store" follows the foreground color. */}
          <Logo variant="full" layout="stacked" size={60} />
        </div>

        <Card className="border-none shadow-xl">
          <CardHeader className="text-center pb-2">
            <CardTitle className="text-xl">Authentication</CardTitle>
            <CardDescription>
              Choose your login method below
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Tabs defaultValue="staff" className="w-full">
              <TabsList className="grid w-full grid-cols-2 mb-8">
                <TabsTrigger value="staff">Staff Login</TabsTrigger>
                <TabsTrigger value="pin">Quick PIN</TabsTrigger>
              </TabsList>
              
              <TabsContent value="staff" className="mt-0">
                <LoginForm />
              </TabsContent>
              
              <TabsContent value="pin" className="mt-0">
                <div className="pt-4">
                  <p className="text-sm text-muted-foreground text-center mb-6">
                    Enter your cashier PIN to quickly access the terminal
                  </p>
                  <PinPad />
                </div>
              </TabsContent>
            </Tabs>
          </CardContent>
        </Card>

        <p className="text-center text-xs text-muted-foreground">
          &copy; 2026 Lumora Technologies. All rights reserved.
        </p>
      </div>
    </div>
  );
}
