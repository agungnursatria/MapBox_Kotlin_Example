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
import com.mapbox.mapboxsdk.annotations.IconFactory
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import com.mapbox.mapboxsdk.annotations.MarkerViewOptions




class MainActivity : AppCompatActivity(), LocationEngineListener, PermissionsListener {

    lateinit var locationPlugin: LocationLayerPlugin
    lateinit var locationEngine: LocationEngine
    lateinit var permissionsManager: PermissionsManager
    lateinit var mapboxMap: MapboxMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Mapbox.getInstance(this, getString(R.string.key_map))
        mapView.apply {
            onCreate(savedInstanceState)
            getMapAsync(OnMapReadyCallback {
                mapboxMap = it
                mapboxMap.addOnMapClickListener {
                    onMapClick(it)
                }
//                initMarker()
                enableLocationPlugin()
            })
        }
    }

    fun onMapClick( latLng: LatLng ){
        var str = String.format("User clicked at: %s", latLng.toString())
        Toast.makeText(this@MainActivity, str, Toast.LENGTH_LONG).show();
    }

    fun initMarker() {

        mapboxMap?.addMarker(MarkerOptions()
                .position(LatLng(-6.8901086, 107.6172076))
                .setTitle("Suitmedia Mobile")
                .snippet("Jalan Sekeloa No. 2, Dipatiukur, Lebakgede, Coblong, Kota Bandung, Jawa Barat 40132"))

        mapboxMap?.addMarker(MarkerOptions()
                .position(LatLng(-6.9740021, 107.6303814))
                .setTitle("Universitas Telkom")
                .snippet("Jl. Telekomunikasi No. 01, Terusan Buah Batu, Sukapura, Dayeuhkolot, Sukapura, Dayeuhkolot, Bandung, Jawa Barat 40257"))
    }

    fun enableLocationPlugin() {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            // Create a location engine instance
            initializeLocationEngine()
            locationPlugin = LocationLayerPlugin(mapView, mapboxMap, locationEngine)
            locationPlugin.setLocationLayerEnabled(true)
            locationPlugin.setCameraMode(CameraMode.TRACKING)
            lifecycle.addObserver(locationPlugin)
        } else {
            permissionsManager = PermissionsManager(this)
            permissionsManager.requestLocationPermissions(this)
        }
    }

    @SuppressLint("MissingPermission")
    fun initializeLocationEngine() {
        locationEngine = LocationEngineProvider(this).obtainBestLocationEngineAvailable()
        locationEngine.setPriority(LocationEnginePriority.HIGH_ACCURACY)
        locationEngine.addLocationEngineListener(this)
        locationEngine.activate()
        val lastLocation = locationEngine.getLastLocation()
        if (lastLocation != null) {
            setCameraPosition(lastLocation!!)
        } else {
            locationEngine.addLocationEngineListener(this)
        }
    }

    private fun setCameraPosition(location: Location) {
        mapboxMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(location.getLatitude(), location.getLongitude()), 17.0))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onLocationChanged(location: Location?) {
        if (location != null) {
            setCameraPosition(location);
            locationEngine.removeLocationEngineListener(this);
        }
    }

    @SuppressLint("MissingPermission")
    override fun onConnected() {
        locationEngine.requestLocationUpdates();
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
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

}
