cmake_minimum_required(VERSION 3.24)
project(QuestLinkRuntimeProxy LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
include(FetchContent)
FetchContent_Declare(openxr_sdk
  GIT_REPOSITORY https://github.com/KhronosGroup/OpenXR-SDK.git
  GIT_TAG release-1.1.51
  GIT_SHALLOW TRUE)
FetchContent_MakeAvailable(openxr_sdk)
add_library(QuestLinkRuntimeProxy SHARED QuestLinkRuntimeProxy.cpp)
target_include_directories(QuestLinkRuntimeProxy PRIVATE
  ${openxr_sdk_SOURCE_DIR}/include
  ${openxr_sdk_SOURCE_DIR}/src/loader)
if(MSVC)
  target_compile_options(QuestLinkRuntimeProxy PRIVATE /W4 /EHsc)
endif()
set_target_properties(QuestLinkRuntimeProxy PROPERTIES OUTPUT_NAME "QuestLinkRuntimeProxy")
