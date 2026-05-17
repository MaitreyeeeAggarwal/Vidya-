cmake_minimum_required(VERSION 3.18.1)
project("vidya_native")

# ONNX Runtime Mobile — downloaded as a prebuilt AAR or extracted .so
# Set ONNXRUNTIME_ROOT to the extracted directory containing include/ and lib/
set(ONNXRUNTIME_ROOT "${CMAKE_SOURCE_DIR}/../onnxruntime-mobile")

add_library(indic_tts_jit SHARED indic_tts_jit.cpp)
add_library(whisper_audio_math SHARED whisper_audio_math.cpp)

target_include_directories(indic_tts_jit PRIVATE
    ${ONNXRUNTIME_ROOT}/include
)

target_link_libraries(indic_tts_jit
    ${ONNXRUNTIME_ROOT}/lib/${ANDROID_ABI}/libonnxruntime.so
    android
    log
)

target_link_libraries(whisper_audio_math
    android
    log
)
