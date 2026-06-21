# Step 66: Electron Desktop Application for Lumora POS

## Overview

This guide covers creating a cross-platform desktop application for the Lumora POS system using Electron. The desktop app will wrap your existing Next.js frontend and communicate with the Spring Boot backend, providing a native experience for Windows, macOS, and Linux.

---

## Architecture Overview

```
┌─────────────────────────────────────────┐
│   Electron Main Process                 │
│   • Window management                   │
│   • Hardware integration (printer, USB) │
│   • Native OS APIs                      │
└──────────────┬──────────────────────────┘
               │
       ┌───────┴────────┐
       │                │
┌──────▼──────┐  ┌──────▼──────────┐
│ Next.js     │  │ IPC Bridge      │
│ Frontend    │  │ • Serial/USB    │
│ (Renderer)  │  │ • File system   │
│             │  │ • Hardware      │
└──────┬──────┘  └─────────────────┘
       │
       └──────────────────────────┐
                                  │
                        ┌─────────▼────────┐
                        │ Spring Boot API  │
                        │ (localhost:8081) │
                        └──────────────────┘
```

---

## Phase 1: Project Setup

### Step 1.1: Create Electron Configuration

Create a new directory structure for Electron:

```
frontend/
├── public/
├── src/
├── electron/                    # NEW
│   ├── main.ts                 # Main process entry
│   ├── preload.ts              # Preload script (IPC bridge)
│   ├── hardware/               # Hardware integration
│   │   ├── printer.ts
│   │   ├── scanner.ts
│   │   └── drawer.ts
│   └── utils/
│       └── autoUpdater.ts
├── electron-builder.config.js  # Build config
├── next.config.mjs
├── package.json
└── tsconfig.json
```

### Step 1.2: Update package.json

Add Electron dependencies and scripts:

```json
{
  "name": "lumora-pos-desktop",
  "version": "1.0.0",
  "main": "electron/main.js",
  "homepage": "./",
  "scripts": {
    "dev": "concurrently \"npm run next-dev\" \"npm run electron-dev\"",
    "next-dev": "next dev -p 3000",
    "electron-dev": "wait-on http://localhost:3000 && electron .",
    "build": "npm run next-build && npm run electron-build",
    "next-build": "next build && next export",
    "electron-build": "npm run next-build && electron-builder",
    "electron-dev-build": "npm run next-build && electron-builder --dir",
    "dist": "npm run build && electron-builder --publish=never",
    "typecheck": "tsc --noEmit"
  },
  "dependencies": {
    "axios": "^1.13.5",
    "date-fns": "^4.1.0",
    "electron-is-dev": "^2.0.0",
    "electron-squirrel-startup": "^1.1.0",
    "react": "^18",
    "react-dom": "^18",
    "serialport": "^9.2.8",
    "usb": "^1.9.2",
    "zustand": "^5.0.11"
  },
  "devDependencies": {
    "@types/node": "^20",
    "@types/react": "^18",
    "@types/react-dom": "^18",
    "concurrently": "^8.2.0",
    "electron": "^latest",
    "electron-builder": "^latest",
    "electron-dev-tools-installer": "^latest",
    "typescript": "^5",
    "wait-on": "^7.0.1"
  }
}
```

### Step 1.3: Install Dependencies

```bash
cd frontend
npm install electron electron-builder electron-is-dev serialport usb concurrently wait-on --save-dev
```

---

## Phase 2: Electron Main Process

### Step 2.1: Create Main Entry Point (`electron/main.ts`)

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
      enableRemoteModule: false,
      sandbox: true,
    },
    icon: path.join(__dirname, '../public/icon.png'),
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

// Setup IPC handlers
setupIPC(mainWindow);

