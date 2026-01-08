#include "logger.h"
#include <ctime>
#include <iomanip>
#include <sstream>

namespace im {

std::string Logger::getCurrentTime() {
    auto now = std::time(nullptr);
    auto tm = *std::localtime(&now);
    
    std::ostringstream oss;
    oss << std::put_time(&tm, "%Y-%m-%d %H:%M:%S");
    return oss.str();
}

std::string Logger::levelToString(Level level) {
    switch (level) {
        case Level::DEBUG: return "DEBUG";
        case Level::INFO:  return "INFO ";
        case Level::WARN:  return "WARN ";
        case Level::ERROR: return "ERROR";
        default: return "UNKNOWN";
    }
}

void Logger::log(Level level, const std::string& message) {
    std::cout << "[" << getCurrentTime() << "] "
              << "[" << levelToString(level) << "] "
              << message << std::endl;
}

void Logger::debug(const std::string& message) {
    log(Level::DEBUG, message);
}

void Logger::info(const std::string& message) {
    log(Level::INFO, message);
}

void Logger::warn(const std::string& message) {
    log(Level::WARN, message);
}

void Logger::error(const std::string& message) {
    log(Level::ERROR, message);
}

}  // namespace im

