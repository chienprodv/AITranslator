package com.ngoctho.aitranslator;

import android.view.View;

/** Adds system-bar insets without AndroidX, useful for targetSdk 36 edge-to-edge behavior. */
public final class UiInsets {
    private UiInsets() {}

    @SuppressWarnings("deprecation")
    public static void apply(View view) {
        if (view == null) return;
        final int baseLeft = view.getPaddingLeft();
        final int baseTop = view.getPaddingTop();
        final int baseRight = view.getPaddingRight();
        final int baseBottom = view.getPaddingBottom();

        view.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(
                    baseLeft + insets.getSystemWindowInsetLeft(),
                    baseTop + insets.getSystemWindowInsetTop(),
                    baseRight + insets.getSystemWindowInsetRight(),
                    baseBottom + insets.getSystemWindowInsetBottom());
            return insets;
        });
        view.requestApplyInsets();
    }
}
