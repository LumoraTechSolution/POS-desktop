'use client';

import { useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { AlertCircle, RotateCcw, Home } from 'lucide-react';
import Link from 'next/link';

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // Log the error to an error reporting service
    console.error('Dashboard Error:', error);
  }, [error]);

  return (
    <div className="flex flex-col items-center justify-center min-h-[70vh] px-4 text-center">
      <div className="flex items-center justify-center w-20 h-20 mb-6 rounded-full bg-destructive/10">
        <AlertCircle className="w-10 h-10 text-destructive" />
      </div>
      
      <h1 className="mb-2 text-3xl font-bold tracking-tight text-foreground">
        Something went wrong!
      </h1>
      
      <p className="max-w-md mb-8 text-muted-foreground">
        We encountered an unexpected error while loading this section. 
        Your data has not been lost, but you may need to try again or refresh your session.
      </p>

      <div className="flex flex-col gap-3 sm:flex-row">
        <Button 
          variant="default" 
          onClick={() => reset()}
          className="flex items-center gap-2"
        >
          <RotateCcw className="w-4 h-4" />
          Try again
        </Button>
        
        <Link href="/dashboard/overview">
          <Button variant="outline" className="flex items-center gap-2 w-full sm:w-auto">
            <Home className="w-4 h-4" />
            Return to Overview
          </Button>
        </Link>
      </div>

      {error.digest && (
        <p className="mt-8 text-xs font-mono text-muted-foreground/50 uppercase tracking-widest">
          Digest: {error.digest}
        </p>
      )}
    </div>
  );
}
