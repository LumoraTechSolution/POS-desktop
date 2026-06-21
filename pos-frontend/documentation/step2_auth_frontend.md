# Step 2: Authentication & Authorization (Frontend)

**Status**: ✅ Completed  
**Objective**: Build a multi-tenant, dual-mode authentication system supporting both Staff (Password) and Cashier (PIN) logins.

---

## Files Created/Modified

### Auth Stores & Services

| File             | Path            | Purpose                                                        |
| ---------------- | --------------- | -------------------------------------------------------------- |
| `authStore.ts`   | `src/stores/`   | Added persistence and JWT lifecycle management.                |
| `authService.ts` | `src/services/` | Centralized API calls for Login, PIN login, and Token Refresh. |

### UI Components (shadcn-like)

| File         | Path                 | Purpose                                                   |
| ------------ | -------------------- | --------------------------------------------------------- |
| `button.tsx` | `src/components/ui/` | Premium button with multiple variants and loading states. |
| `input.tsx`  | `src/components/ui/` | Accessible input fields with standard styles.             |
| `card.tsx`   | `src/components/ui/` | Container for login forms and dashboard modules.          |
| `tabs.tsx`   | `src/components/ui/` | For switching between Email/Password and PIN login modes. |

### Featured Components

| File            | Path                   | Purpose                                                 |
| --------------- | ---------------------- | ------------------------------------------------------- |
| `LoginForm.tsx` | `src/components/auth/` | Integrated with React Hook Form and Zod for validation. |
| `PinPad.tsx`    | `src/components/auth/` | Interactive numeric grid with reactive feedback.        |

---

## Key Features

1. **Dual Login System**:
   - **Staff Mode**: Standard email/password entry for administrators and managers.
   - **Cashier Mode**: Optimized 4-digit PIN entry for fast-paced retail environments.
2. **Token Logic**:
   - Automatic injection of `Authorization` headers.
   - Transparent refresh token handling via Axios interceptors.
3. **Tenant Integration**:
   - `X-Tenant-ID` header management to ensure data isolation.

---

## Performance & UX

- **Micro-animations**: Smooth transitions between login modes.
- **Glassmorphism**: Elegant dark-themed UI following international enterprise standards.
- **Error Handling**: Real-time validation feedback and toast notifications for failed attempts.
