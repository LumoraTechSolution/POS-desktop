# Electron Desktop App - Deployment & Troubleshooting

## 🚀 Distribution

### Windows Distribution

#### 1. Code Signing (Optional but Recommended)

To distribute without SmartScreen warnings:

```javascript
// electron-builder.config.js
module.exports = {
  win: {
    target: ['nsis', 'portable'],
    certificateFile: process.env.WIN_CSC_LINK,
    certificatePassword: process.env.WIN_CSC_KEY_PASSWORD,
    signingHashAlgorithms: ['sha256'],
  },
};
```

Get a certificate from:
- Sectigo: ~$200/year
- DigiCert: ~$300/year
- Code signing services: StartSSL, Comodo

#### 2. NSIS Installer Configuration

```javascript
// electron-builder.config.js
nsis: {
  oneClick: false,
  allowToChangeInstallationDirectory: true,
  createDesktopShortcut: true,
  createStartMenuShortcut: true,
  shortcutName: 'Lumora POS',
  // Custom installer script (optional)
  installerScript: 'installer.nsi',
}
```

#### 3. Build

```bash
npm run dist
```

Output: `dist/Lumora POS 1.0.0.exe` (installer) + `.exe` (portable)

#### 4. Distribution

- Upload to S3 / hosted website
- Setup auto-updater (see below)
- Share download link

### macOS Distribution

#### 1. Code Signing

```javascript
// electron-builder.config.js
mac: {
  target: ['dmg', 'zip'],
  category: 'public.app-category.productivity',
  identity: process.env.MAC_IDENTITY,
  certificateFile: process.env.MAC_CSC_LINK,
  certificatePassword: process.env.MAC_CSC_KEY_PASSWORD,
}
```

Get Apple Developer account ($99/year)

#### 2. Build & Notarize

```bash
# Build
npm run build

# Notarize (Apple requirement for Catalina+)
xcrun altool --notarize-app \
  -f dist/Lumora\ POS-1.0.0.dmg \
  -t macOS \
  --primary-bundle-id com.lumora.pos \
  --username YOUR_APPLE_ID \
  --password YOUR_APP_PASSWORD
```

#### 3. Staple Notarization Ticket

```bash
xcrun stapler staple dist/Lumora\ POS-1.0.0.app
```

#### 4. Create DMG

```bash
npm run dist
```

### Linux Distribution

#### 1. Build

```bash
npm run dist
```

Output: `dist/Lumora POS-1.0.0.AppImage` + `.deb`

#### 2. Publish to Repo

```bash
# Add PPA (Ubuntu)
# OR
# Upload to Flathub
```

#### 3. Create Installation Script

`install.sh`:
```bash
#!/bin/bash
wget https://releases.lumora.com/lumora-pos-latest.AppImage
chmod +x lumora-pos-latest.AppImage
./lumora-pos-latest.AppImage
```

---

## 🔄 Auto-Update Setup

### 1. Backend Setup

Create a GitHub Release or self-hosted releases:

```
https://your-server.com/releases/
├── latest.yml          # Version manifest
├── lumora-pos-1.0.0.exe.blockmap
├── lumora-pos-1.0.0.exe
└── lumora-pos-1.0.0-full.nupkg (Windows Delta)
```

### 2. Generate Release Info

**`latest.yml`** (Windows):
```yaml
version: 1.0.0
files:
  - url: lumora-pos-1.0.0.exe.blockmap
    sha512: abc123...
    size: 12345
  - url: lumora-pos-1.0.0.exe
    sha512: def456...
    size: 67890
path: lumora-pos-1.0.0.exe
sha512: def456...
releaseDate: '2024-01-15T10:00:00Z'
```

Generate with:
```bash
npm run dist -- --publish=always
```

### 3. Configure electron-builder

```javascript
// electron-builder.config.js
publish: [
  {
    provider: 'generic',
    url: 'https://your-server.com/releases/',
  },
],
```

### 4. Enable in App

```typescript
// electron/main.ts
import { autoUpdater } from 'electron-updater';

if (!isDev) {
  autoUpdater.checkForUpdatesAndNotify();
}

autoUpdater.on('update-available', () => {
  mainWindow?.webContents.send('update:available');
});

autoUpdater.on('update-downloaded', () => {
  mainWindow?.webContents.send('update:downloaded');
});

ipcMain.on('app:install-update', () => {
  autoUpdater.quitAndInstall();
});
```

### 5. UI Component

