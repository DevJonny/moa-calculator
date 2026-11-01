#!/usr/bin/env bash
# One-off machine setup for the Wear OS app: JDK, Android SDK packages and a round Wear OS emulator.
# sdkmanager will ask you to review and accept the Android SDK licences.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
IMAGE="system-images;android-36;android-wear-signed;arm64-v8a"
WEAR_DIR="$(cd "$(dirname "$0")/.." && pwd)"

brew install openjdk@21
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  # The old 5.0 install lost its executable bit, so run it through sh.
  sh "$SDK/cmdline-tools/5.0/bin/sdkmanager" "cmdline-tools;latest"
fi
"$SDK/cmdline-tools/latest/bin/sdkmanager" emulator platform-tools "platforms;android-37.2" "build-tools;36.0.0" "$IMAGE"

echo no | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd --force -n Pixel_Watch -k "$IMAGE" -d wearos_large_round

echo "sdk.dir=$SDK" > "$WEAR_DIR/local.properties"
echo "Done. JAVA_HOME=$JAVA_HOME"
