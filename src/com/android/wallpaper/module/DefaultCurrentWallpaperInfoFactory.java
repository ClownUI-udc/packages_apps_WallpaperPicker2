/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.module;

import android.app.WallpaperManager;
import android.content.Context;

import androidx.annotation.Nullable;

import com.android.wallpaper.compat.WallpaperManagerCompat;
import com.android.wallpaper.model.CurrentWallpaperInfoVN;
import com.android.wallpaper.model.LiveWallpaperMetadata;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.WallpaperPreferences.PresentationMode;

/**
 * Default implementation of {@link CurrentWallpaperInfoFactory} which actually constructs
 * {@link WallpaperInfo} instances representing the wallpapers currently set to the device.
 */
public class DefaultCurrentWallpaperInfoFactory implements CurrentWallpaperInfoFactory {

    private final Context mAppContext;
    private final WallpaperRefresher mWallpaperRefresher;
    private final LiveWallpaperInfoFactory mLiveWallpaperInfoFactory;
    private final WallpaperManager mWallpaperManager;

    // Cached copies of the currently-set WallpaperInfo(s) and presentation mode.
    private WallpaperInfo mHomeWallpaper;
    @Nullable
    private WallpaperInfo mLockWallpaper;
    @PresentationMode
    private int mPresentationMode;

    public DefaultCurrentWallpaperInfoFactory(Context context) {
        mAppContext = context.getApplicationContext();
        Injector injector = InjectorProvider.getInjector();
        mWallpaperRefresher = injector.getWallpaperRefresher(mAppContext);
        mLiveWallpaperInfoFactory = injector.getLiveWallpaperInfoFactory(mAppContext);
        mWallpaperManager = WallpaperManager.getInstance(context);
    }

    @Override
    public synchronized void createCurrentWallpaperInfos(final WallpaperInfoCallback callback,
                                                         boolean forceRefresh) {
        if (!forceRefresh && mHomeWallpaper != null
                && mPresentationMode != WallpaperPreferences.PRESENTATION_MODE_ROTATING) {
            callback.onWallpaperInfoCreated(mHomeWallpaper, mLockWallpaper, mPresentationMode);
            return;
        }

        // Clear cached copies if we are refreshing the currently-set WallpaperInfo(s) from the
        // Refresher so that multiple calls to this method after a call with forceRefresh=true don't
        // provide old cached copies.
        if (forceRefresh) {
            clearCurrentWallpaperInfos();
        }

        mWallpaperRefresher.refresh(
                (homeWallpaperMetadata, lockWallpaperMetadata, presentationMode) -> {
                    WallpaperInfo homeWallpaper;
                    if (homeWallpaperMetadata instanceof LiveWallpaperMetadata) {
                        homeWallpaper = mLiveWallpaperInfoFactory.getLiveWallpaperInfo(
                                homeWallpaperMetadata.getWallpaperComponent());
                    } else {
                        homeWallpaper = new CurrentWallpaperInfoVN(
                                homeWallpaperMetadata.getAttributions(),
                                homeWallpaperMetadata.getActionUrl(),
                                homeWallpaperMetadata.getActionLabelRes(),
                                homeWallpaperMetadata.getActionIconRes(),
                                homeWallpaperMetadata.getCollectionId(),
                                WallpaperManagerCompat.FLAG_SYSTEM);
                    }

                    WallpaperInfo lockWallpaper = null;

                    if (lockWallpaperMetadata != null) {

                        if (lockWallpaperMetadata instanceof LiveWallpaperMetadata) {
                            lockWallpaper = mLiveWallpaperInfoFactory.getLiveWallpaperInfo(
                                    lockWallpaperMetadata.getWallpaperComponent());
                        } else {
                            lockWallpaper = new CurrentWallpaperInfoVN(
                                    lockWallpaperMetadata.getAttributions(),
                                    lockWallpaperMetadata.getActionUrl(),
                                    lockWallpaperMetadata.getActionLabelRes(),
                                    lockWallpaperMetadata.getActionIconRes(),
                                    lockWallpaperMetadata.getCollectionId(),
                                    WallpaperManagerCompat.FLAG_LOCK);
                        }
                    }

                    mHomeWallpaper = homeWallpaper;
                    mLockWallpaper = lockWallpaper;
                    mPresentationMode = presentationMode;

                    callback.onWallpaperInfoCreated(homeWallpaper, lockWallpaper, presentationMode);
                });
    }

    @Override
    public void clearCurrentWallpaperInfos() {
        mHomeWallpaper = null;
        mLockWallpaper = null;
    }
}
