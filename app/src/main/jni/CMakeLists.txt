project(video_matting)

cmake_minimum_required(VERSION 3.10)

set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/opencv-mobile-4.5.4-android/sdk/native/jni)
find_package(OpenCV REQUIRED core imgproc)

set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn-20211208-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

include_directories(
        glm
        render
        ${CMAKE_SOURCE_DIR}/util
)

file(GLOB src-files
        ${CMAKE_SOURCE_DIR}/JniImp.cpp
        ${CMAKE_SOURCE_DIR}/BodySeg.cpp
        ${CMAKE_SOURCE_DIR}/nanodet.cpp
        ${CMAKE_SOURCE_DIR}/NativeContext.cpp
        ${CMAKE_SOURCE_DIR}/util/*.cpp
        ${CMAKE_SOURCE_DIR}/render/*.cpp
        )

add_library(video_matting SHARED ${src-files})

target_link_libraries(video_matting ncnn ${OpenCV_LIBS} camera2ndk mediandk GLESv3 EGL)
