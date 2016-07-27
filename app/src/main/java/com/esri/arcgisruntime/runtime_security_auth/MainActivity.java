/**
 * MainActivity for the ArcGIS Runtime Security application framework.
 */

package com.esri.arcgisruntime.runtime_security_auth;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.ArcGISRuntimeException;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.datasource.Feature;
import com.esri.arcgisruntime.datasource.arcgis.ServiceFeatureTable;
import com.esri.arcgisruntime.geometry.Geometry;
import com.esri.arcgisruntime.geometry.GeometryType;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.location.AndroidLocationDataSource;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.LayerList;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.portal.Portal;
import com.esri.arcgisruntime.portal.PortalItem;
import com.esri.arcgisruntime.portal.PortalItemType;
import com.esri.arcgisruntime.portal.PortalQueryResultSet;
import com.esri.arcgisruntime.security.AuthenticationManager;
import com.esri.arcgisruntime.security.DefaultAuthenticationChallengeHandler;
import com.esri.arcgisruntime.security.OAuthConfiguration;
import com.esri.arcgisruntime.tasks.route.DirectionDistanceTextUnits;
import com.esri.arcgisruntime.tasks.route.RouteParameters;
import com.esri.arcgisruntime.tasks.route.RouteTask;
import com.esri.arcgisruntime.tasks.route.Stop;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private MapView mMapView = null;
    private GridView mBasemapGridView = null;
    private ArcGISMap mMap = null;
    private Portal mArcgisPortal = null;
    private FeatureLayer mFeatureLayer;
    private Feature mFeatureToRouteTo;
    private ArrayList<BasemapItem> mBasemapList = null; // maintain a cache of the basemaps we discover
    private int mNextBasemap = 0;
    private boolean mShowErrors = true;
    private boolean mUseOAuth = true;
    private boolean mUserIsLoggedIn = false;
    private boolean mLoadedFeatureService = false;
    private boolean mLoadImagesSerial = true;
    private int mThumbnailsRequested = 0;

    // Configuration to set at initial load or reset:
    private Basemap.Type mStartBasemapType = Basemap.Type.IMAGERY_WITH_LABELS;
    private double mStartLatitude = 40.7576;
    private double mStartLongitude = -73.9857;
    private int mStartLevelOfDetail = 12;
    private String mPortalURL = "http://www.arcgis.com/";
    private String mOAuthRedirectURI = "arcgis-runtime-auth://auth";
    private String mClientId = "OOraRX2FZx7X6cTs";
    private String mLayerItemId = "7995c5a997d248549e563178ad25c3e1";
    private String mLayerServiceURL = "http://services1.arcgis.com/6677msI40mnLuuLr/arcgis/rest/services/US_Breweries/FeatureServer/0";
    private String mRouteTaskURL = "http://route.arcgis.com/arcgis/rest/services/World/Route/NAServer/Route_World";

    // Configuration to restore on resume, reload:
    private Viewpoint mCurrentViewPoint;
    private double mMapScale;

    /**
     * Define a delegation interface for the login completion event
     */
    interface LoginCompletionInterface {
        void onLoginCompleted();
        void onLoginFailed(int errorCode, String errorMessage);
    }

    /**
     * Define a delegation interface for asynchronous loading of thumbnails from the portal.
     */
    public interface BasemapFetchTaskComplete {
        void onBasemapFetchCompleted(final PortalQueryResultSet<PortalItem> portalResultSet);
        void onBasemapFetchFailed(String errorMessage);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // setup the Map button
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        if (fab != null) {
            try {
                fab.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        onClickMapButton(view);
                    }
                });
            } catch (Exception exception) {
                Log.d("MENU-BUTTON", "Cannot create instance of FloatingActionButton");
            }
        }
        setupMap();
    }

    @Override
    protected void onPause() {
        mMapView.pause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.resume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_login).setTitle(mUserIsLoggedIn ? R.string.action_logout : R.string.action_login);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_login) {
            if (mUserIsLoggedIn) {
                logoutUser();
            } else {
                loginUser(null);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Perform all the necessary steps to setup, initialize, and load the map for the first time.
     */
    private void setupMap() {
        ArcGISRuntimeEnvironment.setClientId(mClientId);
        Log.d("setupMap", "ArcGIS version: " + ArcGISRuntimeEnvironment.getAPIVersion() + ", " + ArcGISRuntimeEnvironment.getAPILabel());
        mMapView = (MapView) findViewById(R.id.mapView);
        mMap = new ArcGISMap(mStartBasemapType, mStartLatitude, mStartLongitude, mStartLevelOfDetail);
        mMapView.setMap(mMap);
        loadLayerWithService(mLayerServiceURL);
        setupChallengeHandler();
    }

    /**
     * Set a default challenge handler provided by the SDK. We could implement our own by
     * deriving from the arcgisruntime.security.AuthenticationChallengeHandler interface.
     */
    private void setupChallengeHandler() {
        if (mUseOAuth) {
            try {
                OAuthConfiguration oauthConfig = new OAuthConfiguration(mPortalURL, mClientId, mOAuthRedirectURI);
                AuthenticationManager.addOAuthConfiguration(oauthConfig);
            } catch (Exception exception) {
                showErrorAlert(getString(R.string.system_error), getString(R.string.err_cannot_create_oauth) + exception.getLocalizedMessage());
            }
        }
        try {
            AuthenticationManager.setAuthenticationChallengeHandler(new DefaultAuthenticationChallengeHandler(this));
        } catch (Exception exception) {
            showErrorAlert(getString(R.string.system_error), getString(R.string.err_cannot_create_object) + exception.getLocalizedMessage());
        }
    }

    /**
     * Initiate a user login with the portal configured in mPortalURL. Since this is an asynchronous
     * task, results of the login are reported to LoginCompletionInterface.
     * @param callback use the LoginCompletionInterface to report the result of the login and continue.
     * @return
     */
    private boolean loginUser(final LoginCompletionInterface callback) {
        if (mArcgisPortal == null) {
            mArcgisPortal = new Portal(mPortalURL, true);
        }
        mArcgisPortal.addDoneLoadingListener(new Runnable() {
            @Override
            public void run() {
                final String info;
                final int errorCode;
                LoadStatus loadStatus = mArcgisPortal.getLoadStatus();
                if (loadStatus == LoadStatus.LOADED) {
                    // PortalInfo portalInformation = mArcgisPortal.getPortalInfo();
                    // info = portalInformation.getPortalName() + " for " + portalInformation.getOrganizationName();
                    info = null;
                    errorCode = 0;
                    mUserIsLoggedIn = true;
                } else {
                    ArcGISRuntimeException loadError = mArcgisPortal.getLoadError();
                    if (loadError != null) {
                        errorCode = loadError.getErrorCode();
                        if (errorCode == 25) {
                            Throwable loadErrorReason = loadError.getCause();
                            info = loadErrorReason.getLocalizedMessage();
                        } else {
                            info = loadError.getLocalizedMessage();
                        }
                    } else {
                        errorCode = 99999;
                        info = getString(R.string.err_login_failed);
                    }
                }
                invalidateOptionsMenu();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            if (callback != null) {
                                if (mUserIsLoggedIn) {
                                    callback.onLoginCompleted();
                                } else {
                                    callback.onLoginFailed(errorCode, info);
                                }
                            } else if (!mUserIsLoggedIn) {
                                showErrorAlert(getString(R.string.action_login), getString(R.string.info_not_logged_in) + info + "(" + errorCode + ")");
                            }
                        }
                    }
                });
            }
        });
        mArcgisPortal.loadAsync();
        return true;
    }

    /**
     * Logout is called from the menu. After logout the state is set to logged out so we can then
     * log in as a different user.
     * @return
     */
    private boolean logoutUser() {
        AuthenticationManager.CredentialCache.clear();
        mUserIsLoggedIn = false;
        invalidateOptionsMenu();
        return true;
    }

    /**
     * Iterate the portal query result set and build a cache of the portal items. Also start
     * the fetch of each item's thumbnail.
     * @param portalResultSet
     */
    private void buildPortalItemCache(final PortalQueryResultSet<PortalItem> portalResultSet) {
        if (portalResultSet != null) {
            List<PortalItem> queryResults = portalResultSet.getResults();
            if (queryResults != null && ! queryResults.isEmpty()) {
                if (mBasemapList == null) {
                    mBasemapList = new ArrayList<>(queryResults.size());
                } else if ( ! mBasemapList.isEmpty()) {
                    mBasemapList.clear();
                }
                for (int index = 0; index < queryResults.size(); index ++) {
                    PortalItem portalItem = queryResults.get(index);
                    BasemapItem basemapItem = new BasemapItem(index, portalItem);
                    if (basemapItem != null) {
                        mThumbnailsRequested ++;
                        if ((mLoadImagesSerial && index == 0) || ! mLoadImagesSerial) {
                            basemapItem.loadImage(imageLoadedCompletionInterface);
                        }
                        mBasemapList.add(basemapItem);
                    }
                }
            }
        }
    }

    /**
     * Show a popup dialog of a grid containing the results of a Portal query result. This function
     * assumes the base map list mBasemapList was previously assembled.
     */
    private void showGridDialog() {
        if (mBasemapGridView != null) {
            return; // currently showing
        }
        if (mBasemapList == null || mBasemapList.size() < 1) {
            showErrorAlert(getString(R.string.sequence_error), getString(R.string.err_no_items));
            return;
        }
        GridView gridView = new GridView(this);
        final AlertDialog gridViewAlertDialog;

        gridView.setAdapter(new PortalItemQueryAdapter(MainActivity.this, mBasemapList));
        gridView.setNumColumns(2);
        gridView.setDrawSelectorOnTop(true);
        gridView.setSelector(R.drawable.selector_basemap);

        // Set grid view to alertDialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(gridView);
        builder.setTitle(R.string.title_select_basemap);
        gridViewAlertDialog = builder.show();
        mBasemapGridView = gridView;

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < mBasemapList.size()) {
                    PortalItem portalItem = mBasemapList.get(position).getPortalItem();
                    if (portalItem != null) {
                        Log.d("CLICK", "Clicked on " + portalItem.getTitle());
                        if (gridViewAlertDialog != null) {
                            mBasemapGridView = null;
                            gridViewAlertDialog.dismiss();
                        }
                        changeBasemapToPortalItem(portalItem);
                    }
                }
            }
        });
    }

    /**
     * Set the basemap to a portal item (as long as it is a WEBMAP)
     * If the item is not loaded we attempt to load it
     * @param portalItem
     */
    public void changeBasemapToPortalItem(final PortalItem portalItem) {
        if (portalItem != null && portalItem.getType() == PortalItemType.WEBMAP) {
            mCurrentViewPoint = mMapView.getCurrentViewpoint(Viewpoint.Type.CENTER_AND_SCALE);
            mMapScale = mMapView.getMapScale();
            portalItem.addDoneLoadingListener(new Runnable() {
                @Override
                public void run() {
                    LoadStatus loadStatus = portalItem.getLoadStatus();
                    if (loadStatus == LoadStatus.LOADED) {
                        mMap.setBasemap(new Basemap(portalItem));
                        mMapView.setViewpointAsync(mCurrentViewPoint);
                        mMapView.setViewpointScaleAsync(mMapScale);
                    } else {
                        ArcGISRuntimeException loadError = portalItem.getLoadError();
                        showErrorAlert(getString(R.string.system_error), getString(R.string.err_cannot_load_item) + " " + loadError.getLocalizedMessage());
                    }
                }
            });
            portalItem.loadAsync();
        }
    }

    /**
     * This method cycles through 6 standard base maps. Use this as a test interface to display
     * different base maps.
     */
    public void changeBasemapToBasemapType() {
        Basemap newBasemap;
        mCurrentViewPoint = mMapView.getCurrentViewpoint(Viewpoint.Type.CENTER_AND_SCALE);
        mMapScale = mMapView.getMapScale();
        mNextBasemap = (mNextBasemap + 1) % 6;
        switch(mNextBasemap) {
            case 0:
                newBasemap = Basemap.createImageryWithLabels();
                break;
            case 1:
                newBasemap = Basemap.createStreets();
                break;
            case 2:
                newBasemap = Basemap.createLightGrayCanvas();
                break;
            case 3:
                newBasemap = Basemap.createNationalGeographic();
                break;
            case 4:
                newBasemap = Basemap.createTopographic();
                break;
            default:
                newBasemap = Basemap.createImagery();
                break;
        }
        mMap.setBasemap(newBasemap);
        mMapView.setViewpointAsync(mCurrentViewPoint);
        mMapView.setViewpointScaleAsync(mMapScale);
    }

    /**
     * Request to show the base map selector popup. If we have not loaded the base maps
     * then fetch them from the portal.
     */
    private void showBasemapSelector() {
        if (mArcgisPortal == null) {
            // if we arrived here yet not logged in then some logic is wrong.
            showErrorAlert(getString(R.string.sequence_error), getString(R.string.err_login_required));
            return;
        }
        if (mBasemapList == null) {
            // no base maps in the cache requires us to see if we can load them from the portal
            FetchGroupBasemaps fetchGroupBasemaps = new FetchGroupBasemaps(this, mArcgisPortal, basemapFetchTaskComplete);
            if (fetchGroupBasemaps!= null) {
                try {
                    fetchGroupBasemaps.start();
                } catch (Exception exception) {
                    showErrorAlert(getString(R.string.action_fetch_basemaps), getString(R.string.err_fetching_basemaps));
                }
            }
        } else {
            showGridDialog();
        }
    }

    private void setMapTouchHandler() {
        mMapView.setOnTouchListener(new IdentifyFeatureLayerTouchListener(this, mMapView, mFeatureLayer));
    }

    /**
     * Load a feature layer given a feature service URL.
     * @param serviceURL String The URL pointing to the feature service (from ArcGIS Online.)
     */
    private void loadLayerWithService(String serviceURL) {
        ServiceFeatureTable serviceFeatureTable = new ServiceFeatureTable(serviceURL);
        mFeatureLayer = new FeatureLayer(serviceFeatureTable);
        mMap.getOperationalLayers().add(mFeatureLayer);
        mLoadedFeatureService = true;
        setMapTouchHandler();
    }

    /**
     * Load a feature service given its item id (from ArcGIS Online.)
     * @param itemId String the item id of the feature layer
     */
    private void loadLayerWithItem (String itemId) {
        if (itemId != null && mMap != null && mArcgisPortal != null) {
            try {
                final PortalItem portalItem = new PortalItem(mArcgisPortal, itemId);
                if (portalItem != null) {
                    portalItem.addDoneLoadingListener(new Runnable() {
                        @Override
                        public void run() {
                            layerLoaded(portalItem);
                            setMapTouchHandler();
                        }
                    });
                    portalItem.loadAsync();
                }
            } catch (ArcGISRuntimeException exception) {
                Log.d("loadLayerWithItem", "Runtime exception " + exception.getLocalizedMessage());
            }
        } else {
            Log.d("loadLayerWithItem", "missing required parameter");
        }
    }

    /**
     * Handle processing the result of async load of a portal item that is a layer.
     * @param portalItem
     */
    private void layerLoaded (final PortalItem portalItem) {
        LoadStatus loadStatus = portalItem.getLoadStatus();
        if (loadStatus == LoadStatus.LOADED) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mFeatureLayer = new FeatureLayer(portalItem, 0);
                    LayerList layerList = mMap.getOperationalLayers();
                    if (layerList != null) {
                        layerList.add(mFeatureLayer);
                    }
                }
            });
        } else {
            ArcGISRuntimeException loadError = portalItem.getLoadError();
            showErrorAlert(getString(R.string.system_error), getString(R.string.err_cannot_load_layer) + " " + loadError.getLocalizedMessage());
        }
    }

    /**
     * Handler to respond to the Map button tap. If the user is logged in show the list of
     * basemaps from their organizational account.
     * @param view
     */
    private void onClickMapButton(View view) {
        if ( ! mUserIsLoggedIn) {
            loginUser(loginCompletionCallbackForBasemaps);
        } else {
             showBasemapSelector();
        }
    }

    /**
     * Given a specific basemapItem instance, find that item in the grid view and refresh its content.
     * @param basemapItem
     */
    public void refreshBasemapThumbnail (BasemapItem basemapItem) {
        if (mBasemapGridView == null || basemapItem == null) {
            return;
        }
        Log.d("refreshBasemapThumbnail", "OK somehow need to set bitmap on gridview for " + basemapItem.getIndex());
        View gridViewCell = mBasemapGridView.getChildAt(basemapItem.getIndex());
        if (gridViewCell != null) {
            PortalItem portalItem = basemapItem.getPortalItem();
            TextView textView = (TextView) gridViewCell.findViewById(R.id.textViewMap);
            textView.setText(portalItem.getTitle());
            final ImageView imageView = (ImageView) gridViewCell.findViewById(R.id.imageViewMap);
            if (imageView != null) {
                Bitmap thumbnail = basemapItem.getImage();
                if (thumbnail != null) {
                    imageView.setImageBitmap(thumbnail);
                } else {
                    Log.d("refreshBasemapThumbnail", "No image loaded (yet) for " + portalItem.getTitle());
                }
            }
        }
    }

    /**
     * Implement the login completion interface for loading organization basemaps.
     */
    private final LoginCompletionInterface loginCompletionCallbackForBasemaps = new LoginCompletionInterface() {
        public void onLoginCompleted() {
            loadLayerWithItem(mLayerItemId);
            showBasemapSelector();
        }

        public void onLoginFailed(int errorCode, String errorMessage) {
            showErrorAlert(getString(R.string.action_login), errorMessage + "(" + errorCode + ") " + getString(R.string.info_login_to_continue));
        }
    };

    /**
     * Implement the login completion interface for loading route task.
     */
    private final LoginCompletionInterface loginCompletionCallbackForRouting = new LoginCompletionInterface() {
        public void onLoginCompleted() {
            loadRouteTask();
        }

        public void onLoginFailed(int errorCode, String errorMessage) {
            showErrorAlert(getString(R.string.action_login), errorMessage + "(" + errorCode + ") " + getString(R.string.info_login_to_continue));
        }
    };

    /**
     * Interface for handling asynchronous fetching of the list of available base maps
     */
    private final BasemapFetchTaskComplete basemapFetchTaskComplete = new BasemapFetchTaskComplete() {
        public void onBasemapFetchCompleted(final PortalQueryResultSet<PortalItem> portalResultSet) {
            buildPortalItemCache(portalResultSet);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showGridDialog();
                }
            });
        }

        public void onBasemapFetchFailed(String errorMessage) {
            showErrorAlert(getString(R.string.action_fetch_basemaps), getString(R.string.err_fetching_basemaps) + ": " + errorMessage);
        }
    };

    /**
     * When loading items in serial order, find the next unloaded item and try to load it.
     */
    private void loadNextUnloadedThumbnail() {
        for (int index = 0; index < mBasemapList.size(); index ++) {
            BasemapItem nextBasemapItem = mBasemapList.get(index);
            if (nextBasemapItem != null) {
                if ( ! nextBasemapItem.isLoaded()) {
                    nextBasemapItem.loadImage(imageLoadedCompletionInterface);
                    break;
                }
            }
        }
    }

    /**
     * Implementation of the ImageLoadedCompletionInterface when thumbnails load asynchronously and
     * we receive the delegation so we can determine what to do with the loaded (or failed) image.
     */
    private final BasemapItem.ImageLoadedCompletionInterface imageLoadedCompletionInterface = new BasemapItem.ImageLoadedCompletionInterface() {
        public void onImageCompleted(BasemapItem basemapItem) {
            mThumbnailsRequested --;
            if (mBasemapGridView != null) {
                refreshBasemapThumbnail(basemapItem);
            }
            if (mLoadImagesSerial) {
                loadNextUnloadedThumbnail();
            }
        }

        public void onImageFailed(BasemapItem basemapItem, String errorMessage) {
            mThumbnailsRequested --;
            if (mLoadImagesSerial) {
                loadNextUnloadedThumbnail();
            }
        }
    };

    /**
     * A simple alert box with an OK button to cancel it. Use this to report info and errors
     * to the user.
     * @param errorTitle String A string to use for the title of the alert.
     * @param errorMessage String A string to use for the message to display.
     */
    public void showErrorAlert (String errorTitle, String errorMessage) {
        Log.d(errorTitle, errorMessage);
        if (mShowErrors) {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
            alertDialog.setTitle(errorTitle);
            alertDialog.setMessage(errorMessage);
            alertDialog.setCancelable(true);
            alertDialog.setNegativeButton(R.string.action_OK, null);
            AlertDialog alertDialogInstance = alertDialog.create();
            alertDialogInstance.show();
        }
    }

    /**
     * Begin route from current location to specified feature location. This is the starting
     * point to a rather complex asynchronous sequence of events:
     *   1. User must be logged in. If user is not logged in then login now.
     *   2. Read device location. If locator is not on then turn it on and wait for a stable read.
     *   3. Load RouteTask. This is async and we wait for it to complete.
     *   4. Load default Parameters
     *   5. Set Parameters
     *   6. Set stops: current device location, feature location
     *   7. solve route
     *   8. create graphics overlay
     *   9. getRouteGeometry, add route graphics to graphics layer
     * @param featureToRouteTo The route ends here.
     */
    public void startRouteTask(Feature featureToRouteTo) {
        if (featureToRouteTo != null) {
            mFeatureToRouteTo = featureToRouteTo;
            if ( ! mUserIsLoggedIn) {
                loginUser(loginCompletionCallbackForRouting);
            } else {
                loadRouteTask();
            }
        } else {
            Log.d("startRouteTask", "No feature to end route task!");
        }
    }

    /**
     * Make sure we are able to load the route task
     */
    public void loadRouteTask() {
        final RouteTask routeTask = new RouteTask(mRouteTaskURL);
        if (routeTask != null) {
            routeTask.addDoneLoadingListener(new Runnable() {
                @Override
                public void run() {
                    ArcGISRuntimeException loadError = routeTask.getLoadError();
                    LoadStatus loadStatus = routeTask.getLoadStatus();
                    if (loadError == null && loadStatus == LoadStatus.LOADED) {
                        setupRouteParameters(routeTask);
                    } else {
                        Log.d("startRouteTask", "Not able to load route task status=" + loadStatus + ", error=" + loadError.getCause());
                    }
                }
            });
            routeTask.loadAsync();
        }
    }

    /**
     * With a loaded route task setup the route parameters.
     * @param routeTask
     */
    public void setupRouteParameters(final RouteTask routeTask) {
        if (mFeatureToRouteTo != null && mUserIsLoggedIn) {
            final ListenableFuture<RouteParameters> routeParametersFuture = routeTask.generateDefaultParametersAsync();
            routeParametersFuture.addDoneListener(new Runnable() {
                @Override
                public void run() {
                    try {
                        Stop routeToStop = null;
                        Stop routeFromStop = null;

                        Geometry routeToPoint = mFeatureToRouteTo.getGeometry();
                        if (routeToPoint != null && routeToPoint.getGeometryType() == GeometryType.POINT) {
                            routeToStop = new Stop((Point)routeToPoint);
                        }
                        if (routeToStop != null && routeFromStop != null) {
                            RouteParameters routeParameters = routeParametersFuture.get();
                            routeParameters.setReturnDirections(true);
                            routeParameters.setReturnRoutes(true);
                            routeParameters.setOutputSpatialReference(mMapView.getSpatialReference());
                            routeParameters.setDirectionsDistanceTextUnits(DirectionDistanceTextUnits.IMPERIAL);
                            routeParameters.getStops().add(routeFromStop);
                            routeParameters.getStops().add(routeToStop);
                        } else {
                            Log.d("setupRouteParameters", "Not enough info to solve a route.");
                        }
                    } catch (ArcGISRuntimeException exception) {
                        Log.d("setupRouteParameters", "Runtime exception: (" + exception.getErrorCode() + ") " + exception.getCause());
                    } catch (Exception exception) {
                        Log.d("setupRouteParameters", "Cannot start route: " + exception.getLocalizedMessage());
                    }
                }
            });
        } else {
            Log.d("startRouteTask", "No feature to end route task, or not logged in!");
        }
    }

    public void getDeviceLocation() {
        try {
            AndroidLocationDataSource locationDataSource = new AndroidLocationDataSource(this);
            if (locationDataSource != null) {
                locationDataSource.startAsync();
            }
        } catch (Exception exception) {
            Log.d("getDeviceLocation", "Location datasource fails to init: (" + exception.getLocalizedMessage());
        }
    }
}
