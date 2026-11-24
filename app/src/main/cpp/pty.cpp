#include <jni.h>

#include <android/log.h>
#include <fcntl.h>
#include <poll.h>
#include <pty.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <unistd.h>

#include <atomic>
#include <cerrno>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

namespace {

constexpr const char* kTag = "PtyNative";
constexpr jint kReadClosed = -2;

constexpr int kExitKindRunning = 0;
constexpr int kExitKindExited = 1;
constexpr int kExitKindSignaled = 2;
constexpr int kExitKindUnknown = 3;

enum ChildStage : int32_t {
    kStageSetsid = 1,
    kStageControllingTerminal = 2,
    kStageStandardInput = 3,
    kStageStandardOutput = 4,
    kStageStandardError = 5,
    kStageChdir = 6,
    kStageExecve = 7,
};

struct ChildError {
    int32_t stage;
    int32_t code;
};

struct Handle {
    explicit Handle(int masterFd, pid_t childPid) : master(masterFd), pid(childPid) {}

    std::atomic<bool> closing{false};
    std::atomic<bool> closed{false};
    std::atomic<int> readers{0};
    std::mutex waitMutex;
    std::mutex stateMutex;
    int master;
    pid_t pid;
    int status = 0;
    bool exited = false;
};

void logError(const char* what) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "%s: %s", what, std::strerror(errno));
}

void throwIllegalState(JNIEnv* env, const std::string& message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type != nullptr) env->ThrowNew(type, message.c_str());
}

std::string stringOf(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars == nullptr ? "" : chars;
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::vector<std::string> stringsOf(JNIEnv* env, jobjectArray values) {
    std::vector<std::string> result;
    if (values == nullptr) return result;
    const jsize count = env->GetArrayLength(values);
    result.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        auto entry = static_cast<jstring>(env->GetObjectArrayElement(values, i));
        if (entry == nullptr) continue;
        const char* chars = env->GetStringUTFChars(entry, nullptr);
        result.emplace_back(chars == nullptr ? "" : chars);
        env->ReleaseStringUTFChars(entry, chars);
    }
    return result;
}

Handle* handleOf(jlong pointer) {
    return reinterpret_cast<Handle*>(pointer);
}

void writeAll(int fd, const void* data, size_t length) {
    const auto* bytes = static_cast<const uint8_t*>(data);
    size_t offset = 0;
    while (offset < length) {
        const ssize_t count = ::write(fd, bytes + offset, length - offset);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return;
        offset += static_cast<size_t>(count);
    }
}

const char* stageName(int32_t stage) {
    switch (stage) {
        case kStageSetsid: return "setsid";
        case kStageControllingTerminal: return "TIOCSCTTY";
        case kStageStandardInput: return "dup2(stdin)";
        case kStageStandardOutput: return "dup2(stdout)";
        case kStageStandardError: return "dup2(stderr)";
        case kStageChdir: return "chdir";
        case kStageExecve: return "execve";
        default: return "unknown";
    }
}

void failChild(int errorFd, int32_t stage, int code) {
    const ChildError error{stage, code};
    writeAll(errorFd, &error, sizeof(error));
    ::_exit(127);
}

bool isExited(Handle* handle) {
    std::lock_guard<std::mutex> lock(handle->stateMutex);
    return handle->exited;
}

void markExited(Handle* handle, int status) {
    std::lock_guard<std::mutex> lock(handle->stateMutex);
    if (!handle->exited) {
        handle->exited = true;
        handle->status = status;
    }
}

void refreshExit(Handle* handle) {
    if (isExited(handle)) return;
    std::lock_guard<std::mutex> lock(handle->waitMutex);
    if (isExited(handle)) return;

    int status = 0;
    pid_t result;
    do {
        result = ::waitpid(handle->pid, &status, WNOHANG);
    } while (result < 0 && errno == EINTR);

    if (result == handle->pid || (result < 0 && errno == ECHILD)) {
        markExited(handle, result == handle->pid ? status : 0);
    }
}

