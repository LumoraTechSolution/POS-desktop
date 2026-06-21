import { contextBridge, ipcRenderer } from "electron";

/**
 * Preload for the activation window. Exposes a minimal, audited surface to
 * activation.html — no Node access leaks to the renderer.
 */
contextBridge.exposeInMainWorld("activation", {
  /** { machineName, shortCode, reason } for the initial screen state. */
  getInfo: () => ipcRenderer.invoke("activation:get-info"),
  /** Attempt activation; resolves on success, rejects with a user-facing message. */
  submit: (key: string, machineName: string) =>
    ipcRenderer.invoke("activation:submit", { key, machineName }),
  /** Quit the app from the activation screen. */
  quit: () => ipcRenderer.invoke("activation:quit"),
});
