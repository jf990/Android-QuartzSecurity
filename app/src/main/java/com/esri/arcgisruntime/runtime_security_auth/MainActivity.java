package com.esri.arcgisruntime.runtime_security_auth;

import android.app.AlertDialog;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.GridView;

import com.esri.arcgisruntime.ArcGISRuntimeException;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.datasource.arcgis.ServiceFeatureTable;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.LayerList;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.portal.Portal;
import com.esri.arcgisruntime.portal.PortalGroup;
import com.esri.arcgisruntime.portal.PortalInfo;
import com.esri.arcgisruntime.portal.PortalItem;
import com.esri.arcgisruntime.portal.PortalItemType;
import com.esri.arcgisruntime.portal.PortalQueryParams;
import com.esri.arcgisruntime.portal.PortalQueryResultSet;
import com.esri.arcgisruntime.security.AuthenticationManager;
import com.esri.arcgisruntime.security.DefaultAuthenticationChallengeHandler;
import com.esri.arcgisruntime.security.OAuthConfiguration;

import java.util.List;


public class MainActivity extends AppCompatActivity {

    private MapView mMapView;
    private ArcGISMap mMap;
    private Portal mArcgisPortal = null;
    private FeatureLayer mFeatureLayer;
    private PortalQueryResultSet<PortalItem> mPortalResultSet = null;
    private int mNextBasemap = 0;
    private boolean mUseOAuth = false;
    private boolean mUserIsLoggedIn = false;
    private boolean mLoadedFeatureService = false;

    // Configuration to set at initial load or reset:
    private Basemap.Type mStartBasemapType = Basemap.Type.IMAGERY_WITH_LABELS;
    private double mStartLatitude = 40.7576;
    private double mStartLongitude = -73.9857;
    private int mStartLevelOfDetail = 12;
    private String mPortalURL = "http://www.arcgis.com/";
    private String mOAuthRedirectURI = "arcgis-runtime-auth://auth"; // https://www.arcgis.com/sharing/oauth2/authorize";
    private String mLayerItemId = "7995c5a997d248549e563178ad25c3e1";
    private String mLayerServiceURL = "http://services1.arcgis.com/6677msI40mnLuuLr/arcgis/rest/services/US_Breweries/FeatureServer";

    // Configuration to restore on resume, reload:
    private Viewpoint mCurrentViewPoint;
    private double mMapScale;

