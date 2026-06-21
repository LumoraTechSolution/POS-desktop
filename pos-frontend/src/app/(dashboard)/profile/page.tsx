"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { authService } from "@/services/authService";
import { useAuthStore } from "@/stores/authStore";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { toast } from "sonner";
import { format } from "date-fns";
import {
  Mail,
  Phone,
  Shield,
  Building2,
  Star,
  Clock,
  Lock,
  KeyRound,
  Loader2,
} from "lucide-react";

export default function ProfilePage() {
  const { data: profile, isLoading, refetch } = useQuery({
    queryKey: ["my-profile"],
    queryFn: authService.getMe,
  });

  return (
    <div className="p-8 space-y-6 animate-in fade-in duration-500 max-w-5xl">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">My Profile</h1>
        <p className="text-muted-foreground mt-1">Your account details and security settings.</p>
      </div>

      {isLoading || !profile ? (
        <div className="flex items-center gap-2 text-muted-foreground py-12">
          <Loader2 className="animate-spin" size={18} /> Loading profile…
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
          <DetailsCard profile={profile} onSaved={refetch} />
          <div className="space-y-6">
            <ChangePasswordCard />
            <ChangePinCard hasPin={profile.hasPin} onSaved={refetch} />
          </div>
        </div>
      )}
    </div>
  );
}

type Profile = Awaited<ReturnType<typeof authService.getMe>>;

