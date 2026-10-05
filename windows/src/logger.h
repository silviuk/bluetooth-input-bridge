#ifndef LOGGER_H
#define LOGGER_H

#include <windows.h>
#include <string>
#include <vector>
#include <mutex>

enum class LogLevel {
    Info,
    Warn,
    Error,
    Debug
};

class Logger {
public:
    static Logger& Instance();

    void SetHwnd(HWND hWnd);
    void SetLoggingEnabled(bool enabled);
    bool IsLoggingEnabled() const;
    void Log(LogLevel level, const std::wstring& tag, const std::wstring& message);
    std::wstring GetAllLogs();
    void Clear();

private:
    Logger();
    ~Logger() = default;
    Logger(const Logger&) = delete;
    Logger& operator=(const Logger&) = delete;

    HWND m_hWnd;
    bool m_loggingEnabled;
    mutable std::mutex m_mutex;
    std::vector<std::wstring> m_logs;
    static const size_t MAX_LOG_ENTRIES = 800;
};

#define LOG_INFO(tag, msg)  Logger::Instance().Log(LogLevel::Info, tag, msg)
#define LOG_WARN(tag, msg)  Logger::Instance().Log(LogLevel::Warn, tag, msg)
#define LOG_ERROR(tag, msg) Logger::Instance().Log(LogLevel::Error, tag, msg)
#define LOG_DEBUG(tag, msg) Logger::Instance().Log(LogLevel::Debug, tag, msg)

#endif // LOGGER_H
