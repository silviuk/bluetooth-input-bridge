#ifndef RESOURCE_H
#define RESOURCE_H

#define IDI_APP_ICON            101
#define IDR_TRAY_MENU           102

// Tray Menu Commands
#define IDM_TRAY_OPEN           201
#define IDM_TRAY_TOGGLE_CAPTURE 202
#define IDM_TRAY_DISCONNECT     203
#define IDM_TRAY_CONNECT        204
#define IDM_TRAY_HOME           205
#define IDM_TRAY_BACK           206
#define IDM_TRAY_RECENTS        207
#define IDM_TRAY_EXIT           208

// UI Control IDs
#define IDC_RADIO_SERVER        301
#define IDC_RADIO_CLIENT        302
#define IDC_COMBO_DEVICES       303
#define IDC_BTN_REFRESH         304
#define IDC_BTN_CONNECT         305
#define IDC_BTN_TOGGLE_CAPTURE  306
#define IDC_SLIDER_SENSITIVITY  307
#define IDC_LABEL_SENSITIVITY   308
#define IDC_STATUS_TEXT         309
#define IDC_BTN_MINIMIZE_TRAY   310
#define IDC_BTN_ACT_BACK        311
#define IDC_BTN_ACT_HOME        312
#define IDC_BTN_ACT_RECENTS     313
#define IDC_BTN_ACT_NOTIF       314
#define IDC_BTN_ACT_VOLUP       315
#define IDC_BTN_ACT_VOLDOWN     316
#define IDC_BTN_ACT_LOCK        317

#define WM_TRAYNOTIFY           (WM_APP + 10)

#endif // RESOURCE_H
