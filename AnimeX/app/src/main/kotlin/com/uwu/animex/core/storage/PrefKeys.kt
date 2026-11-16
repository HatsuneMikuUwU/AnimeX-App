package com.uwu.animex.core.storage

object PrefKeys {
    object Accounts {
        const val FILE = "accounts"
        const val ACCOUNT_PREFIX = "account_"
        const val PAYLOAD_PREFIX = "payload_"
    }

    object AniList {
        const val FILE = "anilist"
        const val AUTO_SYNC = "auto_sync"
        const val MAP = "map"
        const val USER = "user"
        const val FAVORITES = "favorites"
        const val EDIT_OPTIONS = "edit_options"
        const val TOTALS = "totals"
    }

    object AniListLibrary {
        const val FILE = "anilist_library"
    }

    object Api {
        const val FILE = "api_state"
        const val DOMAIN = "domain_api"
        const val DOMAIN_ALPHA = "domain_api_alpha"
    }

    object AppUpdate {
        const val FILE = "app_update"
        const val SKIPPED = "skipped_version"
        const val NOTIFIED = "notified_version"
    }

    object Appearance {
        const val FILE = "appearance"
        const val MODE = "mode"
        const val DYNAMIC = "dynamic"
        const val ACCENT = "accent"
        const val AMOLED = "amoled"
        const val BLUR = "blur"
        const val BAR_EFFECT = "bar_effect"
        const val PALETTE_STYLE = "palette_style"
        const val COLOR_SPEC = "color_spec"
        const val CONTRAST = "contrast"
        const val CUSTOM_SEED = "custom_seed"
        const val CMF_TERTIARY = "cmf_tertiary_seed"
        const val ROLE_PRIMARY = "role_primary"
        const val ROLE_SECONDARY = "role_secondary"
        const val ROLE_TERTIARY = "role_tertiary"
        const val ROLE_NEUTRAL = "role_neutral"
        const val ROLE_NEUTRAL_VARIANT = "role_neutral_variant"
        const val ROLE_ERROR = "role_error"
        const val ANIMATE_COLOR = "animate_color_change"
        const val COLOR_ANIM_MS = "color_animation_ms"
        const val UI_SCALE = "ui_scale"
        const val FONT_SCALE = "font_scale"
        const val AUTO_SCALE_UI = "auto_scale_ui"
        const val HOME_CARD_SCALE = "home_card_scale"
        const val COMPACT_NUMBERS = "compact_numbers"
        const val HIDE_STATS = "hide_stats"
        const val HAPTICS = "haptics_enabled"
        const val BACKGROUND_TYPE = "background_type"
        const val BACKGROUND_PARALLAX = "background_parallax"
        const val RANDOM_INTERVAL = "random_interval"
        const val SEASONAL_THEMES = "seasonal_themes"
        const val LAST_RANDOM = "last_random_background"
    }

    object BatteryOptimization {
        const val FILE = "battery_optimization_prefs"
        const val DISMISSED = "prompt_dismissed"
    }

    object Bookmarks {
        const val FILE = "bookmarks_meta"
        const val LOCAL_FAVORITES_DROPPED = "local_favorites_dropped"
    }

    object Cuplix {
        const val FILE = "cuplix"
        const val ENABLED = "enabled"
        const val MUTED = "muted"
    }

    object CuplixSaved {
        const val FILE = "cuplix_saved"
        const val ITEMS = "items"
    }

    object CuplixWatched {
        const val FILE = "cuplix_watched"
        const val KEYS = "keys"
        const val CLIPS = "clips"
    }

    object Downloads {
        const val FILE = "downloads"
        const val ITEMS = "items"
        const val FOLDER = "folder"
    }

    object EpisodeSchedule {
        const val FILE = "episode_schedule"
        const val ITEMS = "items"
        const val LAST_RUN = "last_run"
        const val SIGNATURE = "signature"
        const val NOTIFIED = "notified"
    }

    object EpisodeSort {
        const val FILE = "episode_sort"
    }

    object HomeSection {
        const val FILE = "home_sections"
        const val ORDER = "order"
    }

    object Mal {
        const val FILE = "mal"
        const val AUTO_SYNC = "auto_sync"
        const val USER = "user"
        const val MAP = "map"
        const val TOTALS = "totals"
    }

    object MalLibrary {
        const val FILE = "mal_library"
        const val SORT = "sorting"
        const val LEGACY_LIBRARY = "library"
        const val LEGACY_ENTRIES = "entries"
    }

    object Network {
        const val FILE = "network_settings"
        const val PROVIDER = "doh_provider"
    }

    object Notification {
        const val FILE = "notification_prefs"
        const val ENABLED = "enabled"
        const val EPISODES = "episodes"
        const val APP_UPDATES = "app_updates"
        const val PRERELEASES = "prereleases"
    }

    object NotificationHistory {
        const val FILE = "notification_history"
        const val ITEMS = "items"
    }

    object Onboarding {
        const val FILE = "onboarding"
        const val DONE = "done"
    }

    object Player {
        const val FILE = "player"
        const val QUALITY = "quality"
        const val HIDDEN_BUTTONS = "hidden_buttons"
        const val GESTURES = "gestures_enabled"
        const val PROGRESS_PREF = "progress_preference"
        const val SWITCH_ON_FAILURE = "switch_on_failure"
        const val ORIENTATION = "default_orientation"
        const val SHOW_LOADING_CIRCLE = "show_loading_circle"
        const val REMEMBER_LEVELS = "remember_brightness_volume"
        const val LEGACY_REMEMBER_BRIGHTNESS = "remember_brightness"
        const val LEGACY_REMEMBER_VOLUME = "remember_volume"
        const val BRIGHTNESS_VALUE = "brightness_value"
        const val VOLUME_VALUE = "volume_value"
        const val ENABLE_PIP = "enable_pip"
        const val AUTOPLAY = "autoplay_enabled"
        const val CONTROLS_TIMEOUT = "controls_timeout_ms"
        const val DISPLAY_VOL_PER = "display_vol_as_per"
        const val INVERT_DURATION = "invert_duration"
        const val SPEED_PRESETS = "speed_presets"
        const val PLAYER_FULLSCREEN = "player_fullscreen"
        const val REDUCE_MOTION = "reduce_motion"
        const val ALWAYS_EXTERNAL = "always_use_external_player"
        const val EXTERNAL_PACKAGE = "external_player_package"
        const val SWAP_VOL_BRIGHTNESS = "swap_volume_brightness"
        const val SWAP_INDICATOR = "swap_volume_brightness_indicator"
        const val AUDIO_FOCUS = "audio_focus"
        const val PAUSE_ON_NOISY = "pause_on_headset_unplug"
        const val VOLUME_BOOST = "volume_boost"
        const val BACKGROUND_PLAY = "background_play"
        const val SKIP_INTRO_MS = "skip_intro_ms"
    }

    object SyncSource {
        const val FILE = "sync_source"
        const val LEGACY_FILE = "bookmark_source"
        const val SOURCE = "source"
        const val MIRROR = "mirror"
    }

    object UserSession {
        const val FILE = "user_session"
        const val USER = "user"
    }
}
