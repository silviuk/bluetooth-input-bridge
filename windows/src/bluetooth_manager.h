#ifndef BLUETOOTH_MANAGER_H
#define BLUETOOTH_MANAGER_H

#include <winsock2.h>
#include <ws2bth.h>
#include <bluetoothapis.h>
#include <string>
#include <vector>
#include <functional>
#include <thread>
#include <atomic>
#include <mutex>

struct BluetoothDeviceInfo {
    std::wstring name;
    BTH_ADDR address;
    std::wstring addressStr;
    bool isConnected;
    bool isRemembered;
};

enum class ConnectionStatus {
    Disconnected,
    Listening,
    Connecting,
    Connected,
    Error
};

class BluetoothManager {
public:
    using StatusCallback = std::function<void(ConnectionStatus, const std::wstring&)>;
    using DataReceivedCallback = std::function<void(const uint8_t*, size_t)>;
    using PacketCallback = std::function<void(uint8_t type, const uint8_t* payload, uint8_t len)>;

    BluetoothManager();
    ~BluetoothManager();

    bool Initialize();
    void Cleanup();

    // Device discovery / enumeration
    std::vector<BluetoothDeviceInfo> GetPairedDevices();

    // Connection modes
    bool StartServer(ULONG port = BT_PORT_ANY);
    bool ConnectToDevice(BTH_ADDR address);
    void Disconnect();

    // Data transmission
    bool SendPacket(uint8_t type, const void* payload, uint8_t length);
    bool SendRaw(const uint8_t* data, size_t length);

    // Callbacks & State
    void SetStatusCallback(StatusCallback cb) { m_statusCb = cb; }
    void SetDataCallback(DataReceivedCallback cb) { m_dataCb = cb; }
    void SetPacketCallback(PacketCallback cb) { m_packetCb = cb; }
    ConnectionStatus GetStatus() const { return m_status; }
    std::wstring GetStatusMessage() const { return m_statusMessage; }
    std::wstring GetConnectedDeviceName() const { return m_connectedDeviceName; }
    bool IsConnected() const { return m_status == ConnectionStatus::Connected; }

private:
    void SetStatus(ConnectionStatus status, const std::wstring& msg);
    void ServerThreadProc();
    void ConnectThreadProc(BTH_ADDR address);
    void ReceiveThreadProc();

    SOCKET m_serverSocket;
    SOCKET m_clientSocket;
    std::atomic<ConnectionStatus> m_status;
    std::wstring m_statusMessage;
    std::wstring m_connectedDeviceName;

    std::thread m_serverThread;
    std::thread m_connectThread;
    std::thread m_receiveThread;
    std::atomic<bool> m_running;

    StatusCallback m_statusCb;
    DataReceivedCallback m_dataCb;
    PacketCallback m_packetCb;
    std::mutex m_sendMutex;
    WSAQUERYSETW m_serviceRecord;
    bool m_serviceRegistered;
};

std::wstring FormatBthAddress(BTH_ADDR address);
std::wstring FormatWsaError(int err);

#endif // BLUETOOTH_MANAGER_H
