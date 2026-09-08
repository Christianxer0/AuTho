cmake_minimum_required(VERSION 3.22.1)
project("autho")

add_library(native-lib SHARED
    native-lib.c
)

target_link_libraries(native-lib android log)
target_link_options(native-lib PRIVATE "-Wl,-z,max-page-size=16384")

