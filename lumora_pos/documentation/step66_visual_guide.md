## Visual Implementation Guide: Electron Desktop App

### Directory Structure After Setup

```
frontend/
├── electron/                          # ← NEW: Electron main process
│   ├── main.ts                       # Entry point
│   ├── preload.ts                    # IPC bridge
│   ├── ipc.ts                        # IPC handlers
│   └── hardware/                     # ← NEW: Hardware modules
│       ├── printer.ts
│       ├── scanner.ts
│       └── drawer.ts
│
├── src/
│   ├── hooks/
│   │   └── useElectron.ts            # ← NEW: React hook
│   ├── components/
│   │   ├── Receipt.tsx               # ← UPDATED: Use printer
│   │   └── ReceiptButton.tsx         # ← NEW: Print button
│   └── app/
│       ├── terminal/
│       │   └── page.tsx              # ← UPDATED: Use drawer
│       └── settings/
│           └── page.tsx              # ← NEW: Hardware settings
│
├── public/
│   └── icon.png                      # ← NEW: App icon
│
├── package.json                      # ← UPDATED: New scripts
├── next.config.mjs                   # ← UPDATED: Export config
├── electron-builder.config.js        # ← NEW: Build config
├── setup-electron.bat                # ← NEW: Windows setup
└── setup-electron.sh                 # ← NEW: Linux/macOS setup
```

---

## Component Data Flow

### Web App (Before)
```
┌─────────────────────────┐
│   Browser              │
│ ┌─────────────────────┐ │
│ │ Next.js App         │ │
│ │ - No hardware       │ │
│ │ - Browser print     │ │
│ │ - Online only       │ │
│ └─────────────────────┘ │
│        ↓ HTTP          │
│ ┌─────────────────────┐ │
│ │ Spring Boot API     │ │
│ └─────────────────────┘ │
└─────────────────────────┘
```

### Desktop App (After)
```
┌──────────────────────────────┐
│   Electron Desktop App       │
│ ┌──────────────────────────┐ │
│ │ Main Process             │ │
│ │ - Manages windows        │ │  
│ │ - IPC handlers           │ │
│ │ - Hardware control       │ │
│ │ - Auto-updater           │ │
│ └────────────────┬─────────┘ │
│                  │ IPC        │
│ ┌────────────────▼─────────┐ │
│ │ Renderer (Next.js)       │ │
│ │ - Web UI (unchanged)     │ │
│ │ - useElectron hook       │ │
│ │ - Offline cache          │ │
│ └──────────────┬────────────┘ │
│                │ HTTP         │
│ ┌──────────────▼────────────┐ │
│ │ Spring Boot API           │ │
│ └───────────────────────────┘ │
│                               │
│ Hardware (USB/Serial)         │
│ ├─ 🖨️  Printer               │
│ ├─ 📱 Barcode Scanner         │
│ └─ 🏪 Cash Drawer             │
└──────────────────────────────┘
```

---

## File Creation Checklist with Examples

### 1️⃣ Create electron/main.ts
```
✓ Window configuration
✓ DevTools setup
✓ IPC setup
✓ Menu creation
```

**Key lines:**
- Line 15: BrowserWindow creation
- Line 25: startUrl for dev/prod
- Line 35: IPC setup call
- Line 40: Menu creation

### 2️⃣ Create electron/preload.ts
```
✓ contextBridge setup
✓ Hardware API definitions
✓ Type definitions
```

**Key lines:**
- Line 5: Import contextBridge
- Line 15: Define electronAPI
- Line 30: contextBridge.exposeInMainWorld()
- Line 35: Global type declaration

### 3️⃣ Create electron/ipc.ts
```
✓ Handler registration
✓ Hardware invocation
✓ File operations
✓ System controls
```

**Key lines:**
- Line 5: Handle printer events
- Line 12: Handle drawer events
- Line 18: File dialog operations
- Line 25: Window controls

### 4️⃣ Create src/hooks/useElectron.ts
```
✓ Electron detection
✓ Hardware methods
✓ System controls
```

**Key lines:**
- Line 5: Check for window.electron
- Line 8-15: Printer callbacks
- Line 16-20: Drawer callbacks
- Line 35: Return all methods

### 5️⃣ Update package.json
```json
{
  "main": "electron/main.js",         // ← ADD: Main entry
  "homepage": "./",                   // ← ADD: Relative paths
  "scripts": {
    "dev": "concurrently \"npm run next-dev\" \"npm run electron-dev\"",
    "next-dev": "next dev -p 3000",
    "electron-dev": "wait-on http://localhost:3000 && electron .",
    // ... others
  }
}
```

### 6️⃣ Update next.config.mjs
```javascript
const nextConfig = {
  output: 'export',          // ← CHANGE: Export mode
  distDir: 'out',            // ← ADD: Output directory
  images: {
    unoptimized: true,       // ← ADD: No optimization
  },
};
```

### 7️⃣ Create electron-builder.config.js
```javascript
{
  appId: 'com.lumora.pos',
  productName: 'Lumora POS',
  directories: {
    buildResources: 'public',
    output: 'dist',
  },
  // Platform configs...
}
```

---

## Data Flow Examples

### Example 1: Print Receipt

```
User clicks "Print" button
    ↓
Receipt.tsx calls useElectron()
    ↓
printReceipt(html) → ipcRenderer.invoke('hardware:print-receipt', html)
    ↓
IPC bridge → main process
    ↓
ipcMain handler receives 'hardware:print-receipt'
    ↓
PrinterService.print(html)
    ↓
Convert HTML to ESC/POS commands
    ↓
Send via SerialPort to printer
    ↓
Return success/failure
    ↓
ipcRenderer receives response
    ↓
Toast notification to user
```

