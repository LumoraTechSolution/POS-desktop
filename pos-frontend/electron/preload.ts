import { contextBridge } from 'electron';

// Hardware integration (printer, scanner, cash drawer) currently runs in the
// browser layer via web standards (see receiptPrinterService / useBarcodeScanner).
// This bridge only exposes platform metadata so the renderer can flag itself
// as the desktop build if it wants to.
contextBridge.exposeInMainWorld('lumora', {
  isDesktop: true,
  platform: process.platform,
  versions: {
    electron: process.versions.electron,
    chrome: process.versions.chrome,
    node: process.versions.node,
  },
});
