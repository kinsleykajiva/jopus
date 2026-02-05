#!/bin/bash
set -e

# Clone dependencies
git clone --depth 1 https://github.com/xiph/ogg.git ogg
git clone --depth 1 https://github.com/xiph/opus.git opus-1.6.1
git clone --depth 1 https://github.com/xiph/libopusenc.git libopusenc
git clone --depth 1 https://github.com/xiph/opusfile.git opusfile
git clone --depth 1 https://github.com/xiph/opus-tools.git opus-tools

echo "Dependencies initialized successfully."
