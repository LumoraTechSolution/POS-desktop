import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

export interface SuperAdminUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  role: string;
  passwordChangeRequired?: boolean;
}

interface SuperAdminAuthState {
  user: SuperAdminUser | null;
  // Access token is intentionally memory-only — XSS hardening so a script
  // injection can't lift the JWT from storage. It rehydrates from the
  // refresh token on first protected call.
  token: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;

  setAuth: (user: SuperAdminUser, token: string, refreshToken: string | null) => void;
  setAccessToken: (token: string) => void;
  logout: () => void;
}

export const useSuperAdminStore = create<SuperAdminAuthState>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      refreshToken: null,
      isAuthenticated: false,

      setAuth: (user, token, refreshToken) => {
        set({ user, token, refreshToken, isAuthenticated: true });
      },

      setAccessToken: (token) => set({ token }),

      logout: () => {
        set({ user: null, token: null, refreshToken: null, isAuthenticated: false });
      },
    }),
    {
      name: 'lumora-super-admin-auth',
      // sessionStorage so the refresh token survives a page reload but
      // dies when the browser tab/window closes.
      storage: createJSONStorage(() =>
        typeof window !== 'undefined' ? sessionStorage : localStorage
      ),
      partialize: (state) => ({
        user: state.user,
        // token deliberately omitted — see comment above.
        refreshToken: state.refreshToken,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);
