package com.anb.mapbox_test

import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineListener
import com.mapbox.android.core.location.LocationEnginePriority
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode
import kotlinx.android.synthetic.main.activity_main.*

// classes to calculate a route
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.util.Log;
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode

import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions

class MainActivity : AppCompatActivity(), LocationEngineListener, PermissionsListener {

    lateinit var permissionsManager: PermissionsManager
    lateinit var mapboxMap: MapboxMap
    var locationPlugin: LocationLayerPlugin? = null
    var locationEngine: LocationEngine? = null
    var originLocation: Location? = null
    var destinationMarker: Marker? = null
    var originCoord: LatLng? = null

    // variables for calculating and drawing a route
    var originPosition: Point? = null
    var destinationPosition: Point? = null
    var currentRoute: DirectionsRoute? = null
    var navigationMapRoute: NavigationMapRoute? = null

    val TAG = "DirectionsActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Mapbox.getInstance(this, getString(R.string.key_map))
        mapView.apply {
            onCreate(savedInstanceState)
            getMapAsync(OnMapReadyCallback {
                mapboxMap = it
                enableLocationPlugin()

                originCoord = LatLng(originLocation?.getLatitude()!!, originLocation?.getLongitude()!!)
                mapboxMap.addOnMapClickListener {
                    onMapClick(it)
                }
            })
        }

        btnStartNavigation.setOnClickListener {

            var option = NavigationLauncherOptions.builder()
                    .origin(originPosition)
                    .destination(destinationPosition)
                    .build()

            NavigationLauncher.startNavigation(this@MainActivity, option)
        }
    }

    private fun getRoute(origin: Point, destination: Point) {
        NavigationRoute.builder()
                .accessToken(Mapbox.getAccessToken())
                .origin(origin)
                .destination(destination)
                .build()
                .getRoute(object : Callback<DirectionsResponse> {
                    override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                        // You can get the generic HTTP info about the response
                        if (response.body() == null) {
                            Log.e(TAG, "No routes found, make sure you set the right user and access token.")
                            return
                        } else if (response.body()!!.routes().size < 1) {
                            Log.e(TAG, "No routes found")
                            return
                        }

                        // Mencari jarak terbaik dengan metode KNN, diambil [0] sebagai terbaik
                        currentRoute = response.body()!!.routes()[0]

                        // Draw the route on the map
                        if (navigationMapRoute != null) {
                            navigationMapRoute!!.removeRoute()
                        } else {
                            navigationMapRoute = NavigationMapRoute(null, mapView, mapboxMap, R.style.NavigationMapRoute)
                        }
                        navigationMapRoute!!.addRoute(currentRoute)
                    }

                    override fun onFailure(call: Call<DirectionsResponse>, throwable: Throwable) {
                        Log.e(TAG, "Error: " + throwable.message)
                    }
                })
    }


    fun onMapClick(destinationCoord: LatLng) {
        if (destinationMarker != null)
            mapboxMap.removeMarker(destinationMarker!!)

        destinationMarker = mapboxMap.addMarker(MarkerOptions()
                .position(destinationCoord))

        destinationPosition = Point.fromLngLat(destinationCoord?.getLongitude()!!, destinationCoord?.getLatitude()!!)
        originPosition = Point.fromLngLat(originCoord!!.getLongitude(), originCoord!!.getLatitude())
        getRoute(originPosition!!, destinationPosition!!)

        if (!btnStartNavigation.isEnabled)
            btnStartNavigation.isEnabled = true
    }


    fun enableLocationPlugin() {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            // Create a location engine instance
            initializeLocationEngine()

            locationPlugin = LocationLayerPlugin(mapView, mapboxMap, locationEngine);
            locationPlugin!!.setRenderMode(RenderMode.COMPASS);
        } else {
            permissionsManager = PermissionsManager(this)
            permissionsManager.requestLocationPermissions(this)
        }
    }

    @SuppressLint("MissingPermission")
    fun initializeLocationEngine() {
        locationEngine = LocationEngineProvider(this).obtainBestLocationEngineAvailable()
        locationEngine!!.setPriority(LocationEnginePriority.HIGH_ACCURACY)
        locationEngine!!.activate()

        val lastLocation = locationEngine!!.getLastLocation()
        if (lastLocation != null) {
            originLocation = lastLocation
            setCameraPosition(lastLocation!!)
        } else {
            locationEngine!!.addLocationEngineListener(this)
        }
    }

    private fun setCameraPosition(location: Location) {
        mapboxMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(location.getLatitude(), location.getLongitude()), 16.0))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onLocationChanged(location: Location?) {
        if (location != null) {
            originLocation = location;
            setCameraPosition(location);
            locationEngine?.removeLocationEngineListener(this);
        }
    }

    @SuppressLint("MissingPermission")
    override fun onConnected() {
        locationEngine?.requestLocationUpdates();
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            enableLocationPlugin();
        } else {
            Toast.makeText(this, "Not Granted", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    override fun onStart() {
        super.onStart()
        locationEngine?.requestLocationUpdates();
        locationPlugin?.onStart();
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        locationEngine?.removeLocationUpdates();
        locationPlugin?.onStop();
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        locationEngine?.deactivate();
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

}
