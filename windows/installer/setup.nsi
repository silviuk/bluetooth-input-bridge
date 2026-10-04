; NSIS Installer Script for Bluetooth Input Bridge (Galaxy S24 Ultra)
!include "MUI2.nsh"
!include "x64.nsh"

Name "Bluetooth Input Bridge (S24 Ultra)"
OutFile "../../bin/BluetoothInputBridge-Setup.exe"
InstallDir "$PROGRAMFILES64\BluetoothInputBridge"
InstallDirRegKey HKLM "Software\BluetoothInputBridge" "Install_Dir"
RequestExecutionLevel admin

; UI Settings
!define MUI_ABORTWARNING
!define MUI_ICON "../res/app.ico"
!define MUI_UNICON "../res/app.ico"

; Pages
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES

; Finish page with launch option
!define MUI_FINISHPAGE_RUN "$INSTDIR\BluetoothInputBridge.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Launch Bluetooth Input Bridge now"
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

Section "BluetoothInputBridge (required)" SecCore
  SectionIn RO

  SetOutPath "$INSTDIR"
  File "../../bin/BluetoothInputBridge.exe"
  File "../res/app.ico"

  ; Store installation folder
  WriteRegStr HKLM "Software\BluetoothInputBridge" "Install_Dir" "$INSTDIR"

  ; Create uninstaller
  WriteUninstaller "$INSTDIR\uninstall.exe"

  ; Add/Remove Programs Registry Keys
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\BluetoothInputBridge" "DisplayName" "Bluetooth Input Bridge (Galaxy S24 Ultra)"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\BluetoothInputBridge" "UninstallString" '"$INSTDIR\uninstall.exe"'
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\BluetoothInputBridge" "DisplayIcon" "$INSTDIR\app.ico"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\BluetoothInputBridge" "Publisher" "Google Antigravity"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\BluetoothInputBridge" "DisplayVersion" "1.0.0"
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\BluetoothInputBridge" "NoModify" 1
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\BluetoothInputBridge" "NoRepair" 1

  ; Start Menu Shortcuts
  CreateDirectory "$SMPROGRAMS\Bluetooth Input Bridge"
  CreateShortcut "$SMPROGRAMS\Bluetooth Input Bridge\Bluetooth Input Bridge.lnk" "$INSTDIR\BluetoothInputBridge.exe" "" "$INSTDIR\app.ico" 0
  CreateShortcut "$SMPROGRAMS\Bluetooth Input Bridge\Uninstall.lnk" "$INSTDIR\uninstall.exe" "" "$INSTDIR\uninstall.exe" 0

  ; Desktop Shortcut
  CreateShortcut "$DESKTOP\Bluetooth Input Bridge.lnk" "$INSTDIR\BluetoothInputBridge.exe" "" "$INSTDIR\app.ico" 0
SectionEnd

Section "Uninstall"
  DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\BluetoothInputBridge"
  DeleteRegKey HKLM "Software\BluetoothInputBridge"

  Delete "$INSTDIR\BluetoothInputBridge.exe"
  Delete "$INSTDIR\app.ico"
  Delete "$INSTDIR\uninstall.exe"

  Delete "$DESKTOP\Bluetooth Input Bridge.lnk"
  Delete "$SMPROGRAMS\Bluetooth Input Bridge\Bluetooth Input Bridge.lnk"
  Delete "$SMPROGRAMS\Bluetooth Input Bridge\Uninstall.lnk"
  RMDir "$SMPROGRAMS\Bluetooth Input Bridge"
  RMDir "$INSTDIR"
SectionEnd
