/*
 * The webcam on Linux, for the desktop app's calls: Video4Linux devices that capture YUYV, read through
 * memory-mapped buffers. The Kotlin side is chat.tox.ktox.V4l2.
 */
#define _POSIX_C_SOURCE 200809L // O_CLOEXEC, and the struct timespec videodev2.h uses.

#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <poll.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <unistd.h>

#include <linux/videodev2.h>

enum { CAMERA_BUFFERS = 4 };

typedef struct Camera {
    int fd;
    int width;
    int height;
    int stride;
    int buffers;
    void *start[CAMERA_BUFFERS];
    size_t length[CAMERA_BUFFERS];
} Camera;

static int xioctl(int fd, unsigned long request, void *arg)
{
    int r;
    do {
        r = ioctl(fd, request, arg);
    } while (r == -1 && errno == EINTR);
    return r;
}

/* Whether the device captures pictures we can stream. */
static int can_capture(int fd)
{
    struct v4l2_capability cap = {0};
    if (xioctl(fd, VIDIOC_QUERYCAP, &cap) < 0) {
        return 0;
    }
    const unsigned caps = (cap.capabilities & V4L2_CAP_DEVICE_CAPS) != 0 ? cap.device_caps : cap.capabilities;
    return (caps & V4L2_CAP_VIDEO_CAPTURE) != 0 && (caps & V4L2_CAP_STREAMING) != 0;
}

static void camera_free(Camera *c)
{
    enum v4l2_buf_type type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    xioctl(c->fd, VIDIOC_STREAMOFF, &type);
    for (int i = 0; i < c->buffers; i++) {
        munmap(c->start[i], c->length[i]);
    }
    close(c->fd);
    free(c);
}

/* Starts a device capturing YUYV at about width x height, or returns NULL. */
static Camera *camera_open(const char *path, int width, int height)
{
    Camera *c = calloc(1, sizeof(Camera));
    if (c == NULL) {
        return NULL;
    }
    c->fd = open(path, O_RDWR | O_NONBLOCK | O_CLOEXEC);
    if (c->fd < 0) {
        free(c);
        return NULL;
    }
    struct v4l2_format fmt = {0};
    fmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    fmt.fmt.pix.width = width;
    fmt.fmt.pix.height = height;
    fmt.fmt.pix.pixelformat = V4L2_PIX_FMT_YUYV;
    fmt.fmt.pix.field = V4L2_FIELD_NONE;
    if (!can_capture(c->fd) || xioctl(c->fd, VIDIOC_S_FMT, &fmt) < 0 || fmt.fmt.pix.pixelformat != V4L2_PIX_FMT_YUYV) {
        camera_free(c);
        return NULL;
    }
    c->width = fmt.fmt.pix.width;
    c->height = fmt.fmt.pix.height;
    c->stride = fmt.fmt.pix.bytesperline;
    struct v4l2_requestbuffers req = {0};
    req.count = CAMERA_BUFFERS;
    req.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    req.memory = V4L2_MEMORY_MMAP;
    if (xioctl(c->fd, VIDIOC_REQBUFS, &req) < 0 || req.count == 0) {
        camera_free(c);
        return NULL;
    }
    for (unsigned i = 0; i < req.count && i < CAMERA_BUFFERS; i++) {
        struct v4l2_buffer buf = {0};
        buf.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        buf.memory = V4L2_MEMORY_MMAP;
        buf.index = i;
        if (xioctl(c->fd, VIDIOC_QUERYBUF, &buf) < 0) {
            camera_free(c);
            return NULL;
        }
        void *start = mmap(NULL, buf.length, PROT_READ | PROT_WRITE, MAP_SHARED, c->fd, buf.m.offset);
        if (start == MAP_FAILED) {
            camera_free(c);
            return NULL;
        }
        c->start[i] = start;
        c->length[i] = buf.length;
        c->buffers++;
        if (xioctl(c->fd, VIDIOC_QBUF, &buf) < 0) {
            camera_free(c);
            return NULL;
        }
    }
    enum v4l2_buf_type type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    if (xioctl(c->fd, VIDIOC_STREAMON, &type) < 0) {
        camera_free(c);
        return NULL;
    }
    return c;
}

JNIEXPORT jboolean JNICALL Java_chat_tox_ktox_V4l2_canCapture(JNIEnv *env, jclass cls, jstring path)
{
    const char *name = (*env)->GetStringUTFChars(env, path, NULL);
    if (name == NULL) {
        return JNI_FALSE;
    }
    const int fd = open(name, O_RDWR | O_NONBLOCK | O_CLOEXEC);
    (*env)->ReleaseStringUTFChars(env, path, name);
    if (fd < 0) {
        return JNI_FALSE;
    }
    const int ok = can_capture(fd);
    close(fd);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL Java_chat_tox_ktox_V4l2_open(JNIEnv *env, jclass cls, jstring path, jint width, jint height,
                                                     jintArray size)
{
    if ((*env)->GetArrayLength(env, size) < 3) {
        return 0;
    }
    const char *name = (*env)->GetStringUTFChars(env, path, NULL);
    if (name == NULL) {
        return 0;
    }
    Camera *c = camera_open(name, width, height);
    (*env)->ReleaseStringUTFChars(env, path, name);
    if (c == NULL) {
        return 0;
    }
    const jint got[3] = {c->width, c->height, c->stride};
    (*env)->SetIntArrayRegion(env, size, 0, 3, got);
    return (jlong)(intptr_t)c;
}

JNIEXPORT jint JNICALL Java_chat_tox_ktox_V4l2_read(JNIEnv *env, jclass cls, jlong handle, jbyteArray out,
                                                    jint timeout)
{
    Camera *c = (Camera *)(intptr_t)handle;
    struct pollfd p = {.fd = c->fd, .events = POLLIN};
    const int r = poll(&p, 1, timeout);
    if (r <= 0) {
        return r < 0 && errno != EINTR ? -1 : 0;
    }
    struct v4l2_buffer buf = {0};
    buf.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    buf.memory = V4L2_MEMORY_MMAP;
    if (xioctl(c->fd, VIDIOC_DQBUF, &buf) < 0) {
        return errno == EAGAIN ? 0 : -1;
    }
    const jsize capacity = (*env)->GetArrayLength(env, out);
    jsize used = buf.bytesused < c->length[buf.index] ? (jsize)buf.bytesused : (jsize)c->length[buf.index];
    if (used > capacity) {
        used = capacity;
    }
    (*env)->SetByteArrayRegion(env, out, 0, used, c->start[buf.index]);
    if (xioctl(c->fd, VIDIOC_QBUF, &buf) < 0) {
        return -1;
    }
    return used;
}

JNIEXPORT void JNICALL Java_chat_tox_ktox_V4l2_close(JNIEnv *env, jclass cls, jlong handle)
{
    camera_free((Camera *)(intptr_t)handle);
}
