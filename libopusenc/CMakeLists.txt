cmake_minimum_required(VERSION 3.5)
project(libopusenc C)

# Options
option(BUILD_SHARED_LIBS "Build shared libraries" ON)

# Definitions
add_definitions(-D_CRT_SECURE_NO_WARNINGS)
add_definitions(-DHAVE_CONFIG_H)
add_definitions(-DOUTSIDE_SPEEX)
add_definitions(-DRANDOM_PREFIX=opusenc)

# Includes
include_directories(include src win32)

# Find Dependencies
find_path(OPUS_INCLUDE_DIR NAMES opus.h PATH_SUFFIXES opus PATHS ../install/include NO_DEFAULT_PATH)
find_library(OPUS_LIBRARY NAMES opus PATHS ../install/lib NO_DEFAULT_PATH)
find_path(OGG_INCLUDE_DIR NAMES ogg/ogg.h PATHS ../install/include NO_DEFAULT_PATH)
find_library(OGG_LIBRARY NAMES ogg PATHS ../install/lib NO_DEFAULT_PATH)

# Add BOTH the base include and the opus/ogg specific ones if needed
include_directories(${OPUS_INCLUDE_DIR} ${OPUS_INCLUDE_DIR}/opus ${OGG_INCLUDE_DIR})

# Sources
set(SOURCES
    src/opusenc.c
    src/opus_header.c
    src/ogg_packer.c
    src/picture.c
    src/resample.c
    src/unicode_support.c
)

# Create Library
add_library(opusenc ${SOURCES})
target_link_libraries(opusenc ${OPUS_LIBRARY} ${OGG_LIBRARY})

# Install
install(TARGETS opusenc
    RUNTIME DESTINATION bin
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
)
install(FILES include/opusenc.h DESTINATION include/opus)
install(FILES win32/config.h win32/version.h DESTINATION include/opus)
