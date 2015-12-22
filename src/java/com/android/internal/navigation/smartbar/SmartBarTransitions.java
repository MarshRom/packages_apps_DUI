/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.navigation.smartbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.os.ServiceManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.utils.du.DUActionUtils;
import com.android.internal.navigation.*;

public final class SmartBarTransitions extends BarTransitions {

    private static final int CONTENT_FADE_DURATION = 200;

    private final SmartBarView mView;
    private final IStatusBarService mBarService;

    private boolean mLightsOut;
    private boolean mVertical;
    private int mRequestedMode;
    private boolean mTransparencyAllowedWhenVertical = false;

    public SmartBarTransitions(SmartBarView view) {
        super(view,
                DUActionUtils.getIdentifier(view.getContext(), "nav_background", "drawable",
                        DUActionUtils.PACKAGE_SYSTEMUI),
                DUActionUtils.getIdentifier(view.getContext(), "navigation_bar_background_opaque",
                        "color", DUActionUtils.PACKAGE_SYSTEMUI),
                DUActionUtils.getIdentifier(view.getContext(),
                        "navigation_bar_background_semi_transparent", "color",
                        DUActionUtils.PACKAGE_SYSTEMUI),
                DUActionUtils.getIdentifier(view.getContext(),
                        "navigation_bar_background_transparent", "color",
                        DUActionUtils.PACKAGE_SYSTEMUI),
                DUActionUtils.getIdentifier(view.getContext(), "battery_saver_mode_color", "color",
                        "android"));
        mView = view;
        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
    }

    public void init() {
        applyModeBackground(-1, getMode(), false /*animate*/);
        applyMode(getMode(), false /*animate*/, true /*force*/);
    }

    @Override
    protected void onTransition(int oldMode, int newMode, boolean animate) {
        super.onTransition(oldMode, newMode, animate);
        applyMode(newMode, animate, false /*force*/);
    }

    private void applyMode(int mode, boolean animate, boolean force) {
        // apply to key buttons
        final float alpha = alphaForMode(mode);
        for (String buttonTag : mView.getCurrentSequence()) {
            SmartButtonView button = mView.findCurrentButton(buttonTag);
            if (button != null) {
                button.setQuiescentAlpha(alpha, animate);
            }
        }
        // apply to lights out
        applyLightsOut(isLightsOut(mode), animate, force);
    }

    private float alphaForMode(int mode) {
        final boolean isOpaque = mode == MODE_OPAQUE || mode == MODE_LIGHTS_OUT;
        return isOpaque ? SmartButtonView.DEFAULT_QUIESCENT_ALPHA : 1f;
    }

    private void applyLightsOut(boolean lightsOut, boolean animate, boolean force) {
        if (!force && lightsOut == mLightsOut) return;

        mLightsOut = lightsOut;

        final View navButtons = mView.getCurrentView().findViewWithTag(Res.Common.NAV_BUTTONS);
        final View lowLights = mView.getCurrentView().findViewWithTag(Res.Common.LIGHTS_OUT);
        final boolean isBarPulseFaded = mView.isBarPulseFaded();

        // ok, everyone, stop it right there
        navButtons.animate().cancel();
        lowLights.animate().cancel();

        final float navButtonsAlpha = lightsOut ? 0f : isBarPulseFaded ? SmartBarView.PULSE_ALPHA_FADE : 1f;
        final float lowLightsAlpha = lightsOut ? 1f : 0f;

        if (!animate) {
            navButtons.setAlpha(navButtonsAlpha);
            lowLights.setAlpha(lowLightsAlpha);
            lowLights.setVisibility(lightsOut ? View.VISIBLE : View.GONE);
        } else {
            final int duration = lightsOut ? LIGHTS_OUT_DURATION : LIGHTS_IN_DURATION;
            navButtons.animate()
                .alpha(navButtonsAlpha)
                .setDuration(duration)
                .start();

            lowLights.setOnTouchListener(mLightsOutListener);
            if (lowLights.getVisibility() == View.GONE) {
                lowLights.setAlpha(0f);
                lowLights.setVisibility(View.VISIBLE);
            }
            lowLights.animate()
                .alpha(lowLightsAlpha)
                .setDuration(duration)
                .setInterpolator(new AccelerateInterpolator(2.0f))
                .setListener(lightsOut ? null : new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator _a) {
                        lowLights.setVisibility(View.GONE);
                    }
                })
                .start();
        }
    }

    private final View.OnTouchListener mLightsOutListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                // even though setting the systemUI visibility below will turn these views
                // on, we need them to come up faster so that they can catch this motion
                // event
                applyLightsOut(false, false, false);

                try {
                    mBarService.setSystemUiVisibility(0, View.SYSTEM_UI_FLAG_LOW_PROFILE,
                            "LightsOutListener");
                } catch (android.os.RemoteException ex) {
                }
            }
            return false;
        }
    };
}