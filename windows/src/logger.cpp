#include "logger.h"
#include "resource.h"
#include <sstream>
#include <iomanip>

Logger& Logger::Instance() {
    static Logger s_instance;
    return s_instance;
}

Logger::Logger()
    : m_hWnd(NULL)
{
}

void Logger::SetHwnd(HWND hWnd) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_hWnd = hWnd;
}

void Logger::Log(LogLevel level, const std::wstring& tag, const std::wstring& message) {
    SYSTEMTIME st;
    GetLocalTime(&st);

    const wchar_t* levelStr = L"INFO";
    switch (level) {
        case LogLevel::Info:  levelStr = L"INFO"; break;
        case LogLevel::Warn:  levelStr = L"WARN"; break;
        case LogLevel::Error: levelStr = L"ERROR"; break;
        case LogLevel::Debug: levelStr = L"DEBUG"; break;
    }

    std::wstringstream ss;
    ss << L"["
       << std::setw(2) << std::setfill(L'0') << st.wHour << L":"
       << std::setw(2) << std::setfill(L'0') << st.wMinute << L":"
       << std::setw(2) << std::setfill(L'0') << st.wSecond << L"."
       << std::setw(3) << std::setfill(L'0') << st.wMilliseconds << L"] ["
       << levelStr << L"] ["
       << tag << L"] "
       << message;

    std::wstring entry = ss.str();

    HWND targetHwnd = NULL;
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        if (m_logs.size() >= MAX_LOG_ENTRIES) {
            m_logs.erase(m_logs.begin());
        }
        m_logs.push_back(entry);
        targetHwnd = m_hWnd;
    }

    if (targetHwnd && IsWindow(targetHwnd)) {
        PostMessageW(targetHwnd, WM_APP_LOG_MESSAGE, 0, 0);
    }
}

std::wstring Logger::GetAllLogs() {
    std::lock_guard<std::mutex> lock(m_mutex);
    std::wstringstream ss;
    for (const auto& line : m_logs) {
        ss << line << L"\r\n";
    }
    return ss.str();
}

void Logger::Clear() {
    HWND targetHwnd = NULL;
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        m_logs.clear();
        targetHwnd = m_hWnd;
    }
    if (targetHwnd && IsWindow(targetHwnd)) {
        PostMessageW(targetHwnd, WM_APP_LOG_MESSAGE, 0, 0);
    }
}
