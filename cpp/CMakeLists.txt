cmake_minimum_required(VERSION 3.16)

project(QrCodeGenerator)

set(COMMON_QR_CODE_SOURCE
    ${CMAKE_CURRENT_SOURCE_DIR}/qrcodegen.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/qrcodegen.hpp
    )

add_library(QrCodeGenerator SHARED
    ${COMMON_QR_CODE_SOURCE}
    )

set_target_properties(${PROJECT_NAME} PROPERTIES
    PUBLIC_HEADER qrcodegen.hpp
    SOVERSION 1.8.0
)

target_include_directories(QrCodeGenerator 
    PUBLIC
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}>
        $<INSTALL_INTERFACE:include>
    )

install(TARGETS QrCodeGenerator
        EXPORT QrCodeGeneratorTargets)

install(EXPORT QrCodeGeneratorTargets
    NAMESPACE QrCodeGenerator::
    FILE QrCodeGeneratorTargets.cmake
    DESTINATION lib/cmake/QrCodeGenerator
)
