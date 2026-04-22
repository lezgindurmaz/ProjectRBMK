cmake_minimum_required(VERSION 3.22.1)

project("rbmk_simulator")

# C++17 standardı
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimizasyon bayrakları
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    add_compile_options(-O3 -DNDEBUG -ffast-math)
else()
    add_compile_options(-O0 -g -DDEBUG)
endif()

# Uyarıları aç
add_compile_options(-Wall -Wextra)

# Kaynak dosyaları
add_library(
    rbmk_reactor
    SHARED
    reactor_engine.cpp
    reactor_jni.cpp
)

# Android log kütüphanesi
find_library(log-lib log)

# Bağlantı
target_link_libraries(
    rbmk_reactor
    ${log-lib}
)

# NDK başlık dosyaları
target_include_directories(rbmk_reactor PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)
