#include "bluetooth_manager.h"
#include "protocol.h"
#include "logger.h"
#include <sstream>
#include <iomanip>
#include <iostream>

// Standard SPP UUID {00001101-0000-1000-8000-00805F9B34FB}
static const GUID BT_SerialPortServiceClass_UUID = {
    0x00001101, 0x0000, 0x1000, { 0x80, 0x00, 0x00, 0x80, 0x5F, 0x9B, 0x34, 0xFB }
};

std::wstring FormatBthAddress(BTH_ADDR address) {
    std::wstringstream ss;
    for (int i = 5; i >= 0; --i) {
        uint8_t byte = (uint8_t)((address >> (i * 8)) & 0xFF);
        ss << std::hex << std::uppercase << std::setw(2) << std::setfill(L'0') << (int)byte;
        if (i > 0) ss << L":";
    }
    return ss.str();
}

std::wstring FormatWsaError(int err) {
    switch (err) {
        case 10049: return L"WSAEADDRNOTAVAIL (Cannot assign requested address)";
        case 10050: return L"WSAENETDOWN (Network is down)";
        case 10051: return L"WSAENETUNREACH (Network unreachable)";
        case 10054: return L"WSAECONNRESET (Connection reset by peer)";
        case 10060: return L"WSAETIMEDOUT (Connection timed out - phone unreachable)";
        case 10061: return L"WSAECONNREFUSED (Connection refused - app not listening on phone)";
        case 10065: return L"WSAEHOSTUNREACH (No route to host)";
        default:    return L"Error " + std::to_wstring(err);
    }
}

BluetoothManager::BluetoothManager()
    : m_serverSocket(INVALID_SOCKET)
    , m_clientSocket(INVALID_SOCKET)
    , m_status(ConnectionStatus::Disconnected)
    , m_statusMessage(L"Disconnected")
    , m_connectedDeviceName(L"")
    , m_running(false)
    , m_serviceRegistered(false)
{
    ZeroMemory(&m_serviceRecord, sizeof(m_serviceRecord));
}

BluetoothManager::~BluetoothManager() {
    Cleanup();
}

bool BluetoothManager::Initialize() {
    WSADATA wsaData;
    int err = WSAStartup(MAKEWORD(2, 2), &wsaData);
    if (err != 0) {
        LOG_ERROR(L"Bluetooth", L"WSAStartup failed with error " + std::to_wstring(err));
        SetStatus(ConnectionStatus::Error, L"WSAStartup failed");
        return false;
    }
    LOG_INFO(L"Bluetooth", L"Winsock 2.2 Bluetooth subsystem initialized");
    return true;
}

void BluetoothManager::Cleanup() {
    Disconnect();
    WSACleanup();
}

void BluetoothManager::SetStatus(ConnectionStatus status, const std::wstring& msg) {
    m_status = status;
    m_statusMessage = msg;
    if (m_statusCb) {
        m_statusCb(status, msg);
    }
}

std::vector<BluetoothDeviceInfo> BluetoothManager::GetPairedDevices() {
    std::vector<BluetoothDeviceInfo> devices;
    LOG_INFO(L"Bluetooth", L"Querying paired Bluetooth devices...");

    BLUETOOTH_DEVICE_SEARCH_PARAMS searchParams;
    ZeroMemory(&searchParams, sizeof(searchParams));
    searchParams.dwSize = sizeof(BLUETOOTH_DEVICE_SEARCH_PARAMS);
    searchParams.fReturnAuthenticated = TRUE;  // Paired devices
    searchParams.fReturnRemembered     = TRUE;
    searchParams.fReturnUnknown        = FALSE;
    searchParams.fReturnConnected      = TRUE;
    searchParams.fIssueInquiry         = FALSE; // Fast cached query
    searchParams.cTimeoutMultiplier    = 2;
    searchParams.hRadio                = NULL;

    BLUETOOTH_DEVICE_INFO deviceInfo;
    ZeroMemory(&deviceInfo, sizeof(deviceInfo));
    deviceInfo.dwSize = sizeof(BLUETOOTH_DEVICE_INFO);

    HBLUETOOTH_DEVICE_FIND hFind = BluetoothFindFirstDevice(&searchParams, &deviceInfo);
    if (hFind != NULL) {
        do {
            BluetoothDeviceInfo info;
            info.name = deviceInfo.szName;
            info.address = deviceInfo.Address.ullLong;
            info.addressStr = FormatBthAddress(info.address);
            info.isConnected = deviceInfo.fConnected ? true : false;
            info.isRemembered = deviceInfo.fRemembered ? true : false;
            devices.push_back(info);
            LOG_INFO(L"Bluetooth", L"Found paired device: " + info.name + L" [" + info.addressStr + L"]");
        } while (BluetoothFindNextDevice(hFind, &deviceInfo));
        BluetoothFindDeviceClose(hFind);
    }

    if (devices.empty()) {
        LOG_WARN(L"Bluetooth", L"No paired Bluetooth devices discovered in Windows Settings.");
    } else {
        LOG_INFO(L"Bluetooth", L"Found total " + std::to_wstring(devices.size()) + L" paired device(s)");
    }

    return devices;
}

