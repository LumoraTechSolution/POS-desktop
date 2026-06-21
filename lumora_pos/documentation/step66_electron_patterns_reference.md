# Electron + Next.js POS Patterns Reference

This guide shows common patterns for building a POS desktop app with Electron and Next.js.

---

## 1. Hardware Detection on Startup

**`electron/main.ts`** - Detect printers/scanners when app starts:

```typescript
import { app, BrowserWindow, ipcMain } from 'electron';
import SerialPort from 'serialport';

let mainWindow: BrowserWindow | null = null;

const detectHardware = async () => {
  const ports = await SerialPort.SerialPort.list();
  return ports.map(p => ({
    path: p.path,
    manufacturer: p.manufacturer,
    serialNumber: p.serialNumber,
  }));
};

app.on('ready', async () => {
  const devices = await detectHardware();
  
  // Send to renderer
  ipcMain.handle('get-hardware-status', () => ({
    printers: devices.filter(d => d.manufacturer?.includes('Star')),
    scanners: devices.filter(d => d.manufacturer?.includes('Zebra')),
  }));
});
```

**Frontend** - Show hardware status on dashboard:

```typescript
import { useEffect, useState } from 'react';

export function HardwareStatus() {
  const [hardware, setHardware] = useState<any>(null);

  useEffect(() => {
    if (!window.electron) return;
    
    window.electron.hardware.getStatus?.().then(setHardware);
  }, []);

  return (
    <div className="bg-blue-50 p-4 rounded">
      <h3 className="font-bold">Hardware Status</h3>
      <div className="text-sm mt-2">
        🖨️ Printers: {hardware?.printers?.length || 0}
        📱 Scanners: {hardware?.scanners?.length || 0}
      </div>
    </div>
  );
}
```

---

## 2. Real-Time Barcode Scanning

**`electron/hardware/scanner.ts`** - Stream barcode events:

```typescript
import SerialPort from 'serialport';
import { EventEmitter } from 'events';

export class ScannerManager extends EventEmitter {
  private port: SerialPort.SerialPort | null = null;
  private buffer = '';

  async connect(portPath: string) {
    this.port = new SerialPort.SerialPort({
      path: portPath,
      baudRate: 9600,
    });

    this.port.on('data', (data) => {
      this.buffer += data.toString();
      
      // Barcode ends with CR+LF
      if (this.buffer.includes('\r\n')) {
        const barcode = this.buffer.replace(/[\r\n]/g, '').trim();
        this.emit('barcode', barcode);
        this.buffer = '';
      }
    });

    this.port.on('error', (err) => this.emit('error', err));
  }

  disconnect() {
    if (this.port?.isOpen) {
      this.port.close();
    }
  }
}
```

**`electron/ipc.ts`** - Stream barcodes to frontend:

```typescript
import { ipcMain, BrowserWindow } from 'electron';
import { ScannerManager } from './hardware/scanner';

const scanner = new ScannerManager();

export const setupIPC = (mainWindow: BrowserWindow | null) => {
  ipcMain.handle('scanner:connect', async (event, portPath) => {
    await scanner.connect(portPath);
    
    scanner.on('barcode', (code) => {
      // Send to frontend in real-time
      mainWindow?.webContents.send('barcode:scanned', code);
    });

    return true;
  });

  ipcMain.handle('scanner:disconnect', () => {
    scanner.disconnect();
    return true;
  });
};
```

**Frontend** - Listen for barcodes:

```typescript
import { useEffect, useCallback } from 'react';
import { useCart } from '@/stores/cart';

export function BarcodeListener() {
  const addItem = useCart((s) => s.addItem);

  useEffect(() => {
    if (!window.electron) return;

    // Listen for barcode scans
    const unsubscribe = window.electron.onBarcodeScanned?.(async (barcode) => {
      console.log('📱 Scanned:', barcode);
      
      // Look up product
      const product = await fetch(`/api/v1/products?barcode=${barcode}`)
        .then(r => r.json());
      
      if (product) {
        addItem(product);
      }
    });

    return unsubscribe;
  }, [addItem]);

  return null;
}
```