// Create application menu
const createMenu = () => {
  const template = [
    {
      label: 'File',
      submenu: [
        { label: 'Exit', click: () => app.quit() },
      ],
    },
    {
      label: 'Edit',
      submenu: [
        { role: 'undo' },
        { role: 'redo' },
        { type: 'separator' },
        { role: 'cut' },
        { role: 'copy' },
        { role: 'paste' },
      ],
    },
    {
      label: 'View',
      submenu: [
        { role: 'reload' },
        { role: 'forceReload' },
        { role: 'toggleDevTools' },
      ],
    },
  ];

  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
};

app.on('ready', createMenu);
```

### Step 2.2: Create Preload Script (`electron/preload.ts`)

```typescript
import { contextBridge, ipcRenderer } from 'electron';

export interface ElectronAPI {
  hardware: {
    getPrinters: () => Promise<string[]>;
    printReceipt: (data: string) => Promise<boolean>;
    openCashDrawer: () => Promise<boolean>;
    scanBarcode: () => Promise<string>;
  };
  file: {
    selectFile: (filters: any[]) => Promise<string>;
    exportData: (path: string, data: string) => Promise<boolean>;
  };
  system: {
    minimize: () => void;
    maximize: () => void;
    close: () => void;
    isMaximized: () => Promise<boolean>;
  };
}

const electronAPI: ElectronAPI = {
  hardware: {
    getPrinters: () => ipcRenderer.invoke('hardware:get-printers'),
    printReceipt: (data) => ipcRenderer.invoke('hardware:print-receipt', data),
    openCashDrawer: () => ipcRenderer.invoke('hardware:open-drawer'),
    scanBarcode: () => ipcRenderer.invoke('hardware:scan-barcode'),
  },
  file: {
    selectFile: (filters) => ipcRenderer.invoke('file:select-file', filters),
    exportData: (path, data) => ipcRenderer.invoke('file:export-data', { path, data }),
  },
  system: {
    minimize: () => ipcRenderer.send('system:minimize'),
    maximize: () => ipcRenderer.send('system:maximize'),
    close: () => ipcRenderer.send('system:close'),
    isMaximized: () => ipcRenderer.invoke('system:is-maximized'),
  },
};

contextBridge.exposeInMainWorld('electron', electronAPI);

declare global {
  interface Window {
    electron: ElectronAPI;
  }
}
```

### Step 2.3: IPC Setup (`electron/ipc.ts`)

```typescript
import { ipcMain, BrowserWindow, dialog } from 'electron';
import fs from 'fs';
import path from 'path';
import { PrinterService } from './hardware/printer';
import { ScannerService } from './hardware/scanner';
import { DrawerService } from './hardware/drawer';

const printerService = new PrinterService();
const scannerService = new ScannerService();
const drawerService = new DrawerService();

export const setupIPC = (mainWindow: BrowserWindow | null) => {
  // Hardware: Printer
  ipcMain.handle('hardware:get-printers', async () => {
    return printerService.listPrinters();
  });

  ipcMain.handle('hardware:print-receipt', async (event, receiptData) => {
    try {
      await printerService.printReceipt(receiptData);
      return true;
    } catch (error) {
      console.error('Print error:', error);
      return false;
    }
  });

  // Hardware: Cash Drawer
  ipcMain.handle('hardware:open-drawer', async () => {
    try {
      await drawerService.openDrawer();
      return true;
    } catch (error) {
      console.error('Drawer error:', error);
      return false;
    }
  });

  // Hardware: Barcode Scanner
  ipcMain.handle('hardware:scan-barcode', async () => {
    return scannerService.scan();
  });

  // File operations
  ipcMain.handle('file:select-file', async (event, filters) => {
    const { filePaths } = await dialog.showOpenDialog(mainWindow!, {
      filters,
      properties: ['openFile'],
    });
    return filePaths[0] || null;
  });

  ipcMain.handle('file:export-data', async (event, { filePath, data }) => {
    try {
      fs.writeFileSync(filePath, data);
      return true;
    } catch (error) {
      console.error('Export error:', error);
      return false;
    }
  });

  // System control
  ipcMain.on('system:minimize', () => {
    mainWindow?.minimize();
  });

  ipcMain.on('system:maximize', () => {
    if (mainWindow?.isMaximized()) {
      mainWindow.restore();
    } else {
      mainWindow?.maximize();
    }
  });

  ipcMain.on('system:close', () => {
    mainWindow?.close();
  });

  ipcMain.handle('system:is-maximized', () => {
    return mainWindow?.isMaximized() || false;
  });
};
```

---

## Phase 3: Hardware Integration

### Step 3.1: Thermal Printer Service (`electron/hardware/printer.ts`)

```typescript
import SerialPort from 'serialport';

