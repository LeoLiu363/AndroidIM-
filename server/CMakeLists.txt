cmake_minimum_required(VERSION 3.10)
project(IMServer)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 包含目录
include_directories(src)

# 源文件
set(SOURCES
    src/main.cpp
    src/server/epoll_server.cpp
    src/thread_pool/thread_pool.cpp
    src/protocol/encoder.cpp
    src/protocol/decoder.cpp
    src/handler/login_handler.cpp
    src/handler/message_handler.cpp
    src/handler/user_handler.cpp
    src/handler/friend_handler.cpp
    src/handler/group_handler.cpp
    src/utils/logger.cpp
    src/database/database.cpp
)

# 可执行文件
add_executable(imserver ${SOURCES})

# 查找 MySQL
# 先尝试使用 mysql_config
find_program(MYSQL_CONFIG mysql_config)
if(MYSQL_CONFIG)
    execute_process(
        COMMAND ${MYSQL_CONFIG} --include
        OUTPUT_VARIABLE MYSQL_INCLUDE_FLAGS
        OUTPUT_STRIP_TRAILING_WHITESPACE
    )
    execute_process(
        COMMAND ${MYSQL_CONFIG} --libs
        OUTPUT_VARIABLE MYSQL_LINK_FLAGS
        OUTPUT_STRIP_TRAILING_WHITESPACE
    )
    string(REGEX REPLACE "^-I" "" MYSQL_INCLUDE_DIR "${MYSQL_INCLUDE_FLAGS}")
    string(REGEX REPLACE "^-I" "" MYSQL_INCLUDE_DIR "${MYSQL_INCLUDE_DIR}")
    message(STATUS "Found MySQL via mysql_config: ${MYSQL_INCLUDE_DIR}")
    include_directories(${MYSQL_INCLUDE_DIR})
    set(MYSQL_LIBRARY "${MYSQL_LINK_FLAGS}")
else()
    # 手动查找
    find_path(MYSQL_INCLUDE_DIR mysql/mysql.h
        PATHS
        /usr/include
        /usr/local/include
        /usr/include/mysql
        /usr/local/include/mysql
        /usr/include/mariadb
        /usr/local/include/mariadb
        /usr/include/mysql/mysql
        /www/server/mysql/include
    )
    
    find_library(MYSQL_LIBRARY
        NAMES mysqlclient libmysqlclient mariadbclient
        PATHS
        /usr/lib
        /usr/local/lib
        /usr/lib/x86_64-linux-gnu
        /usr/lib64
        /usr/lib/mysql
        /usr/lib64/mysql
        /www/server/mysql/lib
    )
    
    if(MYSQL_INCLUDE_DIR AND MYSQL_LIBRARY)
        message(STATUS "Found MySQL: ${MYSQL_LIBRARY}")
        include_directories(${MYSQL_INCLUDE_DIR})
    else()
        message(WARNING "MySQL not found. Please install libmysqlclient-dev or mariadb-dev")
        message(WARNING "Continuing without MySQL support - database features will be disabled")
        set(MYSQL_LIBRARY "")
    endif()
endif()

# 链接库
if(MYSQL_LIBRARY)
    target_link_libraries(imserver pthread ${MYSQL_LIBRARY})
else()
    target_link_libraries(imserver pthread)
    message(WARNING "Building without MySQL support")
endif()

