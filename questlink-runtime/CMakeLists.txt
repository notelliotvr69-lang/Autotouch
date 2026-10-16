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
    oleaut32
    mfplat
    mf
    mfuuid
    wmcodecdspuuid
)

if(MSVC)
    target_compile_options(QuestLinkOpenXRRuntime PRIVATE /W4 /permissive-)
    target_link_options(QuestLinkOpenXRRuntime PRIVATE "/DEF:${CMAKE_CURRENT_SOURCE_DIR}/QuestLinkOpenXRRuntime.def")
endif()

include(CTest)
if(BUILD_TESTING)
    add_executable(protocol_test ../questlink-common/protocol_test.cpp)
    target_include_directories(protocol_test PRIVATE include)
    add_executable(runtime_test runtime_test.cpp)
    target_include_directories(runtime_test PRIVATE include)
    target_compile_definitions(runtime_test PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX XR_NO_PROTOTYPES XR_USE_PLATFORM_WIN32 XR_USE_GRAPHICS_API_D3D11)
    target_link_libraries(runtime_test PRIVATE d3d11 dxgi dxguid ws2_32 windowscodecs ole32 oleaut32 mfplat mf mfuuid wmcodecdspuuid)
    if(MSVC)
        target_compile_options(protocol_test PRIVATE /UNDEBUG)
        target_compile_options(runtime_test PRIVATE /UNDEBUG)
    endif()
    add_test(NAME protocol COMMAND protocol_test)
    add_test(NAME runtime COMMAND runtime_test)
endif()