Update `preload.ts`:

```typescript
const electronAPI = {
  onBarcodeScanned: (callback: (code: string) => void) => {
    const handler = (_: any, code: string) => callback(code);
    window.electron.ipcRenderer.on('barcode:scanned', handler);
    return () => window.electron.ipcRenderer.removeListener('barcode:scanned', handler);
  },
};
```

---

## 3. Receipt Printing with ESC/POS

**`electron/hardware/printer.ts`** - Full printer implementation:

```typescript
import SerialPort from 'serialport';

export interface PrinterConfig {
  port: string;
  baudRate?: number;
  width?: number; // chars, usually 32 or 42
}

export class PrinterManager {
  private port: SerialPort.SerialPort | null = null;
  private config: PrinterConfig;

  constructor(config: PrinterConfig) {
    this.config = { baudRate: 9600, width: 32, ...config };
  }

  async connect() {
    this.port = new SerialPort.SerialPort({
      path: this.config.port,
      baudRate: this.config.baudRate,
    });

    await new Promise((resolve) => {
      this.port!.once('open', resolve);
    });
  }

  async printReceipt(receipt: {
    storeName: string;
    storeAddress: string;
    items: Array<{ name: string; qty: number; price: number }>;
    subtotal: number;
    tax: number;
    total: number;
    paymentMethod: string;
  }) {
    if (!this.port?.isOpen) throw new Error('Printer not connected');

    const commands = this.buildReceipt(receipt);
    
    return new Promise<void>((resolve, reject) => {
      this.port!.write(commands, (err) => {
        if (err) reject(err);
        else resolve();
      });
    });
  }

  private buildReceipt(receipt: any): Buffer {
    const cmd: number[] = [];

    // Initialize
    cmd.push(0x1b, 0x40); // ESC @

    // Header
    cmd.push(...this.centerText(receipt.storeName));
    cmd.push(...this.centerText(receipt.storeAddress));
    cmd.push(...this.line());

    // Items
    cmd.push(...this.text('Item                  Qty   Price'));
    cmd.push(...this.line());
    
    for (const item of receipt.items) {
      const line = `${item.name.padEnd(16)} ${item.qty} ${this.formatPrice(item.price * item.qty)}`;
      cmd.push(...this.text(line.substring(0, this.config.width!)));
    }

    cmd.push(...this.line());

    // Totals
    cmd.push(...this.rightAlignedLine(`Subtotal: ${this.formatPrice(receipt.subtotal)}`));
    cmd.push(...this.rightAlignedLine(`Tax:      ${this.formatPrice(receipt.tax)}`));
    cmd.push(...this.rightAlignedLine(`Total:    ${this.formatPrice(receipt.total)}`));

    cmd.push(...this.line());
    cmd.push(...this.centerText(`Payment: ${receipt.paymentMethod}`));

    // Footer
    cmd.push(...this.centerText('Thank you!'));
    
    // Cut paper
    cmd.push(0x1d, 0x56, 0x41, 0x03); // GS V A
    cmd.push(0x0a, 0x0a, 0x0a); // Line feeds

    return Buffer.from(cmd);
  }

  private centerText(text: string): number[] {
    const pad = Math.max(0, Math.floor((this.config.width! - text.length) / 2));
    return this.text(' '.repeat(pad) + text);
  }

  private rightAlignedLine(text: string): number[] {
    const pad = Math.max(0, this.config.width! - text.length);
    return this.text(' '.repeat(pad) + text);
  }

  private line(): number[] {
    return this.text('-'.repeat(this.config.width!));
  }

  private text(str: string): number[] {
    return [...Buffer.from(str + '\n', 'utf-8')];
  }

  private formatPrice(num: number): string {
    return `$${num.toFixed(2)}`;
  }

  disconnect() {
    if (this.port?.isOpen) {
      this.port.close();
    }
  }
}
```

