package com.esri.arcgisruntime.runtime_security_auth;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Map;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.portal.Portal;
import com.esri.arcgisruntime.portal.PortalInfo;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.security.AuthenticationManager;
import com.esri.arcgisruntime.security.DefaultAuthenticationChallengeHandler;

public class MainActivity extends AppCompatActivity {

    private MapView mMapView;
    private double mStartLatitude = 40.7576;
    private double mStartLongitude = -73.9857;
    private int mStartLevelOfDetail = 17;
    private String mPortalURL = "http://www.arcgis.com";
    private boolean mUserIsLoggedIn = false;

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
                Snackbar.make(view, "Login", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        mMapView = (MapView) findViewById(R.id.mapView);
        Map map = new Map(Basemap.Type.IMAGERY_WITH_LABELS, mStartLatitude, mStartLongitude, mStartLevelOfDetail);
        mMapView.setMap(map);
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
                loginInUser();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean loginInUser() {
        final Portal arcgisPortal = new Portal(mPortalURL, true);
        arcgisPortal.addDoneLoadingListener(new Runnable() {
            @Override
            public void run() {
                final String info;
                LoadStatus loadStatus = arcgisPortal.getLoadStatus();
                if (loadStatus == LoadStatus.LOADED) {
                    PortalInfo portalInformation = arcgisPortal.getPortalInfo();
                    info = portalInformation.getPortalName() + " for " + portalInformation.getOrganizationName();
                    mUserIsLoggedIn = true;
                } else {
                    info = "Login failed - but why? cancel? invalid credentials? bad network?";
                }
                invalidateOptionsMenu();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()){
                            AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
                            alertDialog.setTitle(R.string.action_login);
                            alertDialog.setMessage(info);
                            alertDialog.setCancelable(true);
                            alertDialog.setNegativeButton(R.string.action_OK, null);
                            AlertDialog alertDialogInstance = alertDialog.create();
                            alertDialogInstance.show();
                        }
                    }
                });
            }
        });
        arcgisPortal.loadAsync();
        return true;
    }

    private boolean logoutUser () {
        mUserIsLoggedIn = false;
        invalidateOptionsMenu();
        return true;
    }

    /**
     * Set a default challenge handler provided by the SDK. We could implement our own by
     * deriving from AuthenticationChallengeHandler interface.
     */
    private void setupChallengeHandler () {
        DefaultAuthenticationChallengeHandler authenticationChallengeHandler = new DefaultAuthenticationChallengeHandler(this);
        AuthenticationManager.setAuthenticationChallengeHandler(authenticationChallengeHandler);
    }
}
