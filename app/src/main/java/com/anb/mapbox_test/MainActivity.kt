package com.anb.mapbox_test

// classes to calculate a route

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineListener
import com.mapbox.android.core.location.LocationEnginePriority
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.geocoding.v5.GeocodingCriteria
import com.mapbox.api.geocoding.v5.MapboxGeocoding
import com.mapbox.api.geocoding.v5.models.GeocodingResponse
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode
import com.mapbox.mapboxsdk.plugins.places.autocomplete.PlaceAutocomplete
import com.mapbox.mapboxsdk.plugins.places.autocomplete.model.PlaceOptions
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity(), LocationEngineListener, PermissionsListener, View.OnClickListener {

    private val TAG = "BUGG"
    private val REQUEST_CODE_AUTOCOMPLETE = 1
    private var transport = "driving-traffic"
    private lateinit var menuView: Menu

    private lateinit var permissionsManager: PermissionsManager
    private lateinit var mapboxMap: MapboxMap
    private var locationPlugin: LocationLayerPlugin? = null
    private var locationEngine: LocationEngine? = null
    private var originLocation: Location? = null
    private var destinationMarker: Marker? = null
    private var originCoord: LatLng? = null

    // variables for calculating and drawing a route
    private var originPosition: Point? = null
    private var destinationPosition: Point? = null
    private var navigationMapRoute: NavigationMapRoute? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setTitle("")
        Mapbox.getInstance(this, getString(R.string.key_map))
        mapView.apply {
            onCreate(savedInstanceState)
            getMapAsync({
                mapboxMap = it
                enableLocationPlugin()

                originCoord = LatLng(originLocation?.latitude!!, originLocation?.longitude!!)
                mapboxMap.addOnMapClickListener {
                    reverseGeocodingMarker(it)
                }

                btnFindPlace.setOnClickListener(this@MainActivity)
                btnStartNavigation.setOnClickListener(this@MainActivity)
            })
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnFindPlace -> {
                val intent = PlaceAutocomplete.IntentBuilder()
                        .accessToken(Mapbox.getAccessToken())
                        .placeOptions(
                                PlaceOptions.builder()
                                        .backgroundColor(Color.parseColor("#EEEEEE"))
                                        .limit(10)
                                        .hint("Places of Interest")
                                        .geocodingTypes(GeocodingCriteria.TYPE_POI)
                                        .build(PlaceOptions.MODE_CARDS))
                        .build(this@MainActivity)
                startActivityForResult(intent, REQUEST_CODE_AUTOCOMPLETE)
            }
            R.id.btnStartNavigation -> {
                val option = NavigationLauncherOptions.builder()
                        .origin(originPosition)
                        .destination(destinationPosition)
                        .directionsProfile("driving-traffic")
                        .enableOffRouteDetection(true)
                        .build()

                NavigationLauncher.startNavigation(this@MainActivity, option)
            }
        }

    }

    private fun reverseGeocodingMarker(latLng: LatLng) {

        val point: Point = Point.fromLngLat(latLng.longitude, latLng.latitude)
        MapboxGeocoding.builder()
                .accessToken(Mapbox.getAccessToken())
                .mode("mapbox.places")
                .geocodingTypes(GeocodingCriteria.TYPE_POI)
                .query(point)
                .build()
                .enqueueCall(object : Callback<GeocodingResponse> {
                    override fun onResponse(call: Call<GeocodingResponse>, response: Response<GeocodingResponse>) {
                        // Log.d(TAG, call.request().url().uri().toString())
                        // Log.d(TAG, response.body()?.toJson())
                        val features = response.body()?.features()!!
                        val selectedPlace = if (features.isNotEmpty()) features.get(0) else null

                        val markerOption = MarkerOptions()
                                .position(latLng)

                        if (selectedPlace != null) {
                            // Log.d(TAG, "NOT NULL")
                            markerOption.title(selectedPlace.placeName())
                                    .snippet(selectedPlace.address())
                        }

                        initMarker(markerOption)
                    }

                    override fun onFailure(call: Call<GeocodingResponse>, t: Throwable) {
                        Log.e(TAG, "Error: " + t.message)
                        // Log.d(TAG, call.request().url().uri().toString())
                    }
                })

    }

    private fun getRoute(origin: Point, destination: Point) {
        // Profile:
        //     driving-traffic
        //          for automotive routing. This profile factors in current and historic traffic conditions to avoid slowdowns. Traffic is available in these supported geographies.
        //
        //     driving
        //          for automotive routing. This profile shows the fastest routes by preferring high-speed roads, like highways.
        //
        //     walking
        //          for pedestrian and hiking routing. This profile shows the shortest path by using sidewalks and trails.
        //
        //     cycling
        //          for bicycle routing. This profile shows routes that are short and safer for cyclists by avoiding highways and preferring streets with bike lanes.
        //

        NavigationRoute.builder()
                .accessToken(Mapbox.getAccessToken())
                .origin(origin)
                .destination(destination)
                .profile(transport)
                .build()
                .getRoute(object : Callback<DirectionsResponse> {
                    override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                        // Log.d(TAG, call.request().url().uri().toString())
                        if (response.body() == null) {
                            Log.e(TAG, "No routes found, make sure you set the right user and access token.")
                            return
                        } else if (response.body()!!.routes().isEmpty()) {
                            Log.e(TAG, "No routes found")
                            Toast.makeText(this@MainActivity, "No routes found", Toast.LENGTH_SHORT).show()
                            return
                        }

                        val currentRoute = response.body()!!.routes()[0]

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

    private fun initMarker(markerOptions: MarkerOptions) {
        if (destinationMarker != null)
            mapboxMap.removeMarker(destinationMarker!!)
        destinationMarker = mapboxMap.addMarker(markerOptions)

        destinationPosition = Point.fromLngLat(markerOptions.position.longitude, markerOptions.position.latitude)
        originPosition = Point.fromLngLat(originCoord!!.longitude, originCoord!!.latitude)
        getRoute(originPosition!!, destinationPosition!!)

        if (!btnStartNavigation.isEnabled)
            btnStartNavigation.isEnabled = true
    }

    private fun enableLocationPlugin() {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            // Create a location engine instance
            initializeLocationEngine()

            locationPlugin = LocationLayerPlugin(mapView, mapboxMap, locationEngine)
            locationPlugin!!.renderMode = RenderMode.COMPASS
        } else {
            permissionsManager = PermissionsManager(this)
            permissionsManager.requestLocationPermissions(this)
        }
    }

    @SuppressLint("MissingPermission")
    private fun initializeLocationEngine() {
        locationEngine = LocationEngineProvider(this).obtainBestLocationEngineAvailable()
        locationEngine!!.priority = LocationEnginePriority.HIGH_ACCURACY
        locationEngine!!.activate()

        val lastLocation = locationEngine!!.lastLocation
        if (lastLocation != null) {
            originLocation = lastLocation
            setCameraPosition(lastLocation)
        } else {
            locationEngine!!.addLocationEngineListener(this)
        }
    }

    private fun setCameraPosition(location: Location) {
        mapboxMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                17.0
        ))
    }

    private fun moveCamera(coordinate: LatLng) {
        val newCameraPosition = CameraPosition.Builder()
                .target(coordinate)
                .zoom(14.0)
                .build()
        mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(newCameraPosition), 4000)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onLocationChanged(location: Location?) {
        if (location != null) {
            originLocation = location
            setCameraPosition(location)
            locationEngine?.removeLocationEngineListener(this)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onConnected() {
        locationEngine?.requestLocationUpdates()
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            enableLocationPlugin()
        } else {
            Toast.makeText(this, "Not Granted", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        locationEngine?.requestLocationUpdates()
        locationPlugin?.onStart()
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
        locationEngine?.removeLocationUpdates()
        locationPlugin?.onStop()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        locationEngine?.deactivate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_transportation, menu!!)
        menuView = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        menuView.findItem(R.id.traffic).setIcon(R.drawable.ic_traffic_black_24dp)
        menuView.findItem(R.id.car).setIcon(R.drawable.ic_directions_car_black_24dp)
        menuView.findItem(R.id.motorcycle).setIcon(R.drawable.ic_motorcycle_black_24dp)
        menuView.findItem(R.id.walking).setIcon(R.drawable.ic_directions_walk_black_24dp)

        when(item!!.itemId){
            R.id.traffic -> {
                transport = "driving-traffic"
                item.setIcon(R.drawable.ic_traffic_white_24dp)
            }

            R.id.car -> {
                transport = "driving"
                item.setIcon(R.drawable.ic_directions_car_white_24dp)
            }

            R.id.motorcycle -> {
                transport = "cycling"
                item.setIcon(R.drawable.ic_motorcycle_white_24dp)
            }

            R.id.walking -> {
                transport = "walking"
                item.setIcon(R.drawable.ic_directions_walk_white_24dp)
            }
        }

        if (originPosition != null && destinationPosition != null)
            getRoute(originPosition!!, destinationPosition!!)

        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CODE_AUTOCOMPLETE) {

            // Retrieve selected location's CarmenFeature
            val selectedCarmenFeature = PlaceAutocomplete.getPlace(data)
            val point = selectedCarmenFeature.center()
            val placeName = selectedCarmenFeature.placeName()
            val address = selectedCarmenFeature.address()

            val coordinate = LatLng(point!!.latitude(), point.longitude())

            val markerOptions = MarkerOptions()
                    .position(coordinate)
                    .title(placeName)
                    .snippet(address)

            initMarker(markerOptions)
            moveCamera(coordinate)
        }
    }

    override fun onBackPressed() {
        if (destinationMarker != null) {
            mapboxMap.removeMarker(destinationMarker!!)
            destinationMarker = null
            if (navigationMapRoute != null) {
                navigationMapRoute!!.removeRoute()
            }
            if (btnStartNavigation.isEnabled)
                btnStartNavigation.isEnabled = false

            Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
        } else {
            super.onBackPressed()
            this.finish()
        }
    }


}
