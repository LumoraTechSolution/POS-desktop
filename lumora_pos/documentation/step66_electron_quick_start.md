# Quick Start: Electron Desktop App for Lumora POS

## 🚀 5-Minute Setup

### Option 1: Windows
```bash
cd frontend
setup-electron.bat
```

### Option 2: macOS/Linux
```bash
cd frontend
bash setup-electron.sh
```

---

## 📋 Manual Setup (if scripts don't work)

### Step 1: Install Dependencies

```bash
cd frontend

# Install Electron core
npm install --save-dev electron electron-builder electron-is-dev

# Install utilities
npm install --save-dev concurrently wait-on

# Install hardware support
npm install serialport usb
```

### Step 2: Create Electron Directory

```bash
mkdir electron electron/hardware electron/utils
mkdir src/hooks
```

### Step 3: Create `electron/main.ts`

**File:** `frontend/electron/main.ts`

```typescript
import { app, BrowserWindow, ipcMain, Menu } from 'electron';
import path from 'path';
import isDev from 'electron-is-dev';
import { setupIPC } from './ipc';

let mainWindow: BrowserWindow | null = null;

const createWindow = () => {
  mainWindow = new BrowserWindow({
    width: 1920,
    height: 1080,
    minWidth: 1280,
    minHeight: 720,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
    },
  });

  const startUrl = isDev 
    ? 'http://localhost:3000' 
    : `file://${path.join(__dirname, '../out/index.html')}`;

  mainWindow.loadURL(startUrl);

  if (isDev) {
    mainWindow.webContents.openDevTools();
  }

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
};

app.on('ready', createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('activate', () => {
  if (mainWindow === null) {
    createWindow();
  }
});

setupIPC(mainWindow);
```

### Step 4: Create `electron/preload.ts`

**File:** `frontend/electron/preload.ts`

```typescript
import { contextBridge, ipcRenderer } from 'electron';

const electronAPI = {
  hardware: {
    getPrinters: () => ipcRenderer.invoke('hardware:get-printers'),
    printReceipt: (data: string) => ipcRenderer.invoke('hardware:print-receipt', data),
    openCashDrawer: () => ipcRenderer.invoke('hardware:open-drawer'),
  },
  system: {
    minimize: () => ipcRenderer.send('system:minimize'),
    maximize: () => ipcRenderer.send('system:maximize'),
    close: () => ipcRenderer.send('system:close'),
  },
};

contextBridge.exposeInMainWorld('electron', electronAPI);

declare global {
  interface Window {
    electron: typeof electronAPI;
  }
}
```

### Step 5: Create `electron/ipc.ts`

**File:** `frontend/electron/ipc.ts`

```typescript
import { ipcMain, BrowserWindow, dialog } from 'electron';
import fs from 'fs';

export const setupIPC = (mainWindow: BrowserWindow | null) => {
  ipcMain.handle('hardware:get-printers', async () => {
    // TODO: Implement printer detection
    return [];
  });

  ipcMain.handle('hardware:print-receipt', async (event, receiptData) => {
    try {
      console.log('Printing receipt:', receiptData);
      return true;
    } catch (error) {
      console.error('Print error:', error);
      return false;
    }
  });

  ipcMain.handle('hardware:open-drawer', async () => {
    try {
      console.log('Opening cash drawer');
      return true;
    } catch (error) {
      console.error('Drawer error:', error);
      return false;
    }
  });

  ipcMain.on('system:minimize', () => mainWindow?.minimize());
  ipcMain.on('system:maximize', () => {
    if (mainWindow?.isMaximized()) {
      mainWindow.restore();
    } else {
      mainWindow?.maximize();
    }
  });
  ipcMain.on('system:close', () => mainWindow?.close());
};
```

### Step 6: Create `src/hooks/useElectron.ts`

**File:** `frontend/src/hooks/useElectron.ts`

```typescript
import { useCallback } from 'react';

export const useElectron = () => {
  const isElectron = typeof window !== 'undefined' && (window as any).electron;

  return {
    isElectron,
    printReceipt: useCallback(
      async (html: string) => {
        if (!isElectron) return false;
        return (window as any).electron.hardware.printReceipt(html);
      },
      [isElectron]
    ),
    openCashDrawer: useCallback(
      async () => {
        if (!isElectron) return false;
        return (window as any).electron.hardware.openCashDrawer();
      },
      [isElectron]
    ),
    minimize: () => isElectron && (window as any).electron.system.minimize(),
    maximize: () => isElectron && (window as any).electron.system.maximize(),
    close: () => isElectron && (window as any).electron.system.close(),
  };
};
```

### Step 7: Update `package.json`

Add these scripts to `frontend/package.json`:

```json
{
  "main": "electron/main.js",
  "homepage": "./",
  "scripts": {
    "dev": "concurrently \"npm run next-dev\" \"npm run electron-dev\"",
    "next-dev": "next dev -p 3000",
    "electron-dev": "wait-on http://localhost:3000 && electron .",
    "build": "npm run next-build && npm run electron-build",
    "next-build": "next build && next export",
    "electron-build": "npm run next-build && electron-builder",
    "dist": "npm run build && electron-builder --publish=never",
    "typecheck": "tsc --noEmit"
  }
}
```

### Step 8: Update `next.config.mjs`

```javascript
/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  distDir: 'out',
  images: {
    unoptimized: true,
  },
};