bool BluetoothManager::StartServer(ULONG port) {
    Disconnect();

    LOG_INFO(L"Bluetooth", L"Creating RFCOMM server socket...");
    m_serverSocket = socket(AF_BTH, SOCK_STREAM, BTHPROTO_RFCOMM);
    if (m_serverSocket == INVALID_SOCKET) {
        int err = WSAGetLastError();
        LOG_ERROR(L"Bluetooth", L"Failed to create RFCOMM socket: " + FormatWsaError(err));
        SetStatus(ConnectionStatus::Error, L"Failed to create RFCOMM socket");
        return false;
    }

    SOCKADDR_BTH sa;
    ZeroMemory(&sa, sizeof(sa));
    sa.addressFamily = AF_BTH;
    sa.btAddr = 0; // Local radio
    sa.port = (port == 0) ? BT_PORT_ANY : port;

    if (bind(m_serverSocket, (SOCKADDR*)&sa, sizeof(sa)) == SOCKET_ERROR) {
        int err = WSAGetLastError();
        LOG_ERROR(L"Bluetooth", L"Failed to bind RFCOMM socket: " + FormatWsaError(err));
        closesocket(m_serverSocket);
        m_serverSocket = INVALID_SOCKET;
        SetStatus(ConnectionStatus::Error, L"Failed to bind socket");
        return false;
    }

    // Determine allocated port/channel
    int saLen = sizeof(sa);
    if (getsockname(m_serverSocket, (SOCKADDR*)&sa, &saLen) == SOCKET_ERROR) {
        int err = WSAGetLastError();
        LOG_ERROR(L"Bluetooth", L"getsockname failed: " + FormatWsaError(err));
        closesocket(m_serverSocket);
        m_serverSocket = INVALID_SOCKET;
        SetStatus(ConnectionStatus::Error, L"Failed to get socket name");
        return false;
    }

    LOG_INFO(L"Bluetooth", L"RFCOMM server bound to channel/port " + std::to_wstring(sa.port));

    if (listen(m_serverSocket, 1) == SOCKET_ERROR) {
        int err = WSAGetLastError();
        LOG_ERROR(L"Bluetooth", L"listen failed: " + FormatWsaError(err));
        closesocket(m_serverSocket);
        m_serverSocket = INVALID_SOCKET;
        SetStatus(ConnectionStatus::Error, L"Failed to listen on socket");
        return false;
    }

    // Register SDP service
    ZeroMemory(&m_serviceRecord, sizeof(m_serviceRecord));
    m_serviceRecord.dwSize = sizeof(m_serviceRecord);
    m_serviceRecord.lpszServiceInstanceName = (LPWSTR)L"Lapdroid_Bridge";
    m_serviceRecord.lpszComment = (LPWSTR)L"Lapdroid - Windows to Android Bluetooth Input Bridge";
    m_serviceRecord.lpServiceClassId = (LPGUID)&BT_SerialPortServiceClass_UUID;
    m_serviceRecord.dwNumberOfCsAddrs = 1;

    CSADDR_INFO csAddr;
    ZeroMemory(&csAddr, sizeof(csAddr));
    csAddr.LocalAddr.lpSockaddr = (LPSOCKADDR)&sa;
    csAddr.LocalAddr.iSockaddrLength = sizeof(sa);
    csAddr.iSocketType = SOCK_STREAM;
    csAddr.iProtocol = BTHPROTO_RFCOMM;

    m_serviceRecord.lpcsaBuffer = &csAddr;

    if (WSASetServiceW(&m_serviceRecord, RNRSERVICE_REGISTER, 0) == SOCKET_ERROR) {
        int err = WSAGetLastError();
        LOG_WARN(L"Bluetooth", L"WSASetService SDP registration warning (err " + std::to_wstring(err) + L")");
    } else {
        m_serviceRegistered = true;
        LOG_INFO(L"Bluetooth", L"SDP service record 'Lapdroid_Bridge' published successfully");
    }

    m_running = true;
    SetStatus(ConnectionStatus::Listening, L"Waiting for Android phone to connect...");
    LOG_INFO(L"Bluetooth", L"Server is active and waiting for Android phone connection...");

    m_serverThread = std::thread(&BluetoothManager::ServerThreadProc, this);
    return true;
}

