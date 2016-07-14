package com.esri.arcgisruntime.runtime_security_auth;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.esri.arcgisruntime.portal.PortalItem;

import java.util.ArrayList;

/**
 * PortalItemQueryAdapter is a gridView adapter that uses a PortalQueryResultSet and builds a
 * gridView display of all the items. Each item has a thumbnail image and a title using
 * basemap_grid_item.xml
 */
public class PortalItemQueryAdapter extends BaseAdapter {

    private Context mContext;
    private Activity mActivity;
    private ArrayList<BasemapItem> mPortalResults;

    public PortalItemQueryAdapter(Activity activity, ArrayList<BasemapItem> portalResultSet) {
        mActivity = activity;
        mContext = activity.getBaseContext();
        mPortalResults = portalResultSet;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        if (position > mPortalResults.size()) {
            return null;
        }
        View gridViewCell;

        if (convertView == null) {
            boolean errorLoadingImage = false;
            BasemapItem basemapItem = mPortalResults.get(position);
            final PortalItem portalItem = basemapItem.getPortalItem();
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            gridViewCell = inflater.inflate(R.layout.basemap_grid_item, null);
            TextView textView = (TextView) gridViewCell.findViewById(R.id.textViewMap);
            textView.setText(portalItem.getTitle());
            final ImageView imageView = (ImageView) gridViewCell.findViewById(R.id.imageViewMap);
            if (imageView != null && portalItem.getThumbnailFileName() != null) {
                Bitmap thumbnail = basemapItem.getImage();
                if (thumbnail != null) {
                    imageView.setImageBitmap(thumbnail);
                } else {
                    Log.d("getView", "No image loaded (yet) for " + portalItem.getTitle());
                }
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
        return mPortalResults.get(position).getIndex();
    }
}
