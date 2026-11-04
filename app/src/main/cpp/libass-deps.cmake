# Source tarballs of libass and its dependencies, all from the official releases.
# Every tarball is checked against its SHA-256, nothing prebuilt is downloaded.
#
# The libass and FreeType hashes come from tarballs with a valid PGP signature:
#   libass   Oneric          DDAFC19E46B2AD2D6B26F84397A08E0CFFD70951
#   FreeType Werner Lemberg  E30674707856409FF1948010BE6C3AAC63AD8E3F
# FriBidi and HarfBuzz do not sign their releases.
#
# When updating a dependency change the version and the hash together.

set(LIBASS_DEPS_DIR "${CMAKE_BINARY_DIR}/libass-deps" CACHE PATH
    "Where the dependency sources are downloaded")

set(LIBASS_VERSION 0.17.5)
set(LIBASS_URL "https://github.com/libass/libass/releases/download/${LIBASS_VERSION}/libass-${LIBASS_VERSION}.tar.xz")
set(LIBASS_SHA256 2dca25c0e0c837ddf00b52011b3f82cac1e4ddd3ad018227806b0c2288864acc)

set(FREETYPE_VERSION 2.15.0)
set(FREETYPE_URL "https://download.savannah.gnu.org/releases/freetype/freetype-${FREETYPE_VERSION}.tar.xz")
set(FREETYPE_SHA256 93cd47eb719c325107f2d3cf087d06299e944c3b48a40b55537302304dea1dd5)

set(FRIBIDI_VERSION 1.0.17)
set(FRIBIDI_URL "https://github.com/fribidi/fribidi/releases/download/v${FRIBIDI_VERSION}/fribidi-${FRIBIDI_VERSION}.tar.xz")
set(FRIBIDI_SHA256 6949dcde27d41cebad1fd741fcafc36d55a1020d2d872d4a6eb3914caabbada2)

set(HARFBUZZ_VERSION 14.6.0)
set(HARFBUZZ_URL "https://github.com/harfbuzz/harfbuzz/releases/download/${HARFBUZZ_VERSION}/harfbuzz-${HARFBUZZ_VERSION}.tar.xz")
set(HARFBUZZ_SHA256 d07a007327277708a2a73ae437887cdbaf282937f6d03ca5467723e9099af586)

# Downloads and extracts a tarball if needed, sets <name>_SOURCE_DIR
function(libass_fetch_source name url sha256 top_dir)
    get_filename_component(archive_name "${url}" NAME)
    set(archive "${LIBASS_DEPS_DIR}/${archive_name}")
    set(extract_dir "${LIBASS_DEPS_DIR}/${name}")
    set(stamp "${extract_dir}/.sha256-${sha256}")

    if(NOT EXISTS "${stamp}")
        set(have_archive FALSE)
        if(EXISTS "${archive}")
            file(SHA256 "${archive}" existing_hash)
            if(existing_hash STREQUAL sha256)
                set(have_archive TRUE)
            endif()
        endif()

        if(NOT have_archive)
            message(STATUS "Downloading ${url}")
            file(DOWNLOAD "${url}" "${archive}"
                EXPECTED_HASH SHA256=${sha256}
                TLS_VERIFY ON
                STATUS download_status)
            list(GET download_status 0 download_code)
            if(NOT download_code EQUAL 0)
                list(GET download_status 1 download_error)
                file(REMOVE "${archive}")
                message(FATAL_ERROR "Could not download ${url}: ${download_error}")
            endif()
        endif()

        file(REMOVE_RECURSE "${extract_dir}")
        file(ARCHIVE_EXTRACT INPUT "${archive}" DESTINATION "${extract_dir}")
        file(TOUCH "${stamp}")
    endif()

    set(${name}_SOURCE_DIR "${extract_dir}/${top_dir}" PARENT_SCOPE)
endfunction()

# All ABIs are configured in parallel, only download once
file(MAKE_DIRECTORY "${LIBASS_DEPS_DIR}")
file(LOCK "${LIBASS_DEPS_DIR}/.lock" GUARD FILE TIMEOUT 900)

libass_fetch_source(LIBASS "${LIBASS_URL}" ${LIBASS_SHA256} "libass-${LIBASS_VERSION}")
libass_fetch_source(FREETYPE "${FREETYPE_URL}" ${FREETYPE_SHA256} "freetype-${FREETYPE_VERSION}")
libass_fetch_source(FRIBIDI "${FRIBIDI_URL}" ${FRIBIDI_SHA256} "fribidi-${FRIBIDI_VERSION}")
libass_fetch_source(HARFBUZZ "${HARFBUZZ_URL}" ${HARFBUZZ_SHA256} "harfbuzz-${HARFBUZZ_VERSION}")

file(LOCK "${LIBASS_DEPS_DIR}/.lock" RELEASE)