void BluetoothManager::ServerThreadProc() {
    SOCKADDR_BTH clientAddr;
    int addrLen = sizeof(clientAddr);
    ZeroMemory(&clientAddr, sizeof(clientAddr));

    SOCKET clientSock = accept(m_serverSocket, (SOCKADDR*)&clientAddr, &addrLen);
    if (clientSock == INVALID_SOCKET) {
        if (m_running) {
            LOG_INFO(L"Bluetooth", L"Server accept thread stopped");
            SetStatus(ConnectionStatus::Disconnected, L"Server stopped");
        }
        return;
    }

    m_clientSocket = clientSock;
    m_connectedDeviceName = FormatBthAddress(clientAddr.btAddr);

    LOG_INFO(L"Bluetooth", L"Incoming client connection accepted from " + m_connectedDeviceName);
    std::wstring msg = L"Connected to " + m_connectedDeviceName;
    SetStatus(ConnectionStatus::Connected, msg);

    // Launch receive thread
    m_receiveThread = std::thread(&BluetoothManager::ReceiveThreadProc, this);
}

bool BluetoothManager::ConnectToDevice(BTH_ADDR address) {
    Disconnect();

    m_running = true;
    SetStatus(ConnectionStatus::Connecting, L"Connecting to " + FormatBthAddress(address) + L"...");
    LOG_INFO(L"Bluetooth", L"Launching asynchronous connection thread to " + FormatBthAddress(address) + L"...");

    if (m_connectThread.joinable()) {
        m_connectThread.join();
    }
    m_connectThread = std::thread(&BluetoothManager::ConnectThreadProc, this, address);
    return true;
}

void BluetoothManager::ConnectThreadProc(BTH_ADDR address) {
    std::wstring addrStr = FormatBthAddress(address);
    LOG_INFO(L"Bluetooth", L"[ConnectThread] Target device: " + addrStr);

    SOCKET sock = INVALID_SOCKET;
    bool connected = false;

    // Strategy 1: Connect via SDP SPP UUID (port = 0 / BT_PORT_ANY)
    LOG_INFO(L"Bluetooth", L"[ConnectThread] Strategy 1: Attempting SDP SPP UUID resolution...");
    sock = socket(AF_BTH, SOCK_STREAM, BTHPROTO_RFCOMM);
    if (sock != INVALID_SOCKET) {
        SOCKADDR_BTH sa;
        ZeroMemory(&sa, sizeof(sa));
        sa.addressFamily = AF_BTH;
        sa.btAddr = address;
        sa.serviceClassId = BT_SerialPortServiceClass_UUID;
        sa.port = 0; // Trigger Winsock SDP lookup

        if (connect(sock, (SOCKADDR*)&sa, sizeof(sa)) == 0) {
            connected = true;
            LOG_INFO(L"Bluetooth", L"[ConnectThread] Successfully connected via SDP SPP UUID!");
        } else {
            int err = WSAGetLastError();
            LOG_WARN(L"Bluetooth", L"[ConnectThread] SDP connect failed: " + FormatWsaError(err));
            closesocket(sock);
            sock = INVALID_SOCKET;
        }
    }

    // Strategy 2: Direct RFCOMM Channel fallback (channels 1 through 4)
    if (!connected && m_running) {
        LOG_INFO(L"Bluetooth", L"[ConnectThread] Strategy 2: Attempting direct RFCOMM channels (1-4)...");
        for (ULONG ch = 1; ch <= 4 && m_running && !connected; ++ch) {
            sock = socket(AF_BTH, SOCK_STREAM, BTHPROTO_RFCOMM);
            if (sock == INVALID_SOCKET) break;

            SOCKADDR_BTH sa;
            ZeroMemory(&sa, sizeof(sa));
            sa.addressFamily = AF_BTH;
            sa.btAddr = address;
            sa.port = ch;

            LOG_INFO(L"Bluetooth", L"[ConnectThread] Trying direct RFCOMM channel " + std::to_wstring(ch) + L"...");
            if (connect(sock, (SOCKADDR*)&sa, sizeof(sa)) == 0) {
                connected = true;
                LOG_INFO(L"Bluetooth", L"[ConnectThread] Connected on direct RFCOMM channel " + std::to_wstring(ch) + L"!");
                break;
            } else {
                int err = WSAGetLastError();
                LOG_DEBUG(L"Bluetooth", L"[ConnectThread] Channel " + std::to_wstring(ch) + L" failed: " + FormatWsaError(err));
                closesocket(sock);
                sock = INVALID_SOCKET;
            }
        }
    }

    if (!m_running) {
        if (sock != INVALID_SOCKET) closesocket(sock);
        LOG_INFO(L"Bluetooth", L"[ConnectThread] Connection cancelled by user");
        return;
    }

    if (connected && sock != INVALID_SOCKET) {
        m_clientSocket = sock;
        m_connectedDeviceName = addrStr;
        SetStatus(ConnectionStatus::Connected, L"Connected to " + m_connectedDeviceName);
        LOG_INFO(L"Bluetooth", L"Link established with Android phone. Ready for input capture (F12).");
        m_receiveThread = std::thread(&BluetoothManager::ReceiveThreadProc, this);
    } else {
        LOG_ERROR(L"Bluetooth", L"Connection failed. Please ensure Lapdroid is running on Android in Server Mode or paired properly.");
        SetStatus(ConnectionStatus::Error, L"Connection failed (check Android app)");
    }
}

