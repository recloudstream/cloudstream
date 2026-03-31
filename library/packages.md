# Module Library

CloudStream multiplatform library providing core streaming functionality, extractors, and APIs for
both common and Android platforms.

# Package com.lagradost.cloudstream3

Root package with core library classes and interfaces.

## Main Classes

- **MainAPI** - Core API interface defining provider functionality
- **MainActivity** - Activity interface for UI implementation
- **ParCollections** - Parallel collection utilities

# Package com.lagradost.cloudstream3.extractors

Video extractors for various streaming hosts (100+ extractors). Includes implementations for:

- Streamplay, StreamSB, StreamTape
- Vidplay, Vidstream, VidMoly
- Videa, VkExtractor
- DoodExtractor
- MixDrop, Mp4Upload
- YoutubeExtractor
- And many more hosting services

# Package com.lagradost.cloudstream3.extractors.helper

Extractor helper utilities for specific streaming services:

- GogoHelper - Gogoanime streaming support
- NineAnimeHelper - 9Anime streaming support
- WcoHelper - WatchCartoonOnline support
- VstreamhubHelper - Vstreamhub support
- AsianEmbedHelper - Asian drama embed support
- AesHelper, CryptoJSHelper - Cryptography utilities

# Package com.lagradost.cloudstream3.metaproviders

Meta providers for content aggregation from multiple sources.

# Package com.lagradost.cloudstream3.mvvm

MVVM extensions and architecture components for reactive programming.

# Package com.lagradost.cloudstream3.network

Network utilities including WebViewResolver for handling JavaScript-based video sources.

# Package com.lagradost.cloudstream3.plugins

Plugin system interfaces:

- BasePlugin - Base plugin class
- CloudstreamPlugin - Plugin annotation and entry point

# Package com.lagradost.cloudstream3.syncproviders

Sync provider interfaces for anime/manga tracking integration.

# Package com.lagradost.cloudstream3.utils

Utility functions:

- SubtitleHelper - Subtitle processing
- M3u8Helper - HLS stream handling
- JsUnpacker - JavaScript unpacking utilities
- Coroutines - Coroutine extensions
- StringUtils - String utilities
- AppUtils - Application utilities

# Package com.lagradost.api

Android-specific API utilities.

## Main Classes

- **Log** - Logging utility
- **ContextHelper** - Android context helper