**Frontend** - Print receipt component:

```typescript
import { useElectron } from '@/hooks/useElectron';
import { Sale } from '@/types';

export function PrintReceiptButton({ sale }: { sale: Sale }) {
  const { isElectron } = useElectron();

  const handlePrint = async () => {
    if (!isElectron) {
      window.print();
      return;
    }

    try {
      const receiptData = {
        storeName: 'Lumora POS',
        storeAddress: '123 Main St',
        items: sale.items,
        subtotal: sale.subtotal,
        tax: sale.tax,
        total: sale.total,
        paymentMethod: sale.paymentMethod,
      };

      await (window as any).electron.hardware.printReceipt(receiptData);
    } catch (error) {
      console.error('Print failed:', error);
    }
  };

  return (
    <button 
      onClick={handlePrint}
      className="px-4 py-2 bg-blue-600 text-white rounded"
    >
      {isElectron ? '🖨️ Print Receipt' : '📄 Browser Print'}
    </button>
  );
}
```

---

## 4. Offline Mode with Local Storage

**Store for offline sync:**

```typescript
// src/stores/offlineQueue.ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface QueuedSale {
  id: string;
  data: any;
  timestamp: number;
  synced: boolean;
}

export const useOfflineQueue = create<{
  queue: QueuedSale[];
  addSale: (sale: any) => void;
  markSynced: (id: string) => void;
  clearQueue: () => void;
}>(
  persist(
    (set) => ({
      queue: [],
      addSale: (sale) =>
        set((state) => ({
          queue: [
            ...state.queue,
            {
              id: Math.random().toString(),
              data: sale,
              timestamp: Date.now(),
              synced: false,
            },
          ],
        })),
      markSynced: (id) =>
        set((state) => ({
          queue: state.queue.map((s) =>
            s.id === id ? { ...s, synced: true } : s
          ),
        })),
      clearQueue: () => set({ queue: [] }),
    }),
    { name: 'offline-queue' }
  )
);
```

**Use in checkout:**

```typescript
import { useOfflineQueue } from '@/stores/offlineQueue';

export function Checkout() {
  const queue = useOfflineQueue();
  const [isOnline, setIsOnline] = useState(navigator.onLine);

  const handleCheckout = async (sale: Sale) => {
    try {
      if (!isOnline) {
        // Store locally
        queue.addSale(sale);
        toast.info('Sale saved offline. Will sync when online.');
        return;
      }

      // Try to sync offline queue first
      for (const item of queue.queue.filter((s) => !s.synced)) {
        await api.post('/sales', item.data);
        queue.markSynced(item.id);
      }

      // Then process current sale
      await api.post('/sales', sale);
      toast.success('Sale completed');
    } catch (error) {
      queue.addSale(sale);
      toast.warning('Saved offline');
    }
  };

  useEffect(() => {
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => setIsOnline(false);

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  return <div>{/* UI */}</div>;
}
```

---

## 5. Settings Persistence

**`electron/main.ts`** - Store app state:

```typescript
import Store from 'electron-store';

const store = new Store({
  defaults: {
    printerPort: '/dev/ttyUSB0',
    scannerPort: '/dev/ttyUSB1',
    receiptWidth: 32,
    companyName: 'Lumora POS',
  },
});

ipcMain.handle('settings:get', (event, key) => store.get(key));
ipcMain.handle('settings:set', (event, key, value) => {
  store.set(key, value);
  mainWindow?.webContents.send('settings:changed', { key, value });
});
```

**Frontend** - Use settings:

```typescript
import { create } from 'zustand';

export const useSettings = create((set) => {
  if (typeof window !== 'undefined' && (window as any).electron) {
    (window as any).electron.ipcRenderer?.on('settings:changed', ({ key, value }: any) => {
      set((state: any) => ({ [key]: value }));
    });
  }

  return {
    printerPort: '/dev/ttyUSB0',
    setPrinterPort: async (port) => {
      await (window as any).electron.ipc.invoke('settings:set', 'printerPort', port);
      set({ printerPort: port });
    },
  };
});
```

