#!/bin/zsh
set -euo pipefail
cd "${0:A:h:h}"
if [[ -d .tools/jdk-17.0.21+9/Contents/Home ]]; then
  export JAVA_HOME="$PWD/.tools/jdk-17.0.21+9/Contents/Home"
fi
if [[ -d .tools/android-sdk ]]; then
  export ANDROID_HOME="$PWD/.tools/android-sdk"
fi
./gradlew --no-daemon testDebugUnitTest lintRelease assembleRelease
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk 'dist/静听背景音-v1.3.0-release.apk'
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs 'dist/静听背景音-v1.3.0-release.apk' > dist/signature-verification.txt
shasum -a 256 dist/*.apk > dist/SHA256SUMS.txt
python3 scripts/verify_audio.py > dist/audio-verification.txt