function DetailsCard({ profile, onSaved }: { profile: Profile; onSaved: () => void }) {
  const [editing, setEditing] = useState(false);
  const [firstName, setFirstName] = useState(profile.firstName);
  const [lastName, setLastName] = useState(profile.lastName);
  const [phone, setPhone] = useState(profile.phone ?? "");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setFirstName(profile.firstName);
    setLastName(profile.lastName);
    setPhone(profile.phone ?? "");
  }, [profile]);

  const save = async () => {
    if (!firstName.trim() || !lastName.trim()) {
      toast.error("First and last name are required.");
      return;
    }
    setSaving(true);
    try {
      await authService.updateMyProfile({ firstName, lastName, phone: phone || undefined });
      toast.success("Profile updated.");
      setEditing(false);
      onSaved();
    } catch (e: unknown) {
      toast.error((e as { response?: { data?: { message?: string } } })?.response?.data?.message || "Failed to update profile.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <div className="flex items-start gap-4">
        <div className="w-14 h-14 rounded-full bg-primary flex items-center justify-center text-primary-foreground text-lg font-bold shrink-0">
          {profile.firstName?.[0]}
          {profile.lastName?.[0]}
        </div>
        <div className="flex-1 min-w-0">
          {editing ? (
            <div className="grid grid-cols-2 gap-3">
              <Input value={firstName} onChange={(e) => setFirstName(e.target.value)} placeholder="First name" />
              <Input value={lastName} onChange={(e) => setLastName(e.target.value)} placeholder="Last name" />
            </div>
          ) : (
            <h2 className="text-xl font-bold">{profile.firstName} {profile.lastName}</h2>
          )}
          <div className="flex flex-wrap gap-1.5 mt-2">
            {profile.roles.map((r) => (
              <span key={r} className="inline-flex items-center gap-1 text-[10px] font-semibold px-2 py-0.5 rounded-full border border-border bg-muted text-muted-foreground">
                <Shield size={10} /> {r}
              </span>
            ))}
          </div>
        </div>
        {!editing && (
          <Button variant="outline" size="sm" onClick={() => setEditing(true)}>Edit</Button>
        )}
      </div>

      <div className="mt-5 space-y-3 text-sm">
        <Row icon={<Mail size={15} />} label="Email" value={profile.email} />
        {editing ? (
          <div className="flex items-center gap-2">
            <Phone size={15} className="text-muted-foreground" />
            <Input value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="Phone" className="h-8" />
          </div>
        ) : (
          <Row icon={<Phone size={15} />} label="Phone" value={profile.phone || "—"} />
        )}
        {profile.primaryBranchName && (
          <Row icon={<Star size={15} />} label="Primary branch" value={profile.primaryBranchName} />
        )}
        {profile.branches.length > 0 && (
          <Row
            icon={<Building2 size={15} />}
            label="Branches"
            value={profile.branches.map((b) => b.name).join(", ")}
          />
        )}
        <Row
          icon={<Clock size={15} />}
          label="Last login"
          value={profile.lastLoginAt ? format(new Date(profile.lastLoginAt), "PPp") : "—"}
        />
      </div>

      {editing && (
        <div className="flex gap-3 mt-5">
          <Button variant="outline" className="flex-1" onClick={() => setEditing(false)} disabled={saving}>Cancel</Button>
          <Button className="flex-1" onClick={save} disabled={saving}>
            {saving && <Loader2 size={16} className="mr-2 animate-spin" />} Save
          </Button>
        </div>
      )}
    </Card>
  );
}

function ChangePasswordCard() {
  const router = useRouter();
  const logout = useAuthStore((s) => s.logout);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    if (newPassword.length < 8) {
      toast.error("New password must be at least 8 characters.");
      return;
    }
    if (newPassword !== confirm) {
      toast.error("New passwords do not match.");
      return;
    }
    setSaving(true);
    try {
      await authService.changePassword({ currentPassword, newPassword });
      toast.success("Password changed. Please sign in again.");
      logout();
      router.push("/login");
    } catch (e: unknown) {
      toast.error((e as { response?: { data?: { message?: string } } })?.response?.data?.message || "Failed to change password.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <CardTitle icon={<Lock size={18} />} title="Change password" subtitle="You'll be signed out after changing it." />
      <div className="space-y-3 mt-4">
        <Input type="password" placeholder="Current password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} />
        <Input type="password" placeholder="New password (min 8 chars)" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
        <Input type="password" placeholder="Confirm new password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
        <Button onClick={submit} disabled={saving || !currentPassword || !newPassword}>
          {saving && <Loader2 size={16} className="mr-2 animate-spin" />} Update password
        </Button>
      </div>
    </Card>
  );
}

function ChangePinCard({ hasPin, onSaved }: { hasPin: boolean; onSaved: () => void }) {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPin, setNewPin] = useState("");
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    if (newPin.length !== 4) {
      toast.error("PIN must be exactly 4 digits.");
      return;
    }
    setSaving(true);
    try {
      await authService.changePin({ currentPassword, newPin });
      toast.success("PIN updated.");
      setCurrentPassword("");
      setNewPin("");
      onSaved();
    } catch (e: unknown) {
      toast.error((e as { response?: { data?: { message?: string } } })?.response?.data?.message || "Failed to update PIN.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <CardTitle
        icon={<KeyRound size={18} />}
        title={hasPin ? "Change PIN" : "Set PIN"}
        subtitle="Your 4-digit PIN is used for quick terminal login and manager approvals. Confirm with your account password — not your PIN."
      />
      <div className="space-y-3 mt-4">
        <Input type="password" placeholder="Account password (not your PIN)" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} />
        <Input
          type="password"
          inputMode="numeric"
          maxLength={4}
          placeholder="New 4-digit PIN"
          value={newPin}
          onChange={(e) => setNewPin(e.target.value.replace(/\D/g, ""))}
        />
        <Button onClick={submit} disabled={saving || !currentPassword || newPin.length !== 4}>
          {saving && <Loader2 size={16} className="mr-2 animate-spin" />} {hasPin ? "Update PIN" : "Set PIN"}
        </Button>
      </div>
    </Card>
  );
}

function Card({ children }: { children: React.ReactNode }) {
  return <div className="bg-card border border-border rounded-2xl p-6">{children}</div>;
}

function CardTitle({ icon, title, subtitle }: { icon: React.ReactNode; title: string; subtitle?: string }) {
  return (
    <div className="flex items-start gap-3">
      <div className="p-2 bg-primary/10 rounded-lg border border-primary/20 text-primary">{icon}</div>
      <div>
        <h3 className="font-bold">{title}</h3>
        {subtitle && <p className="text-xs text-muted-foreground mt-0.5">{subtitle}</p>}
      </div>
    </div>
  );
}

function Row({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="flex items-center gap-3">
      <span className="text-muted-foreground">{icon}</span>
      <span className="text-muted-foreground w-32 shrink-0">{label}</span>
      <span className="font-medium text-foreground truncate">{value}</span>
    </div>
  );
}