### Example 2: Barcode Scan

```
Real-world: User scans barcode with scanner
    ↓
SerialPort data event in ScannerService
    ↓
Emit 'barcode' event with code
    ↓
IPC handler receives event
    ↓
mainWindow.webContents.send('barcode:scanned', code)
    ↓
Frontend listens with useEffect
    ↓
ipcRenderer.on('barcode:scanned', (code) => ...)
    ↓
Look up product in API
    ↓
Add to cart
    ↓
Cart updates UI automatically
```

### Example 3: Offline Transaction

```
Sale attempted (no internet)
    ↓
useOfflineQueue.addSale(sale)
    ↓
Sale stored in localStorage
    ↓
User sees "Saved offline" toast
    ↓
[Later] Internet reconnects
    ↓
Zustand persistence detects online
    ↓
For each queued sale:
  - POST to /api/v1/sales
  - Mark as synced
    ↓
Queue clears
    ↓
All sales successfully synced
```

---

## Command Reference

### Setup
```bash
# Windows
cd frontend
setup-electron.bat

# macOS/Linux
cd frontend
bash setup-electron.sh
```

### Development
```bash
# Start dev mode (Next.js + Electron)
npm run dev

# Check for TypeScript errors
npm run typecheck

# Test Next.js build
npm run next-build

# Test Electron build
npm run electron-dev-build
```

### Production
```bash
# Build for all platforms
npm run dist

# Build for specific platform
npm run build -- --win     # Windows
npm run build -- --mac     # macOS
npm run build -- --linux   # Linux
```

### Debugging
```bash
# Open DevTools in app
F12 or Ctrl+Shift+I

# Check Electron main process logs
tail -f ~/.config/Lumora\ POS/logs/main.log

# List available serial ports
node -e "require('serialport').SerialPort.list().then(p => console.log(p))"
```

---

## Integration Points with Existing Code

### Terminal Page Changes
**File:** `frontend/src/app/terminal/page.tsx`

```typescript
// Add at top
import { useElectron } from '@/hooks/useElectron';

export default function TerminalPage() {
  const { openCashDrawer, isElectron } = useElectron();
  
  // Add to checkout handler
  const handleCheckout = async (sale: Sale) => {
    // ... existing code ...
    
    // NEW: Open drawer for cash payments
    if (isElectron && sale.paymentMethod === 'CASH') {
      await openCashDrawer();
    }
  };
}
```

### Receipt Component Changes
**File:** `frontend/src/components/Receipt.tsx`

```typescript
// Add at top
import { useElectron } from '@/hooks/useElectron';

export function Receipt({ sale }: { sale: Sale }) {
  const { printReceipt, isElectron } = useElectron();
  
  const handlePrint = async () => {
    if (isElectron) {
      // NEW: Native printing
      const success = await printReceipt(generateReceiptHTML(sale));
      if (success) toast.success('Receipt printed');
    } else {
      // Fallback: Browser print
      window.print();
    }
  };
}
```

---

## Testing Checklist

### Phase 1: Setup ✅
```
□ npm run dev starts without errors
□ Electron window opens
□ DevTools opens automatically
□ Console shows no errors
```

### Phase 2: IPC ✅
```
□ window.electron exists in DevTools console
□ window.electron.hardware exists
□ window.electron.system exists
□ No errors in IPC calls
```

### Phase 3: Hardware ✅
```
□ Printer detected
□ Scanner connected
□ Drawer responds to control
□ No serial port errors
```

### Phase 4: Components ✅
```
□ Receipt prints to hardware printer
□ Cash drawer opens on checkout
□ Barcode scan adds items
□ No IPC errors in console
```

### Phase 5: Build ✅
```
□ npm run dist succeeds
□ Installer creates
□ App starts from installer
□ All features work in built app
```

---

## Quick Troubleshooting Map

```
Problem: White screen
└─ Check: Next.js running? → Port correct? → Preload path?

Problem: window.electron undefined
└─ Check: Preload exists? → contextIsolation enabled? → Bridge exported?

Problem: Serial port not found
└─ Check: Device connected? → Drivers installed? → Port permissions?

Problem: Build fails
└─ Check: Out directory exists? → Files array correct? → Dependencies built?

Problem: Print fails
└─ Check: Printer connected? → Port configured? → ESC/POS compatible?
```

---

## File Size Reference

Typical app sizes after build:

```
Windows .exe installer:  ~150-200 MB
Windows portable .exe:   ~120-150 MB
macOS .dmg:              ~130-160 MB
Linux .AppImage:         ~140-180 MB
```

(Varies based on dependencies and Node modules included)

---

## Performance Targets

After optimization:

```
App startup time:        < 3 seconds
Memory usage:            150-300 MB
CPU idle:                < 1%
Receipt print time:      1-2 seconds
Barcode scan latency:    < 100ms
```

---

## Success Indicators

You know it's working when:

✅ Opening the app shows your POS interface  
✅ DevTools console shows no errors  
✅ Hardware devices are detected  
✅ Receipts print correctly  
✅ App works offline  
✅ npm run dist creates installers  

---

## Next Action

1. **Run setup script** (choose one):
   - Windows: `setup-electron.bat`
   - macOS/Linux: `bash setup-electron.sh`

2. **Start development**:
   ```bash
   npm run dev
   ```

3. **Verify setup** in DevTools:
   ```javascript
   window.electron  // Should be defined
   ```

4. **Proceed to next phase** in documentation

---

**Ready to build? Let's go! 🚀**
