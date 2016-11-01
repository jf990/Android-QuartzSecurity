/**
 * Ask the portal for all the basemaps that belong to the organization of the logged in user. This is
 * an asynchronous task that goes to the portal, gets the group basemap query, then performs
 * the query and fetches a set of results (PortalQueryResultSet). The results are passed on to the
 * BasemapFetchTaskComplete.onBasemapFetchCompleted interface. If there is an error then
 * BasemapFetchTaskComplete.onBasemapFetchFailed is called.
 */

package com.esri.arcgisruntime.runtime_security_auth;

import android.app.Activity;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.portal.Portal;
import com.esri.arcgisruntime.portal.PortalGroup;
import com.esri.arcgisruntime.portal.PortalInfo;
import com.esri.arcgisruntime.portal.PortalItem;
import com.esri.arcgisruntime.portal.PortalQueryParameters;
import com.esri.arcgisruntime.portal.PortalQueryResultSet;


public class FetchGroupBasemaps {

    private MainActivity.BasemapFetchTaskComplete mDelegate = null;
    private Portal mArcgisPortal;
    private final Activity mActivity;

    public FetchGroupBasemaps(Activity activity, Portal portal, MainActivity.BasemapFetchTaskComplete response){
        this.mActivity = activity;
        this.mArcgisPortal = portal;
        mDelegate = response;
    }

    public Void start() throws Exception {
        try {
            PortalInfo portalInformation = mArcgisPortal.getPortalInfo();
            PortalQueryParameters queryParams = new PortalQueryParameters();
            queryParams.setQuery(portalInformation.getBasemapGalleryGroupQuery());
            queryParams.setCanSearchPublic(true);
            final ListenableFuture<PortalQueryResultSet<PortalGroup>> groupFuture = mArcgisPortal.findGroupsAsync(queryParams);
            groupFuture.addDoneListener(new Runnable() {
                @Override
                public void run() {
                    try {
                        PortalQueryResultSet<PortalGroup> basemapGroupResult = groupFuture.get();
                        PortalGroup group = basemapGroupResult.getResults().get(0);
                        PortalQueryParameters basemapQueryParams = new PortalQueryParameters();
                        basemapQueryParams.setQueryForItemsInGroup(group.getGroupId());
                        final ListenableFuture<PortalQueryResultSet<PortalItem>> contentFuture = mArcgisPortal.findItemsAsync(basemapQueryParams);
                        contentFuture.addDoneListener(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    PortalQueryResultSet<PortalItem> portalResultSet = contentFuture.get();
                                    if (portalResultSet != null) {
                                        mDelegate.onBasemapFetchCompleted(portalResultSet);
                                    } else {
                                        mDelegate.onBasemapFetchFailed(mActivity.getString(R.string.err_no_items));
                                    }
                                } catch (Exception exception) {
                                    mDelegate.onBasemapFetchFailed(mActivity.getString(R.string.err_cannot_load_query) + exception.getLocalizedMessage());
                                }
                            }
                        });
                    } catch (Exception exception) {
                        mDelegate.onBasemapFetchFailed(mActivity.getString(R.string.err_cannot_query_portal) + exception.getLocalizedMessage());
                    }
                }
            });
        } catch (Exception exception) {
            mDelegate.onBasemapFetchFailed(exception.getLocalizedMessage());
        }
        return null;
    }
}
