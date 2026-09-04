cmake_minimum_required(VERSION 3.22.1)
project("autho")

add_library(${CMAKE_PROJECT_NAME} SHARED
        autho.cpp
        native-lib.c

)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
)