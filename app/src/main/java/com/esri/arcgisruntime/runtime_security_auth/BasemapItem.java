/**
 * A class used to represent a way to cache portal items, their current state, and their respective
 * thumbnails after they've loaded. We also track the loading state here as it is asynchronous and
 * we don't want anyone else to worry about that.
 */

package com.esri.arcgisruntime.runtime_security_auth;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.portal.PortalItem;

public class BasemapItem {

    // define callback interface for async load of image completion event
    public interface ImageLoadedCompletionInterface {
        void onImageCompleted(BasemapItem basemapItem);
        void onImageFailed(BasemapItem basemapItem, String errorMessage);
    }

    private final PortalItem mPortalItem;
    private int mIndex;
    private Bitmap mImage;
    private boolean mLoadPending;
    private boolean mLoaded;
    private ListenableFuture<byte[]> mItemThumbnailDataFuture;

    public BasemapItem(int index, PortalItem item) {
        this.mIndex = index;
        this.mPortalItem = item;
        this.mLoadPending = false;
        this.mLoaded = false;
    }

    public PortalItem getPortalItem() {
        return mPortalItem;
    }

    public Bitmap getImage() {
        return mImage;
    }

    public int getIndex() {
        return mIndex;
    }

    public boolean isLoaded() {
        return mLoaded;
    }

    public void loadImage(final ImageLoadedCompletionInterface imageLoadComplete) {
        if (mLoadPending || mLoaded || mPortalItem == null) {
            return; // don't bother if there is nothing to load or we are already loading it
        }
        mLoadPending = true;
        mItemThumbnailDataFuture = mPortalItem.fetchThumbnailAsync();
        mItemThumbnailDataFuture.addDoneListener(new Runnable() {
            @Override
            public void run() {
                loadImageCompletionHandler(imageLoadComplete);
            }
        });
    }

    private void loadImageCompletionHandler (final ImageLoadedCompletionInterface imageLoadComplete) {
        try {
            byte[] itemThumbnailData = mItemThumbnailDataFuture.get();
            if ((itemThumbnailData != null) && (itemThumbnailData.length > 0)) {
                mImage = BitmapFactory.decodeByteArray(itemThumbnailData, 0, itemThumbnailData.length);
                mLoaded = true;
                if (imageLoadComplete != null) {
                    imageLoadComplete.onImageCompleted(this);
                }
            }
        } catch (Exception exception) {
            Log.d("BasemapItem.loadImage", "load FAILED for " + mPortalItem.getTitle() + " (" + mIndex + "): " + exception.getLocalizedMessage());
            if (imageLoadComplete != null) {
                imageLoadComplete.onImageFailed(this, exception.getLocalizedMessage());
            } else {
                Log.d("BasemapItem.loadImage", "Unable to load thumbnail for " + mPortalItem.getTitle() + " (" + mIndex + "): " + exception.getLocalizedMessage());
            }
        }
        mLoadPending = false;
    }
}
