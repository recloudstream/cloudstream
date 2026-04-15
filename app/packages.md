# Module App

CloudStream Android application module providing the user interface and core functionality for the
streaming app.

# Package com.lagradost.cloudstream3

Root package containing core application classes and main entry points.

## Main Classes

- **CloudStreamApp** - Application class with initialization and crash handling
- **MainActivity** - Main activity handling app lifecycle
- **CommonActivity** - Base activity class with common functionality
- **AcraApplication** - Crash reporting integration

# Package com.lagradost.cloudstream3.actions

Action implementations for handling various video playback intents and external app integration.

# Package com.lagradost.cloudstream3.actions.temp

External app integration packages for video playback:
- VLC, MX Player, MPV, LibreTorrent, Just Player, etc.
- Web video casting and clipboard actions
- ChromeCast support

# Package com.lagradost.cloudstream3.mvvm

MVVM architecture components including ViewModel extensions and lifecycle management.

# Package com.lagradost.cloudstream3.network

Network utilities including:

- RequestsHelper - HTTP request handling
- CloudflareKiller - Cloudflare bypass
- DohProviders - DNS over HTTPS providers

# Package com.lagradost.cloudstream3.plugins

Plugin management system:

- Plugin - Plugin data model
- PluginManager - Plugin lifecycle management
- RepositoryManager - Plugin repository handling

# Package com.lagradost.cloudstream3.receivers

Broadcast receivers for system events and notifications.

# Package com.lagradost.cloudstream3.services

Background services:

- VideoDownloadService - Video download handling
- DownloadQueueService - Download queue management
- SubscriptionWorkManager - Anime/manga subscription updates
- BackupWorkManager - Data backup automation

# Package com.lagradost.cloudstream3.subtitles

Subtitle processing and selection utilities.

# Package com.lagradost.cloudstream3.syncproviders

Sync providers for tracking anime/manga watch history:

- AniListApi - AniList integration
- MALApi - MyAnimeList integration
- KitsuApi - Kitsu integration
- SimklApi - SIMKL integration

# Package com.lagradost.cloudstream3.syncproviders.providers

API implementations for sync providers:

- AniListApi - AniList API implementation
- MALApi - MyAnimeList API implementation
- KitsuApi - Kitsu API implementation
- SimklApi - SIMKL API implementation
- LocalList - Local list storage
- OpenSubtitlesApi - OpenSubtitles API
- Subdl, Addic7ed - Subtitle sources

# Package com.lagradost.cloudstream3.ui

User interface components including player, fragments, and dialogs.

# Package com.lagradost.cloudstream3.ui.account

Account management:
- AccountSelectActivity - Account selection UI
- AccountViewModel - Account state management
- AccountAdapter - Account list adapter

# Package com.lagradost.cloudstream3.ui.download

Download management:

- DownloadFragment - Download UI
- DownloadViewModel - Download state management
- DownloadAdapter - Download list adapter

# Package com.lagradost.cloudstream3.ui.download.button

Download button components:
- DownloadButton - Download button UI
- PieFetchButton - Pie-style progress button
- BaseFetchButton - Base button implementation

# Package com.lagradost.cloudstream3.ui.download.queue

Download queue management:
- DownloadQueueFragment - Queue UI
- DownloadQueueViewModel - Queue state management
- DownloadQueueAdapter - Queue list adapter

# Package com.lagradost.cloudstream3.ui.home

Home screen:
- HomeFragment - Home UI
- HomeViewModel - Home state management
- HomeParentItemAdapter, HomeChildItemAdapter - List adapters

# Package com.lagradost.cloudstream3.ui.library

Library view:
- LibraryFragment - Library UI
- LibraryViewModel - Library state management
- PageAdapter, ViewpagerAdapter - View pager adapters

# Package com.lagradost.cloudstream3.ui.player

Video player:
- FullScreenPlayer - Full screen video player
- DownloadedPlayerActivity - Downloaded video playback
- PlayerGeneratorViewModel - Player state management
- ExtractorLinkGenerator - Stream link generation

# Package com.lagradost.cloudstream3.ui.player.source_priority

Stream quality management:
- SourcePriorityDialog - Source priority UI
- QualityProfileDialog - Quality profile settings
- PriorityAdapter, ProfilesAdapter - Adapters

# Package com.lagradost.cloudstream3.ui.quicksearch

Quick search functionality:
- QuickSearchFragment - Quick search UI

# Package com.lagradost.cloudstream3.ui.result

Result/details view:
- ResultFragment - Media details UI
- ResultViewModel2 - Details state management
- EpisodeAdapter - Episode list adapter
- ActorAdaptor - Actor/cast adapter

# Package com.lagradost.cloudstream3.ui.search

Search functionality:
- SearchFragment - Search UI
- SearchViewModel - Search state management
- SearchAdaptor, SearchHistoryAdaptor - Search adapters
- SearchSuggestionAdapter - Search suggestions

# Package com.lagradost.cloudstream3.ui.settings

Settings and preferences:

- SettingsFragment - Main settings UI
- SettingsGeneral, SettingsPlayer, SettingsUI - Settings categories
- SettingsProviders, SettingsAccount, SettingsUpdates - Specific settings

# Package com.lagradost.cloudstream3.ui.settings.extensions

Extensions management:
- ExtensionsFragment - Extensions UI
- PluginsFragment, PluginDetailsFragment - Plugin management
- ExtensionsViewModel, PluginsViewModel - State management

# Package com.lagradost.cloudstream3.ui.settings.testing

Testing utilities:
- TestFragment - Testing UI
- TestViewModel - Test state management
- TestResultAdapter - Test results adapter

# Package com.lagradost.cloudstream3.ui.settings.utils

Settings utilities:
- DirectoryPicker - Directory selection dialog

# Package com.lagradost.cloudstream3.ui.setup

Initial setup wizard:
- SetupFragmentLanguage - Language setup
- SetupFragmentLayout - Layout preferences
- SetupFragmentProviderLanguage - Provider language settings
- SetupFragmentExtensions - Extension setup

# Package com.lagradost.cloudstream3.ui.subtitles

Subtitle management:
- SubtitlesFragment - Subtitle selection UI
- ChromecastSubtitlesFragment - Chromecast subtitle sync

# Package com.lagradost.cloudstream3.utils

Utility functions:

- DataStore - Preferences storage
- UIHelper - UI utilities
- DownloadUtils - Download helpers
- ImageUtil - Image processing

# Package com.lagradost.cloudstream3.utils.downloader

Download utilities:
- DownloadManager - Download management
- DownloadQueueManager - Queue management
- DownloadFileManagement - File handling
- DownloadUtils - Core download utilities

# Package com.lagradost.cloudstream3.widget

Custom Android widgets:

- LinearRecycleViewLayoutManager
- CenterZoomLayoutManager
- FlowLayout
