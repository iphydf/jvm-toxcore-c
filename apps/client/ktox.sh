#!/usr/bin/env bash
# Runs kTox with a JDK that has AWT's X11 libraries (the build's is headless).
# That JDK comes from nix and doesn't look in the system's library directories,
# so Skiko gets nix's libGL.so.1, and on NixOS the GPU drivers too.
set -eu
java=$PWD/$1 libgl=$(dirname "$(readlink -f "$PWD/$2")") app=$PWD/$3
shift 3
LD_LIBRARY_PATH=$libgl${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}
if [ -d /run/opengl-driver/lib ]; then
  LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/run/opengl-driver/lib
fi
export LD_LIBRARY_PATH
JAVABIN=$java exec "$app" "$@"