export class PrinterService {
  private ports: Map<string, SerialPort.SerialPort> = new Map();

  async listPrinters(): Promise<string[]> {
    const ports = await SerialPort.SerialPort.list();
    return ports.map(p => p.path);
  }

  async connectPrinter(portPath: string, baudRate = 9600): Promise<boolean> {
    try {
      const port = new SerialPort.SerialPort({ path: portPath, baudRate });
      this.ports.set(portPath, port);
      return true;
    } catch (error) {
      console.error('Failed to connect printer:', error);
      return false;
    }
  }

  async printReceipt(receiptHTML: string, printerPort = '/dev/ttyUSB0'): Promise<void> {
    const port = this.ports.get(printerPort);
    if (!port) {
      throw new Error(`Printer not connected: ${printerPort}`);
    }

    // ESC/POS commands
    const commands = this.htmlToEscPos(receiptHTML);
    
    return new Promise((resolve, reject) => {
      port.write(commands, (error) => {
        if (error) reject(error);
        else resolve();
      });
    });
  }

  private htmlToEscPos(html: string): Buffer {
    // Parse receipt HTML and convert to ESC/POS commands
    const commands: number[] = [];

    // Initialize printer
    commands.push(0x1b, 0x40); // ESC @

    // Example: Center align
    commands.push(0x1b, 0x61, 0x01); // ESC a 1

    // Add text (simplified)
    const textBytes = Buffer.from('Lumora POS Receipt\n\n', 'utf-8');
    commands.push(...Array.from(textBytes));

    // Cut paper
    commands.push(0x1d, 0x56, 0x41, 0x03); // GS V A 3

    return Buffer.from(commands);
  }

  disconnect(printerPort: string): void {
    const port = this.ports.get(printerPort);
    if (port) {
      port.close();
      this.ports.delete(printerPort);
    }
  }
}
```

### Step 3.2: Barcode Scanner Service (`electron/hardware/scanner.ts`)

```typescript
import SerialPort from 'serialport';

export class ScannerService {
  private scannerPort: SerialPort.SerialPort | null = null;
  private buffer: string = '';

  async connectScanner(portPath: string, baudRate = 9600): Promise<boolean> {
    try {
      this.scannerPort = new SerialPort.SerialPort({ path: portPath, baudRate });

      this.scannerPort.on('data', (data) => {
        this.buffer += data.toString();
        if (this.buffer.includes('\r') || this.buffer.includes('\n')) {
          const code = this.buffer.trim();
          // Send barcode data to renderer
          // (implemented via IPC event)
        }
      });

      return true;
    } catch (error) {
      console.error('Failed to connect scanner:', error);
      return false;
    }
  }

  async scan(): Promise<string> {
    return new Promise((resolve) => {
      const handler = (data: string) => {
        this.scannerPort?.off('data', handler);
        resolve(data);
      };
      this.scannerPort?.on('data', (data) => {
        handler(data.toString().trim());
      });
    });
  }

  disconnect(): void {
    if (this.scannerPort) {
      this.scannerPort.close();
      this.scannerPort = null;
    }
  }
}
```

### Step 3.3: Cash Drawer Service (`electron/hardware/drawer.ts`)

```typescript
import SerialPort from 'serialport';

export class DrawerService {
  private drawerPort: SerialPort.SerialPort | null = null;