---

## 6. App Update Check

**`electron/main.ts`** - Setup auto-updater:

```typescript
import { autoUpdater } from 'electron-updater';

if (!isDev) {
  autoUpdater.checkForUpdatesAndNotify();
}

autoUpdater.on('update-available', () => {
  mainWindow?.webContents.send('update:available');
});

autoUpdater.on('update-downloaded', () => {
  mainWindow?.webContents.send('update:ready');
});

ipcMain.on('app:restart', () => {
  autoUpdater.quitAndInstall();
});
```

**Frontend** - Show update notification:

```typescript
import { useEffect, useState } from 'react';
import { AlertDialog, AlertDialogAction } from '@/components/ui/alert-dialog';

export function UpdateNotification() {
  const [updateReady, setUpdateReady] = useState(false);

  useEffect(() => {
    if (!window.electron) return;

    (window as any).electron.ipcRenderer?.on('update:ready', () => {
      setUpdateReady(true);
    });
  }, []);

  if (!updateReady) return null;

  return (
    <AlertDialog open={updateReady}>
      <AlertDialog.Content>
        <AlertDialog.Title>Update Available</AlertDialog.Title>
        <AlertDialog.Description>
          A new version is ready to install. Restart now?
        </AlertDialog.Description>
        <AlertDialog.Action
          onClick={() => (window as any).electron.ipcRenderer?.send('app:restart')}
        >
          Restart
        </AlertDialog.Action>
      </AlertDialog.Content>
    </AlertDialog>
  );
}
```

---

## 7. Error Logging

**`electron/main.ts`** - Centralized error handling:

```typescript
import fs from 'fs';
import path from 'path';

const logPath = path.join(app.getPath('userData'), 'logs');

ipcMain.handle('log:error', (event, { message, stack, context }) => {
  const timestamp = new Date().toISOString();
  const log = `[${timestamp}] ${message}\n${stack}\nContext: ${JSON.stringify(context)}\n\n`;

  fs.appendFileSync(path.join(logPath, 'errors.log'), log);
  console.error(message, stack);
});
```

**Frontend** - Global error handler:

```typescript
export function ErrorReporter() {
  useEffect(() => {
    const handleError = (event: ErrorEvent) => {
      if (window.electron) {
        (window as any).electron.ipc.invoke('log:error', {
          message: event.message,
          stack: event.error?.stack,
          context: { url: window.location.href },
        });
      }
    };

    window.addEventListener('error', handleError);
    return () => window.removeEventListener('error', handleError);
  }, []);

  return null;
}
```

---

## 8. Multi-Window Support

**`electron/main.ts`** - Settings window:

```typescript
let settingsWindow: BrowserWindow | null = null;

ipcMain.handle('window:open-settings', () => {
  if (settingsWindow?.isDestroyed() === false) {
    settingsWindow.focus();
    return;
  }

  settingsWindow = new BrowserWindow({
    width: 600,
    height: 400,
    parent: mainWindow!,
    modal: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
    },
  });

  const url = isDev
    ? 'http://localhost:3000/settings'
    : `file://${path.join(__dirname, '../out/settings/page.html')}`;

  settingsWindow.loadURL(url);

  settingsWindow.on('closed', () => {
    settingsWindow = null;
  });
});
```

**Frontend** - Open settings:

```typescript
const openSettings = () => {
  (window as any).electron.ipc.invoke('window:open-settings');
};
```

---

## Checklist

- [ ] Implement hardware detection
- [ ] Add barcode scanning
- [ ] Implement receipt printing
- [ ] Add offline sync queue
- [ ] Persist user settings
- [ ] Setup auto-updater
- [ ] Add error logging
- [ ] Handle multi-window scenarios

---

## Resources

- ESC/POS Spec: https://github.com/receipt-print-hq/receipt-print-hq
- SerialPort: https://serialport.io/
- Electron Best Practices: https://www.electronjs.org/docs/tutorial/security
