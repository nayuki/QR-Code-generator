#
# Makefile for QR Code generator (C++)
#
# Copyright (c) Project Nayuki. (MIT License)
# https://www.nayuki.io/page/qr-code-generator-library
#
# Permission is hereby granted, free of charge, to any person obtaining a copy of
# this software and associated documentation files (the "Software"), to deal in
# the Software without restriction, including without limitation the rights to
# use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
# the Software, and to permit persons to whom the Software is furnished to do so,
# subject to the following conditions:
# - The above copyright notice and this permission notice shall be included in
#   all copies or substantial portions of the Software.
# - The Software is provided "as is", without warranty of any kind, express or
#   implied, including but not limited to the warranties of merchantability,
#   fitness for a particular purpose and noninfringement. In no event shall the
#   authors or copyright holders be liable for any claim, damages or other
#   liability, whether in an action of contract, tort or otherwise, arising from,
#   out of or in connection with the Software or the use or other dealings in the
#   Software.
#
cmake_minimum_required(VERSION 3.0)

project(QrCode VERSION 1.0.0)

add_definitions("-Wall")
add_definitions("-Wextra")
add_definitions("-Werror")
#add_definitions("-fsanitize=undefined")

set(${PROJECT_NAME}_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/${PROJECT_NAME}")

include_directories(${${PROJECT_NAME}_INCLUDE_DIR})

file(GLOB ${PROJECT_NAME}_PUBLIC_HEADERS "${${PROJECT_NAME}_INCLUDE_DIR}/*.hpp")
file(GLOB ${PROJECT_NAME}_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/src/*.cpp")

add_library(${PROJECT_NAME} SHARED ${${PROJECT_NAME}_SOURCES})

set_target_properties(${PROJECT_NAME} PROPERTIES CXX_STANDARD 11
                                                 CXX_STANDARD_REQUIRED YES
                                                 CXX_EXTENSIONS NO)

set_target_properties(${PROJECT_NAME} PROPERTIES PUBLIC_HEADER "${${PROJECT_NAME}_PUBLIC_HEADERS}")
set_target_properties(${PROJECT_NAME} PROPERTIES VERSION ${PROJECT_VERSION} SOVERSION ${PROJECT_VERSION_MAJOR})

# Installation options
include(GNUInstallDirs)
set(${PROJECT_NAME}_INSTALL_LIB_DIR ${CMAKE_INSTALL_LIBDIR} CACHE PATH "Installation directory for libraries")
set(${PROJECT_NAME}_INSTALL_INCLUDE_DIR ${CMAKE_INSTALL_INCLUDEDIR}/${PROJECT_NAME} CACHE PATH "Installation directory for header files")
set(${PROJECT_NAME}_INSTALL_CMAKE_DIR ${CMAKE_INSTALL_LIBDIR}/cmake/${PROJECT_NAME} CACHE PATH "Installation directory for CMake files")

# Get absolute path where to install files
foreach(p LIB BIN INCLUDE CMAKE)
    set(var ${PROJECT_NAME}_INSTALL_${p}_DIR)
    if(NOT IS_ABSOLUTE "${${var}}")
        set(${var} "${CMAKE_INSTALL_PREFIX}/${${var}}")
    endif()
endforeach()

# Exporting target
export(TARGETS ${PROJECT_NAME} FILE "${PROJECT_BINARY_DIR}/${PROJECT_NAME}.cmake")
export(PACKAGE ${PROJECT_NAME})

file(RELATIVE_PATH ${PROJECT_NAME}_REL_INCLUDE_DIR "${${PROJECT_NAME}_INSTALL_CMAKE_DIR}" "${${PROJECT_NAME}_INSTALL_INCLUDE_DIR}")

## configure for build tree
set(CONF_INCLUDE_DIRS "${PROJECT_SOURCE_DIR}" "${PROJECT_BINARY_DIR}")
configure_file(${PROJECT_NAME}Config.cmake.in "${PROJECT_BINARY_DIR}/${PROJECT_NAME}Config.cmake" @ONLY)

## configure for install tree
set(CONF_INCLUDE_DIRS "\${${PROJECT_NAME}_CMAKE_DIR}/${REL_INCLUDE_DIR}")
configure_file(${PROJECT_NAME}Config.cmake.in "${PROJECT_BINARY_DIR}${CMAKE_FILES_DIRECTORY}/${PROJECT_NAME}Config.cmake" @ONLY)

##  ... for both
configure_file(${PROJECT_NAME}ConfigVersion.cmake.in  "${PROJECT_BINARY_DIR}/${PROJECT_NAME}ConfigVersion.cmake" @ONLY)

install(TARGETS ${PROJECT_NAME}
        EXPORT ${PROJECT_NAME} DESTINATION "${${PROJECT_NAME}_INSTALL_CMAKE_DIR}" COMPONENT dev
        LIBRARY DESTINATION "${${PROJECT_NAME}_INSTALL_LIB_DIR}"
        PUBLIC_HEADER DESTINATION "${${PROJECT_NAME}_INSTALL_INCLUDE_DIR}" COMPONENT dev)

install(FILES
        "${PROJECT_BINARY_DIR}${CMAKE_FILES_DIRECTORY}/${PROJECT_NAME}Config.cmake"
        "${PROJECT_BINARY_DIR}/${PROJECT_NAME}ConfigVersion.cmake"
        DESTINATION "${${PROJECT_NAME}_INSTALL_CMAKE_DIR}" COMPONENT dev)