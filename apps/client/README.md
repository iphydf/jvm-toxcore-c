# kTox

A Tox chat client for desktops and Android phones, written with Compose
Multiplatform on top of tox4j from this repository.

- `shared`: the client (`ToxClient`, the SQLite store) and its whole UI. It shows the
  chat list beside the open chat on wide windows and one page at a time on phones.
- `desktopApp`: the desktop window.
- `androidApp`: the Android app, which keeps Tox connected in a foreground service and
  shows notifications for messages, friend requests and calls.

Calls are with one friend at a time, with sound and video. On Android, the microphone and
camera keep going in the background, and a call rings over the lock screen. On desktops,
sound goes through Java Sound's default devices, and on Linux video comes from webcams
that Video4Linux gives in YUYV.

## Native library

tox4j needs `libtox4j-c.so`, with toxcore linked in. `native/build.sh` builds it and its
dependencies in `~/.cache/tox4j-client` and puts it in `native/out`, with
`libktox-v4l2.so`, the webcam for calls, on Linux desktops:

```sh
native/build.sh desktop                  # this machine
native/build.sh android arm64-v8a x86_64 # needs ANDROID_NDK_HOME
```

It needs cmake, ninja, make, perl, pkg-config, nasm, a C/C++ compiler and a JDK.
c-toxcore comes from `TOXCORE_SRC` if set, otherwise its master is cloned.

## Running

```sh
./gradlew :desktopApp:run                   # the desktop app
./gradlew :desktopApp:run --args='--profile DIR'
./gradlew :desktopApp:packageDeb            # also packageRpm, packageAppImage
./gradlew :androidApp:assembleDebug         # androidApp/build/outputs/apk
./gradlew :shared:jvmTest                   # tests, with the desktop library
```

In the toktok-stack workspace, Bazel builds the desktop app and its native libraries
itself (Android stays with Gradle):

```sh
bazel run //jvm-toxcore-c/apps/client:ktox -- [--profile DIR]
bazel test //jvm-toxcore-c/apps/client:all
```

The desktop profile is in `~/.local/share/ktox` unless `--profile` says otherwise:
`profile.tox` for toxcore and `chats.db` for chats and settings.

## Bootstrap nodes

kTox starts from the nodes in `Nodes.kt` and never fetches a node list itself. Run
`tools/update-nodes` before each release.
