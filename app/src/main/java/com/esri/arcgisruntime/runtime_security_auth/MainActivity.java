package com.esri.arcgisruntime.runtime_security_auth;

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

public class MainActivity extends AppCompatActivity {

    private MapView mMapView;
    private double mStartLat = 40.7576;
    private double mStartLon = -73.9857;
    private int mStartLOD = 17;
    private boolean mUserIsLoggedIn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Login", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        mMapView = (MapView) findViewById(R.id.mapView);
        Map map = new Map(Basemap.Type.IMAGERY_WITH_LABELS, mStartLat, mStartLon, mStartLOD);
        mMapView.setMap(map);
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
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
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
        final Portal arcgisPortal = new Portal("http://www.arcgis.com", true);
        arcgisPortal.addDoneLoadingListener(new Runnable() {
            @Override
            public void run() {
                String info;
                LoadStatus loadStatus = arcgisPortal.getLoadStatus();
                if (loadStatus == LoadStatus.LOADED) {
                    PortalInfo portalInformation = arcgisPortal.getPortalInfo();
                    info = portalInformation.getPortalName();
                    mUserIsLoggedIn = true;
                } else {
                    info = "Login failed";
                }
            }
        });
        arcgisPortal.loadAsync();
        return true;
    }

    private boolean logoutUser () {
        mUserIsLoggedIn = false;
        return true;
    }
}
