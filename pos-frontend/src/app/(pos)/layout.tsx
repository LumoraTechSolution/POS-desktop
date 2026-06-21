import AuthGuard from '@/components/providers/AuthGuard';

export default function PosLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <AuthGuard>
      <div className="h-screen w-full bg-black text-white selection:bg-primary/30">
        {children}
      </div>
    </AuthGuard>
  );
}
