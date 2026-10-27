/*
 * EffectDroid Native — GameActivity host for libeffectcraft_android.so
 * (EffectCraft's eframe/egui UI built for aarch64-linux-android).
 *
 * Pattern from rust-mobile/rust-android-examples/agdk-eframe, adapted for EffectCraft's
 * edge-to-edge immersive shell.
 */
package com.ideaatmosphere.effectdroid.nativeapp;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.androidgamesdk.GameActivity;

public class MainActivity extends GameActivity {

    static {
        // Must match android.app.lib_name in AndroidManifest.xml and the cdylib name
        // produced by `cargo ndk … -p effectcraft-android` (libeffectcraft_android.so).
        System.loadLibrary("effectcraft_android");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        hideSystemUI();
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    private void hideSystemUI() {
        // Draw behind notches/cutouts, hide bars, let the user swipe them back transiently.
        getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        View decor = getWindow().getDecorView();
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), decor);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.hide(WindowInsetsCompat.Type.displayCutout());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }
}
