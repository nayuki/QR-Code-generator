# ==============================
# === Project initialization ===
# ==============================

cmake_minimum_required(VERSION 3.12)

project(QR-Code-generator
    VERSION 1.8.0
    DESCRIPTION "High-quality QR Code generator library in Java, TypeScript/JavaScript, Python, C++, C, Rust"
    HOMEPAGE_URL "https://www.nayuki.io/page/qr-code-generator-library"
    LANGUAGES C CXX
)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# =======================
# === Project options ===
# =======================

option(BUILD_EXAMPLES "Build examples and demos" OFF)
option(BUILD_TESTS "Build and run unit tests" OFF)

# =======================
# === Paths detection ===
# =======================

include(GNUInstallDirs)

# ============================
# === CMake config helpers ===
# ============================

include(CMakePackageConfigHelpers)

# ========================
# === C library target ===
# ========================

set(QRCODEGEN_NAME "qrcodegen")

set(QRCODEGEN_SOURCES
    c/qrcodegen.c
)

set(QRCODEGEN_HEADERS
    c/qrcodegen.h
)

add_library(${QRCODEGEN_NAME}
    ${QRCODEGEN_SOURCES}
    ${QRCODEGEN_HEADERS}
)

target_include_directories(${QRCODEGEN_NAME} PUBLIC
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/c>
    $<INSTALL_INTERFACE:${CMAKE_INSTALL_INCLUDEDIR}/${QRCODEGEN_NAME}>
)

set_property(TARGET ${QRCODEGEN_NAME} PROPERTY PUBLIC_HEADER ${QRCODEGEN_HEADERS})
set_property(TARGET ${QRCODEGEN_NAME} PROPERTY VERSION ${CMAKE_PROJECT_VERSION})
set_property(TARGET ${QRCODEGEN_NAME} PROPERTY SOVERSION 1)

install(TARGETS ${QRCODEGEN_NAME}
    EXPORT ${QRCODEGEN_NAME}-targets
    RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
    BUNDLE DESTINATION ${CMAKE_INSTALL_BINDIR}
    PUBLIC_HEADER DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}/${QRCODEGEN_NAME}
)

configure_package_config_file(cmake/${QRCODEGEN_NAME}-config.cmake.in
    ${QRCODEGEN_NAME}-config.cmake
    INSTALL_DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/${QRCODEGEN_NAME}
)
write_basic_package_version_file(${CMAKE_CURRENT_BINARY_DIR}/${QRCODEGEN_NAME}-config-version.cmake
    COMPATIBILITY ExactVersion
)

install(FILES
    ${CMAKE_CURRENT_BINARY_DIR}/${QRCODEGEN_NAME}-config.cmake
    ${CMAKE_CURRENT_BINARY_DIR}/${QRCODEGEN_NAME}-config-version.cmake
    DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/${QRCODEGEN_NAME}
)

install(EXPORT ${QRCODEGEN_NAME}-targets
    DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/${QRCODEGEN_NAME}
    NAMESPACE ${QRCODEGEN_NAME}::
)

configure_file(cmake/${QRCODEGEN_NAME}.pc.in
    ${CMAKE_CURRENT_BINARY_DIR}/${QRCODEGEN_NAME}.pc
    @ONLY
)

install(FILES
    ${CMAKE_CURRENT_BINARY_DIR}/${QRCODEGEN_NAME}.pc
    DESTINATION ${CMAKE_INSTALL_LIBDIR}/pkgconfig
)

# =========================
# === C examples target ===
# =========================

if(BUILD_EXAMPLES)
    set(QRCODEGENDEMO_SOURCES
        c/qrcodegen-demo.c
    )

    add_executable(${QRCODEGEN_NAME}-demo
        ${QRCODEGENDEMO_SOURCES}
    )

    target_link_libraries(${QRCODEGEN_NAME}-demo PRIVATE
        ${QRCODEGEN_NAME}
    )
endif()

# ======================
# === C tests target ===
# ======================

