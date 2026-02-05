#!/bin/bash
set -e

# Clone dependencies (overwrite if exists)
for dir in ogg opus-1.6.1 libopusenc opusfile opus-tools; do
    if [ -d "$dir" ]; then
        echo "Removing existing directory: $dir"
        rm -rf "$dir"
    fi
done

git clone --depth 1 https://github.com/xiph/ogg.git ogg
git clone --depth 1 https://github.com/xiph/opus.git opus-1.6.1
git clone --depth 1 https://github.com/xiph/libopusenc.git libopusenc
git clone --depth 1 https://github.com/xiph/opusfile.git opusfile
git clone --depth 1 https://github.com/xiph/opus-tools.git opus-tools

echo "Dependencies initialized successfully."