export default nextConfig;
```

### Step 9: Create `electron-builder.config.js`

**File:** `frontend/electron-builder.config.js`

```javascript
module.exports = {
  appId: 'com.lumora.pos',
  productName: 'Lumora POS',
  directories: {
    buildResources: 'public',
    output: 'dist',
  },
  files: [
    'out/**/*',
    'electron/**/*',
    'node_modules/**/*',
    'package.json',
  ],
  win: {
    target: ['nsis', 'portable'],
  },
  nsis: {
    oneClick: false,
    allowToChangeInstallationDirectory: true,
    createDesktopShortcut: true,
  },
  mac: {
    target: ['dmg', 'zip'],
  },
  linux: {
    target: ['AppImage', 'deb'],
  },
};
```

---

## 🧪 Test Your Setup

### Start Development Build

```bash
cd frontend
npm run dev
```

You should see:
1. ✅ Next.js dev server starts on http://localhost:3000
2. ✅ Electron waits for the server
3. ✅ Electron window opens with the POS app
4. ✅ Dev tools open automatically

### Test Hardware IPC

Open DevTools console and run:

```javascript
// Test printer detection
window.electron.hardware.getPrinters().then(printers => console.log('Printers:', printers));

// Test cash drawer
window.electron.hardware.openCashDrawer().then(result => console.log('Drawer:', result));

// Test window controls
window.electron.system.minimize();
```

---

## 🏗️ Build for Distribution

### Windows

```bash
npm run dist
```

Creates: `dist/Lumora POS 1.0.0.exe` + portable version

### macOS

```bash
npm run dist
```

Creates: `dist/Lumora POS-1.0.0.dmg` + ZIP

### Linux

```bash
npm run dist
```

Creates: `dist/Lumora POS-1.0.0.AppImage` + `.deb`

---

## 📦 Integration Points

### Using Hardware in Components

```typescript
import { useElectron } from '@/hooks/useElectron';

export function ReceiptButton() {
  const { printReceipt, isElectron } = useElectron();

  const handlePrint = async () => {
    if (isElectron) {
      const success = await printReceipt('<html>Receipt...</html>');
      console.log('Printed:', success);
    } else {
      window.print(); // Fallback to browser print
    }
  };

  return (
    <button onClick={handlePrint}>
      {isElectron ? '🖨️ Print' : '📄 Print (Browser)'}
    </button>
  );
}
```

### Cash Drawer on Checkout

```typescript
export function CheckoutPanel() {
  const { openCashDrawer, isElectron } = useElectron();

  const handleCheckout = async (sale: Sale) => {
    // Process sale...
    
    if (isElectron && sale.paymentMethod === 'CASH') {
      await openCashDrawer();
    }
  };

  return (
    // UI...
  );
}
```

---

## 🔧 Hardware Integration (Advanced)

### Printer Service

**File:** `frontend/electron/hardware/printer.ts`

```typescript
import SerialPort from 'serialport';

export class PrinterService {
  private port: SerialPort.SerialPort | null = null;

  async connect(portPath: string) {
    this.port = new SerialPort.SerialPort({ path: portPath, baudRate: 9600 });
  }

  async print(text: string) {
    if (!this.port) throw new Error('Printer not connected');
    
    const buffer = Buffer.from([
      0x1b, 0x40,  // Initialize
      0x1b, 0x61, 0x01,  // Center align
      ...text.split('').map(c => c.charCodeAt(0)),
      0x1d, 0x56, 0x41, 0x03,  // Cut paper
    ]);
    
    return new Promise((resolve, reject) => {
      this.port!.write(buffer, (err) => {
        if (err) reject(err);
        else resolve(true);
      });
    });
  }
}
```

Update `electron/ipc.ts`:

```typescript
import { PrinterService } from './hardware/printer';

const printerService = new PrinterService();

export const setupIPC = (mainWindow: BrowserWindow | null) => {
  ipcMain.handle('hardware:print-receipt', async (event, receiptHTML) => {
    try {
      await printerService.connect('COM3'); // Windows
      await printerService.print(receiptHTML);
      return true;
    } catch (error) {
      console.error(error);
      return false;
    }
  });
};
```

---

## 📝 Troubleshooting

| Problem | Solution |
|---------|----------|
| **White screen** | Check Next.js is running on port 3000; check `startUrl` |
| **IPC not working** | Verify `preload.js` path; check context isolation is enabled |
| **Build fails** | Run `npm run next-build` first; check `out/` directory exists |
| **Port 3000 in use** | Change in `next-dev` script: `next dev -p 3001` |
| **Printer not found** | Connect printer; run `SerialPort.list()` in console |
| **App won't start** | Delete `node_modules`; run `npm install` again |

---

## ✅ Checklist

- [ ] Dependencies installed
- [ ] `electron/main.ts` created
- [ ] `electron/preload.ts` created
- [ ] `electron/ipc.ts` created
- [ ] `useElectron` hook created
- [ ] `package.json` scripts updated
- [ ] `next.config.mjs` exports enabled
- [ ] `electron-builder.config.js` created
- [ ] `npm run dev` works
- [ ] Hardware IPC tests pass
- [ ] Distribution build succeeds

---

## 🎯 Next Steps

1. **Test dev mode**: `npm run dev`
2. **Integrate printer**: Follow hardware integration section
3. **Build installer**: `npm run dist`
4. **Distribute**: Share `.exe` / `.dmg` / `.AppImage`
5. **Auto-update**: Implement electron-updater (optional)

---

## 📚 References

- [Full Implementation Guide](step66_electron_desktop_app.md)
- [Electron Docs](https://www.electronjs.org/docs)
- [electron-builder](https://www.electron.build/)
- [serialport](https://serialport.io/)
