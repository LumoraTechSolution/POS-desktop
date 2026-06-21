# Lumora POS - Electron Desktop App Implementation Guide

## 📚 Documentation Overview

This step provides **complete guidance** for transforming your Lumora POS web app into a native desktop application using Electron.

### 📋 Documents Created

| Document | Purpose | Audience |
|----------|---------|----------|
| [step66_electron_desktop_app.md](step66_electron_desktop_app.md) | **Complete technical implementation** with all code, architecture, and 6-phase development plan | Developers implementing the feature |
| [step66_electron_quick_start.md](step66_electron_quick_start.md) | **5-minute setup** with automated scripts and quick manual setup | Anyone starting the project |
| [step66_electron_patterns_reference.md](step66_electron_patterns_reference.md) | **Common patterns** for POS-specific features (barcode scanning, receipt printing, offline mode) | Reference while implementing |
| [step66_electron_deployment.md](step66_electron_deployment.md) | **Distribution, troubleshooting, and security** for shipping production builds | Operations and QA |

---

## 🚀 Quick Start (5 minutes)

### Windows
```bash
cd frontend
setup-electron.bat
npm run dev
```

### macOS/Linux
```bash
cd frontend
bash setup-electron.sh
npm run dev
```

---

## 📊 Architecture

```
┌─────────────────────────────────────────┐
│  Electron App (Native Desktop)          │
│  ├─ Window Management                   │
│  ├─ Hardware APIs (IPC)                 │
│  └─ Auto-update & Offline Cache         │
└──────────────┬──────────────────────────┘
               │
       ┌───────┴────────┐
       │                │
┌──────▼──────┐  ┌──────▼──────┐
│  Next.js    │  │  IPC Bridge │
│  Frontend   │  │  • Printer  │
│  (Renderer) │  │  • Scanner  │
│             │  │  • Drawer   │
└──────┬──────┘  └─────────────┘
       │
       └─────────────────────────┐
                                 │
                    ┌────────────▼────────┐
                    │ Spring Boot API     │
                    │ (localhost:8081)    │
                    └─────────────────────┘
```

---

## 🎯 Implementation Phases

### Phase 1: Setup ✅
- Install Electron dependencies
- Create electron/ directory
- Configure package.json

**Time:** 15 minutes  
**Effort:** S

### Phase 2: Core Infrastructure ✅
- Main process (main.ts)
- Preload script (preload.ts)
- IPC handlers (ipc.ts)

**Time:** 30 minutes  
**Effort:** S

### Phase 3: Hardware Integration
- Thermal printer (ESC/POS)
- Barcode scanner (Serial)
- Cash drawer control

**Time:** 2-3 hours (1 per device)  
**Effort:** M

### Phase 4: Frontend Integration ✅
- useElectron hook
- Receipt component updates
- Terminal page integration

**Time:** 1 hour  
**Effort:** S

### Phase 5: Build Configuration ✅
- electron-builder setup
- Next.js export config
- Platform-specific settings

**Time:** 30 minutes  
**Effort:** S

### Phase 6: Distribution
- Build installers
- Code signing
- Auto-updater setup
- Release deployment

**Time:** 2-4 hours  
**Effort:** M

---

## 📝 Implementation Order

### Week 1: Foundation
1. Run setup script (15 min)
2. Start dev mode: `npm run dev` (5 min)
3. Test IPC in DevTools (15 min)
4. ✅ Phase 1-5 Complete

### Week 2: Printer Integration
1. Implement PrinterService (2 hours)
2. Connect hardware IPC (1 hour)
3. Update Receipt component (1 hour)
4. Test with real printer (1 hour)

### Week 3: Additional Hardware
1. Scanner integration (2 hours)
2. Cash drawer control (1 hour)
3. Offline sync queue (2 hours)

### Week 4: Polish & Release
1. Error handling (1 hour)
2. Build for all platforms (1 hour)
3. Setup auto-updater (2 hours)
4. Test distribution builds (2 hours)

---

## 🔑 Key Features

### Out of the Box
- ✅ Cross-platform (Windows, macOS, Linux)
- ✅ IPC bridge for hardware communication
- ✅ Window controls (minimize, maximize, close)
- ✅ File selection dialogs
- ✅ Settings persistence
- ✅ Auto-update ready

### With Hardware Integration
- ✅ Thermal receipt printer (ESC/POS)
- ✅ Barcode scanner (Serial/USB)
- ✅ Cash drawer control
- ✅ Real-time barcode streaming
- ✅ Offline transaction queue

### Production Ready
- ✅ Code signing support
- ✅ Auto-updater (Squirrel/generic)
- ✅ Error logging
- ✅ Performance monitoring
- ✅ Security hardening

---

## 🛠️ Setup Files Created

### Setup Scripts
- `frontend/setup-electron.bat` - Windows automated setup
- `frontend/setup-electron.sh` - macOS/Linux automated setup

### Core Files (Ready to Create)
- `electron/main.ts` - Main process entry
- `electron/preload.ts` - IPC bridge
- `electron/ipc.ts` - IPC handlers
- `src/hooks/useElectron.ts` - React hook
- `electron-builder.config.js` - Build config

