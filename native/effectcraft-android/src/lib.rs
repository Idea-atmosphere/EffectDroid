//! EffectCraft for Android — the native `aarch64-linux-android` port of the desktop app
//! (EffectDroid's Linux arm64 → Android arm64 track).
//!
//! The workspace's desktop app (`apps/effectcraft`) is a binary wired to Linux/macOS/Windows
//! services (rfd dialogs, cpal audio, zbus portals, AppImage/DMG/NSIS glue), none of which
//! exist for Android. This crate is the minimal equivalent entry: it boots the same
//! [`EffectcraftApp`](effectcraft_ui_egui::EffectcraftApp) — engine, session, compositor,
//! GPU renderers — on a GameActivity window, with the desktop-only hooks left unset.
//!
//! CI (`scripts/build-native.sh`) copies this file set into the upstream clone as
//! `apps/effectcraft-android/` and builds it with `cargo ndk`.
#![cfg(target_os = "android")]

use std::sync::OnceLock;

use effectcraft_ui_egui::EffectcraftApp;
use winit::platform::android::activity::AndroidApp;

/// The eframe app creator: the desktop `apps/effectcraft/src/main.rs` closure without the
/// desktop-only pieces (rfd file dialogs, cpal audio, control server, launch guard).
fn build(
    cc: &eframe::CreationContext<'_>,
) -> Result<Box<dyn eframe::App>, Box<dyn std::error::Error + Send + Sync>> {
    match &cc.wgpu_render_state {
        Some(rs) => log::info!("graphics: {:?}", rs.adapter.get_info()),
        None => log::info!("graphics: no wgpu device"),
    }

    // GPU failure reporting (device lost / uncaptured errors) — the same bridge the desktop
    // and web apps install, so the UI can explain and recover from a lost Vulkan device.
    let gpu_failures = effectcraft_ui_egui::gpu_failure::GpuFailureBridge::new(&cc.egui_ctx);
    if let Some(rs) = &cc.wgpu_render_state {
        let errors = gpu_failures.clone();
        rs.device.on_uncaptured_error(std::sync::Arc::new(move |error| {
            let message = format!("uncaptured GPU error: {error}");
            if errors.report(&message, false) {
                log::error!("{message}");
            }
        }));
        let lost = gpu_failures.clone();
        rs.device.set_device_lost_callback(move |reason, message| {
            lost.report(&format!("GPU device lost ({reason:?}): {message}"), true);
        });
    }

    let mut session = effectcraft_host::session();
    session.check_footage_on_open = true;
    session.autosave.background = true;
    // Settings, shortcut presets and the crash-recovery sentinel live in the config directory.
    // `EFFECTCRAFT_CONFIG_DIR` is set in `android_main` to the app's internal data path.
    if let Some(dir) = effectcraft_host::config_dir() {
        let plugins = dir.join("Plug-ins");
        if plugins.is_dir() {
            let _ = session.execute(
                "effect.plugins.load",
                serde_json::json!({ "folder": plugins.to_string_lossy() }),
            );
        }
        session.config = Some(std::sync::Arc::new(
            effectcraft_engine::config::DirConfig::new(dir),
        ));
    }
    session.load_settings();

    let recovery = session.begin_recovery();
    let show_home = session.prefs.startup.show_home_on_launch;
    let _ = session.execute("window.restoreScriptPanels", serde_json::json!({}));

    let mut app = EffectcraftApp::new(session);
    app.set_gpu_failure_bridge(gpu_failures);
    app.ui.start_screen = show_home;
    if let Some(r) = recovery {
        app.offer_recovery(r);
    }
    // Desktop-only hooks intentionally left unset on Android v1:
    //  - rfd has no Android backend (no SAF file dialogs yet),
    //  - cpal audio output devices are not wired (preview audio is silent),
    //  - system-theme following reads desktop portals/registries.
    // See docs/ANDROID.md → "Native port: status & gaps".
    log::info!("app created; the window shows after its first frame");
    Ok(Box::new(app))
}

/// Android entry: called on the activity's main-loop thread by android-activity's
/// `GameActivity` glue (native-activity-style `android_main` symbol; the pattern from
/// rust-mobile/android-activity and eframe's `NativeOptions::android_app`).
///
/// May run again after the activity is destroyed and recreated — global state goes through
/// [`OnceLock`].
#[unsafe(no_mangle)]
fn android_main(app: AndroidApp) {
    static ONCE: OnceLock<()> = OnceLock::new();
    ONCE.get_or_init(|| {
        // SAFETY (edition 2024): set before any other thread starts using the environment —
        // `android_main` runs before the app's own threads exist, and nothing else calls
        // `set_var`. Points EffectCraft's config dir (settings, session, recovery) at the
        // app's private storage: internal data path survives updates, is wiped on uninstall.
        unsafe {
            if let Some(internal) = app.internal_data_path() {
                let dir = internal.join("EffectCraftData");
                let _ = std::fs::create_dir_all(&dir);
                std::env::set_var("EFFECTCRAFT_CONFIG_DIR", &dir);
            }
        }
        // First, so start-up warnings and panics are recorded (stderr + session log).
        effectcraft_engine::logging::install();
        effectcraft_engine::logging::install_panic_hook();
        if let Some(dir) = effectcraft_host::config_dir() {
            if let Err(e) = effectcraft_engine::logging::attach_session_log(
                &effectcraft_engine::logging::log_dir(Some(&dir)),
            ) {
                log::warn!("no session log file: {e}");
            }
        }
        // The font menus list installed fonts; Android keeps them in /system/fonts.
        effectcraft_engine::text::fonts::scan_system_in_background();
    });

    log::info!("effectcraft {}: opening the window", env!("CARGO_PKG_VERSION"));
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default().with_title("EffectCraft"),
        android_app: Some(app),
        ..Default::default()
    };
    if let Err(e) = eframe::run_native("EffectCraft", options, Box::new(|cc| build(cc))) {
        log::error!("effectcraft android: {e}");
    }
}
