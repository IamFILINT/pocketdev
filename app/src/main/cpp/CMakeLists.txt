cmake_minimum_required(VERSION 3.22.1)

project("pocketdev_pty")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(pocketdev_pty SHARED pty.cpp)

find_library(log-lib log)
target_link_libraries(pocketdev_pty ${log-lib})
