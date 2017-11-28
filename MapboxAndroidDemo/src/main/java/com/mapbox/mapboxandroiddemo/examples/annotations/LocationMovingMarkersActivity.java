package com.mapbox.mapboxandroiddemo.examples.annotations;

import android.graphics.PointF;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.mapbox.mapboxandroiddemo.R;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerMode;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.services.android.location.LostLocationEngine;
import com.mapbox.services.android.telemetry.location.LocationEngine;
import com.mapbox.services.android.telemetry.location.LocationEngineListener;
import com.mapbox.services.android.telemetry.location.LocationEnginePriority;
import com.mapbox.services.android.telemetry.permissions.PermissionsListener;
import com.mapbox.services.android.telemetry.permissions.PermissionsManager;
import com.mapbox.services.api.ServicesException;
import com.mapbox.services.api.geocoding.v5.GeocodingCriteria;
import com.mapbox.services.api.geocoding.v5.MapboxGeocoding;
import com.mapbox.services.api.geocoding.v5.models.CarmenFeature;
import com.mapbox.services.api.geocoding.v5.models.GeocodingResponse;
import com.mapbox.services.commons.models.Position;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

public class LocationMovingMarkersActivity extends AppCompatActivity implements OnMapReadyCallback,
  LocationEngineListener, PermissionsListener, MapboxMap.OnCameraMoveCanceledListener {

  private PermissionsManager permissionsManager;
  private LocationLayerPlugin locationPlugin;
  private LocationEngine locationEngine;
  private MapboxMap mapboxMap;
  private MapView mapView;
  private ImageView hoveringMarker;

  private EditText originEditText;
  private Marker droppedMarker;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Mapbox access token is configured here. This needs to be called either in your application
    // object or in the same activity which contains the mapview.
    Mapbox.getInstance(this, getString(R.string.access_token));

    // This contains the MapView in XML and needs to be called after the access token is configured.
    setContentView(R.layout.activity_location_with_moving_marker);

    originEditText = findViewById(R.id.origin_edittext);

    mapView = findViewById(R.id.mapView);
    mapView.onCreate(savedInstanceState);
    mapView.getMapAsync(this);

    // When user is still picking a location, we hover a marker above the mapboxMap in the center.
    // This is done by using an image view with the default marker found in the SDK. You can
    // swap out for your own marker image, just make sure it matches up with the dropped marker.
    hoveringMarker = new ImageView(this);
    hoveringMarker.setImageResource(R.drawable.red_marker);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT,
      ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
    hoveringMarker.setLayoutParams(params);
    mapView.addView(hoveringMarker);
  }

  @Override
  public void onMapReady(MapboxMap mapboxMap) {
    LocationMovingMarkersActivity.this.mapboxMap = mapboxMap;
    enableLocationPlugin();
    addMarkers();
  }

  @Override
  public void onCameraMoveCanceled() {
    Timber.d("onCameraMoveCanceled");
    if (mapboxMap != null) {
      if (droppedMarker == null) {
        // We first find where the hovering marker position is relative to the mapboxMap.
        // Then we set the visibility to gone.
        float coordinateX = hoveringMarker.getLeft() + (hoveringMarker.getWidth() / 2);
        float coordinateY = hoveringMarker.getBottom();
        float[] coords = new float[] {coordinateX, coordinateY};
        final LatLng latLng = mapboxMap.getProjection().fromScreenLocation(new PointF(coords[0], coords[1]));
        hoveringMarker.setVisibility(View.GONE);

        // Create the marker icon the dropped marker will be using.
        Icon icon = IconFactory.getInstance(this).fromResource(R.drawable.red_marker);

        // Placing the marker on the mapboxMap as soon as possible causes the illusion
        // that the hovering marker and dropped marker are the same.
        droppedMarker = mapboxMap.addMarker(new MarkerOptions().position(latLng).icon(icon));

        // Finally we get the geocoding information
        reverseGeocode(latLng);
      } else {
        // When the marker is dropped, the user has clicked the button to cancel.
        // Therefore, we pick the marker back up.
        mapboxMap.removeMarker(droppedMarker);

        // Lastly, set the hovering marker back to visible.
        hoveringMarker.setVisibility(View.VISIBLE);
        droppedMarker = null;
      }
    }
  }

  private void addMarkers() {

  }

  private void reverseGeocode(final LatLng point) {
    // This method is used to reverse geocode where the user has dropped the marker.
    try {
      MapboxGeocoding client = new MapboxGeocoding.Builder()
        .setAccessToken(getString(R.string.access_token))
        .setCoordinates(Position.fromCoordinates(point.getLongitude(), point.getLatitude()))
        .setGeocodingType(GeocodingCriteria.TYPE_ADDRESS)
        .build();

      client.enqueueCall(new Callback<GeocodingResponse>() {
        @Override
        public void onResponse(Call<GeocodingResponse> call, Response<GeocodingResponse> response) {

          List<CarmenFeature> results = response.body().getFeatures();
          if (results.size() > 0) {
            CarmenFeature feature = results.get(0);
            // If the geocoder returns a result, we take the first in the list and update
            // the dropped marker snippet with the information. Lastly we open the info
            // window.
            if (feature != null) {
              Timber.d(feature.getAddress());
              Timber.d(feature.getLanguage());
              Timber.d(feature.getMatchingPlaceName());
              Timber.d(feature.getMatchingText());
              Timber.d(feature.getText());
              Timber.d(feature.getPlaceType()[0]);
              originEditText.setText(feature.getAddress());
            }
          }
        }

        @Override
        public void onFailure(Call<GeocodingResponse> call, Throwable throwable) {
          Timber.d("Geocoding Failure: " + throwable.getMessage());
        }
      });
    } catch (ServicesException servicesException) {
      Timber.d("Error geocoding: " + servicesException.toString());
      servicesException.printStackTrace();
    }
  }


  @SuppressWarnings( {"MissingPermission"})
  private void enableLocationPlugin() {
    // Check if permissions are enabled and if not request
    if (PermissionsManager.areLocationPermissionsGranted(this)) {
      // Create an instance of LOST location engine
      initializeLocationEngine();

      locationPlugin = new LocationLayerPlugin(mapView, mapboxMap, locationEngine);
      locationPlugin.setLocationLayerEnabled(LocationLayerMode.TRACKING);
    } else {
      permissionsManager = new PermissionsManager(this);
      permissionsManager.requestLocationPermissions(this);
    }
  }

  @SuppressWarnings( {"MissingPermission"})
  private void initializeLocationEngine() {
    locationEngine = new LostLocationEngine(LocationMovingMarkersActivity.this);
    locationEngine.setPriority(LocationEnginePriority.HIGH_ACCURACY);
    locationEngine.activate();

    Location lastLocation = locationEngine.getLastLocation();
    if (lastLocation != null) {
      setCameraPosition(lastLocation);
    } else {
      locationEngine.addLocationEngineListener(this);
    }
  }

  private void setCameraPosition(Location location) {
    mapboxMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
      new LatLng(location.getLatitude(), location.getLongitude()), 16));
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  @Override
  public void onExplanationNeeded(List<String> permissionsToExplain) {

  }

  @Override
  public void onPermissionResult(boolean granted) {

    if (granted) {
      enableLocationPlugin();
    } else {
      Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show();
      finish();
    }
  }

  @Override
  @SuppressWarnings( {"MissingPermission"})
  public void onConnected() {
    locationEngine.requestLocationUpdates();
  }

  @Override
  public void onLocationChanged(Location location) {
    if (location != null) {
      setCameraPosition(location);
      locationEngine.removeLocationEngineListener(this);
    }
  }

  @Override
  @SuppressWarnings( {"MissingPermission"})
  protected void onStart() {
    super.onStart();
    if (locationPlugin != null) {
      locationPlugin.onStart();
    }
    mapView.onStart();
  }

  @Override
  protected void onResume() {
    super.onResume();
    mapView.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    mapView.onPause();
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (locationEngine != null) {
      locationEngine.removeLocationUpdates();
    }
    if (locationPlugin != null) {
      locationPlugin.onStop();
    }
    mapView.onStop();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    mapView.onSaveInstanceState(outState);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    mapView.onDestroy();
    if (locationEngine != null) {
      locationEngine.deactivate();
    }
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    mapView.onLowMemory();
  }
}
