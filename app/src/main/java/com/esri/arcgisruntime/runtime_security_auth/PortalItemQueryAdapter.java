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

    private Context context;
    private Activity activity;
    private PortalQueryResultSet<PortalItem> portalResultSet;
    private List<PortalItem> portalResults;

    public PortalItemQueryAdapter(Context context, Activity activity, PortalQueryResultSet<PortalItem> portalResultSet) {
        this.context = context;
        this.activity = activity;
        this.portalResultSet = portalResultSet;
        this.portalResults = portalResultSet.getResults();
    }

    public View getView(int position, View convertView, ViewGroup parent) {

        if (position > portalResults.size()) {
            return null;
        }
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View gridView;
        final PortalItem portalItem = portalResults.get(position);
        boolean errorLoadingImage = false;

        if (convertView == null) {
            gridView = inflater.inflate(R.layout.basemap_grid_item, null);
            TextView textView = (TextView) gridView
                    .findViewById(R.id.textViewMap);
            textView.setText(portalItem.getTitle());
            final ImageView imageView = (ImageView) gridView
                    .findViewById(R.id.imageViewMap);
            if (portalItem.getThumbnailFileName() != null) {
                final ListenableFuture<byte[]> itemThumbnailDataFuture = portalItem.fetchThumbnailAsync();
                itemThumbnailDataFuture.addDoneListener(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            byte[] itemThumbnailData = itemThumbnailDataFuture.get();
                            if ((itemThumbnailData != null) && (itemThumbnailData.length > 0)) {
                                Bitmap itemThumbnail = BitmapFactory.decodeByteArray(itemThumbnailData, 0, itemThumbnailData.length);
                                final BitmapDrawable drawable = new BitmapDrawable(context.getResources(), itemThumbnail);
                                if (drawable != null) {
                                    activity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            // TODO: map image view to component in view
                                            if (imageView != null) {
                                                imageView.setImageBitmap(drawable.getBitmap());
                                            }
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
            gridView = convertView;
        }
        return gridView;
    }

    @Override
    public int getCount() {
        return portalResults.size();
    }

    @Override
    public Object getItem(int position) {
        if (position < portalResults.size()) {
            return portalResults.get(position);
        } else {
            return null;
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }
}
