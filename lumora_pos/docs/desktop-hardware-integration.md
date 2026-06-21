# Lumora POS — Desktop Hardware Integration

> **Status:** v1 plan — Windows desktop, ESC/POS thermal printers + cash drawer + USB barcode scanner.
> **See also:** `docs/desktop-electron-build.md` (the bundle this plugs into),
> `docs/three-sku-delivery-strategy.md` (umbrella).
> **Replaces** the "covered separately" placeholder in build doc § 8.

---

## 1. The cardinal rule — read this before anything else

**A printer or cash-drawer failure must never block a sale from completing.** The database write is the source of truth for retail. Hardware actions are best-effort side effects. If the printer is offline, the drawer is unplugged, or the receipt rendering throws, the sale still commits, the variance still calculates, the cashier still sees their next customer.

Every code path below treats hardware as a fire-and-forget after the sale is persisted. UI surfaces a non-blocking warning. A "Reprint last receipt" affordance covers the recovery case.

This rule is not negotiable. Code review must reject any change that makes a hardware call awaitable on the sale-creation path.

---

## 2. Current state

The frontend already ships partial hardware abstractions. The good news: most of it stays.

| Component | What exists | Verdict for Desktop |
|---|---|---|
| `frontend/src/hooks/useBarcodeScanner.ts` | Global keyboard listener with timing-based scanner detection (50 ms inter-key threshold, terminates on Enter) | **Ships unchanged.** USB barcode scanners emulate keyboards; Electron's renderer is Chromium. The same code works on web SaaS and desktop. |
| `frontend/src/services/hardwareService.ts` | Stores config in `localStorage`; declares modes `'browser_print' \| 'raw_usb' \| 'qz_tray'`; `kickCashDrawer()` is a `console.warn` stub | **Add a fourth mode** `'electron_native'`. Keep the others for the Web SKU. |
| `frontend/src/services/receiptPrinterService.ts` | Renders an HTML receipt and calls `window.print()` (Chromium print dialog) | Works but goes through the OS print dialog (extra UI step, slow, no native ESC/POS features like cut, drawer kick, font size). **Add an ESC/POS path** for `electron_native` mode. |
| `frontend/src/components/settings/HardwareSettings.tsx` | UI for printer mode, paper width, drawer kick code | **Extend** the form when running under Electron (`window.lumora?.isDesktop`) to expose transport (TCP / serial / Windows queue), address, and a "Test print" button. |
| Cash drawer | Stub only | **Build the real thing**, almost always via drawer-thru-printer (see § 7). |

---

## 3. Transport options — pick one per install, two supported

