cmake_minimum_required(VERSION 3.22.1)
project(animexcore LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

foreach(masked_secret_name
        ANIMEX_MASKED_SECRET_MY_ANIME_LIST
        ANIMEX_MASKED_SECRET_ANILIST
        ANIMEX_MASKED_SECRET_API_GATE)
  if(NOT DEFINED ${masked_secret_name})
    set(${masked_secret_name} "")
  endif()
endforeach()

add_library(animexcore SHARED
  animex_core.cpp
  jni_bridge.cpp
)

target_compile_definitions(animexcore PRIVATE
  "ANIMEX_MASKED_SECRET_MY_ANIME_LIST=\"${ANIMEX_MASKED_SECRET_MY_ANIME_LIST}\""
  "ANIMEX_MASKED_SECRET_ANILIST=\"${ANIMEX_MASKED_SECRET_ANILIST}\""
  "ANIMEX_MASKED_SECRET_API_GATE=\"${ANIMEX_MASKED_SECRET_API_GATE}\""
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
