cmake_minimum_required(VERSION 3.20)
project(QuestLinkOpenXRRuntime LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(QuestLinkOpenXRRuntime SHARED
    runtime.cpp
)

target_compile_definitions(QuestLinkOpenXRRuntime PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX)

if(MSVC)
    target_compile_options(QuestLinkOpenXRRuntime PRIVATE /W4 /permissive-)
    target_link_options(QuestLinkOpenXRRuntime PRIVATE "/DEF:${CMAKE_CURRENT_SOURCE_DIR}/QuestLinkOpenXRRuntime.def")
endif()
