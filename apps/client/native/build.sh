#!/bin/sh
# Builds libtox4j-c.so, the binding's JNI library, with toxcore and everything it needs linked in, and on
# Linux desktops libktox-v4l2.so, the webcam for calls.
#
# Usage: build.sh desktop          for this machine, into native/out/desktop/OS-ARCH
#        build.sh android ABI...   for Android ABIs, into native/out/android/ABI
#
# Needs cmake, ninja, make, perl, pkg-config, nasm, a C/C++ compiler and a JDK; Android also needs
# ANDROID_NDK_HOME. c-toxcore comes from TOXCORE_SRC if set, otherwise its master is cloned.
# Everything is built in BUILD_DIR, by default ~/.cache/tox4j-client.
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
TOX4J=$(cd "$HERE/../../.." && pwd)
BUILD_DIR=${BUILD_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/tox4j-client}
DOWNLOADS=$BUILD_DIR/downloads
JOBS=$(nproc)

# Unpacks a checked download into $BUILD/$1 and goes there.
unpack() {
  name=$1 url=$2 sha256=$3
  file=$DOWNLOADS/$(basename "$url")
  [ -f "$file" ] || curl -fsSLo "$file" "$url"
  echo "$sha256  $file" | sha256sum -c
  rm -rf "${BUILD:?}/$name" && mkdir -p "$BUILD/$name"
  tar xf "$file" -C "$BUILD/$name" --strip-components=1
  cd "$BUILD/$name"
}

# Whether $1 still needs building in $BUILD.
todo() {
  [ ! -f "$BUILD/$1.done" ]
}

done_() {
  touch "$BUILD/$1.done"
}

