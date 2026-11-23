#include <jni.h>

#include <android/log.h>
#include <pty.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <unistd.h>

#include <cerrno>
#include <cstring>
#include <string>
#include <vector>

namespace {

constexpr const char* kTag = "PtyNative";

struct Handle {
    int master;
    pid_t pid;
    int status;
    bool exited;
};

void logError(const char* what) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "%s: %s", what, std::strerror(errno));
}

Handle* handleOf(jlong pointer) {
    return reinterpret_cast<Handle*>(pointer);
}

}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_pocketdev_terminal_PtyNative_open(
        JNIEnv* env,
        jclass,
        jint cols,
        jint rows,
        jstring cwd,
        jstring shell,
        jobjectArray environment) {
    if (cols <= 0 || rows <= 0) return 0;

    const char* cwdChars = env->GetStringUTFChars(cwd, nullptr);
    const char* shellChars = env->GetStringUTFChars(shell, nullptr);
    if (cwdChars == nullptr || shellChars == nullptr) {
        if (cwdChars != nullptr) env->ReleaseStringUTFChars(cwd, cwdChars);
        if (shellChars != nullptr) env->ReleaseStringUTFChars(shell, shellChars);
        return 0;
    }

    std::vector<std::string> envStrings;
    const jsize count = env->GetArrayLength(environment);
    for (jsize i = 0; i < count; ++i) {
        auto entry = static_cast<jstring>(env->GetObjectArrayElement(environment, i));
        if (entry == nullptr) continue;
        const char* chars = env->GetStringUTFChars(entry, nullptr);
        envStrings.emplace_back(chars == nullptr ? "" : chars);
        env->ReleaseStringUTFChars(entry, chars);
    }

    int master = -1;
    int slave = -1;
    struct winsize window {};
    window.ws_col = static_cast<unsigned short>(cols);
    window.ws_row = static_cast<unsigned short>(rows);
    if (::openpty(&master, &slave, nullptr, nullptr, &window) != 0) {
        logError("openpty");
        env->ReleaseStringUTFChars(cwd, cwdChars);
        env->ReleaseStringUTFChars(shell, shellChars);
        return 0;
    }

    const pid_t pid = ::fork();
    if (pid < 0) {
        logError("fork");
        ::close(master);
        ::close(slave);
        env->ReleaseStringUTFChars(cwd, cwdChars);
        env->ReleaseStringUTFChars(shell, shellChars);
        return 0;
    }

    if (pid == 0) {
        ::close(master);
        ::setsid();
        ::ioctl(slave, TIOCSCTTY, 0);
        ::dup2(slave, STDIN_FILENO);
        ::dup2(slave, STDOUT_FILENO);
        ::dup2(slave, STDERR_FILENO);
        if (slave > STDERR_FILENO) ::close(slave);
        ::chdir(cwdChars);

        std::vector<char*> childEnvironment;
        childEnvironment.reserve(envStrings.size() + 1);
        for (auto& value : envStrings) childEnvironment.push_back(const_cast<char*>(value.c_str()));
        childEnvironment.push_back(nullptr);

        char* arguments[] = {const_cast<char*>(shellChars), nullptr};
        ::execve(shellChars, arguments, childEnvironment.data());
        ::_exit(127);
    }

    ::close(slave);
    env->ReleaseStringUTFChars(cwd, cwdChars);
    env->ReleaseStringUTFChars(shell, shellChars);
    return reinterpret_cast<jlong>(new Handle{master, pid, 0, false});
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

    std::vector<jbyte> bytes(static_cast<size_t>(length));
    ssize_t count;
    do {
        count = ::read(handle->master, bytes.data(), bytes.size());
    } while (count < 0 && errno == EINTR);

    if (count > 0) {
        env->SetByteArrayRegion(buffer, 0, static_cast<jsize>(count), bytes.data());
    }
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
    if (handle == nullptr || cols <= 0 || rows <= 0) return JNI_FALSE;

    struct winsize window {};
    window.ws_col = static_cast<unsigned short>(cols);
    window.ws_row = static_cast<unsigned short>(rows);
    if (::ioctl(handle->master, TIOCSWINSZ, &window) != 0) {
        logError("ioctl(TIOCSWINSZ)");
        return JNI_FALSE;
    }
    if (!handle->exited) ::kill(-handle->pid, SIGWINCH);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_pocketdev_terminal_PtyNative_isRunning(JNIEnv*, jclass, jlong pointer) {
    Handle* handle = handleOf(pointer);
    if (handle == nullptr || handle->exited) return JNI_FALSE;

    int status = 0;
    const pid_t result = ::waitpid(handle->pid, &status, WNOHANG);
    if (result == handle->pid || (result < 0 && errno == ECHILD)) {
        handle->exited = true;
        handle->status = status;
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_pocketdev_terminal_PtyNative_hangup(JNIEnv*, jclass, jlong pointer) {
    Handle* handle = handleOf(pointer);
    if (handle == nullptr || handle->exited || handle->pid <= 0) return;
    ::kill(-handle->pid, SIGHUP);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_pocketdev_terminal_PtyNative_terminate(JNIEnv*, jclass, jlong pointer) {
    Handle* handle = handleOf(pointer);
    if (handle == nullptr || handle->exited || handle->pid <= 0) return;
    ::kill(-handle->pid, SIGKILL);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_pocketdev_terminal_PtyNative_close(JNIEnv*, jclass, jlong pointer) {
    Handle* handle = handleOf(pointer);
    if (handle == nullptr) return;

    if (handle->pid > 0 && !handle->exited) {
        ::kill(-handle->pid, SIGHUP);
        for (int attempt = 0; attempt < 10; ++attempt) {
            int status = 0;
            const pid_t result = ::waitpid(handle->pid, &status, WNOHANG);
            if (result == handle->pid || (result < 0 && errno == ECHILD)) {
                handle->exited = true;
                handle->status = status;
                break;
            }
            ::usleep(20000);
        }
        if (!handle->exited) {
            ::kill(-handle->pid, SIGKILL);
            int status = 0;
            ::waitpid(handle->pid, &status, 0);
            handle->exited = true;
            handle->status = status;
        }
    }

    if (handle->master >= 0) ::close(handle->master);
    delete handle;
}
