; Custom NSIS hooks for Lumora POS — provisions the bundled PostgreSQL
; instance and registers it as a Windows service. The heavy lifting lives in
; install-postgres.ps1 / uninstall-postgres.ps1 which are extracted by
; electron-builder under $INSTDIR\resources\.

!macro customInstall
  DetailPrint "Setting up bundled PostgreSQL service..."
  nsExec::ExecToLog 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\resources\install-postgres.ps1" -InstallDir "$INSTDIR"'
  Pop $0
  ${If} $0 != 0
    MessageBox MB_ICONSTOP "PostgreSQL setup failed (exit code $0).$\r$\nCheck C:\ProgramData\Lumora POS\logs\install.log for details.$\r$\nInstallation will be rolled back."
    Abort
  ${EndIf}
  DetailPrint "PostgreSQL service is running."
!macroend

!macro customUnInstall
  ; NOTE: by the time this macro runs, electron-builder's uninstaller has ALREADY
  ; done `RMDir /r $INSTDIR`, so the bundled uninstall-postgres.ps1 and pg_ctl.exe
  ; are gone — we cannot call them here. Do the teardown self-contained with sc.exe
  ; (always present on Windows). The database lives in C:\ProgramData\Lumora POS\,
  ; outside $INSTDIR, so it survives unless we explicitly delete it below.
  ;
  ; Skip everything on an in-place upgrade (${isUpdated}): the new version's
  ; install-postgres.ps1 reconciles the existing service + cluster, so an upgrade
  ; must never prompt or tear anything down.
  ${ifNot} ${isUpdated}
    DetailPrint "Stopping PostgreSQL service..."
    nsExec::ExecToLog 'sc.exe stop LumoraPOSPostgres'
    Pop $0
    Sleep 2000

    ; Default to KEEP. /SD IDNO makes a silent uninstall keep data too.
    MessageBox MB_YESNO|MB_ICONQUESTION "Delete the StoreX database (all sales, products, customers, settings)?$\r$\n$\r$\nChoose 'No' to keep your data and reuse it on a future reinstall." /SD IDNO IDYES un_wipe IDNO un_keep

    un_wipe:
      DetailPrint "Removing PostgreSQL service and database..."
      nsExec::ExecToLog 'sc.exe delete LumoraPOSPostgres'
      Pop $0
      SetShellVarContext all   ; $APPDATA -> C:\ProgramData
      RMDir /r "$APPDATA\Lumora POS\pgdata"
      Delete "$APPDATA\Lumora POS\db.properties"
      Goto un_done

    un_keep:
      ; Keep the data, but still unregister the service so it isn't left pointing at
      ; the now-deleted binaries. The cluster + db.properties stay for a reinstall.
      DetailPrint "Removing PostgreSQL service (keeping database)..."
      nsExec::ExecToLog 'sc.exe delete LumoraPOSPostgres'
      Pop $0

    un_done:
  ${endIf}
!macroend