```typescript
import { useEffect, useState } from 'react';

export function UpdateCheck() {
  const [updateAvailable, setUpdateAvailable] = useState(false);
  const [updateReady, setUpdateReady] = useState(false);

  useEffect(() => {
    if (!window.electron) return;

    (window as any).electron.ipcRenderer?.on('update:available', () => {
      setUpdateAvailable(true);
    });

    (window as any).electron.ipcRenderer?.on('update:downloaded', () => {
      setUpdateReady(true);
    });
  }, []);

  if (!updateReady) return null;

  return (
    <div className="fixed bottom-4 right-4 bg-blue-600 text-white p-4 rounded-lg shadow-lg">
      <p className="font-bold">Update Ready</p>
      <p className="text-sm mb-3">Restart to install the latest version</p>
      <button
        onClick={() => (window as any).electron.ipcRenderer?.send('app:install-update')}
        className="bg-white text-blue-600 px-4 py-2 rounded font-bold"
      >
        Restart Now
      </button>
    </div>
  );
}
```

---

## 🐛 Troubleshooting

### Common Issues

#### White Screen on Startup

**Problem:** App window opens but shows blank white screen

**Solutions:**
1. Check if Next.js dev server is running
   ```bash
   curl http://localhost:3000
   ```

2. Check `startUrl` in `main.ts` matches dev port

3. Check preload script path is correct

4. Enable DevTools in main.ts:
   ```typescript
   if (isDev) {
     mainWindow.webContents.openDevTools();
   }
   ```

5. Check console for CORS errors - ensure backend is running

#### "Cannot find module" Errors

**Problem:** `Cannot find module 'serialport'`

**Solutions:**
```bash
# Rebuild native modules for Electron
npm install --save-dev @electron-builder/squirrel-windows

# Or rebuild
npm rebuild serialport --build-from-source
```

#### IPC Not Working

**Problem:** `window.electron` is undefined in components

**Solutions:**
1. Check preload script is specified in BrowserWindow:
   ```typescript
   webPreferences: {
     preload: path.join(__dirname, 'preload.js'),
     contextIsolation: true,
     nodeIntegration: false,
   }
   ```

2. Check preload TypeScript compiles to JS:
   ```bash
   npx tsc electron/preload.ts --outDir out
   ```

3. Verify context bridge exports in preload:
   ```typescript
   contextBridge.exposeInMainWorld('electron', electronAPI);
   ```

#### Port Already in Use

**Problem:** `Port 3000 already in use` when running `npm run dev`

**Solutions:**
```bash
# Option 1: Use different port
"electron-dev": "wait-on http://localhost:3001 && NEXT_PUBLIC_PORT=3001 electron ."

# Option 2: Kill process on port
# Windows
netstat -ano | findstr :3000
taskkill /PID <PID> /F

# macOS/Linux
lsof -ti:3000 | xargs kill -9
```

#### Printer Not Detected

**Problem:** `SerialPort.list()` returns empty

**Solutions:**
1. Check device manager / system preferences for connected devices

2. Install required drivers:
   - Star Micronics: https://www.star-m.jp/products/s_print03.html
   - Zebra: https://www.zebra.com/us/en/support-support/downloads.html

3. Test with:
   ```bash
   npm install -g serialport
   serialport-list
   ```

4. On Linux, may need udev rules:
   ```bash
   sudo usermod -a -G dialout $USER
   sudo usermod -a -G tty $USER
   ```

#### Build Fails

**Problem:** `npm run dist` fails with cryptic error

**Solutions:**
```bash
# Clean everything
rm -rf node_modules out dist .next
npm install

# Build Next.js first
npm run next-build

# Then Electron
npm run electron-build
```

#### App Won't Start After Build

**Problem:** Packaged app fails to launch

**Solutions:**
1. Test with:
   ```bash
   npm run electron-dev-build
   ./out/Lumora POS.exe (Windows)
   ```

2. Check `package.json` main field:
   ```json
   {
     "main": "electron/main.js",
     "homepage": "./"
   }
   ```

3. Verify all files in `files` section of electron-builder config exist

4. Check DevTools in built app:
   ```bash
   # Open DevTools in built app to see errors
   ```

#### API Calls Return 403/CORS

**Problem:** Frontend can't reach backend during development

**Solutions:**
1. Ensure backend is running:
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

2. Check CORS config in `application.yml`:
   ```yaml
   server.cors.allowed-origins: "http://localhost:3000,http://127.0.0.1:3000"
   ```

3. In Electron, CORS rules are different. Add:
   ```typescript
   // electron/main.ts
   mainWindow.webContents.session.webRequest.onBeforeSendHeaders(
     (details, callback) => {
       callback({ requestHeaders: { ...details.requestHeaders } });
     }
   );
   ```