    // define callback interface for login completion event
    interface LoginCompletionInterface {
        void onLoginCompleted();
        void onLoginFailed(int errorCode, String errorMessage);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // setup the Map button
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onClickMapButton(view);
            }
        });

        mMapView = (MapView) findViewById(R.id.mapView);
        mMap = new ArcGISMap(mStartBasemapType, mStartLatitude, mStartLongitude, mStartLevelOfDetail);
        loadLayerWithService(mLayerServiceURL);
        mMapView.setMap(mMap);
        setupChallengeHandler();
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
     * Initiate a user login with the portal configured in mPortalURL. Since this is an asynchronous
     * task, results of the login are reported to LoginCompletionInterface.
     * @param callback
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

    private boolean logoutUser() {
        mUserIsLoggedIn = false;
        invalidateOptionsMenu();
        return true;
    }

    /**
     * Set a default challenge handler provided by the SDK. We could implement our own by
     * deriving from the arcgisruntime.security.AuthenticationChallengeHandler interface.
     */
    private void setupChallengeHandler() {
        if (mUseOAuth) {
            try {
                OAuthConfiguration oauthConfig = new OAuthConfiguration(mPortalURL, getString(R.string.client_id), mOAuthRedirectURI);
                AuthenticationManager.addOAuthConfiguration(oauthConfig);
            } catch (Exception exception) {
                showErrorAlert(getString(R.string.system_error), "Cannot setup OAuth: " + exception.getLocalizedMessage());
            }
        }
        try {
            AuthenticationManager.setAuthenticationChallengeHandler(new DefaultAuthenticationChallengeHandler(this));
        } catch (Exception exception) {
            showErrorAlert(getString(R.string.system_error), getString(R.string.err_cannot_create_object) + exception.getLocalizedMessage());
        }
    }

    /**
     * Show a popup dialog of a grid containing the results of a Portal query result.
     * @param portalResultSet A set of PortalItems, Each item is displayed in a grid cell showing
     *                        thumbnail and title.
     */
    private void showGridDialog(final PortalQueryResultSet<PortalItem> portalResultSet) {
        GridView gridView = new GridView(this);
        final AlertDialog gridViewAlertDialog;

        gridView.setAdapter(new PortalItemQueryAdapter(getApplicationContext(), MainActivity.this, portalResultSet));
        gridView.setNumColumns(2);
        gridView.setDrawSelectorOnTop(true);
        gridView.setSelector(R.drawable.selector_basemap);

        // Set grid view to alertDialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(gridView);
        builder.setTitle(R.string.title_select_basemap);
        gridViewAlertDialog = builder.show();

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < portalResultSet.getTotalResults()) {
                    List<PortalItem> portalResults = portalResultSet.getResults();
                    if (portalResults != null) {
                        PortalItem portalItem = portalResults.get(position);
                        if (portalItem != null) {
                            if (gridViewAlertDialog != null) {
                                gridViewAlertDialog.dismiss();
                            }
                            changeBasemapToPortalItem(portalItem);
                        }
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
     * This method cycles through 6 standard basemaps.
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
     * Query the Portal for a list of available basemaps. When the result set comes back we
     * display the results in a popup dialog grid.
     */
    private void showBasemapSelector() {
        if (mArcgisPortal == null) {
            // if we arrived here yet not logged in then some logic is wrong.
            return;
        }
        PortalInfo portalInformation = mArcgisPortal.getPortalInfo();
        PortalQueryParams queryParams = new PortalQueryParams();
        queryParams.setQuery(portalInformation.getBasemapGalleryGroupQuery());
        final ListenableFuture<PortalQueryResultSet<PortalGroup>> groupFuture = mArcgisPortal.findGroupsAsync(queryParams);
        groupFuture.addDoneListener(new Runnable() {
            @Override
            public void run() {
                try {
                    PortalQueryResultSet<PortalGroup> basemapGroupResult = groupFuture.get();
                    PortalGroup group = basemapGroupResult.getResults().get(0);
                    PortalQueryParams basemapQueryParams = new PortalQueryParams();
                    basemapQueryParams.setQueryForItemsInGroup(group.getId());
                    final ListenableFuture<PortalQueryResultSet<PortalItem>> contentFuture = mArcgisPortal.findItemsAsync(basemapQueryParams);
                    contentFuture.addDoneListener(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                mPortalResultSet = contentFuture.get();
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        showGridDialog(mPortalResultSet);
                                    }
                                });
                            } catch (Exception exception) {
                                showErrorAlert(getString(R.string.system_error), getString(R.string.err_cannot_load_query) + exception.getLocalizedMessage());
                            }
                        }
                    });
                } catch (Exception exception) {
                    showErrorAlert(getString(R.string.system_error), getString(R.string.err_cannot_query_portal) + exception.getLocalizedMessage());
                }
            }
        });
    }

    private void loadLayerWithService(String serviceURL) {
        ServiceFeatureTable serviceFeatureTable = new ServiceFeatureTable(serviceURL);
        mFeatureLayer = new FeatureLayer(serviceFeatureTable);
        mMap.getOperationalLayers().add(mFeatureLayer);
        mLoadedFeatureService = true;
    }

    /**
     * Handler to respond to the Map button tap. If the user is logged in show the list of
     * basemaps from their organizational account.
     * @param view
     */
    private void onClickMapButton(View view) {
        if ( ! mUserIsLoggedIn) {
            loginUser(loginCompletionCallback);
        } else {
             showBasemapSelector();
        }
    }

    /**
     * A simple interface for handling asynchronous events after a login succeeds or fails.
     */
    private final LoginCompletionInterface loginCompletionCallback = new LoginCompletionInterface () {
        public void onLoginCompleted() {
            showBasemapSelector();
        }

        public void onLoginFailed(int errorCode, String errorMessage) {
            showErrorAlert(getString(R.string.action_login), errorMessage + "(" + errorCode + ") " + getString(R.string.info_login_to_continue));
        }
    };

    /**
     * A simple alert box with an OK button to cancel it.
     * @param errorTitle String A string to use for the title of the alert.
     * @param errorMessage String A string to use for the message to display.
     */
    private void showErrorAlert (String errorTitle, String errorMessage) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
        alertDialog.setTitle(errorTitle);
        alertDialog.setMessage(errorMessage);
        alertDialog.setCancelable(true);
        alertDialog.setNegativeButton(R.string.action_OK, null);
        AlertDialog alertDialogInstance = alertDialog.create();
        alertDialogInstance.show();
    }
}