# Builds everything for one target. Set before: NAME, OUT, CONFIGURE_HOST, VPX_TARGET, VPX_AS, CMAKE_TARGET and
# LDFLAGS_SO.
build() {
  BUILD=$BUILD_DIR/$NAME
  PREFIX=$BUILD/prefix
  mkdir -p "$BUILD" "$DOWNLOADS" "$OUT"
  export PKG_CONFIG_LIBDIR=$PREFIX/lib/pkgconfig
  export PKG_CONFIG_PATH=$PKG_CONFIG_LIBDIR
  CMAKE="-G Ninja -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=$PREFIX -DCMAKE_INSTALL_LIBDIR=lib
    -DCMAKE_PREFIX_PATH=$PREFIX -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DBUILD_SHARED_LIBS=OFF
    -DCMAKE_CXX_STANDARD=17 $CMAKE_TARGET"

  if todo libsodium; then
    unpack libsodium https://github.com/jedisct1/libsodium/releases/download/1.0.22-RELEASE/libsodium-1.0.22.tar.gz \
      adbdd8f16149e81ac6078a03aca6fc03b592b89ef7b5ed83841c086191be3349
    # shellcheck disable=SC2086
    ./configure $CONFIGURE_HOST --prefix="$PREFIX" --disable-shared --with-pic
    make -j"$JOBS" install
    done_ libsodium
  fi

  if todo opus; then
    unpack opus https://downloads.xiph.org/releases/opus/opus-1.6.1.tar.gz \
      6ffcb593207be92584df15b32466ed64bbec99109f007c82205f0194572411a1
    # shellcheck disable=SC2086
    ./configure $CONFIGURE_HOST --prefix="$PREFIX" --disable-shared --with-pic --disable-doc \
      --disable-extra-programs
    make -j"$JOBS" install
    done_ opus
  fi

  if todo libvpx; then
    unpack libvpx https://storage.googleapis.com/downloads.webmproject.org/releases/webm/libvpx-1.17.0.tar.gz \
      7f98aae5c52c46e85b7b26feabcab9b1554605e771bd1df1d6e6a00ee22fa8ed
    # shellcheck disable=SC2086
    AS=$VPX_AS ./configure --prefix="$PREFIX" $VPX_TARGET --disable-vp9 --disable-examples \
      --disable-tools --disable-docs --disable-unit-tests --enable-pic
    # libvpx's Android targets assume GNU as and strip.
    case $NAME in
      android-*) sed -i -e "s|^AS=as|AS=$CC -c|" -e "s|^STRIP=strip|STRIP=$STRIP|" ./*.mk ;;
    esac
    make -j"$JOBS" install
    done_ libvpx
  fi

  rm -rf "$BUILD/c-toxcore"
  if [ -n "${TOXCORE_SRC-}" ]; then
    cp -r "$TOXCORE_SRC" "$BUILD/c-toxcore"
  else
    git clone --depth=1 --recursive https://github.com/TokTok/c-toxcore "$BUILD/c-toxcore"
  fi
  cd "$BUILD/c-toxcore"
  # shellcheck disable=SC2086
  cmake -B _build $CMAKE -DENABLE_SHARED=OFF -DENABLE_STATIC=ON -DBOOTSTRAP_DAEMON=OFF \
    -DDHT_BOOTSTRAP=OFF -DMUST_BUILD_TOXAV=ON -DEXPERIMENTAL_API=ON -DUNITTEST=OFF -DAUTOTEST=OFF
  cmake --build _build -j"$JOBS" --target install

  rm -rf "$BUILD/tox4j"
  # shellcheck disable=SC2086
  cmake -S "$TOX4J/lib/src/main/cpp" -B "$BUILD/tox4j" $CMAKE \
    -DCMAKE_SHARED_LINKER_FLAGS="$LDFLAGS_SO" -DCMAKE_SKIP_RPATH=ON
  cmake --build "$BUILD/tox4j" -j"$JOBS"
  "${STRIP:-strip}" -o "$OUT/libtox4j-c.so" "$BUILD/tox4j/libtox4j-c.so"
  echo "built $OUT/libtox4j-c.so"
}

desktop() {
  NAME=desktop
  # Compose Desktop's names, so packaging takes the library for its platform.
  case $(uname -s) in
    Linux) OS=linux ;;
    Darwin) OS=macos ;;
    *) echo "unsupported OS: $(uname -s)" >&2 && exit 1 ;;
  esac
  case $(uname -m) in
    x86_64) ARCH=x64 ;;
    aarch64 | arm64) ARCH=arm64 ;;
    *) echo "unsupported CPU: $(uname -m)" >&2 && exit 1 ;;
  esac
  OUT=$HERE/out/desktop/$OS-$ARCH
  CONFIGURE_HOST=
  VPX_TARGET=
  VPX_AS=nasm
  CMAKE_TARGET=
  # The library runs in other people's JVMs, so it takes no C++ runtime with it.
  LDFLAGS_SO="-static-libstdc++ -static-libgcc"
  build
  if [ "$OS" = linux ]; then
    cc -O2 -Wall -Wextra -Wno-unused-parameter -shared -fPIC -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
      "$HERE/v4l2.c" -o "$OUT/libktox-v4l2.so"
  fi
}

android() {
  ABI=$1
  API=26
  case $ABI in
    arm64-v8a) TARGET=aarch64-linux-android VPX=arm64-android-gcc ;;
    armeabi-v7a) TARGET=armv7a-linux-androideabi VPX=armv7-android-gcc ;;
    x86_64) TARGET=x86_64-linux-android VPX=x86_64-android-gcc ;;
    x86) TARGET=i686-linux-android VPX=x86-android-gcc ;;
    *) echo "unknown ABI: $ABI" >&2 && exit 1 ;;
  esac
  NDK=$ANDROID_NDK_HOME
  TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64
  NAME=android-$ABI
  OUT=$HERE/out/android/$ABI
  CONFIGURE_HOST=--host=$TARGET
  VPX_TARGET="--target=$VPX --libc=$TOOLCHAIN/sysroot"
  VPX_AS=
  CMAKE_TARGET="-DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=$ABI
    -DANDROID_PLATFORM=android-$API -DANDROID_STL=c++_static
    -DCMAKE_FIND_ROOT_PATH=$BUILD_DIR/$NAME/prefix"
  # Android 15 devices can use 16 KB pages.
  LDFLAGS_SO="-Wl,-z,max-page-size=16384 -llog"
  (
    # Host binutils from the environment would build for the wrong machine.
    unset AS LD NM OBJCOPY OBJDUMP READELF SIZE STRINGS
    export CC=$TOOLCHAIN/bin/$TARGET$API-clang
    export CXX=$TOOLCHAIN/bin/$TARGET$API-clang++
    export AR=$TOOLCHAIN/bin/llvm-ar
    export RANLIB=$TOOLCHAIN/bin/llvm-ranlib
    export STRIP=$TOOLCHAIN/bin/llvm-strip
    export CFLAGS="-O2 -fPIC"
    build
  )
}

case ${1-} in
  desktop) desktop ;;
  android)
    shift
    [ $# -gt 0 ] || set -- arm64-v8a x86_64
    for abi in "$@"; do
      (android "$abi")
    done
    ;;
  *) echo "usage: $0 desktop | android [ABI...]" >&2 && exit 1 ;;
esac
