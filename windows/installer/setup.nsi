; NSIS Installer Script for Lapdroid
!include "MUI2.nsh"
!include "x64.nsh"

Name "Lapdroid"
OutFile "../../bin/Lapdroid-Setup.exe"
InstallDir "$PROGRAMFILES64\Lapdroid"
InstallDirRegKey HKLM "Software\Lapdroid" "Install_Dir"
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
!define MUI_FINISHPAGE_RUN "$INSTDIR\Lapdroid.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Launch Lapdroid now"
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

Section "Lapdroid (required)" SecCore
  SectionIn RO

  SetOutPath "$INSTDIR"
  File "../../bin/Lapdroid.exe"
  File "../res/app.ico"

  ; Store installation folder
  WriteRegStr HKLM "Software\Lapdroid" "Install_Dir" "$INSTDIR"

  ; Create uninstaller
  WriteUninstaller "$INSTDIR\uninstall.exe"

  ; Add/Remove Programs Registry Keys
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Lapdroid" "DisplayName" "Lapdroid - Bluetooth Keyboard & Touchpad"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Lapdroid" "UninstallString" '"$INSTDIR\uninstall.exe"'
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Lapdroid" "DisplayIcon" "$INSTDIR\app.ico"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Lapdroid" "Publisher" "Lapdroid Open Source"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Lapdroid" "DisplayVersion" "0.2.0"
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Lapdroid" "NoModify" 1
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Lapdroid" "NoRepair" 1

  ; Start Menu Shortcuts
  CreateDirectory "$SMPROGRAMS\Lapdroid"
  CreateShortcut "$SMPROGRAMS\Lapdroid\Lapdroid.lnk" "$INSTDIR\Lapdroid.exe" "" "$INSTDIR\app.ico" 0
  CreateShortcut "$SMPROGRAMS\Lapdroid\Uninstall.lnk" "$INSTDIR\uninstall.exe" "" "$INSTDIR\uninstall.exe" 0

  ; Desktop Shortcut
  CreateShortcut "$DESKTOP\Lapdroid.lnk" "$INSTDIR\Lapdroid.exe" "" "$INSTDIR\app.ico" 0
SectionEnd

Section "Uninstall"
  DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Lapdroid"
  DeleteRegKey HKLM "Software\Lapdroid"

  Delete "$INSTDIR\Lapdroid.exe"
  Delete "$INSTDIR\app.ico"
  Delete "$INSTDIR\uninstall.exe"

  Delete "$DESKTOP\Lapdroid.lnk"
  Delete "$SMPROGRAMS\Lapdroid\Lapdroid.lnk"
  Delete "$SMPROGRAMS\Lapdroid\Uninstall.lnk"
  RMDir "$SMPROGRAMS\Lapdroid"
  RMDir "$INSTDIR"
SectionEnd