void BluetoothManager::ReceiveThreadProc() {
    uint8_t buffer[512];
    LOG_INFO(L"Bluetooth", L"Receive thread started");

    while (m_running && m_clientSocket != INVALID_SOCKET) {
        int bytesRead = recv(m_clientSocket, (char*)buffer, sizeof(buffer), 0);
        if (bytesRead > 0) {
            if (m_dataCb) {
                m_dataCb(buffer, (size_t)bytesRead);
            }
        } else if (bytesRead == 0 || bytesRead == SOCKET_ERROR) {
            LOG_INFO(L"Bluetooth", L"Connection closed by remote device or lost");
            break;
        }
    }

    if (m_running) {
        SetStatus(ConnectionStatus::Disconnected, L"Connection lost");
    }
}

void BluetoothManager::Disconnect() {
    m_running = false;

    if (m_serviceRegistered) {
        WSASetServiceW(&m_serviceRecord, RNRSERVICE_DELETE, 0);
        m_serviceRegistered = false;
        LOG_INFO(L"Bluetooth", L"SDP service record unregistered");
    }

    if (m_clientSocket != INVALID_SOCKET) {
        shutdown(m_clientSocket, SD_BOTH);
        closesocket(m_clientSocket);
        m_clientSocket = INVALID_SOCKET;
    }

    if (m_serverSocket != INVALID_SOCKET) {
        closesocket(m_serverSocket);
        m_serverSocket = INVALID_SOCKET;
    }

    if (m_connectThread.joinable()) {
        m_connectThread.join();
    }
    if (m_serverThread.joinable()) {
        m_serverThread.join();
    }
    if (m_receiveThread.joinable()) {
        m_receiveThread.join();
    }

    SetStatus(ConnectionStatus::Disconnected, L"Disconnected");
    LOG_INFO(L"Bluetooth", L"BluetoothManager disconnected and cleaned up");
}

bool BluetoothManager::SendPacket(uint8_t type, const void* payload, uint8_t length) {
    if (m_clientSocket == INVALID_SOCKET || m_status != ConnectionStatus::Connected) {
        return false;
    }

    uint8_t packet[260];
    packet[0] = PROTOCOL_MAGIC_0;
    packet[1] = PROTOCOL_MAGIC_1;
    packet[2] = type;
    packet[3] = length;

    if (length > 0 && payload != nullptr) {
        memcpy(&packet[4], payload, length);
    }

    uint8_t cs = CalcChecksum(type, length, (const uint8_t*)payload);
    packet[4 + length] = cs;

    size_t totalLen = 4 + length + 1;
    return SendRaw(packet, totalLen);
}

bool BluetoothManager::SendRaw(const uint8_t* data, size_t length) {
    if (m_clientSocket == INVALID_SOCKET || m_status != ConnectionStatus::Connected) {
        return false;
    }

    std::lock_guard<std::mutex> lock(m_sendMutex);
    int sent = send(m_clientSocket, (const char*)data, (int)length, 0);
    return (sent == (int)length);
}
