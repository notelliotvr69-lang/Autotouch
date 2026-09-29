cmake_minimum_required(VERSION 3.24)
project(QuestLinkOpenVRDriver LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(driver_questlink SHARED src/driver.cpp)
target_include_directories(driver_questlink PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/third_party)
target_compile_definitions(driver_questlink PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX)
target_link_libraries(driver_questlink PRIVATE d3d11 dxgi dxguid ws2_32 windowscodecs ole32)
set_target_properties(driver_questlink PROPERTIES PREFIX "" OUTPUT_NAME "driver_questlink")

if(MSVC)
  target_compile_options(driver_questlink PRIVATE /W4 /EHsc /permissive-)
endif()