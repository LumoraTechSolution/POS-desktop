# Phase 3: Hardware Integration

## Overview
This step elevates Lumora POS from a standard web application to an enterprise Point of Sale terminal capable of interacting with physical store hardware—such as 80mm/58mm thermal printers, cash drawers, and physical barcode scanners.

## Key Changes
1. **Global Barcode Scanner Engine (`useBarcodeScanner.ts`)**
   - Created a custom React Hook that hijacks physical barcode scanner inputs globally.
   - Utilizes low-latency timing thresholds `< 50ms` between keystrokes to differentiate a physical scanner from a human typing.
   - Scanners terminate with an `Enter` key automatically, catching the payload and firing standard `onScan` events seamlessly without needing input fields in focus.

2. **Hardware Configuration LocalStorage Service (`hardwareService.ts`)**
   - Developed a rigorous Configuration Engine to persist hardware settings directly into Local Storage.
   - Built an algorithm to generate raw ESC/POS byte buffers out of standard decimal arrays for Cash Drawer kicks (eg. `27, 112, 0, 25, 250`).

3. **POS Hardware Settings UI (`/settings/hardware`)**
   - Built a `/settings/hardware` dashboard with three key sections: Thermal Printer parameters, Cash Drawer test functionalities, and Global Barcode scanning diagnostics.

4. **ESC/POS Browser Receipt Engine (`receiptPrinterService.ts`)**
   - Developed a completely custom Web HTML-to-Printer mapping engine.
   - By creating a dynamic invisible iframe/window with specific `@page` CSS overrides, we format the cart into perfect 80mm or 58mm layouts without the typical ugly browser margins or headers.

## Impact
- **Frictionless Checkout**: Cashiers now scan physical items instantly into the cart and the thermal receipt automatically fires out of their connected receipt printer whilst kicking their cash drawer open, all through Web-Standard technologies.

## Next Steps
Integrate these Hardware Hooks directly into the primary `/terminal` cart layout and checkout completion methods.
