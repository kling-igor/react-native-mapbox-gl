package com.mapbox.reactnativemapboxgl;

import android.content.Context;
import android.graphics.PointF;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.JSApplicationIllegalArgumentException;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.common.ReactConstants;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.MapboxMapOptions;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.UiSettings;

import java.util.Map;
import java.util.logging.Handler;

import javax.annotation.Nullable;

public class ReactNativeMapboxGLView extends RelativeLayout implements
        OnMapReadyCallback, LifecycleEventListener,
        MapboxMap.OnMapClickListener, MapboxMap.OnMapLongClickListener,
        MapboxMap.OnMyBearingTrackingModeChangeListener, MapboxMap.OnMyLocationTrackingModeChangeListener,
        MapboxMap.OnMyLocationChangeListener,
        MapView.OnMapChangedListener
{

    private MapboxMap _map = null;
    private MapView _mapView = null;
    private ReactNativeMapboxGLManager _manager;
    private boolean _paused = false;

    private CameraPosition.Builder _initialCamera = new CameraPosition.Builder();
    private MapboxMapOptions _mapOptions;
    private int _locationTrackingMode;
    private int _bearingTrackingMode;
    private boolean _trackingModeUpdateScheduled = false;
    private boolean _showsUserLocation;
    private boolean _zoomEnabled = true;
    private boolean _scrollEnabled = true;
    private boolean _rotateEnabled = true;
    private boolean _enableOnRegionWillChange = false;
    private boolean _enableOnRegionDidChange = false;
    private int _paddingTop, _paddingRight, _paddingBottom, _paddingLeft;

    private android.os.Handler _trackingModeHandler;

    @UiThread
    public ReactNativeMapboxGLView(Context context, ReactNativeMapboxGLManager manager) {
        super(context);
        _trackingModeHandler = new android.os.Handler();
        _manager = manager;
        _mapOptions = MapboxMapOptions.createFromAttributes(context, null);
        _mapOptions.zoomGesturesEnabled(true);
        _mapOptions.rotateGesturesEnabled(true);
        _mapOptions.scrollGesturesEnabled(true);
        _mapOptions.tiltGesturesEnabled(true);
    }

    // Lifecycle methods

    public void onAfterUpdateTransaction() {
        if (_mapView != null) { return; }
        setupMapView();
        _paused = false;
        _mapView.onResume();
        _manager.getContext().addLifecycleEventListener(this);
    }

    public void onDrop() {
        if (_mapView == null) { return; }
        _manager.getContext().removeLifecycleEventListener(this);
        if (!_paused) {
            _paused = true;
            _mapView.onPause();
        }
        destroyMapView();
        _mapView = null;
    }

    @Override
    public void onHostResume() {
        _paused = false;
        _mapView.onResume();
    }

    @Override
    public void onHostPause() {
        _paused = true;
        _mapView.onPause();
    }

    @Override
    public void onHostDestroy() {
        onDrop();
    }

    // Initialization

    private void setupMapView() {
        _mapOptions.camera(_initialCamera.build());
        _mapView = new MapView(this.getContext(), _mapOptions);
        this.addView(_mapView);
        _mapView.addOnMapChangedListener(this);
        _mapView.onCreate(null);
        _mapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(MapboxMap mapboxMap) {
        if (_mapView == null) { return; }
        _map = mapboxMap;

        // Configure map
        _map.setMyLocationEnabled(_showsUserLocation);
        _map.getTrackingSettings().setMyLocationTrackingMode(_locationTrackingMode);
        _map.getTrackingSettings().setMyBearingTrackingMode(_bearingTrackingMode);
        _map.setPadding(_paddingLeft, _paddingTop, _paddingRight, _paddingBottom);
        UiSettings uiSettings = _map.getUiSettings();
        uiSettings.setZoomGesturesEnabled(_zoomEnabled);
        uiSettings.setScrollGesturesEnabled(_scrollEnabled);
        uiSettings.setRotateGesturesEnabled(_rotateEnabled);

        // If these settings changed between setupMapView() and onMapReady(), coerce them to their right values
        // This doesn't happen in the current implementation of MapView, but let's be future proof
        if (_map.isDebugActive() != _mapOptions.getDebugActive()) {
            _map.setDebugActive(_mapOptions.getDebugActive());
        }
        if (!_map.getStyleUrl().equals(_mapOptions.getStyle())) {
            _map.setStyleUrl(_mapOptions.getStyle());
        }
        if (uiSettings.isLogoEnabled() != _mapOptions.getLogoEnabled()) {
            uiSettings.setLogoEnabled(_mapOptions.getLogoEnabled());
        }
        if (uiSettings.isAttributionEnabled() != _mapOptions.getAttributionEnabled()) {
            uiSettings.setAttributionEnabled(_mapOptions.getAttributionEnabled());
        }
        if (uiSettings.isCompassEnabled() != _mapOptions.getCompassEnabled()) {
            uiSettings.setCompassEnabled(_mapOptions.getCompassEnabled());
        }

        // Attach listeners
        _map.setOnMapClickListener(this);
        _map.setOnMapLongClickListener(this);
        _map.setOnMyLocationTrackingModeChangeListener(this);
        _map.setOnMyBearingTrackingModeChangeListener(this);
        _map.setOnMyLocationChangeListener(this);
    }

    private void destroyMapView() {
        _mapView.removeOnMapChangedListener(this);
        if (_map != null) {
            _map.setOnMapClickListener(null);
            _map.setOnMapLongClickListener(null);
            _map.setOnMyLocationTrackingModeChangeListener(null);
            _map.setOnMyBearingTrackingModeChangeListener(null);
            _map.setOnMyLocationChangeListener(null);
            _map = null;
        }
        _mapView.onDestroy();
    }

    // Props

    public void setInitialZoomLevel(double value) {
        _initialCamera.zoom(value);
    }

    public void setInitialDirection(double value) {
        _initialCamera.bearing(value);
    }

    public void setInitialCenterCoordinate(double lat, double lon) {
        _initialCamera.target(new LatLng(lat, lon));
    }

    public void setEnableOnRegionDidChange(boolean value) {
        _enableOnRegionDidChange = value;
    }

    public void setEnableOnRegionWillChange(boolean value) {
        _enableOnRegionWillChange = value;
    }

    public void setShowsUserLocation(boolean value) {
        if (_showsUserLocation == value) { return; }
        _showsUserLocation = value;
        if (_map != null) { _map.setMyLocationEnabled(value); }
    }

    public void setRotateEnabled(boolean value) {
        if (_rotateEnabled == value) { return; }
        _rotateEnabled = value;
        if (_map != null) {
            _map.getUiSettings().setRotateGesturesEnabled(value);
        }
    }

    public void setScrollEnabled(boolean value) {
        if (_scrollEnabled == value) { return; }
        _scrollEnabled = value;
        if (_map != null) {
            _map.getUiSettings().setScrollGesturesEnabled(value);
        }
    }

    public void setZoomEnabled(boolean value) {
        if (_zoomEnabled == value) { return; }
        _zoomEnabled = value;
        if (_map != null) {
            _map.getUiSettings().setZoomGesturesEnabled(value);
        }
    }

    public void setStyleURL(String styleURL) {
        if (styleURL.equals(_mapOptions.getStyle())) { return; }
        _mapOptions.styleUrl(styleURL);
        if (_map != null) { _map.setStyleUrl(styleURL); }
    }

    public void setDebugActive(boolean value) {
        if (_mapOptions.getDebugActive() == value) { return; }
        _mapOptions.debugActive(value);
        if (_map != null) { _map.setDebugActive(value); };
    }

    public void setLocationTracking(int value) {
        if (_locationTrackingMode == value) { return; }
        _locationTrackingMode = value;
        if (_map != null) { _map.getTrackingSettings().setMyLocationTrackingMode(value); };
    }

    public void setBearingTracking(int value) {
        if (_bearingTrackingMode == value) { return; }
        _bearingTrackingMode = value;
        if (_map != null) { _map.getTrackingSettings().setMyBearingTrackingMode(value); };
    }

    public void setAttributionButtonIsHidden(boolean value) {
        if (_mapOptions.getAttributionEnabled() == !value) { return; }
        _mapOptions.attributionEnabled(!value);
        if (_map != null) {
            _map.getUiSettings().setAttributionEnabled(!value);
        }
    }

    public void setLogoIsHidden(boolean value) {
        if (_mapOptions.getLogoEnabled() == !value) { return; }
        _mapOptions.logoEnabled(!value);
        if (_map != null) {
            _map.getUiSettings().setLogoEnabled(!value);
        }
    }

    public void setCompassIsHidden(boolean value) {
        if (_mapOptions.getCompassEnabled() == !value) { return; }
        _mapOptions.compassEnabled(!value);
        if (_map != null) {
            _map.getUiSettings().setCompassEnabled(!value);
        }
    }

    public void setContentInset(int top, int right, int bottom, int left) {
        if (top == _paddingTop &&
            bottom == _paddingBottom &&
            left == _paddingLeft &&
            right == _paddingRight) { return; }
        _paddingTop = top;
        _paddingRight = right;
        _paddingBottom = bottom;
        _paddingLeft = left;
        if (_map != null) { _map.setPadding(left, top, right, bottom); }
    }

    // Events

    void emitEvent(String name, @Nullable WritableMap event) {
        if (event == null) {
            event = Arguments.createMap();
        }
        ((ReactContext)getContext())
                .getJSModule(RCTEventEmitter.class)
                .receiveEvent(getId(), name, event);
    }

    @Override
    public void onMapClick(LatLng point) {
        PointF screenCoords = _map.getProjection().toScreenLocation(point);

        WritableMap event = Arguments.createMap();
        WritableMap src = Arguments.createMap();
        src.putDouble("latitude", point.getLatitude());
        src.putDouble("longitude", point.getLongitude());
        src.putDouble("screenCoordX", screenCoords.x);
        src.putDouble("screenCoordY", screenCoords.y);
        event.putMap("src", src);

        emitEvent("onTap", event);
    }

    @Override
    public void onMapLongClick(@NonNull LatLng point) {
        PointF screenCoords = _map.getProjection().toScreenLocation(point);

        WritableMap event = Arguments.createMap();
        WritableMap src = Arguments.createMap();
        src.putDouble("latitude", point.getLatitude());
        src.putDouble("longitude", point.getLongitude());
        src.putDouble("screenCoordX", screenCoords.x);
        src.putDouble("screenCoordY", screenCoords.y);
        event.putMap("src", src);

        emitEvent("onLongPress", event);
    }

    @Override
    public void onMyLocationChange(@Nullable Location location) {
        WritableMap event = Arguments.createMap();
        WritableMap src = Arguments.createMap();

        if (location == null) {
            src.putString("message", "Could not get user location");
            event.putMap("src", src);
            emitEvent("onLocateUserFailed", event);
            return;
        }

        src.putDouble("latitude", location.getLatitude());
        src.putDouble("longitude", location.getLongitude());
        src.putDouble("magneticHeading", location.getBearing());
        src.putDouble("trueHeading", location.getBearing());
        src.putDouble("headingAccuracy", 10.0);
        src.putBoolean("isUpdating", true);
        event.putMap("src", src);
        emitEvent("onUpdateUserLocation", event);
    }

    class TrackingModeChangeRunnable implements Runnable {
        ReactNativeMapboxGLView target;
        TrackingModeChangeRunnable(ReactNativeMapboxGLView target) {
            this.target = target;
        }
        @Override
        public void run() {
            target.onTrackingModeChange();
        }
    }

    public void onTrackingModeChange() {
        if (!_trackingModeUpdateScheduled) { return; }
        _trackingModeUpdateScheduled = false;

        for (int mode = 0; mode < ReactNativeMapboxGLModule.locationTrackingModes.length; mode++) {
            if (_locationTrackingMode == ReactNativeMapboxGLModule.locationTrackingModes[mode] &&
                _bearingTrackingMode == ReactNativeMapboxGLModule.bearingTrackingModes[mode]) {
                WritableMap event = Arguments.createMap();
                event.putInt("src", mode);
                emitEvent("onChangeUserTrackingMode", event);
                break;
            }
        }
    }

    @Override
    @UiThread
    public void onMyBearingTrackingModeChange(int myBearingTrackingMode) {
        if (_bearingTrackingMode == myBearingTrackingMode) { return; }
        _bearingTrackingMode = myBearingTrackingMode;
        _trackingModeUpdateScheduled = true;
        _trackingModeHandler.post(new TrackingModeChangeRunnable(this));

    }

    @Override
    @UiThread
    public void onMyLocationTrackingModeChange(int myLocationTrackingMode) {
        if (_locationTrackingMode == myLocationTrackingMode) { return; }
        _locationTrackingMode = myLocationTrackingMode;
        _trackingModeUpdateScheduled = true;
        _trackingModeHandler.post(new TrackingModeChangeRunnable(this));
    }

    WritableMap serializeCurrentRegion() {
        CameraPosition camera = _map == null
                ? _initialCamera.build()
                : _map.getCameraPosition();

        WritableMap event = Arguments.createMap();
        WritableMap src = Arguments.createMap();
        src.putDouble("longitude", camera.target.getLongitude());
        src.putDouble("latitude", camera.target.getLatitude());
        src.putDouble("zoomLevel", camera.zoom);
        src.putDouble("direction", camera.bearing);
        src.putDouble("pitch", camera.tilt);
        event.putMap("src", src);
        return event;
    }

    @Override
    public void onMapChanged(int change) {
        switch (change) {
            case MapView.REGION_WILL_CHANGE:
            case MapView.REGION_WILL_CHANGE_ANIMATED:
                if (_enableOnRegionWillChange) {
                    emitEvent("onRegionWillChange", serializeCurrentRegion()); // TODO: These should be throttled
                }
                break;
            case MapView.REGION_DID_CHANGE:
            case MapView.REGION_DID_CHANGE_ANIMATED:
                if (_enableOnRegionDidChange) {
                    emitEvent("onRegionDidChange", serializeCurrentRegion());
                }
                break;
            case MapView.WILL_START_LOADING_MAP:
                emitEvent("onStartLoadingMap", null);
                break;
            case MapView.DID_FINISH_LOADING_MAP:
                emitEvent("onFinishLoadingMap", null);
                break;
        }
    }

    // Getters

    public CameraPosition getCameraPosition() {
        if (_map == null) { return _initialCamera.build(); }
        return _map.getCameraPosition();
    }

    public LatLngBounds getBounds() {
        if (_map == null) { return new LatLngBounds.Builder().build(); };
        return _map.getProjection().getVisibleRegion().latLngBounds;
    }

    // Camera setters

    public void setCameraPosition(CameraPosition position, int duration, @Nullable Runnable callback) {
        if (_map == null) {
            _initialCamera = new CameraPosition.Builder(position);
            if (callback != null) { callback.run(); }
            return;
        }

        CameraUpdate update = CameraUpdateFactory.newCameraPosition(position);
        setCameraUpdate(update, duration, callback);
    }

    public void setCameraUpdate(CameraUpdate update, int duration, @Nullable Runnable callback) {
        if (_map == null) {
            return;
        }

        if (duration == 0) {
            _map.moveCamera(update);
            if (callback != null) { callback.run(); }
        } else {
            // Ugh... Java callbacks suck
            class CameraCallback implements MapboxMap.CancelableCallback {
                Runnable callback;
                CameraCallback(Runnable callback) {
                    this.callback = callback;
                }
                @Override
                public void onCancel() {
                    if (callback != null) { callback.run(); }
                }

                @Override
                public void onFinish() {
                    if (callback != null) { callback.run(); }
                }
            }

            _map.animateCamera(update, duration, new CameraCallback(callback));
        }
    }
}
