cmake_minimum_required(VERSION 3.22.1)

project(byedpi_native)

find_package(Python3 REQUIRED COMPONENTS Interpreter)
set(BYE_DPI_PREPARE ${CMAKE_CURRENT_SOURCE_DIR}/../../../../tools/prepare-byedpi.py)
set(BYE_DPI_PREPARED ${CMAKE_CURRENT_BINARY_DIR}/byedpi-prepared)
file(GLOB BYE_DPI_PATCHES CONFIGURE_DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/patches/*.patch)
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS
    ${BYE_DPI_PREPARE} ${BYE_DPI_PATCHES}
    ${CMAKE_CURRENT_SOURCE_DIR}/automatic_access.c
    ${CMAKE_CURRENT_SOURCE_DIR}/automatic_access.h)
execute_process(
    COMMAND ${Python3_EXECUTABLE} ${BYE_DPI_PREPARE} --output ${BYE_DPI_PREPARED}
    RESULT_VARIABLE BYE_DPI_PREPARE_RESULT)
if(NOT BYE_DPI_PREPARE_RESULT EQUAL 0)
    message(FATAL_ERROR "Failed to prepare the pinned ByeDPI sources and patches")
endif()

file(GLOB BYE_DPI_SRC ${BYE_DPI_PREPARED}/*.c)
list(REMOVE_ITEM BYE_DPI_SRC ${BYE_DPI_PREPARED}/win_service.c)

add_library(byedpi SHARED ${BYE_DPI_SRC} native-lib.c)
target_include_directories(byedpi PRIVATE ${BYE_DPI_PREPARED})

target_compile_options(byedpi PRIVATE -std=c99 -O2 -Wall -Wno-unused -Wextra -Wno-unused-parameter -pedantic)
target_compile_definitions(byedpi PRIVATE ANDROID_APP)

target_link_libraries(byedpi PRIVATE android log)
