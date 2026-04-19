cmake_minimum_required(VERSION 3.22.1)

project("native_media")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(
        native_media
        SHARED
        native-media/native_media_jni.cpp
        native-media/audio/waveform_processor.cpp
        native-media/call/call_core.cpp
)

target_include_directories(
        native_media
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/native-media
)

find_library(log-lib log)

target_link_libraries(
        native_media
        ${log-lib}
)