  async connectDrawer(portPath: string, baudRate = 9600): Promise<boolean> {
    try {
      this.drawerPort = new SerialPort.SerialPort({ path: portPath, baudRate });
      return true;
    } catch (error) {
      console.error('Failed to connect drawer:', error);
      return false;
    }
  }

  async openDrawer(printerPort?: SerialPort.SerialPort): Promise<void> {
    const port = printerPort || this.drawerPort;
    if (!port) {
      throw new Error('Drawer port not connected');
    }

    // ESC/POS cash drawer open command
    const commands = Buffer.from([0x1b, 0x70, 0x00, 0x19, 0x19]);

    return new Promise((resolve, reject) => {
      port.write(commands, (error) => {
        if (error) reject(error);
        else resolve();
      });
    });
  }

  disconnect(): void {
    if (this.drawerPort) {
      this.drawerPort.close();
      this.drawerPort = null;
    }
  }
}
```

---

## Phase 4: Frontend Integration

### Step 4.1: Create Electron Hook (`frontend/src/hooks/useElectron.ts`)

```typescript
import { useCallback } from 'react';

export const useElectron = () => {
  const isElectron = typeof window !== 'undefined' && window.electron;

  const printReceipt = useCallback(async (receiptHTML: string): Promise<boolean> => {
    if (!isElectron) return false;
    return window.electron.hardware.printReceipt(receiptHTML);
  }, [isElectron]);

  const openCashDrawer = useCallback(async (): Promise<boolean> => {
    if (!isElectron) return false;
    return window.electron.hardware.openCashDrawer();
  }, [isElectron]);

  const scanBarcode = useCallback(async (): Promise<string> => {
    if (!isElectron) return '';
    return window.electron.hardware.scanBarcode();
  }, [isElectron]);

  const getPrinters = useCallback(async (): Promise<string[]> => {
    if (!isElectron) return [];
    return window.electron.hardware.getPrinters();
  }, [isElectron]);

  const minimize = useCallback(() => {
    if (isElectron) window.electron.system.minimize();
  }, [isElectron]);

  const maximize = useCallback(() => {
    if (isElectron) window.electron.system.maximize();
  }, [isElectron]);

  const close = useCallback(() => {
    if (isElectron) window.electron.system.close();
  }, [isElectron]);

  return {
    isElectron,
    printReceipt,
    openCashDrawer,
    scanBarcode,
    getPrinters,
    minimize,
    maximize,
    close,
  };
};
```

### Step 4.2: Update Receipt Component

Modify [frontend/src/components/Receipt.tsx](frontend/src/components/Receipt.tsx) to use Electron:

```typescript
import { useElectron } from '@/hooks/useElectron';

export const Receipt = ({ sale }: { sale: Sale }) => {
  const { printReceipt, isElectron } = useElectron();

  const handlePrint = async () => {
    const receiptHTML = generateReceiptHTML(sale);
    
    if (isElectron) {
      const success = await printReceipt(receiptHTML);
      if (success) {
        toast.success('Receipt printed');
      } else {
        toast.error('Print failed');
      }
    } else {
      // Fallback: browser print
      window.print();
    }
  };

  return (
    <div>
      {/* Receipt content */}
      <button onClick={handlePrint}>
        {isElectron ? 'Print Receipt' : 'Print (Browser)'}
      </button>
    </div>
  );
};
```

### Step 4.3: Update Terminal Page for Drawer Integration

```typescript
// frontend/src/app/terminal/page.tsx
import { useElectron } from '@/hooks/useElectron';

