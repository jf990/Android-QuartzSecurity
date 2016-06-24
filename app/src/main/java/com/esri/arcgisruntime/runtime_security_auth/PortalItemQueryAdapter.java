package com.esri.arcgisruntime.runtime_security_auth;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.portal.PortalItem;
import com.esri.arcgisruntime.portal.PortalQueryResultSet;

import java.util.List;

/**
 * PortalItemQueryAdapter is a gridView adapter that uses a PortalQueryResultSet and builds a
 * gridView display of all the items. Each item has a thumbnail image and a title using
 * basemap_grid_item.xml
 */
public class PortalItemQueryAdapter extends BaseAdapter {

    private Context mContext;
    private Activity mActivity;
    private List<PortalItem> mPortalResults;

    public PortalItemQueryAdapter(Context context, Activity activity, PortalQueryResultSet<PortalItem> portalResultSet) {
        mContext = context;
        mActivity = activity;
        mPortalResults = portalResultSet.getResults();
    }

    public View getView(int position, View convertView, ViewGroup parent) {

        if (position > mPortalResults.size()) {
            return null;
        }
        View gridViewCell;

        if (convertView == null) {
            boolean errorLoadingImage = false;
            final PortalItem portalItem = mPortalResults.get(position);
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            gridViewCell = inflater.inflate(R.layout.basemap_grid_item, null);
            TextView textView = (TextView) gridViewCell.findViewById(R.id.textViewMap);
            textView.setText(portalItem.getTitle());
            final ImageView imageView = (ImageView) gridViewCell.findViewById(R.id.imageViewMap);
            if (imageView != null && portalItem.getThumbnailFileName() != null) {
                final ListenableFuture<byte[]> itemThumbnailDataFuture = portalItem.fetchThumbnailAsync();
                itemThumbnailDataFuture.addDoneListener(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            byte[] itemThumbnailData = itemThumbnailDataFuture.get();
                            if ((itemThumbnailData != null) && (itemThumbnailData.length > 0)) {
                                Bitmap itemThumbnail = BitmapFactory.decodeByteArray(itemThumbnailData, 0, itemThumbnailData.length);
                                final BitmapDrawable drawable = new BitmapDrawable(mContext.getResources(), itemThumbnail);
                                if (drawable != null) {
                                    mActivity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            imageView.setImageBitmap(drawable.getBitmap());
                                        }
                                    });
                                }
                            }
                        } catch (Exception exception) {
                            Log.d("Basemaps query", "Unable to load thumbnail for " + portalItem.getTitle());
                        }
                    }
                });
            }
            if (errorLoadingImage) {
                imageView.setImageResource(R.drawable.no_thumbnail);
            }
        } else {
            gridViewCell = convertView;
        }
        return gridViewCell;
    }

    @Override
    public int getCount() {
        return mPortalResults.size();
    }

    @Override
    public Object getItem(int position) {
        if (position < mPortalResults.size()) {
            return mPortalResults.get(position);
        } else {
            return null;
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }
}