### Reference Files (Already Created)
- `documentation/step66_electron_desktop_app.md` - Full implementation
- `documentation/step66_electron_quick_start.md` - Quick start
- `documentation/step66_electron_patterns_reference.md` - Common patterns
- `documentation/step66_electron_deployment.md` - Distribution guide

---

## 💡 Quick Reference

### Start Development
```bash
cd frontend
npm run dev
```

### Run Tests
```bash
npm run typecheck
npm run build  # Test Next.js build
npm run electron-dev-build  # Test Electron build
```

### Build for Distribution
```bash
npm run dist
# Creates: dist/Lumora POS-1.0.0.exe (Windows)
#          dist/Lumora POS-1.0.0.dmg (macOS)
#          dist/Lumora POS-1.0.0.AppImage (Linux)
```

### Common IPC Calls
```javascript
// In components
const { printReceipt, openCashDrawer, isElectron } = useElectron();

await printReceipt('<html>Receipt...</html>');
await openCashDrawer();
```

---

## 📖 Document Navigation

### Starting Development?
→ **Read:** [step66_electron_quick_start.md](step66_electron_quick_start.md)

### Need Full Implementation Details?
→ **Read:** [step66_electron_desktop_app.md](step66_electron_desktop_app.md)

### Building Specific Features?
→ **Read:** [step66_electron_patterns_reference.md](step66_electron_patterns_reference.md)

### Ready to Ship?
→ **Read:** [step66_electron_deployment.md](step66_electron_deployment.md)

---

## ✅ Before You Start

### Prerequisites
- [ ] Node.js 20+
- [ ] npm or yarn
- [ ] Java 17+ (for backend)
- [ ] PostgreSQL running
- [ ] Backend accessible at localhost:8081

### System Requirements
- Windows 10+ / macOS 10.13+ / Ubuntu 18.04+
- 2GB RAM minimum
- 500MB disk space

---

## 🎓 Key Concepts

### IPC (Inter-Process Communication)
The secure bridge between Electron's main process (native) and renderer (web app). All hardware access goes through IPC.

### Context Isolation
Security feature that prevents the renderer from directly accessing Node.js. All communication goes through contextBridge.

### Preload Script
JavaScript that runs in the renderer's global scope before the app loads. Exposes safe IPC methods via `window.electron`.

### ESC/POS
The standard printer command set for thermal printers. We build command buffers and send via SerialPort.

### Auto-Update
Electron apps check for new versions and install in the background. Users are prompted to restart.

---

## 🚨 Common Pitfalls

1. **Forgetting Next.js export** → App shows blank screen
   - Fix: Set `output: 'export'` in next.config.mjs

2. **Wrong preload path** → `window.electron` is undefined
   - Fix: Verify path in BrowserWindow webPreferences

3. **Printer not detected** → Serial port returns empty
   - Fix: Install printer drivers; check device manager

4. **Port already in use** → Can't start dev server
   - Fix: Change port or kill existing process

5. **Build succeeds but app won't start** → Check files array
   - Fix: Verify all required files are included in electron-builder config

---

## 📞 Support Resources

### Official Docs
- [Electron](https://www.electronjs.org/docs)
- [electron-builder](https://www.electron.build/)
- [serialport](https://serialport.io/docs)

### POS/Retail
- [ESC/POS Specification](https://github.com/receipt-print-hq/receipt-print-hq)
- [Star Micronics](https://www.star-m.jp/)
- [Zebra Printer Drivers](https://www.zebra.com/us/en/support-support/downloads.html)

### Lumora Internal
- Backend API: [backend/README.md](../backend/README.md)
- Frontend Setup: [frontend/README.md](../frontend/README.md)

---

## 📅 Timeline Estimate

| Phase | Effort | Timeline |
|-------|--------|----------|
| Setup (Phase 1-5) | **S** | 2-3 hours |
| Printer Integration | **M** | 3-4 hours |
| Scanner + Drawer | **M** | 4-5 hours |
| Offline + Settings | **M** | 3-4 hours |
| Build & Distribution | **M** | 2-3 hours |
| **Total** | **~M** | **2 weeks** |

---

## Next Steps

1. **Choose your path:**
   - 🏃 Fast Track: Run setup script → start dev → integrate one hardware at a time
   - 🚶 Thorough: Read full guide first → understand architecture → implement systematically

2. **Get setup:**
   ```bash
   cd frontend
   setup-electron.bat  # Windows
   # OR
   bash setup-electron.sh  # macOS/Linux
   ```

3. **Start dev server:**
   ```bash
   npm run dev
   ```

4. **Reference documentation as needed:**
   - Hardware issues → [patterns_reference.md](step66_electron_patterns_reference.md)
   - Distribution → [deployment.md](step66_electron_deployment.md)
   - Architecture → [desktop_app.md](step66_electron_desktop_app.md)

---

## 🎉 Success Criteria

You'll know you've successfully set up Electron when:

- [ ] Running `npm run dev` opens an Electron window with the POS app
- [ ] DevTools opens automatically (you see the app)
- [ ] Running `window.electron.hardware.getPrinters()` in console works
- [ ] `npm run dist` creates installer files in `dist/` directory
- [ ] The app works offline and syncs sales when online
- [ ] Receipt printing works with your thermal printer

---

## 📞 Questions?

Refer to the troubleshooting section in [step66_electron_deployment.md](step66_electron_deployment.md) or check the Electron docs.

**Happy coding! 🚀**
