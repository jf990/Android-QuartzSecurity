/**
 * IdentifyFeatureLayerTouchListener is a touch listener class to respond to touch events on the map view.
 */

package com.esri.arcgisruntime.runtime_security_auth;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.util.Linkify;
import android.util.Log;
import android.view.MotionEvent;

import com.esri.arcgisruntime.ArcGISRuntimeException;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.datasource.Feature;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.mapping.GeoElement;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.IdentifyLayerResult;
import com.esri.arcgisruntime.mapping.view.MapView;

import java.util.List;
import java.util.Map;


public class IdentifyFeatureLayerTouchListener extends DefaultMapViewOnTouchListener {

    private Context mContext = null;
    private MapView mMapView = null; // reference to the map view we are working on
    private FeatureLayer mFeatureLayer = null; // reference to the layer to identify features in
    private static Feature mLastFeatureSelected = null;

    /**
     * Construct a touch listener
     * @param context - application context
     * @param mapView - reference to our main activity map view
     * @param layerToIdentify - reference to the feature layer we want to use to identify features
     */
    public IdentifyFeatureLayerTouchListener(Context context, MapView mapView, FeatureLayer layerToIdentify) {
        super(context, mapView);
        mContext = context;
        mMapView = mapView;
        mFeatureLayer = layerToIdentify;
    }

    /**
     * Respond to a single touch event
     * @param motionEvent
     * @return
     */
    @Override
    public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
        // get the screen point where user tapped
        final android.graphics.Point screenPoint = new android.graphics.Point((int) motionEvent.getX(), (int) motionEvent.getY());
        Log.d("onSingleTapConfirmed", "Touch detected at " + screenPoint.toString());
        return identifyFeatureNearPoint(screenPoint);
    }

    /**
     * Setup the asynchronous handler to identify any features on the map near the screen point provided.
     * @param screenPoint
     * @return {boolean} Returns false if we are not able to fire the future.
     */
    public boolean identifyFeatureNearPoint(final android.graphics.Point screenPoint) {
        boolean identified = true;
        if (mMapView != null && mFeatureLayer != null) {
            try {
                final ListenableFuture<IdentifyLayerResult> identifyFuture = mMapView.identifyLayerAsync(mFeatureLayer, screenPoint, 32.0, 1);
                identifyFuture.addDoneListener(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // get the identify results from the future - returns when the operation is complete
                            IdentifyLayerResult identifyLayerResult = identifyFuture.get();
                            if (identifyLayerResult != null) {
                                FeatureLayer featureLayer = null;
                                if (identifyLayerResult.getLayerContent() instanceof FeatureLayer) {
                                    featureLayer = (FeatureLayer) identifyLayerResult.getLayerContent();
                                }
                                List<GeoElement> identifiedFeaturesList = identifyLayerResult.getIdentifiedElements();
                                if (identifiedFeaturesList.size() > 0) {
                                    // Our app only wants one feature selected at a time. Monitor the last
                                    // selected feature so we can pass it to the route task if the user asks for a route.
                                    featureLayer.clearSelection();
                                    mLastFeatureSelected = (Feature) identifiedFeaturesList.get(0);
                                    showInfoForFeature(featureLayer, mLastFeatureSelected);
                                } else {
                                    Log.d("onSingleTapConfirmed", "No features detected near " + screenPoint.toString());
                                }
                            } else {
                                Log.d("onSingleTapConfirmed", "No layers detected near " + screenPoint.toString());
                            }
                        } catch (ArcGISRuntimeException exception) {
                            showErrorAlert(mContext.getString(R.string.unknown_error), mContext.getString(R.string.err_fetching_feature) + " " + exception.getErrorCode() + " " + exception.getCause());
                        } catch (Exception exception) {
                            showErrorAlert(mContext.getString(R.string.unknown_error), mContext.getString(R.string.err_fetching_feature) + " " + exception.getMessage());
                        }
                    }
                });
            } catch (IllegalArgumentException exception) {
                Log.d("onSingleTapConfirmed", "bad arg for identifyPopupsAsync: " + exception.getLocalizedMessage());
                identified = false;
            } catch (ArcGISRuntimeException exception) {
                Log.d("onSingleTapConfirmed", "Runtime exception: (" + exception.getErrorCode() + ") " + exception.getCause());
                identified = false;
            } catch (Exception exception) {
                Log.d("onSingleTapConfirmed", "exception for identifyPopupsAsync: " + exception.getLocalizedMessage());
                identified = false;
            }
        }
        return identified;
    }

    /**
     * Once we are able to identify a feature on the map then build some GUI to display the info to the user.
     * Display name, contact, website, description.
     * @param arcgisFeature {Feature} the feature we wish to display information about.
     */
    public void showInfoForFeature (FeatureLayer featureLayer, Feature arcgisFeature) {
        if (arcgisFeature != null) {
            if (featureLayer != null) {
                featureLayer.selectFeature(arcgisFeature);
            }
            Map<String, Object> featureAttributes = arcgisFeature.getAttributes();
            if (featureAttributes != null) {
                String title = (String) featureAttributes.get("name");
                String website = (String) featureAttributes.get("website");
                String phoneNumber = (String) featureAttributes.get("contact");
                String description = (String) featureAttributes.get("description");
                createPopupDialog(title, description, phoneNumber, website);
            }
        }
    }

    /**
     * Creates an alert dialog for the given attributes. Here we are going to display the title and
     * description fields from our feature service, plus an OK button and a Route button.
     * @param title {String} Name or title of feature to display
     * @param description {String} description field of the feature
     * @param phoneNumber {String} contact telephone number
     * @param website {String} URL to link to
     */
    private void createPopupDialog(String title, String description, String phoneNumber, String website) {
        AppCompatActivity activity = (AppCompatActivity) mContext;

        String formattedDescription = "";
        if (phoneNumber != null && phoneNumber.length() > 0) {
            formattedDescription = phoneNumber;
        }
        if (website != null && website.length() > 0) {
            if (formattedDescription.length() > 0) {
                formattedDescription += " - ";
            }
            formattedDescription += website;
        }
        if (formattedDescription.length() > 0) {
            formattedDescription += "\n";
        }
        if (description != null && description.length() > 0) {
            formattedDescription += description;
        }
        final SpannableString fullDescription = new SpannableString(formattedDescription);
        Linkify.addLinks(fullDescription, Linkify.ALL);

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(activity);
        alertDialog.setTitle(title);
        alertDialog.setIcon(R.drawable.arcgisruntime_mapview_magnifier);
        alertDialog.setMessage(fullDescription);
        alertDialog.setCancelable(true);
        alertDialog.setPositiveButton(R.string.action_route, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                if (mContext != null) {
                    ((MainActivity) mContext).startRouteTask(mLastFeatureSelected);
                }
            }
        });
        alertDialog.setNegativeButton(R.string.action_OK, null); // this just dismisses the dialog
        AlertDialog alertDialogInstance = alertDialog.create();
        alertDialogInstance.show();
    }

    private void showErrorAlert(String title, String message) {
        if (mContext != null) {
            ((MainActivity) mContext).showErrorAlert(title, message);
        }
    }
}