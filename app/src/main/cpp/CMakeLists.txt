cmake_minimum_required(VERSION 3.22.1)
project("autho")

add_library(native-lib SHARED
        native-lib.c
)

find_library(log-lib log)
find_library(android-lib android)
find_library(opensles-lib OpenSLES)   # <-- fixes slCreateEngine / SL_IID_*

target_link_libraries(
        native-lib
        ${log-lib}
        ${android-lib}
        ${opensles-lib}
)

target_link_options(native-lib PRIVATE "-Wl,-z,max-page-size=16384")