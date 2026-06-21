import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { User } from '@/types/auth';
import { bindSentryUser, clearSentryUser } from '@/lib/sentry';

export type { User };

/** How the current session was authenticated. PIN is the at-the-register quick
 *  login (lands straight on the POS terminal); PASSWORD is the full management
 *  login (lands on the dashboard). Drives where the terminal sends a user who
 *  cancels the Start-Shift prompt. */
export type LoginMethod = 'PIN' | 'PASSWORD';

interface AuthState {
  user: User | null;
  token: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  /** True when the user logged in but must rotate an admin-set password before
   *  entering the app. While true, `token` is a change-password-scoped token and
   *  `isAuthenticated` is false. */
  passwordChangeRequired: boolean;
  /** How this session was authenticated (PIN vs email/password). Null when signed out. */
  loginMethod: LoginMethod | null;

  // Actions
  setAuth: (user: User, token: string, refreshToken: string, loginMethod?: LoginMethod) => void;
  /** Stash the change-password-scoped token + identity for the forced-change flow. */
  setPendingPasswordChange: (user: User, token: string) => void;
  logout: () => void;
  hasPermission: (permission: string) => boolean;
  hasRole: (role: string) => boolean;
  hasFeature: (feature: string) => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      token: null,
      refreshToken: null,
      isAuthenticated: false,
      passwordChangeRequired: false,
      loginMethod: null,

      setAuth: (user: User, token: string, refreshToken: string, loginMethod: LoginMethod = 'PASSWORD') => {
        set({ user, token, refreshToken, isAuthenticated: true, passwordChangeRequired: false, loginMethod });
        bindSentryUser(user);
      },

      setPendingPasswordChange: (user: User, token: string) => {
        set({ user, token, refreshToken: null, isAuthenticated: false, passwordChangeRequired: true });
      },

      logout: () => {
        set({ user: null, token: null, refreshToken: null, isAuthenticated: false, passwordChangeRequired: false, loginMethod: null });
        clearSentryUser();
      },

      hasPermission: (permission: string) => {
        const state = get();
        return state.user?.permissions?.includes(permission) ?? false;
      },

      hasRole: (role: string) => {
        const state = get();
        return state.user?.roles?.includes(role) ?? false;
      },

      hasFeature: (feature: string) => {
        const state = get();
        return state.user?.featuresEnabled?.includes(feature) ?? false;
      },
    }),
    {
      name: 'lumora-pos-auth',
      // sessionStorage: survives page reload within the same tab,
      // but clears automatically when the browser session ends.
      storage: createJSONStorage(() =>
        typeof window !== 'undefined' ? sessionStorage : localStorage
      ),
      partialize: (state) => ({
        user: state.user,
        // token is intentionally omitted — access tokens are memory-only to reduce XSS exposure.
        // refreshToken is kept so the silent-refresh flow works across page reloads within the
        // same browser session (sessionStorage is cleared when the tab/window is closed).
        refreshToken: state.refreshToken,
        isAuthenticated: state.isAuthenticated,
        passwordChangeRequired: state.passwordChangeRequired,
        loginMethod: state.loginMethod,
      }),
    }
  )
);
