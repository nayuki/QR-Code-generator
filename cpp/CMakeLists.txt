project(libqrcodegen)

add_library(qrcodegen STATIC 
    BitBuffer.cpp QrSegment.cpp QrCode.cpp)

target_include_directories(qrcodegen PUBLIC 
    ${CMAKE_CURRENT_SOURCE_DIR})