if (BUILD_TESTS)
    set(QRCODEGENTEST_SOURCES
        c/qrcodegen-test.c
    )

    add_library(${QRCODEGEN_NAME}-testable OBJECT
        ${QRCODEGEN_SOURCES}
        ${QRCODEGEN_HEADERS}
    )

    target_compile_options(${QRCODEGEN_NAME}-testable PUBLIC
        -DQRCODEGEN_TEST
    )

    add_executable(${QRCODEGEN_NAME}-test
        ${QRCODEGENTEST_SOURCES}
    )

    target_link_libraries(${QRCODEGEN_NAME}-test PRIVATE
        ${QRCODEGEN_NAME}-testable
    )

    add_test(NAME ${QRCODEGEN_NAME}-test COMMAND ${QRCODEGEN_NAME}-test)
endif()

# ==========================
# === C++ library target ===
# ==========================

set(QRCODEGENCPP_NAME "qrcodegencpp")

set(QRCODEGENCPP_SOURCES
    cpp/qrcodegen.cpp
)

set(QRCODEGENCPP_HEADERS
    cpp/qrcodegen.hpp
)

add_library(${QRCODEGENCPP_NAME}
    ${QRCODEGENCPP_SOURCES}
    ${QRCODEGENCPP_HEADERS}
)

target_include_directories(${QRCODEGENCPP_NAME} PUBLIC
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/cpp>
    $<INSTALL_INTERFACE:${CMAKE_INSTALL_INCLUDEDIR}/${QRCODEGENCPP_NAME}>
)

set_property(TARGET ${QRCODEGENCPP_NAME} PROPERTY PUBLIC_HEADER ${QRCODEGENCPP_HEADERS})
set_property(TARGET ${QRCODEGENCPP_NAME} PROPERTY VERSION ${CMAKE_PROJECT_VERSION})
set_property(TARGET ${QRCODEGENCPP_NAME} PROPERTY SOVERSION 1)

install(TARGETS ${QRCODEGENCPP_NAME}
    EXPORT ${QRCODEGENCPP_NAME}-targets
    RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
    BUNDLE DESTINATION ${CMAKE_INSTALL_BINDIR}
    PUBLIC_HEADER DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}/${QRCODEGENCPP_NAME}
)

configure_package_config_file(cmake/${QRCODEGENCPP_NAME}-config.cmake.in
    ${QRCODEGENCPP_NAME}-config.cmake
    INSTALL_DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/${QRCODEGENCPP_NAME}
)
write_basic_package_version_file(${CMAKE_CURRENT_BINARY_DIR}/${QRCODEGENCPP_NAME}-config-version.cmake
    COMPATIBILITY ExactVersion
)

install(FILES
    ${CMAKE_CURRENT_BINARY_DIR}/${QRCODEGENCPP_NAME}-config.cmake
    ${CMAKE_CURRENT_BINARY_DIR}/${QRCODEGENCPP_NAME}-config-version.cmake
    DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/${QRCODEGENCPP_NAME}
)

install(EXPORT ${QRCODEGENCPP_NAME}-targets
    DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/${QRCODEGENCPP_NAME}
    NAMESPACE ${QRCODEGENCPP_NAME}::
)

configure_file(cmake/${QRCODEGENCPP_NAME}.pc.in
    ${CMAKE_CURRENT_BINARY_DIR}/${QRCODEGENCPP_NAME}.pc
    @ONLY
)

install(FILES
    ${CMAKE_CURRENT_BINARY_DIR}/${QRCODEGENCPP_NAME}.pc
    DESTINATION ${CMAKE_INSTALL_LIBDIR}/pkgconfig
)

# ===========================
# === C++ examples target ===
# ===========================

if(BUILD_EXAMPLES)
    set(QRCODEGENCPPDEMO_SOURCES
        cpp/QrCodeGeneratorDemo.cpp
    )

    add_executable(${QRCODEGENCPP_NAME}-demo
        ${QRCODEGENCPPDEMO_SOURCES}
    )

    target_link_libraries(${QRCODEGENCPP_NAME}-demo PRIVATE
        ${QRCODEGENCPP_NAME}
    )
endif()

# ====================
# === Tests export ===
# ====================

if (BUILD_TESTS)
    enable_testing()
endif()