export default function TerminalPage() {
  const { openCashDrawer, isElectron } = useElectron();

  const handleCheckout = async (sale: Sale) => {
    // ... process sale ...

    if (isElectron && sale.paymentMethod === 'CASH') {
      await openCashDrawer();
    }
  };

  return (
    <div>
      {/* Terminal UI */}
    </div>
  );
}
```

---

## Phase 5: Build Configuration

### Step 5.1: Create `electron-builder.config.js`

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
    certificateFile: process.env.WIN_CSC_LINK,
    certificatePassword: process.env.WIN_CSC_KEY_PASSWORD,
  },
  nsis: {
    oneClick: false,
    allowToChangeInstallationDirectory: true,
    createDesktopShortcut: true,
    createStartMenuShortcut: true,
  },
  mac: {
    target: ['dmg', 'zip'],
    category: 'public.app-category.productivity',
  },
  linux: {
    target: ['AppImage', 'deb'],
    category: 'Utility',
  },
};
```

### Step 5.2: Update `next.config.mjs`

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

---

## Phase 6: Development & Deployment

### Step 6.1: Run Development Build

```bash
cd frontend
npm run dev
```

This will:
1. Start Next.js dev server on port 3000
2. Wait for server to be ready
3. Launch Electron pointing to http://localhost:3000

### Step 6.2: Build for Distribution

```bash
# Build and create installers for all platforms
npm run dist

# Or build for specific platform
npm run build  # Windows/Mac/Linux
```

### Step 6.3: Auto-Update Setup

Create `electron/utils/autoUpdater.ts`:

```typescript
import { autoUpdater } from 'electron-updater';
import { BrowserWindow } from 'electron';

export const setupAutoUpdater = (mainWindow: BrowserWindow) => {
  autoUpdater.checkForUpdatesAndNotify();

  autoUpdater.on('update-available', () => {
    mainWindow.webContents.send('update-available');
  });

  autoUpdater.on('update-downloaded', () => {
    mainWindow.webContents.send('update-downloaded');
  });
};
```

---

## Implementation Checklist

- [ ] Install Electron dependencies
- [ ] Create electron/ directory structure
- [ ] Implement main process (main.ts)
- [ ] Create preload script with IPC API
- [ ] Setup IPC handlers
- [ ] Implement hardware services (printer, scanner, drawer)
- [ ] Create `useElectron` hook
- [ ] Update Receipt component
- [ ] Update Terminal page for drawer
- [ ] Configure electron-builder
- [ ] Update next.config.mjs for export
- [ ] Test dev build
- [ ] Build distribution packages
- [ ] Setup auto-updater
- [ ] Document deployment procedure

---

## Key Considerations

### Security
- ✅ Context isolation enabled
- ✅ Node integration disabled
- ✅ Preload script validates IPC
- ✅ HTTPS for API calls in production
- ⚠️ Store JWT token securely (not in localStorage)

### Performance
- Use `next export` for static build
- Bundle native modules with electron-builder
- Lazy-load hardware modules
- Cache printer/scanner availability

### Cross-Platform Testing
- Windows: Test on Windows 10/11
- macOS: Sign app with developer certificate
- Linux: Test on Ubuntu LTS

### Offline Capability
- Cache API responses with TanStack Query
- Implement background sync
- Queue transactions during offline periods

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| White screen on startup | Ensure `http://localhost:3000` is available; check Next.js dev server |
| Serial port not found | List ports: `SerialPort.SerialPort.list()` |
| Print fails | Verify printer connection; test ESC/POS commands separately |
| Build fails | Clear `out/` dir; run `npm run next-build` first |
| IPC not working | Verify preload script path; check context isolation settings |

---

## Next Steps

1. **Start with Phase 1-2**: Setup Electron scaffold and IPC
2. **Phase 3**: Implement one hardware feature (printer) first
3. **Phase 4**: Integrate with React components
4. **Phase 5**: Build and test installers
5. **Phase 6**: Setup auto-update and deployment pipeline

---

## Resources

- [Electron Documentation](https://www.electronjs.org/docs)
- [electron-builder](https://www.electron.build/)
- [Electron Security](https://www.electronjs.org/docs/tutorial/security)
- [ESC/POS Specification](https://github.com/receipt-print-hq/receipt-print-hq/wiki)
