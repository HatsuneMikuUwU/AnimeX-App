cmake_minimum_required(VERSION 3.22.1)
project(animexcore LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

foreach(k AX_K0 AX_K1 AX_K2 AX_K3)
    if(NOT DEFINED ${k})
        set(${k} "")
    endif()
endforeach()

add_library(animexcore SHARED
    ax_core.cpp
    jni_bridge.cpp
)

target_compile_definitions(animexcore PRIVATE
    "AX_K0=\"${AX_K0}\""
    "AX_K1=\"${AX_K1}\""
    "AX_K2=\"${AX_K2}\""
    "AX_K3=\"${AX_K3}\""
)

target_compile_options(animexcore PRIVATE
    -Os -Wall -Wextra
    -ffunction-sections -fdata-sections
)

target_link_options(animexcore PRIVATE
    -Wl,--gc-sections
    -Wl,--build-id=none
    -Wl,-z,max-page-size=16384
    -s
)
