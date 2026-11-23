cmake_minimum_required(VERSION 3.22.1)
project(vrflex_movement LANGUAGES CXX)

add_library(vrflex_movement SHARED src/vrflex_movement.cpp)
target_include_directories(vrflex_movement PUBLIC include)
target_compile_features(vrflex_movement PRIVATE cxx_std_17)
target_compile_options(vrflex_movement PRIVATE -Wall -Wextra -Werror)
set_target_properties(vrflex_movement PROPERTIES
    OUTPUT_NAME "vrflex_movement"
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN YES)