Print transport is a per-install setting (the customer's hardware drives it), not a runtime decision. We support **two transports in v1**, plus a fallback.

### 3.1 TCP / network printer (RAW port 9100) — first-class

Modern thermal printers (Epson TM-T82, Bixolon SRP-330, most Xprinter Ethernet models) accept raw ESC/POS bytes on TCP port 9100 (HP JetDirect / RAW protocol). No drivers, no permissions, no admin.

```ts
import net from 'node:net';

async function sendTcp(host: string, port: number, bytes: Uint8Array, timeoutMs = 5000) {
  return new Promise<void>((resolve, reject) => {
    const socket = net.createConnection({ host, port });
    const timer = setTimeout(() => { socket.destroy(); reject(new Error('printer timeout')); }, timeoutMs);
    socket.once('error', err => { clearTimeout(timer); reject(err); });
    socket.once('connect', () => {
      socket.end(Buffer.from(bytes), () => { clearTimeout(timer); resolve(); });
    });
  });
}
```

**Pros:** zero install footprint on Windows; multiple terminals can target the same printer (when the multi-terminal LAN extension lands — see build doc § 8); status read works (`GS r 1` gives back paper/cover/drawer state).

**Cons:** customer must assign the printer a static IP or DHCP reservation. Document this in the install runbook.

### 3.2 Windows raw print queue — second-class but most-customer-friendly

When the printer is USB-attached, the customer installs the manufacturer's Windows driver, the OS exposes the printer as a queue ("EPSON TM-T82III Receipt"), and we send raw ESC/POS bytes through the spooler — bypassing the driver's formatting but using its transport.

Use the `pdf-to-printer` ecosystem's RAW path, or shell out to a tiny helper. The proven approach is the **Windows Print API via `node-printer`** (or its maintained fork `@thiagoelg/node-printer`):

```ts
import printer from '@thiagoelg/node-printer';

async function sendWindowsRaw(queueName: string, bytes: Uint8Array) {
  return new Promise<void>((resolve, reject) => {
    printer.printDirect({
      data: Buffer.from(bytes),
      printer: queueName,
      type: 'RAW',
      success: () => resolve(),
      error: (err: unknown) => reject(err as Error),
    });
  });
}
```

**Pros:** the customer's existing printer driver "just works"; status visible in the standard Windows printer dialog; handles USB hot-plug.

**Cons:** `node-printer` is a native Node module — must be rebuilt for Electron's exact ABI (see § 8). No clean status-read API; failures surface as job errors after the fact, not before.

### 3.3 Serial / virtual COM port — fallback

Older or industrial printers expose RS-232 either natively or via a USB-to-Serial adapter that creates a `COMx` virtual port. Use the `serialport` package:

```ts
import { SerialPort } from 'serialport';

async function sendSerial(path: string, baudRate: number, bytes: Uint8Array) {
  return new Promise<void>((resolve, reject) => {
    const port = new SerialPort({ path, baudRate, autoOpen: false });
    port.open(err => {
      if (err) return reject(err);
      port.write(Buffer.from(bytes), wErr => {
        if (wErr) { port.close(); return reject(wErr); }
        port.drain(() => port.close(closeErr => closeErr ? reject(closeErr) : resolve()));
      });
    });
  });
}
```

**Pros:** works for legacy hardware that customers refuse to replace.

**Cons:** customer has to know the COM port number and baud (typically 9600 or 19200, no parity, 8N1); zero auto-discovery; another native module rebuild.

### 3.4 Explicitly **not** supported in v1

- **WebUSB / direct libusb.** Requires the customer to swap the printer's Windows driver for WinUSB (via Zadig). This breaks the printer for every other Windows app and is a support nightmare. Hard no.
- **QZ Tray** (`'qz_tray'` mode in `hardwareService.ts`). Browser-era escape hatch. Keep the enum value for the Web SKU; do not wire it for Desktop.
- **Bluetooth printers.** Defer until a customer asks.

### 3.5 Recommendation matrix

| Customer scenario | Use this transport |
|---|---|
| New customer buying hardware on our recommendation | TCP/Ethernet printer with static IP |
| Existing USB printer, customer has Windows driver installed | Windows raw print queue |
| Legacy serial printer, customer won't upgrade | Serial COM port |
| Anything else | Decline politely, recommend a TCP printer |

---

## 4. The preload bridge contract

The Electron renderer cannot touch `serialport`, `net`, or `node-printer` directly — those are Node-side modules and we do not enable `nodeIntegration` in renderers. All hardware calls flow through the preload bridge defined in `frontend/electron/preload.ts`.

```ts
// frontend/electron/preload.ts (extends what the build doc already declares)
import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('lumora', {
  isDesktop: true,
  getRuntimeInfo: () => ipcRenderer.invoke('lumora:get-runtime-info'),

  printer: {
    list: () => ipcRenderer.invoke('lumora:printer:list'),
    test: (cfg: PrinterConfig) => ipcRenderer.invoke('lumora:printer:test', cfg),
    printRaw: (bytes: Uint8Array, cfg: PrinterConfig) =>
      ipcRenderer.invoke('lumora:printer:print-raw', { bytes: Array.from(bytes), cfg }),
    status: (cfg: PrinterConfig) => ipcRenderer.invoke('lumora:printer:status', cfg),
  },

  drawer: {
    kick: (cfg: PrinterConfig, pin: 0 | 1 = 0) =>
      ipcRenderer.invoke('lumora:drawer:kick', { cfg, pin }),
  },
});

interface PrinterConfig {
  transport: 'tcp' | 'windows_raw' | 'serial';
  address: string;          // "192.168.1.50:9100" | "EPSON TM-T82III Receipt" | "COM3"
  baudRate?: number;        // serial only — default 9600
  paperWidth: '58mm' | '80mm';
}
```

Type the contract in `frontend/types/lumora.d.ts` (already in build doc's "Files to create").

**IPC handlers** live in a new file `frontend/electron/services/hardware.ts`. Each handler:

1. Parses + validates the `PrinterConfig` (reject anything not in the allowed transport union — never trust the renderer).
2. Delegates to a transport-specific function (`sendTcp` / `sendWindowsRaw` / `sendSerial`).
3. Wraps the call in a 5-second timeout.
4. Logs success/failure to `%LOCALAPPDATA%\LumoraPOS\logs\hardware.log` (separate from `electron.log` to make printer triage easier).
5. Returns `{ ok: true }` or `{ ok: false, code: 'OFFLINE' | 'TIMEOUT' | 'PAPER_OUT' | 'COVER_OPEN' | 'OTHER', message }`.

The renderer never throws on a hardware error; it inspects `result.ok` and surfaces a non-blocking toast.

---

## 5. Frontend changes

### 5.1 `hardwareService.ts`

Extend the union and config shape:

```ts
export type PrinterMode = 'browser_print' | 'raw_usb' | 'qz_tray' | 'electron_native';

export interface HardwareConfig {
  printerMode: PrinterMode;
  printerTarget: string;
  paperWidth: '58mm' | '80mm';
  cashDrawerKick: boolean;
  kickCode: string;

  // electron_native only
  transport?: 'tcp' | 'windows_raw' | 'serial';
  address?: string;
  baudRate?: number;
  drawerPin?: 0 | 1;            // most printers wire drawer 1 to the only DK port
}
```

Replace the stubbed `kickCashDrawer()` with a real implementation that branches on mode:

```ts
async kickCashDrawer(): Promise<void> {
  const cfg = this.getConfig();
  if (!cfg.cashDrawerKick) return;

  if (cfg.printerMode === 'electron_native' && window.lumora?.drawer) {
    await window.lumora.drawer.kick(this.toPrinterConfig(cfg), cfg.drawerPin ?? 0);
    return;
  }

  if (cfg.printerMode === 'browser_print') {
    console.warn('💳 Cash Drawer Kick Simulated [Browser Print Mode]');
    return;
  }
  // raw_usb / qz_tray — Web SKU paths, not implemented in v1
}
```

`hardwareService` is sync today; `kickCashDrawer` becomes `async` but callers must **not** await it on the sale path (cardinal rule — § 1).

### 5.2 `receiptPrinterService.ts`

When `printerMode === 'electron_native'`, route through ESC/POS instead of the HTML print window. Add a peer module `frontend/src/services/escposBuilder.ts` that produces the byte stream from `ReceiptData`. Use `node-thermal-printer` or a small hand-rolled builder — the receipt is a well-known shape, ~150 lines of byte assembly:

```ts
// escposBuilder.ts — sketch
const ESC = 0x1B, GS = 0x1D, LF = 0x0A;

export function buildReceiptBytes(data: ReceiptData, paperWidth: '58mm' | '80mm'): Uint8Array {
  const cols = paperWidth === '80mm' ? 48 : 32;
  const out: number[] = [];
  out.push(ESC, 0x40);                         // initialise printer
  out.push(ESC, 0x61, 0x01);                   // center
  out.push(ESC, 0x21, 0x30);                   // double width + height
  pushText(out, data.tenantName);
  out.push(LF, ESC, 0x21, 0x00);               // normal size
  if (data.tenantAddressLine1) pushText(out, data.tenantAddressLine1, LF);
  // ... items, totals, payment, footer ...
  out.push(GS, 0x56, 0x42, 0x00);              // partial cut (GS V B)
  return Uint8Array.from(out);
}
```

Update `processHardwareCheckoutActions` to switch on mode:

```ts
async processHardwareCheckoutActions(data: ReceiptData) {
  const cfg = hardwareService.getConfig();

  if (cfg.printerMode === 'electron_native' && window.lumora?.printer) {
    const printerCfg = hardwareService.toPrinterConfig(cfg);
    const bytes = buildReceiptBytes(data, cfg.paperWidth);

    // fire-and-forget — sale already committed
    window.lumora.printer.printRaw(bytes, printerCfg)
      .then(r => !r.ok && toast.error(`Receipt failed: ${r.code}`))
      .catch(() => toast.error('Receipt failed'));

    if (cfg.cashDrawerKick) {
      window.lumora.drawer.kick(printerCfg, cfg.drawerPin ?? 0).catch(() => {/* silent */});
    }
    return;
  }

  // existing browser_print path unchanged
  if (cfg.cashDrawerKick) hardwareService.kickCashDrawer();
  this.printBrowserReceipt(data);
}
```

### 5.3 `HardwareSettings.tsx`

When `window.lumora?.isDesktop`, render extra fields:

- **Transport** (radio: TCP / Windows queue / Serial)
- **Address** (`192.168.1.50:9100` / queue name picker populated from `window.lumora.printer.list()` / `COMx` picker)
- **Baud rate** (serial only)
- **Drawer pin** (0 / 1, default 0)
- **Test print** button → calls `window.lumora.printer.test(cfg)` and shows the result

Hide these fields on the Web SKU (`!window.lumora?.isDesktop`). Existing browser-print fields keep working unchanged for Web.

---

## 6. POS terminal integration — what gets called when

Map the existing checkout flow to hardware calls. Read `frontend/src/app/(pos)/terminal/page.tsx` for the current sale-completion path.

```
[Cashier clicks Pay]
  └─ saleService.createSale(...)               ← awaitable, must succeed
     └─ on 200:
         ├─ updateUI(saleCompleted)            ← user sees "Done" immediately
         ├─ receiptPrinterService.processHardwareCheckoutActions(data)
         │    ├─ build ESC/POS bytes
         │    ├─ window.lumora.printer.printRaw(...)  ← fire-and-forget
         │    └─ window.lumora.drawer.kick(...)       ← fire-and-forget
         └─ resetCart()
```

The next sale can begin while the printer is still spitting paper. If the printer call is still pending when the cashier rings up the next item, that's fine — Node's IPC and TCP send are non-blocking.

**"Reprint last receipt"** lives in the POS header (button visible if `lastReceipt` is set in a small Zustand store). It re-invokes `processHardwareCheckoutActions(lastReceipt.data)`. No backend round-trip — the receipt data is already cached client-side from the last successful sale.

---

## 7. Cash drawer wiring — the practical reality

In retail, **99% of cash drawers are wired through the printer.** The drawer's RJ11 cable plugs into the printer's "DK" port. Sending the kick bytes (ESC/POS sequence `ESC p m t1 t2`, typically `0x1B 0x70 0x00 0x19 0xFA`) over the printer's transport opens the drawer.

This means: **same transport, same address, no separate drawer config.** If you can print, you can kick the drawer. Only the kick byte sequence differs.

```ts
// frontend/electron/services/hardware.ts — drawer kick handler
ipcMain.handle('lumora:drawer:kick', async (_, { cfg, pin }) => {
  // ESC/POS drawer kick: ESC p m t1 t2
  //   m  = drawer pin (0 or 1)
  //   t1 = pulse on time  (0x19 = 25 * 2ms = 50ms)
  //   t2 = pulse off time (0xFA = 250 * 2ms = 500ms)
  const bytes = Uint8Array.of(0x1B, 0x70, pin & 0x01, 0x19, 0xFA);
  return sendByTransport(cfg, bytes);
});
```

**Standalone USB-to-RJ11 drawer adapters** exist (e.g., POS-X DK series) for setups without a thermal printer. Out of scope for v1 — if a customer asks, we'll add a `'usb_drawer'` transport then.

---

## 8. Native modules in the Electron build

`serialport` and `@thiagoelg/node-printer` are **native modules** — they ship `.node` binaries compiled against a specific Node ABI. Electron embeds its own Node version that does not match system Node. Without rebuilding, the modules either fail to load or crash silently.

### 8.1 Install + rebuild

```bash
cd frontend
npm i serialport @thiagoelg/node-printer
npm i -D @electron/rebuild
```

Add a postinstall hook in `frontend/package.json`:

```jsonc
"scripts": {
  "postinstall": "electron-rebuild -f -w serialport,@thiagoelg/node-printer"
}
```

`electron-rebuild` compiles the native modules against the embedded Electron ABI. Required toolchain on Windows:

- Visual Studio 2022 Build Tools with the "Desktop development with C++" workload.
- Python 3.x (`node-gyp` dependency).

Document the build-machine requirements in the team README.

### 8.2 electron-builder packaging

Native `.node` files cannot live inside `app.asar` — they must be unpacked at runtime. Update the `asarUnpack` glob in the build doc's electron-builder config:

```jsonc
"build": {
  "asar": true,
  "asarUnpack": [
    "**/*.node",
    "node_modules/serialport/**/*",
    "node_modules/@serialport/**/*",
    "node_modules/@thiagoelg/node-printer/**/*"
  ]
}
```

This is additive to what the build doc already specifies (just `**/*.node`); the explicit module paths defend against electron-builder's tree-shaking pulling in the JS but missing transitive native deps.

### 8.3 Code-protection interaction

`docs/desktop-code-protection.md` § 3 Layer 3 (bytenode) **must not** compile `frontend/electron/services/hardware.ts` if it directly imports `serialport` — bytenode and native module loading interact poorly. Two options:

- **Recommended:** keep `hardware.ts` as plain JS in the bytenode `targets` exclusion list. It is glue code, not high-value IP.
- Alternative: extract the protocol logic (ESC/POS byte assembly, status parsing) into a separate `escposProtocol.ts` and bytenode-compile only that, leaving the transport file uncompiled.

Update the code-protection doc's `compile-electron.js` `targets` array to omit `services/hardware.js`.

---

## 9. Smoke test on real hardware

A green run on **all printers + drawer combinations** the team owns is the definition of done. The team already has these models in the lab:

| Printer | Transport tested | Notes |
|---|---|---|
| **Epson TM-T82III** | TCP (Ethernet model) + USB (Windows raw queue) | Reference model; ESC/POS spec written by Epson. If anything works, this works. |
| **Xprinter XP-T80A** | USB (Windows raw queue) | Common budget thermal in SEA / Sri Lanka. Some firmware revisions have a buggy drawer pulse — verify both pins. |
| **Bixolon SRP-330II** | TCP + USB | Korean mid-range. Native ESC/POS + Bixolon's own command extensions; we use only the standard subset. |
| **Generic 58mm Xprinter (XP-58IIH)** | USB | Test 58mm paper width path; column count is 32, not 48. |

For each printer:

1. Connect printer + drawer (drawer cable into printer DK1 port).
2. In Settings → Hardware: pick mode `electron_native`, set transport + address, click **Test print**.
3. Confirm self-test page prints with no garbled characters and correct paper width.
4. From the POS terminal, ring up a sale, click Pay → drawer opens within 1 s, receipt prints, cut is clean.
5. Pull the printer's network cable (or unplug USB) mid-sale → next sale completes, toast appears, no hang, no crash, no orphan child process.
6. Reconnect → next sale prints normally without restarting the app.
7. Open paper compartment mid-print → status read returns `COVER_OPEN`, UI shows "Printer not ready"; sale still completes.
8. Run out of paper → ESC/POS status returns `PAPER_OUT`; sale still completes; "Reprint last receipt" works after paper is replaced.

Negative tests:

- Configure a TCP printer with a bogus IP (`192.168.99.99:9100`) → Test print fails with `TIMEOUT` within 5 s; UI does not freeze.
- Configure a Windows queue name that doesn't exist → `node-printer` returns an error; surfaced as `OTHER`; sale path unaffected.
- Block the printer port at the Windows firewall → same as offline; recover by adding the firewall rule (document this).

---

## 10. Failure modes & UX

| Failure | Detection | UX |
|---|---|---|
| Printer offline (no power, network unreachable) | `sendTcp` socket timeout (5 s); `sendSerial` open error; `sendWindowsRaw` job error | Toast: "Receipt could not print. Sale completed. Click here to reprint." |
| Out of paper | ESC/POS `GS r 1` status read after print returns `PAPER_OUT` flag | Same toast + the offline indicator turns yellow on the POS header |
| Cover open | Status flag `COVER_OPEN` | Same toast |
| Drawer disconnected from printer | **Not detectable in software.** Drawer-thru-printer has no return signal. | None. Cashier notices when drawer doesn't open and uses the manual key. Documented in customer training. |
| Drawer kicked but customer left it open (printer thinks it's closed by default) | Most printers report `DRAWER_OPEN` on `GS r 2`, but reliability varies — do not gate sales on this. | Optional: a discreet header indicator. Not v1. |
| Native module load failure on launch (Electron rebuild missed) | `require('serialport')` throws at IPC handler load | Splash shows "Hardware module unavailable — receipt printing disabled." App still runs in browser-print fallback. Logged to `electron.log` for support. |
| Printer found but garbled output (wrong code page) | Visual only | Hardware Settings has a "Code page" dropdown (default PC437); document common values (PC437, PC850, PC852, PC858) for non-English receipts. |

**Header status indicator** in `POSHeader.tsx`: a small printer icon coloured green (last print ok), yellow (warning — paper low / cover open), red (offline). Updated by a 30-second background poll of `window.lumora.printer.status(cfg)` when in `electron_native` mode. Polling is silent on failure; never blocks UI.

---

## 11. Files to create / modify

**Create:**

- `frontend/electron/services/hardware.ts` — IPC handlers for `lumora:printer:*` and `lumora:drawer:*`; transport implementations.
- `frontend/src/services/escposBuilder.ts` — ESC/POS byte builder for receipts (replaces HTML for `electron_native` mode).
- `installer/firewall-rules.nsh` — NSIS snippet to add a Windows Firewall outbound exception for the chosen printer port (only matters for non-default TCP ports; document but ship empty in v1).

**Modify:**

- `frontend/electron/preload.ts` — add `printer` and `drawer` namespaces (build doc currently exposes only `getRuntimeInfo`).
- `frontend/electron/main.ts` — register the new IPC handlers from `services/hardware.ts` after app ready.
- `frontend/src/services/hardwareService.ts` — add `'electron_native'` mode, async `kickCashDrawer()`, new config fields.
- `frontend/src/services/receiptPrinterService.ts` — branch on `printerMode === 'electron_native'`; route to ESC/POS via preload.
- `frontend/src/components/settings/HardwareSettings.tsx` — desktop-only fields (transport / address / baud / drawer pin / Test button).
- `frontend/src/components/pos/POSHeader.tsx` — add the printer status indicator.
- `frontend/types/lumora.d.ts` — extend the `window.lumora` type with `printer` and `drawer` namespaces.
- `frontend/package.json` — add `serialport`, `@thiagoelg/node-printer`, `@electron/rebuild`, `postinstall` script, extended `asarUnpack` config.
- `docs/desktop-electron-build.md` — update § 4.3 preload sketch with the new namespaces; update § 4.5 `asarUnpack`; replace the § 8 "covered separately" line with a cross-reference to this doc.
- `docs/desktop-code-protection.md` — exclude `frontend/electron/services/hardware.js` from the bytenode `targets`.

**No backend changes.** Hardware integration is entirely a frontend + Electron concern.

---

## 12. Out of scope (v1)

- WebUSB / direct libusb (§ 3.4).
- QZ Tray (§ 3.4).
- Bluetooth printers.
- Standalone USB-to-RJ11 cash drawers (§ 7).
- Customer-facing displays (CFDs / pole displays).
- Weighing scales (RS-232 scales speak their own protocol per manufacturer; out of scope until a customer needs it).
- Card reader / EFTPOS integration. Payment terminals are vendor-locked appliances with their own SDKs (Verifone, Ingenico, MPGS) — separate workstream.
- Multi-printer setups (kitchen + receipt + label) — single receipt printer per terminal in v1.
- Receipt logo printing. ESC/POS bitmap upload is finicky per model; defer until a customer asks.
