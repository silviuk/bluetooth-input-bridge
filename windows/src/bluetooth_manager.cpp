#include "bluetooth_manager.h"
#include "protocol.h"
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
        SetStatus(ConnectionStatus::Error, L"WSAStartup failed");
        return false;
    }
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

    BLUETOOTH_DEVICE_SEARCH_PARAMS searchParams;
    ZeroMemory(&searchParams, sizeof(searchParams));
    searchParams.dwSize = sizeof(BLUETOOTH_DEVICE_SEARCH_PARAMS);
    searchParams.fReturnAuthenticated = TRUE;  // Paired devices
    searchParams.fReturnRemembered     = TRUE;
    searchParams.fReturnUnknown        = FALSE;
    searchParams.fReturnConnected      = TRUE;
    searchParams.fIssueInquiry         = FALSE; // Fast, don't wait for active inquiry
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
        } while (BluetoothFindNextDevice(hFind, &deviceInfo));
        BluetoothFindDeviceClose(hFind);
    }

    return devices;
}

bool BluetoothManager::StartServer(ULONG port) {
    Disconnect();

    m_serverSocket = socket(AF_BTH, SOCK_STREAM, BTHPROTO_RFCOMM);
    if (m_serverSocket == INVALID_SOCKET) {
        SetStatus(ConnectionStatus::Error, L"Failed to create RFCOMM socket");
        return false;
    }

    SOCKADDR_BTH sa;
    ZeroMemory(&sa, sizeof(sa));
    sa.addressFamily = AF_BTH;
    sa.btAddr = 0; // Local radio
    sa.port = port;

    if (bind(m_serverSocket, (SOCKADDR*)&sa, sizeof(sa)) == SOCKET_ERROR) {
        closesocket(m_serverSocket);
        m_serverSocket = INVALID_SOCKET;
        SetStatus(ConnectionStatus::Error, L"Failed to bind socket");
        return false;
    }

    // Determine allocated port/channel
    int saLen = sizeof(sa);
    if (getsockname(m_serverSocket, (SOCKADDR*)&sa, &saLen) == SOCKET_ERROR) {
        closesocket(m_serverSocket);
        m_serverSocket = INVALID_SOCKET;
        SetStatus(ConnectionStatus::Error, L"Failed to get socket name");
        return false;
    }

    if (listen(m_serverSocket, 1) == SOCKET_ERROR) {
        closesocket(m_serverSocket);
        m_serverSocket = INVALID_SOCKET;
        SetStatus(ConnectionStatus::Error, L"Failed to listen on socket");
        return false;
    }

    // Register SDP service
    ZeroMemory(&m_serviceRecord, sizeof(m_serviceRecord));
    m_serviceRecord.dwSize = sizeof(m_serviceRecord);
    m_serviceRecord.lpszServiceInstanceName = (LPWSTR)L"S24_Input_Bridge";
    m_serviceRecord.lpszComment = (LPWSTR)L"Windows to S24 Ultra Bluetooth Input Bridge";
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
        // Warning only: some stacks don't strictly require explicit SDP if RFCOMM port is known
    } else {
        m_serviceRegistered = true;
    }

    m_running = true;
    SetStatus(ConnectionStatus::Listening, L"Waiting for S24 Ultra to connect...");

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
            SetStatus(ConnectionStatus::Disconnected, L"Server stopped");
        }
        return;
    }

    m_clientSocket = clientSock;
    m_connectedDeviceName = FormatBthAddress(clientAddr.btAddr);

    std::wstring msg = L"Connected to " + m_connectedDeviceName;
    SetStatus(ConnectionStatus::Connected, msg);

    // Launch receive thread
    m_receiveThread = std::thread(&BluetoothManager::ReceiveThreadProc, this);
}

bool BluetoothManager::ConnectToDevice(BTH_ADDR address, ULONG port) {
    Disconnect();

    m_clientSocket = socket(AF_BTH, SOCK_STREAM, BTHPROTO_RFCOMM);
    if (m_clientSocket == INVALID_SOCKET) {
        SetStatus(ConnectionStatus::Error, L"Failed to create RFCOMM socket");
        return false;
    }

    SOCKADDR_BTH sa;
    ZeroMemory(&sa, sizeof(sa));
    sa.addressFamily = AF_BTH;
    sa.btAddr = address;
    sa.serviceClassId = BT_SerialPortServiceClass_UUID;
    sa.port = port; // RFCOMM channel

    SetStatus(ConnectionStatus::Connecting, L"Connecting to device " + FormatBthAddress(address) + L"...");

    if (connect(m_clientSocket, (SOCKADDR*)&sa, sizeof(sa)) == SOCKET_ERROR) {
        closesocket(m_clientSocket);
        m_clientSocket = INVALID_SOCKET;
        SetStatus(ConnectionStatus::Error, L"Connection failed (check phone is in bridge mode)");
        return false;
    }

    m_connectedDeviceName = FormatBthAddress(address);
    m_running = true;
    SetStatus(ConnectionStatus::Connected, L"Connected to " + m_connectedDeviceName);

    m_receiveThread = std::thread(&BluetoothManager::ReceiveThreadProc, this);
    return true;
}

void BluetoothManager::ReceiveThreadProc() {
    uint8_t buffer[512];
    while (m_running && m_clientSocket != INVALID_SOCKET) {
        int bytesRead = recv(m_clientSocket, (char*)buffer, sizeof(buffer), 0);
        if (bytesRead > 0) {
            if (m_dataCb) {
                m_dataCb(buffer, (size_t)bytesRead);
            }
        } else if (bytesRead == 0 || bytesRead == SOCKET_ERROR) {
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

    if (m_serverThread.joinable()) {
        m_serverThread.join();
    }
    if (m_receiveThread.joinable()) {
        m_receiveThread.join();
    }

    SetStatus(ConnectionStatus::Disconnected, L"Disconnected");
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
