#ifndef LOGGER_H
#define LOGGER_H

#include <string>
#include <iostream>
#include <sstream>
#include <iomanip>
#include <ctime>

namespace im {

class Logger {
public:
    enum class Level {
        DEBUG,
        INFO,
        WARN,
        ERROR
    };
    
    static void log(Level level, const std::string& message);
    static void debug(const std::string& message);
    static void info(const std::string& message);
    static void warn(const std::string& message);
    static void error(const std::string& message);

private:
    static std::string getCurrentTime();
    static std::string levelToString(Level level);
};

}  // namespace im

#endif  // LOGGER_H

