/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d111.gadget;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.android.gallery3d111.R;
import com.android.gallery3d111.app.GalleryApp;
import com.android.gallery3d111.common.ApiHelper;
import com.android.gallery3d111.data.ContentListener;

@TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
public class WidgetService extends RemoteViewsService {

    @SuppressWarnings("unused")
    private static final String TAG = "GalleryAppWidgetService";

    public static final String EXTRA_WIDGET_TYPE = "widget-type";
    public static final String EXTRA_ALBUM_PATH = "album-path";

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        int type = intent.getIntExtra(EXTRA_WIDGET_TYPE, 0);
        String albumPath = intent.getStringExtra(EXTRA_ALBUM_PATH);

        return new PhotoRVFactory((GalleryApp) getApplicationContext(),
                id, type, albumPath);
    }

    private static class PhotoRVFactory implements
            RemoteViewsFactory, ContentListener {

        private final int mAppWidgetId;
        private final int mType;
        private final String mAlbumPath;
        private final GalleryApp mApp;

        private WidgetSource mSource;

        public PhotoRVFactory(GalleryApp app, int id, int type, String albumPath) {
            mApp = app;
            mAppWidgetId = id;
            mType = type;
            mAlbumPath = albumPath;
        }

        @Override
        public void onCreate() {
            if (mType == WidgetDatabaseHelper.TYPE_ALBUM) {
                mSource = new MediaSetSource(mApp.getDataManager(), mAlbumPath);
            } else {
                mSource = new LocalPhotoSource(mApp.getAndroidContext());
            }
            mSource.setContentListener(this);
            AppWidgetManager.getInstance(mApp.getAndroidContext())
                    .notifyAppWidgetViewDataChanged(
                    mAppWidgetId, R.id.appwidget_stack_view);
            mApp.getContentResolver().registerContentObserver(  
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mObserver);  
        }
        
        private ContentObserver mObserver = new ContentObserver(new Handler()) {          
            @Override  
            public void onChange(boolean selfChange) {  
                mHandler.removeMessages(MSG);
                mHandler.sendEmptyMessageDelayed(MSG, 3000);
            }  
        }; 
        
        private final int MSG = 0x01;
        private Handler mHandler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                case MSG:
                    mSource.reload();
                    AppWidgetManager.getInstance(mApp.getAndroidContext())
                    .notifyAppWidgetViewDataChanged(
                    mAppWidgetId, R.id.appwidget_stack_view);
                    break;
                }
            }
            
        };

        @Override
        public void onDestroy() {
            mSource.close();
            mSource = null;
            mApp.getContentResolver().unregisterContentObserver(mObserver);
        }

        @Override
        public int getCount() {
            return mSource.size();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public RemoteViews getLoadingView() {
            RemoteViews rv = new RemoteViews(
                    mApp.getAndroidContext().getPackageName(),
                    R.layout.appwidget_loading_item);
            rv.setProgressBar(R.id.appwidget_loading_item, 0, 0, true);
            return rv;
        }

        @Override
        public RemoteViews getViewAt(int position) {
            Bitmap bitmap = mSource.getImage(position);
            if (bitmap == null) return getLoadingView();
            RemoteViews views = new RemoteViews(
                    mApp.getAndroidContext().getPackageName(),
                    R.layout.appwidget_photo_item);
            views.setImageViewBitmap(R.id.appwidget_photo_item, bitmap);
            views.setOnClickFillInIntent(R.id.appwidget_photo_item, new Intent()
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .setData(mSource.getContentUri(position)));
            return views;
        }

        @Override
        public void onDataSetChanged() {
            mSource.reload();
        }

        @Override
        public void onContentDirty() {
            AppWidgetManager.getInstance(mApp.getAndroidContext())
                    .notifyAppWidgetViewDataChanged(
                    mAppWidgetId, R.id.appwidget_stack_view);
        }
    }
}
