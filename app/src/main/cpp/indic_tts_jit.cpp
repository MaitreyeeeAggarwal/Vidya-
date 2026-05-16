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
    //
    // Step 1: Convert input text to phoneme IDs via a language-specific
    //         grapheme-to-phoneme (G2P) lookup table. For production, this
    //         would use AI4Bharat's Indic-G2P mappings.
    //
    // Step 2: Run FastPitch acoustic model
    //   Input:  int64[1, seq_len]  (phoneme IDs)
    //   Output: float[1, 80, T]    (mel-spectrogram)
    //
    // Step 3: Run HiFi-GAN vocoder
    //   Input:  float[1, 80, T]    (mel-spectrogram from step 2)
    //   Output: float[1, 1, T*256] (raw audio waveform at 22050 Hz)
    //
    // Step 4: Convert float waveform to int16 PCM samples

    // Placeholder: return silence until G2P + full inference is wired
    std::vector<int16_t> pcm_output;

    LOGI("Synthesis placeholder for: %.40s...", input_text);

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
