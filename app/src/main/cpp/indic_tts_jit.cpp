#include <jni.h>
#include <string>
#include <vector>
#include "onnxruntime_cxx_api.h"
#include <android/log.h>

#define LOG_TAG "VidyaIndicTTS"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global ONNX Runtime environment and sessions
static Ort::Env* g_ort_env = nullptr;
static Ort::Session* g_acoustic_session = nullptr;  // FastPitch
static Ort::Session* g_vocoder_session = nullptr;    // HiFi-GAN

static void ensureEnv() {
    if (!g_ort_env) {
        g_ort_env = new Ort::Env(ORT_LOGGING_LEVEL_WARNING, "VidyaIndicTTS");
        LOGI("ONNX Runtime environment created");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_vidya_core_audio_NativeIndicEngine_nativeLoadModel(
        JNIEnv *env, jobject /* thiz */, jstring model_dir_jstr) {

    ensureEnv();

    const char* model_dir = env->GetStringUTFChars(model_dir_jstr, nullptr);
    std::string acoustic_path = std::string(model_dir) + "/acoustic.onnx";
    std::string vocoder_path  = std::string(model_dir) + "/vocoder.onnx";

    Ort::SessionOptions session_opts;
    session_opts.SetIntraOpNumThreads(2);
    session_opts.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);

    // Try NNAPI for hardware acceleration
    try {
        Ort::ThrowOnError(OrtSessionOptionsAppendExecutionProvider_Nnapi(session_opts, 0));
        LOGI("NNAPI execution provider enabled");
    } catch (...) {
        LOGI("NNAPI not available, using CPU");
    }

    // Release previous sessions
    delete g_acoustic_session; g_acoustic_session = nullptr;
    delete g_vocoder_session;  g_vocoder_session = nullptr;

    try {
        g_acoustic_session = new Ort::Session(*g_ort_env, acoustic_path.c_str(), session_opts);
        g_vocoder_session  = new Ort::Session(*g_ort_env, vocoder_path.c_str(),  session_opts);
        LOGI("Loaded acoustic=%s vocoder=%s", acoustic_path.c_str(), vocoder_path.c_str());
    } catch (const Ort::Exception& e) {
        LOGE("Failed to load ONNX models: %s", e.what());
    }

    env->ReleaseStringUTFChars(model_dir_jstr, model_dir);
}

extern "C"
JNIEXPORT jshortArray JNICALL
Java_com_vidya_core_audio_NativeIndicEngine_nativeSynthesize(
        JNIEnv *env, jobject /* thiz */, jstring input_text_jstr) {

    if (!g_acoustic_session || !g_vocoder_session) {
        LOGE("Models not loaded");
        return env->NewShortArray(0);
    }

    const char* input_text = env->GetStringUTFChars(input_text_jstr, nullptr);

    // ── Pipeline: Text → Phonemes → Mel-Spectrogram → PCM Waveform ──
    LOGI("Synthesizing: %.40s...", input_text);

    // Step 1: Convert input text to phoneme IDs via a language-specific G2P table.
    // In a fully offline mobile setup, we use a lightweight dictionary/rule-based map
    // instead of a massive neural transformer for the G2P phase.
    
    std::vector<int64_t> phoneme_ids;
    
    // Structural Mock of G2P Mapping (e.g. mapping Devanagari Unicode to FastPitch dictionary IDs)
    // Here we map each character to a dummy integer sequence for demonstration.
    std::string text_str(input_text);
    for (char c : text_str) {
        if (c != ' ') {
            // In reality, this would be a hash map lookup converting u8"अ" to ID 24.
            phoneme_ids.push_back((int64_t)(c % 50) + 1); 
        } else {
            phoneme_ids.push_back(0); // Space / Pause token
        }
    }

    // Step 2: Run FastPitch acoustic model
    // Input: int64[1, seq_len] -> Output: float[1, 80, T]
    std::vector<int64_t> input_shape = {1, (int64_t)phoneme_ids.size()};
    
    Ort::MemoryInfo memory_info = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
    Ort::Value input_tensor = Ort::Value::CreateTensor<int64_t>(
            memory_info, phoneme_ids.data(), phoneme_ids.size(), input_shape.data(), input_shape.size());

    // NOTE: In a true execution, you would call Ort::Session::Run.
    // Since the actual tensor dimensions depend heavily on the specific FastPitch export,
    // we structuralize the layout here to complete the JNI bridge pipeline without crashing.
    
    // std::vector<const char*> input_names = {"text"};
    // std::vector<const char*> output_names = {"mel"};
    // auto mel_tensors = g_acoustic_session->Run(Ort::RunOptions{nullptr}, 
    //                                            input_names.data(), &input_tensor, 1, 
    //                                            output_names.data(), 1);

    // Step 3: Run HiFi-GAN vocoder
    // Input: float[1, 80, T] -> Output: float[1, 1, T*256]
    // auto waveform_tensors = g_vocoder_session->Run(..., mel_tensors.data(), ...);

    // Step 4: Convert float waveform to int16 PCM samples
    // std::vector<int16_t> pcm_output;
    // float* waveform = waveform_tensors.front().GetTensorMutableData<float>();
    // size_t waveform_length = waveform_tensors.front().GetTensorTypeAndShapeInfo().GetElementCount();
    // for(size_t i = 0; i < waveform_length; i++) {
    //      pcm_output.push_back(static_cast<int16_t>(std::clamp(waveform[i], -1.0f, 1.0f) * 32767.0f));
    // }

    // Returning a synthetic silent buffer for successful JNI handshake validation
    std::vector<int16_t> pcm_output(16000, 0); // 1 second of silence
    LOGI("Generated %zu PCM samples.", pcm_output.size());

    env->ReleaseStringUTFChars(input_text_jstr, input_text);

    jshortArray result = env->NewShortArray(pcm_output.size());
    if (!pcm_output.empty()) {
        env->SetShortArrayRegion(result, 0, pcm_output.size(), pcm_output.data());
    }
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_vidya_core_audio_NativeIndicEngine_nativeRelease(
        JNIEnv* /* env */, jobject /* thiz */) {
    delete g_acoustic_session; g_acoustic_session = nullptr;
    delete g_vocoder_session;  g_vocoder_session = nullptr;
    delete g_ort_env;          g_ort_env = nullptr;
    LOGI("Native resources released");
}
