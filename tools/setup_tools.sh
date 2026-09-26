#!/bin/bash
# Downloads the build toolchain into tools/ (not committed: too large). All sources are GitHub/raw.githubusercontent.
set -e
cd "$(dirname "$0")"
dl(){ [ -s "$2" ] || { echo "downloading $2"; curl -sSL -o "$2" "$1"; }; }
if [ ! -x jdk-21.0.5+11/bin/javac ]; then dl "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jdk_x64_linux_hotspot_21.0.5_11.tar.gz" jdk.tgz; tar xzf jdk.tgz; rm jdk.tgz; fi
dl https://raw.githubusercontent.com/Sable/android-platforms/master/android-35/android.jar android-35.jar
if [ ! -s dx.jar ]; then dl https://github.com/pxb1988/dex2jar/releases/download/v2.4/dex-tools-v2.4.zip d2j.zip; unzip -q -o d2j.zip "dex-tools-v2.4/lib/dx-30.0.2.jar"; mv dex-tools-v2.4/lib/dx-30.0.2.jar dx.jar; rm -rf dex-tools-v2.4 d2j.zip; fi
dl https://github.com/iBotPeaches/Apktool/releases/download/v3.0.3/apktool_3.0.3.jar apktool.jar
dl https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar uber.jar
if [ "$1" = "--art" ]; then   # only needed for the art pipeline (inpainting / matting)
  dl https://github.com/neureps/carousel-fit-models/releases/download/lama-v1/lama-fp32-1faef5301d78.onnx lama.onnx   # sha256 1faef5301d78db7dda502fe59966957ec4b79dd64e16f03ed96913c7a4eb68d6
  dl https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2net.onnx u2net.onnx
fi
echo "tools ready"
