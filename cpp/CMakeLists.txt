cmake_minimum_required(VERSION 2.8.12)
project(QR_Code_generator)
##Add project files
File(GLOB SRCFILES *.cpp)
File(GLOB HEADFILES *.h *.hpp)
add_executable(${PROJECT_NAME} ${SRCFILES} ${HEADFILES})