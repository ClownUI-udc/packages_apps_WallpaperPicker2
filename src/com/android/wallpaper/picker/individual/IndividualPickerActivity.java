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
package com.android.wallpaper.picker.individual;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.wallpaper.R;
import com.android.wallpaper.compat.BuildCompat;
import com.android.wallpaper.model.Category;
import com.android.wallpaper.model.InlinePreviewIntentFactory;
import com.android.wallpaper.model.PickerIntentFactory;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.LiveWallpaperStatusChecker;
import com.android.wallpaper.module.NoBackupImageWallpaper;
import com.android.wallpaper.module.WallpaperPersister;
import com.android.wallpaper.picker.BaseActivity;
import com.android.wallpaper.picker.PreviewActivity.PreviewActivityIntentFactory;
import com.android.wallpaper.util.ActivityUtils;
import com.android.wallpaper.util.DiskBasedLogger;

/**
 * Activity that can be launched from the Android wallpaper picker and allows users to pick from
 * various wallpapers and enter a preview mode for specific ones.
 */
public class IndividualPickerActivity extends BaseActivity {
    private static final String TAG = "IndividualPickerAct";
    private static final String EXTRA_CATEGORY_COLLECTION_ID =
            "com.android.wallpaper.category_collection_id";
    private static final int PREVIEW_WALLPAPER_REQUEST_CODE = 0;
    private static final int NO_BACKUP_IMAGE_WALLPAPER_REQUEST_CODE = 1;
    private static final String KEY_CATEGORY_COLLECTION_ID = "key_category_collection_id";

    private InlinePreviewIntentFactory mPreviewIntentFactory;
    private WallpaperPersister mWallpaperPersister;
    private LiveWallpaperStatusChecker mLiveWallpaperStatusChecker;
    private Category mCategory;
    private String mCategoryCollectionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_fragment_with_toolbar);

        // Set toolbar as the action bar.
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mPreviewIntentFactory = new PreviewActivityIntentFactory();
        Injector injector = InjectorProvider.getInjector();
        mWallpaperPersister = injector.getWallpaperPersister(this);
        mLiveWallpaperStatusChecker = injector.getLiveWallpaperStatusChecker(this);

        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(R.id.fragment_container);

        mCategoryCollectionId = (savedInstanceState == null)
                ? getIntent().getStringExtra(EXTRA_CATEGORY_COLLECTION_ID)
                : savedInstanceState.getString(KEY_CATEGORY_COLLECTION_ID);
        mCategory = injector.getCategoryProvider(this).getCategory(mCategoryCollectionId);
        if (mCategory == null) {
            DiskBasedLogger.e(TAG, "Failed to find the category.", this);
        }

        setTitle(mCategory.getTitle());
        getSupportActionBar().setTitle(mCategory.getTitle());
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Use updated fancy arrow icon for O+.
        if (BuildCompat.isAtLeastO()) {
            Drawable navigationIcon = ContextCompat.getDrawable(
                    this, R.drawable.material_ic_arrow_back_black_24);

            // This Drawable's state is shared across the app, so make a copy of it before applying a
            // color tint as not to affect other clients elsewhere in the app.
            navigationIcon = navigationIcon.getConstantState().newDrawable().mutate();
            navigationIcon.setColorFilter(
                    ContextCompat.getColor(this, R.color.accent_color), Mode.SRC_IN);

            // Need to explicitly check against 19 rather than using BuildCompat in order to avoid a
            // NoSuchMethodError here in UI tests running on pre-API 19 emulators.
            if (VERSION.SDK_INT >= 19) {
                navigationIcon.setAutoMirrored(true);
            }

            toolbar.setNavigationIcon(navigationIcon);
        }

        if (fragment == null) {
            fragment = IndividualPickerFragment.newInstance(mCategoryCollectionId);
            fm.beginTransaction()
                    .add(R.id.fragment_container, fragment)
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // Handle Up as a Global back since the only entry point to IndividualPickerActivity is from
            // TopLevelPickerActivity.
            onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case PREVIEW_WALLPAPER_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    mWallpaperPersister.onLiveWallpaperSet();

                    // The wallpaper was set, so finish this activity with result OK.
                    finishWithResultOk();
                }
                break;

            case NO_BACKUP_IMAGE_WALLPAPER_REQUEST_CODE:
                // User clicked "Set wallpaper" in live wallpaper preview UI.
                // NOTE: Don't check for the result code prior to KitKat MR2 because a bug on those versions
                // caused the result code to be discarded from LivePicker so we can't rely on it.
                if ((!BuildCompat.isAtLeastL() || resultCode == Activity.RESULT_OK)
                        && mLiveWallpaperStatusChecker.isNoBackupImageWallpaperSet()
                        && mCategory.getWallpaperRotationInitializer().startRotation(getApplicationContext())) {
                    finishWithResultOk();
                }
                break;

            default:
                Log.e(TAG, "Invalid request code: " + requestCode);
        }
    }

    /**
     * Shows the preview activity for the given wallpaper.
     */
    public void showPreview(WallpaperInfo wallpaperInfo) {
        mWallpaperPersister.setWallpaperInfoInPreview(wallpaperInfo);
        wallpaperInfo.showPreview(this, mPreviewIntentFactory, PREVIEW_WALLPAPER_REQUEST_CODE);
    }

    /**
     * Shows the system live wallpaper preview for the {@link NoBackupImageWallpaper} which is used to
     * draw rotating wallpapers on pre-N Android builds.
     */
    public void showNoBackupImageWallpaperPreview() {
        Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        ComponentName componentName = new ComponentName(this, NoBackupImageWallpaper.class);
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, componentName);
        ActivityUtils.startActivityForResultSafely(
                this, intent, NO_BACKUP_IMAGE_WALLPAPER_REQUEST_CODE);
    }

    private void finishWithResultOk() {
        try {
            Toast.makeText(this, R.string.wallpaper_set_successfully_message,
                    Toast.LENGTH_SHORT).show();
        } catch (NotFoundException e) {
            Log.e(TAG, "Could not show toast " + e);
        }

        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        setResult(Activity.RESULT_OK);
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);

        bundle.putString(KEY_CATEGORY_COLLECTION_ID, mCategoryCollectionId);
    }

    /**
     * Default implementation of intent factory that provides an intent to start an
     * IndividualPickerActivity.
     */
    public static class IndividualPickerActivityIntentFactory implements PickerIntentFactory {
        @Override
        public Intent newIntent(Context ctx, String collectionId) {
            return new Intent(ctx, IndividualPickerActivity.class).putExtra(
                    EXTRA_CATEGORY_COLLECTION_ID, collectionId);
        }
    }
}
