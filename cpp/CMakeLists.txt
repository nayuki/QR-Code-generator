cmake_minimum_required(VERSION 3.22)

project(qrcodegen LANGUAGES CXX VERSION 1.8)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS "${CMAXE_CXX_FLAGS} -Wall -Wextra -Wno-unused-parameter -Wno-missing-field-initializers -Wcast-qual -Wconversion -Wshadow")

add_library(${PROJECT_NAME} STATIC
    qrcodegen.hpp
    qrcodegen.cpp
)

target_include_directories(${PROJECT_NAME} PUBLIC ${CMAKE_CURRENT_LIST_DIR})
