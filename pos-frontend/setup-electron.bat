@echo off
REM Quick-start script for Electron desktop app setup
REM Run from: cd frontend && setup-electron.bat

echo.
echo =====================================
echo Lumora POS - Electron Desktop Setup
echo =====================================
echo.

REM Step 1: Install dependencies
echo [1/5] Installing Electron dependencies...
call npm install --save-dev ^
  electron ^
  electron-builder ^
  electron-is-dev ^
  electron-squirrel-startup ^
  concurrently ^
  wait-on ^
  @types/node ^
  typescript

call npm install ^
  serialport ^
  usb

if %ERRORLEVEL% neq 0 (
    echo ERROR: Failed to install dependencies
    exit /b 1
)

REM Step 2: Create directory structure
echo.
echo [2/5] Creating Electron directory structure...
if not exist "electron" mkdir electron
if not exist "electron\hardware" mkdir electron\hardware
if not exist "electron\utils" mkdir electron\utils
if not exist "src\hooks" mkdir src\hooks

REM Step 3: Create main.ts
echo.
echo [3/5] Creating Electron main process...
(
echo import { app, BrowserWindow, ipcMain, Menu } from 'electron';
echo import path from 'path';
echo import isDev from 'electron-is-dev';
echo import { setupIPC } from './ipc';
echo.
echo let mainWindow: BrowserWindow ^| null = null;
echo.
echo const createWindow = ^(^) =^> {
echo   mainWindow = new BrowserWindow^(^{
echo     width: 1920,
echo     height: 1080,
echo     minWidth: 1280,
echo     minHeight: 720,
echo     webPreferences: {
echo       preload: path.join^(__dirname, 'preload.js'^),
echo       nodeIntegration: false,
echo       contextIsolation: true,
echo       enableRemoteModule: false,
echo       sandbox: true,
echo     },
echo     icon: path.join^(__dirname, '../public/icon.png'^),
echo   }^);
echo.
echo   const startUrl = isDev 
echo     ? 'http://localhost:3000' 
echo     : `file://$^{path.join^(__dirname, '../out/index.html'^)^}`;
echo.
echo   mainWindow.loadURL^(startUrl^);
echo.
echo   if ^(isDev^) {
echo     mainWindow.webContents.openDevTools^(^);
echo   }
echo.
echo   mainWindow.on^('closed', ^(^) =^> {
echo     mainWindow = null;
echo   }^);
echo };
echo.
echo app.on^('ready', createWindow^);
echo.
echo app.on^('window-all-closed', ^(^) =^> {
echo   if ^(process.platform !== 'darwin'^) {
echo     app.quit^(^);
echo   }
echo }^);
echo.
echo app.on^('activate', ^(^) =^> {
echo   if ^(mainWindow === null^) {
echo     createWindow^(^);
echo   }
echo }^);
echo.
echo setupIPC^(mainWindow^);
) > electron\main.ts

REM Step 4: Create preload.ts
echo.
echo [4/5] Creating Preload script...
(
echo import { contextBridge, ipcRenderer } from 'electron';
echo.
echo export interface ElectronAPI {
echo   hardware: {
echo     getPrinters: ^(^) =^> Promise^<string[]^>;
echo     printReceipt: ^(data: string^) =^> Promise^<boolean^>;
echo     openCashDrawer: ^(^) =^> Promise^<boolean^>;
echo     scanBarcode: ^(^) =^> Promise^<string^>;
echo   };
echo   file: {
echo     selectFile: ^(filters: any[]^) =^> Promise^<string^>;
echo     exportData: ^(path: string, data: string^) =^> Promise^<boolean^>;
echo   };
echo   system: {
echo     minimize: ^(^) =^> void;
echo     maximize: ^(^) =^> void;
echo     close: ^(^) =^> void;
echo     isMaximized: ^(^) =^> Promise^<boolean^>;
echo   };
echo }
echo.
echo const electronAPI: ElectronAPI = {
echo   hardware: {
echo     getPrinters: ^(^) =^> ipcRenderer.invoke^('hardware:get-printers'^),
echo     printReceipt: ^(data^) =^> ipcRenderer.invoke^('hardware:print-receipt', data^),
echo     openCashDrawer: ^(^) =^> ipcRenderer.invoke^('hardware:open-drawer'^),
echo     scanBarcode: ^(^) =^> ipcRenderer.invoke^('hardware:scan-barcode'^),
echo   },
echo   file: {
echo     selectFile: ^(filters^) =^> ipcRenderer.invoke^('file:select-file', filters^),
echo     exportData: ^(path, data^) =^> ipcRenderer.invoke^('file:export-data', { path, data }^),
echo   },
echo   system: {
echo     minimize: ^(^) =^> ipcRenderer.send^('system:minimize'^),
echo     maximize: ^(^) =^> ipcRenderer.send^('system:maximize'^),
echo     close: ^(^) =^> ipcRenderer.send^('system:close'^),
echo     isMaximized: ^(^) =^> ipcRenderer.invoke^('system:is-maximized'^),
echo   },
echo };
echo.
echo contextBridge.exposeInMainWorld^('electron', electronAPI^);
) > electron\preload.ts

REM Step 5: Create IPC setup
echo.
echo [5/5] Creating IPC setup...
(
echo import { ipcMain, BrowserWindow, dialog } from 'electron';
echo import fs from 'fs';
echo.
echo export const setupIPC = ^(mainWindow: BrowserWindow ^| null^) =^> {
echo   ipcMain.handle^('hardware:get-printers', async ^(^) =^> {
echo     return [];
echo   }^);
echo.
echo   ipcMain.handle^('hardware:print-receipt', async ^(event, receiptData^) =^> {
echo     try {
echo       console.log^('Print receipt:', receiptData^);
echo       return true;
echo     } catch ^(error^) {
echo       console.error^('Print error:', error^);
echo       return false;
echo     }
echo   }^);
echo.
echo   ipcMain.handle^('hardware:open-drawer', async ^(^) =^> {
echo     try {
echo       console.log^('Opening cash drawer'^);
echo       return true;
echo     } catch ^(error^) {
echo       console.error^('Drawer error:', error^);
echo       return false;
echo     }
echo   }^);
echo.
echo   ipcMain.handle^('hardware:scan-barcode', async ^(^) =^> {
echo     return '';
echo   }^);
echo };
) > electron\ipc.ts

echo.
echo =====================================
echo Setup Complete!
echo =====================================
echo.
echo Next steps:
echo 1. Update package.json with these scripts:
echo    "dev": "concurrently \"npm run next-dev\" \"npm run electron-dev\""
echo    "next-dev": "next dev -p 3000"
echo    "electron-dev": "wait-on http://localhost:3000 && electron ."
echo    "build": "npm run next-build && npm run electron-build"
echo    "next-build": "next build && next export"
echo    "electron-build": "npm run next-build && electron-builder"
echo.
echo 2. Update next.config.mjs:
echo    const nextConfig = {
echo      output: 'export',
echo      distDir: 'out',
echo      images: { unoptimized: true },
echo    };
echo.
echo 3. Run: npm run dev
echo.
echo Done! Happy coding!
echo.
pause
