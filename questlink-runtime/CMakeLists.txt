cmake_minimum_required(VERSION 3.24)
project(QuestLinkOpenXRRuntime LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(QuestLinkOpenXRRuntime SHARED
    runtime.cpp
)

target_compile_definitions(QuestLinkOpenXRRuntime PRIVATE
    WIN32_LEAN_AND_MEAN
    NOMINMAX
    XR_NO_PROTOTYPES
    XR_USE_PLATFORM_WIN32
    XR_USE_GRAPHICS_API_D3D11
)

target_include_directories(QuestLinkOpenXRRuntime PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(QuestLinkOpenXRRuntime PRIVATE
    d3d11
    dxgi
    dxguid
    ws2_32
    windowscodecs
    ole32
)

if(MSVC)
    target_compile_options(QuestLinkOpenXRRuntime PRIVATE /W4 /permissive-)
    target_link_options(QuestLinkOpenXRRuntime PRIVATE "/DEF:${CMAKE_CURRENT_SOURCE_DIR}/QuestLinkOpenXRRuntime.def")
endif()