jlong encodeExit(int kind, int code) {
    return (static_cast<jlong>(code & 0xFF) << 8) | static_cast<jlong>(kind & 0xFF);
}

jlong exitInfo(Handle* handle) {
    refreshExit(handle);
    std::lock_guard<std::mutex> lock(handle->stateMutex);
    if (!handle->exited) return 0;
    if (WIFEXITED(handle->status)) {
        return encodeExit(kExitKindExited, WEXITSTATUS(handle->status));
    }
    if (WIFSIGNALED(handle->status)) {
        return encodeExit(kExitKindSignaled, WTERMSIG(handle->status));
    }
    return encodeExit(kExitKindUnknown, 0);
}

}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_pocketdev_terminal_PtyNative_open(
        JNIEnv* env,
        jclass,
        jint cols,
        jint rows,
        jstring cwd,
        jstring executable,
        jobjectArray argv,
        jobjectArray environment) {
    if (cols <= 0 || rows <= 0) {
        throwIllegalState(env, "PTY dimensions must be positive");
        return 0;
    }

    const std::string workingDirectory = stringOf(env, cwd);
    const std::string executablePath = stringOf(env, executable);
    const std::vector<std::string> arguments = stringsOf(env, argv);
    const std::vector<std::string> envStrings = stringsOf(env, environment);
    if (workingDirectory.empty() || executablePath.empty() || arguments.empty()) {
        throwIllegalState(env, "PTY launch requires cwd, executable, and argv");
        return 0;
    }

    std::vector<char*> childArguments;
    childArguments.reserve(arguments.size() + 1);
    for (const auto& value : arguments) childArguments.push_back(const_cast<char*>(value.c_str()));
    childArguments.push_back(nullptr);

    std::vector<char*> childEnvironment;
    childEnvironment.reserve(envStrings.size() + 1);
    for (const auto& value : envStrings) childEnvironment.push_back(const_cast<char*>(value.c_str()));
    childEnvironment.push_back(nullptr);

    int errorPipe[2] = {-1, -1};
    if (::pipe2(errorPipe, O_CLOEXEC) != 0) {
        logError("pipe2");
        throwIllegalState(env, std::string("pipe2 failed: ") + std::strerror(errno));
        return 0;
    }

    int master = -1;
    int slave = -1;
    struct winsize window {};
    window.ws_col = static_cast<unsigned short>(cols);
    window.ws_row = static_cast<unsigned short>(rows);
    if (::openpty(&master, &slave, nullptr, nullptr, &window) != 0) {
        logError("openpty");
        const int code = errno;
        ::close(errorPipe[0]);
        ::close(errorPipe[1]);
        throwIllegalState(env, std::string("openpty failed: ") + std::strerror(code));
        return 0;
    }

    const pid_t pid = ::fork();
    if (pid < 0) {
        logError("fork");
        const int code = errno;
        ::close(errorPipe[0]);
        ::close(errorPipe[1]);
        ::close(master);
        ::close(slave);
        throwIllegalState(env, std::string("fork failed: ") + std::strerror(code));
        return 0;
    }

    if (pid == 0) {
        ::close(errorPipe[0]);
        ::close(master);
        if (::setsid() < 0) failChild(errorPipe[1], kStageSetsid, errno);
        if (::ioctl(slave, TIOCSCTTY, 0) < 0) failChild(errorPipe[1], kStageControllingTerminal, errno);
        if (::dup2(slave, STDIN_FILENO) < 0) failChild(errorPipe[1], kStageStandardInput, errno);
        if (::dup2(slave, STDOUT_FILENO) < 0) failChild(errorPipe[1], kStageStandardOutput, errno);
        if (::dup2(slave, STDERR_FILENO) < 0) failChild(errorPipe[1], kStageStandardError, errno);
        if (slave > STDERR_FILENO) ::close(slave);
        if (::chdir(workingDirectory.c_str()) < 0) failChild(errorPipe[1], kStageChdir, errno);
        ::execve(executablePath.c_str(), childArguments.data(), childEnvironment.data());
        failChild(errorPipe[1], kStageExecve, errno);
    }

    ::close(errorPipe[1]);
    ::close(slave);

    ChildError childError{};
    size_t received = 0;
    while (received < sizeof(childError)) {
        ssize_t count;
        do {
            count = ::read(errorPipe[0], reinterpret_cast<uint8_t*>(&childError) + received, sizeof(childError) - received);
        } while (count < 0 && errno == EINTR);
        if (count <= 0) break;
        received += static_cast<size_t>(count);
    }
    ::close(errorPipe[0]);

    if (received > 0) {
        int status = 0;
        pid_t result;
        do {
            result = ::waitpid(pid, &status, 0);
        } while (result < 0 && errno == EINTR);
        ::close(master);
        const std::string message = std::string("PTY child setup failed at ") +
            stageName(childError.stage) + ": " + std::strerror(childError.code);
        throwIllegalState(env, message);
        return 0;
    }

    return reinterpret_cast<jlong>(new Handle(master, pid));
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_pocketdev_terminal_PtyNative_read(
        JNIEnv* env,
        jclass,
        jlong pointer,
        jbyteArray buffer) {
    Handle* handle = handleOf(pointer);
    if (handle == nullptr) return -1;
    const jsize length = env->GetArrayLength(buffer);
    if (length <= 0) return 0;
    if (handle->closing.load() || handle->master < 0) return kReadClosed;

    std::vector<jbyte> bytes(static_cast<size_t>(length));
    handle->readers.fetch_add(1);
    ssize_t count = -1;
    while (!handle->closing.load()) {
        struct pollfd descriptor {handle->master, POLLIN, 0};
        int ready;
        do {
            ready = ::poll(&descriptor, 1, 100);
        } while (ready < 0 && errno == EINTR);
        if (ready < 0) {
            count = -1;
            break;
        }
        if (ready == 0 || (descriptor.revents & (POLLIN | POLLHUP | POLLERR)) == 0) continue;
        do {
            count = ::read(handle->master, bytes.data(), bytes.size());
        } while (count < 0 && errno == EINTR);
        if (count < 0 && errno == EIO) count = 0;
        break;
    }
    handle->readers.fetch_sub(1);

    if (handle->closing.load()) return kReadClosed;
    if (count > 0) env->SetByteArrayRegion(buffer, 0, static_cast<jsize>(count), bytes.data());
    return static_cast<jint>(count);
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_pocketdev_terminal_PtyNative_write(
        JNIEnv* env,
        jclass,
        jlong pointer,
        jbyteArray buffer) {
    Handle* handle = handleOf(pointer);
    if (handle == nullptr) return -1;
    const jsize length = env->GetArrayLength(buffer);
    if (length <= 0) return 0;
    if (handle->closing.load() || handle->master < 0) return kReadClosed;

    std::vector<jbyte> bytes(static_cast<size_t>(length));
    env->GetByteArrayRegion(buffer, 0, length, bytes.data());

    size_t offset = 0;
    while (offset < bytes.size()) {
        const ssize_t count = ::write(
                handle->master,
                bytes.data() + offset,
                bytes.size() - offset);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return offset == 0 ? -1 : static_cast<jint>(offset);
        offset += static_cast<size_t>(count);
    }
    return length;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_pocketdev_terminal_PtyNative_resize(
        JNIEnv*,
        jclass,
        jlong pointer,
        jint cols,
        jint rows) {
    Handle* handle = handleOf(pointer);
    if (handle == nullptr || handle->closing.load() || cols <= 0 || rows <= 0) return JNI_FALSE;

    struct winsize window {};
    window.ws_col = static_cast<unsigned short>(cols);
    window.ws_row = static_cast<unsigned short>(rows);
    if (::ioctl(handle->master, TIOCSWINSZ, &window) != 0) {
        logError("ioctl(TIOCSWINSZ)");
        return JNI_FALSE;
    }

    refreshExit(handle);
    std::lock_guard<std::mutex> lock(handle->stateMutex);
    if (!handle->exited && handle->pid > 0) ::kill(-handle->pid, SIGWINCH);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_pocketdev_terminal_PtyNative_isRunning(JNIEnv*, jclass, jlong pointer) {
    Handle* handle = handleOf(pointer);
    if (handle == nullptr || handle->closing.load()) return JNI_FALSE;
    return exitInfo(handle) == 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_pocketdev_terminal_PtyNative_exitInfo(JNIEnv*, jclass, jlong pointer) {
    Handle* handle = handleOf(pointer);
    if (handle == nullptr) return 0;
    return exitInfo(handle);
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_pocketdev_terminal_PtyNative_waitForExit(
        JNIEnv*,
        jclass,
        jlong pointer,
        jint timeoutMillis) {
    Handle* handle = handleOf(pointer);
    if (handle == nullptr) return 0;

    const auto deadline = std::chrono::steady_clock::now() +
        std::chrono::milliseconds(timeoutMillis < 0 ? 0 : timeoutMillis);
    while (std::chrono::steady_clock::now() < deadline) {
        const jlong info = exitInfo(handle);
        if (info != 0) return info;
        ::usleep(10000);
    }
    return exitInfo(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_pocketdev_terminal_PtyNative_hangup(JNIEnv*, jclass, jlong pointer) {
    Handle* handle = handleOf(pointer);
    if (handle == nullptr || handle->closing.load()) return;
    refreshExit(handle);
    std::lock_guard<std::mutex> lock(handle->stateMutex);
    if (!handle->exited && handle->pid > 0) ::kill(-handle->pid, SIGHUP);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_pocketdev_terminal_PtyNative_terminate(JNIEnv*, jclass, jlong pointer) {
    Handle* handle = handleOf(pointer);
    if (handle == nullptr || handle->closing.load()) return;
    refreshExit(handle);
    std::lock_guard<std::mutex> lock(handle->stateMutex);
    if (!handle->exited && handle->pid > 0) ::kill(-handle->pid, SIGKILL);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_pocketdev_terminal_PtyNative_requestStop(JNIEnv*, jclass, jlong pointer) {
    Handle* handle = handleOf(pointer);
    if (handle == nullptr) return;
    handle->closing.store(true);
    refreshExit(handle);
    std::lock_guard<std::mutex> lock(handle->stateMutex);
    if (!handle->exited && handle->pid > 0) ::kill(-handle->pid, SIGHUP);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_pocketdev_terminal_PtyNative_close(JNIEnv*, jclass, jlong pointer) {
    Handle* handle = handleOf(pointer);
    if (handle == nullptr) return;
    bool expected = false;
    if (!handle->closed.compare_exchange_strong(expected, true)) return;

    handle->closing.store(true);
    refreshExit(handle);
    {
        std::lock_guard<std::mutex> lock(handle->stateMutex);
        if (!handle->exited && handle->pid > 0) ::kill(-handle->pid, SIGHUP);
    }

    for (int attempt = 0; attempt < 50 && !isExited(handle); ++attempt) {
        refreshExit(handle);
        ::usleep(20000);
    }

    {
        std::lock_guard<std::mutex> wait(handle->waitMutex);
        if (!isExited(handle)) {
            bool killRequired;
            {
                std::lock_guard<std::mutex> state(handle->stateMutex);
                killRequired = !handle->exited && handle->pid > 0;
            }
            if (killRequired) ::kill(-handle->pid, SIGKILL);
            int status = 0;
            pid_t result;
            do {
                result = ::waitpid(handle->pid, &status, 0);
            } while (result < 0 && errno == EINTR);
            markExited(handle, result == handle->pid ? status : 0);
        }
    }

    for (int attempt = 0; attempt < 50 && handle->readers.load() > 0; ++attempt) {
        ::usleep(20000);
    }

    if (handle->master >= 0) ::close(handle->master);
    handle->master = -1;
    delete handle;
}
