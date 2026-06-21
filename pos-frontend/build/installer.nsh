; Custom NSIS hooks for Lumora POS — provisions the bundled PostgreSQL
; instance and registers it as a Windows service. The heavy lifting lives in
; install-postgres.ps1 / uninstall-postgres.ps1 which are extracted by
; electron-builder under $INSTDIR\resources\.

!macro customInstall
  DetailPrint "Setting up bundled PostgreSQL service..."
  nsExec::ExecToLog 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\resources\install-postgres.ps1" -InstallDir "$INSTDIR"'
  Pop $0
  ${If} $0 != 0
    MessageBox MB_ICONSTOP "PostgreSQL setup failed (exit code $0).$\r$\nCheck $APPDATA\Lumora POS\logs\install.log for details.$\r$\nInstallation will be rolled back."
    Abort
  ${EndIf}
  DetailPrint "PostgreSQL service is running."
!macroend

!macro customUnInstall
  ; Default: keep user data. Ask if they want to wipe it.
  MessageBox MB_YESNO|MB_ICONQUESTION "Delete the StoreX database (all sales, products, customers, settings)?$\r$\n$\r$\nChoose 'No' to keep your data and reuse it on a future reinstall." IDYES wipe IDNO keep
  wipe:
    StrCpy $R0 "no"
    Goto run
  keep:
    StrCpy $R0 "yes"
  run:
  DetailPrint "Stopping PostgreSQL service..."
  nsExec::ExecToLog 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\resources\uninstall-postgres.ps1" -InstallDir "$INSTDIR" -KeepData "$R0"'
  Pop $0
  ; Don't abort uninstall on partial failure — just log.
!macroend
