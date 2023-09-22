# Set this to the minimum version your project supports.
cmake_minimum_required(VERSION 3.18)
project(CrashHandler)
find_library(log-lib log)
add_library(native-lib SHARED src/main/cpp/native-lib.cpp)
target_link_libraries(native-lib ${log-lib})