#### Settings Not Persisting

**Problem:** App settings lost after restart

**Solutions:**
1. Install electron-store:
   ```bash
   npm install electron-store
   ```

2. Use in main process:
   ```typescript
   import Store from 'electron-store';
   const store = new Store();
   ipcMain.handle('settings:get', (e, key) => store.get(key));
   ```

3. Save to userData path:
   ```typescript
   const appData = app.getPath('userData');
   // Settings saved here automatically
   ```

#### Sign Certificate Issues on macOS

**Problem:** `Certificate not found` when signing app

**Solutions:**
```bash
# List available certificates
security find-identity -v -p codesigning

# Use specific certificate
export MAC_IDENTITY="Developer ID Application: Your Name (ABC123XYZ)"
npm run dist
```

---

## 📊 Performance Optimization

### 1. Bundle Size

```bash
# Analyze bundle
npm install --save-dev webpack-bundle-analyzer

# Check
npm run build -- --analyze
```

### 2. Native Module Optimization

```javascript
// electron-builder.config.js
files: [
  'out/**/*',
  'electron/**/*',
  'node_modules/**/package.json',
  'node_modules/serialport/**',  // Include only needed
],
```

### 3. Memory Usage

```typescript
// electron/main.ts
// Enable garbage collection
const maxMemory = 512 * 1024 * 1024; // 512MB
mainWindow.webContents.session.setMaxListeners(0);

// Monitor
setInterval(() => {
  const mem = process.getProcessMemoryInfo();
  console.log('Memory:', mem.heapUsed / 1024 / 1024, 'MB');
}, 5000);
```

---

## 🔐 Security Hardening

### 1. Content Security Policy

```typescript
// electron/main.ts
mainWindow.webContents.session.webRequest.onHeadersReceived(
  (details, callback) => {
    callback({
      responseHeaders: {
        ...details.responseHeaders,
        'Content-Security-Policy': [
          "default-src 'self'",
          "script-src 'self'",
          "style-src 'self' 'unsafe-inline'",
        ].join('; '),
      },
    });
  }
);
```

### 2. Disable Dangerous Features

```typescript
// electron/main.ts
mainWindow.webContents.on('before-input-event', (event, input) => {
  // Disable DevTools in production
  if (!isDev && input.control && input.shift && input.key.toLowerCase() === 'i') {
    event.preventDefault();
  }
});
```

### 3. Verify Backend TLS

```typescript
// In Electron, validate SSL certificates
const protocol = isDev ? 'http' : 'https';
const backendUrl = `${protocol}://backend.lumora.com/api/v1`;

// Reject self-signed certs in production
app.on('certificate-error', (event, webContents, url, error, certificate, callback) => {
  if (isDev) {
    callback(true); // Allow in dev
  } else {
    callback(false); // Reject in prod
  }
});
```

---

## 📝 Release Checklist

- [ ] Version bumped in `package.json`
- [ ] CHANGELOG.md updated
- [ ] All tests passing
- [ ] TypeScript compiles clean (`tsc --noEmit`)
- [ ] Build succeeds locally (`npm run dist`)
- [ ] Tested on Windows/macOS/Linux
- [ ] Hardware integration tested
- [ ] Offline mode tested
- [ ] Auto-updater tested
- [ ] Screenshots/docs updated
- [ ] Release notes written
- [ ] Upload to releases server
- [ ] Test auto-update works

---

## 🎓 Best Practices

| Do | Don't |
|----|-------|
| Use context isolation | Don't use `nodeIntegration: true` |
| Sign releases | Don't distribute unsigned binaries |
| Version all releases | Don't use `latest` for production |
| Test all platforms | Don't assume Windows-only works everywhere |
| Use IPC for sensitive ops | Don't expose Node APIs directly |
| Log errors | Don't silently fail |
| Update regularly | Don't use outdated Electron |
| Cache hardware status | Don't poll every render |

---

## Resources

- [Electron Security Checklist](https://www.electronjs.org/docs/tutorial/security)
- [electron-builder Documentation](https://www.electron.build/)
- [electron-updater](https://github.com/electron-userland/electron-builder/wiki/Auto-Update)
- [Notarization Requirements](https://developer.apple.com/documentation/xcode/notarizing_macos_software_before_distribution)

---

## Support

For issues, check:
1. DevTools console (`F12` in dev mode)
2. Application logs (see [step66_electron_patterns_reference.md](step66_electron_patterns_reference.md#7-error-logging))
3. GitHub Issues or Community Discord
