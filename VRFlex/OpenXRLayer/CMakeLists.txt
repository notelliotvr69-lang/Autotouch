cmake_minimum_required(VERSION 3.22.1)
project(vrflex_openxr_pull_layer LANGUAGES CXX)

if(NOT DEFINED OPENXR_SDK_INCLUDE)
  message(FATAL_ERROR "Set OPENXR_SDK_INCLUDE to the Khronos OpenXR-SDK include directory")
endif()

add_library(XR_APILAYER_NOVENDOR_pull SHARED src/XR_APILAYER_NOVENDOR_pull.cpp)
target_include_directories(XR_APILAYER_NOVENDOR_pull PRIVATE "${OPENXR_SDK_INCLUDE}")
target_compile_features(XR_APILAYER_NOVENDOR_pull PRIVATE cxx_std_17)
target_compile_definitions(XR_APILAYER_NOVENDOR_pull PRIVATE XR_USE_PLATFORM_ANDROID)
target_compile_options(XR_APILAYER_NOVENDOR_pull PRIVATE -Wall -Wextra -Werror)
set_target_properties(XR_APILAYER_NOVENDOR_pull PROPERTIES
    OUTPUT_NAME "XR_APILAYER_NOVENDOR_pull"
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN YES)
