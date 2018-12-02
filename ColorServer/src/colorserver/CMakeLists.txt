cmake_minimum_required(VERSION 2.8)

project(Color)

add_library(${PROJECT_NAME} SHARED "highlight.cpp")

set(JAVA_HOME "C:/Program Files/Java/jdk-11.0.1")
set(CMAKE_SHARED_LINKER_FLAGS "-static-libgcc -static-libstdc++ -static")

include_directories(${JAVA_HOME}/include ${JAVA_HOME}/include/win32)
