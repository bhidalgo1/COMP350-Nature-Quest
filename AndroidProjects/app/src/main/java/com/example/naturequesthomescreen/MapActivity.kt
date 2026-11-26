package com.example.naturequesthomescreen

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.natureobservation.feature.posts.ObservationRepository
import com.example.naturequesthomescreen.ui.theme.appearancePreferences
import com.example.naturequesthomescreen.ui.theme.savedDarkMode
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.TileOverlay
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.maps.android.heatmaps.Gradient
import com.google.maps.android.heatmaps.HeatmapTileProvider
import java.util.Locale

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_PICK_LOCATION = "pick_location"
        const val EXTRA_LATITUDE = "observation_latitude"
        const val EXTRA_LONGITUDE = "observation_longitude"

        fun putLocation(intent: Intent, point: LatLng): Intent =
            intent.putExtra(EXTRA_LATITUDE, point.latitude)
                .putExtra(EXTRA_LONGITUDE, point.longitude)

        fun readLocation(intent: Intent?): LatLng? {
            if (intent == null || !intent.hasExtra(EXTRA_LATITUDE) || !intent.hasExtra(EXTRA_LONGITUDE)) {
                return null
            }
            return LatLng(
                intent.getDoubleExtra(EXTRA_LATITUDE, 0.0),
                intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
            )
        }
    }

    private lateinit var googleMap: GoogleMap
    private lateinit var placeTitle: TextView
    private lateinit var placeDetail: TextView

    private var pickingLocation = false
    private var selectedLocation: LatLng? = null
    private var selectionMarker: Marker? = null
    private var initialTargetLocation: LatLng? = null

    private val userMarkers = mutableListOf<Marker>()
    private val repository by lazy { ObservationRepository(this) }

    private var isHeatmapEnabled = false
    private var heatmapOverlay: TileOverlay? = null

    // Approximate center of CSU Channel Islands.
    private val campus = LatLng(34.1621, -119.0436)

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            enableMyLocation()
        }
    }

    private fun applyAppearanceMode() {
        val mode = when (appearancePreferences(this).savedDarkMode()) {
            true -> AppCompatDelegate.MODE_NIGHT_YES
            false -> AppCompatDelegate.MODE_NIGHT_NO
            null -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (delegate.localNightMode != mode) delegate.localNightMode = mode
    }

    private fun isMapDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppearanceMode()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isMapDark()
            isAppearanceLightNavigationBars = !isMapDark()
        }

        pickingLocation = intent.getBooleanExtra(EXTRA_PICK_LOCATION, false)
        initialTargetLocation = readLocation(intent)

        placeTitle = findViewById(R.id.placeTitle)
        placeDetail = findViewById(R.id.placeDetail)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment

        mapFragment.getMapAsync(this)
        showCampus()

        val addPinBtn = findViewById<Button>(R.id.addPinButton)
        addPinBtn.text = if (pickingLocation) "Use this location" else "Post Here with Pin"

        addPinBtn.setOnClickListener {
            if (!::googleMap.isInitialized) return@setOnClickListener

            val point = selectedLocation ?: googleMap.cameraPosition.target

            if (pickingLocation) {
                setResult(RESULT_OK, putLocation(Intent(), point))
                finish()
            } else {
                val postIntent = Intent(this@MapActivity, PhotoUploadActivity::class.java)
                startActivity(putLocation(postIntent, point))
            }
        }

        findViewById<Button>(R.id.resetButton).setOnClickListener {
            if (::googleMap.isInitialized) {
                selectionMarker?.remove()
                selectionMarker = null
                selectedLocation = null
                val target = initialTargetLocation ?: campus
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(target, 17f)
                )

                if (initialTargetLocation == null) {
                    showCampus()
                }
            }
        }

        findViewById<Button>(R.id.heatmapToggleButton)?.setOnClickListener {
            toggleHeatmap()
        }

        findViewById<View>(R.id.backToHomeButton)?.setOnClickListener {
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        applyAppearanceMode()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        if (isMapDark()) {
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark))
        }

        enableMyLocation()

        val startLocation = initialTargetLocation ?: campus
        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(startLocation, 17f)
        )

        val campusMarker = googleMap.addMarker(
            MarkerOptions()
                .position(campus)
                .title("CSU Channel Islands")
                .snippet("CSU Channel Islands Campus")
        )

        googleMap.setOnMarkerClickListener { marker ->
            marker.showInfoWindow()
            selectedLocation = marker.position

            if (marker == campusMarker) {
                showCampus()
            } else {
                showPin(marker)
            }

            true
        }

        googleMap.setOnMapClickListener { point ->
            selectedLocation = point
            selectionMarker?.remove()
            selectionMarker = googleMap.addMarker(
                MarkerOptions()
                    .position(point)
                    .title("Selected Pin Location")
                    .snippet(
                        String.format(
                            Locale.US,
                            "%.5f, %.5f",
                            point.latitude,
                            point.longitude
                        )
                    )
            )
            selectionMarker?.showInfoWindow()
            showSelectedLocation(point)
        }

        googleMap.setOnMapLongClickListener { point ->
            selectedLocation = point
            selectionMarker?.remove()
            selectionMarker = googleMap.addMarker(
                MarkerOptions()
                    .position(point)
                    .title("Selected Pin Location")
                    .snippet(
                        String.format(
                            Locale.US,
                            "%.5f, %.5f",
                            point.latitude,
                            point.longitude
                        )
                    )
            )
            selectionMarker?.showInfoWindow()
            showSelectedLocation(point)
        }

        lifecycleScope.launch {
            try {
                repository.ensureSignedIn()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                Toast.makeText(this@MapActivity, "Could not load map posts.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            feedRegistration = repository.observeFeed(
                onUpdate = { posts ->
                    val targetId = intent.getStringExtra("pin_id")
                    val visibleIds = posts.mapTo(mutableSetOf()) { it.id }
                    val removed = userMarkers.filter { (it.tag as? String) !in visibleIds }
                    if (removed.isNotEmpty()) showCampus()
                    removed.forEach { it.remove() }
                    userMarkers.removeAll(removed.toSet())
                    posts.forEach { post ->
                        val lat = post.latitude
                        val lng = post.longitude
                        if (lat != null && lng != null) {
                            val pos = LatLng(lat, lng)
                            val existingMarker = userMarkers.find {
                                it.tag == post.id
                            }
                            if (existingMarker != null) {
                                existingMarker.title = post.title
                                if (post.description.isNotBlank()) {
                                    existingMarker.snippet = post.description
                                }
                                if (targetId == post.id || (initialTargetLocation != null &&
                                            initialTargetLocation!!.latitude == lat &&
                                            initialTargetLocation!!.longitude == lng)) {
                                    existingMarker.showInfoWindow()
                                    showPin(existingMarker)
                                }
                            } else {
                                val marker = googleMap.addMarker(
                                    MarkerOptions()
                                        .position(pos)
                                        .title(post.title)
                                        .snippet(post.description.ifBlank {
                                            String.format(Locale.US, "%.5f, %.5f", lat, lng)
                                        })
                                        .draggable(true)
                                )
                                if (marker != null) {
                                    marker.tag = post.id
                                    userMarkers.add(marker)
                                    if (targetId == post.id || (initialTargetLocation != null &&
                                                initialTargetLocation!!.latitude == lat &&
                                                initialTargetLocation!!.longitude == lng)) {
                                        marker.showInfoWindow()
                                        showPin(marker)
                                    }
                                }
                            }
                        }
                    }
                    if (isHeatmapEnabled) {
                        refreshHeatmap()
                    }
                },
                onError = {}
            )
        }
    }

    private var feedRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onDestroy() {
        feedRegistration?.remove()
        super.onDestroy()
    }

    private fun enableMyLocation() {
        if (!::googleMap.isInitialized) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                googleMap.isMyLocationEnabled = true
                googleMap.uiSettings.isMyLocationButtonEnabled = true
            } catch (_: SecurityException) {
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun showSelectedLocation(point: LatLng) {
        findViewById<LinearLayout>(R.id.detailsContainer)?.visibility = View.VISIBLE
        placeTitle.text = "Selected Pin Location"
        placeDetail.text = String.format(
            Locale.US,
            "Latitude %.5f · Longitude %.5f\n(Tap '%s' to create observation post)",
            point.latitude,
            point.longitude,
            if (pickingLocation) "Use this location" else "Post Here with Pin"
        )
    }

    private fun showCampus() {
        placeTitle.text = ""
        placeDetail.text = ""
        findViewById<LinearLayout>(R.id.detailsContainer)?.visibility = View.GONE
    }





    private fun showPin(marker: Marker) {
        findViewById<LinearLayout>(R.id.detailsContainer)?.visibility = View.VISIBLE
        placeTitle.text = marker.title ?: "User Pin"

        val descriptionText = marker.snippet
        val locationText = String.format(
            Locale.US,
            "Latitude %.5f · Longitude %.5f (Draggable)",
            marker.position.latitude,
            marker.position.longitude
        )

        placeDetail.text = if (!descriptionText.isNullOrBlank()) {
            "$descriptionText\n$locationText"
        } else {
            locationText
        }
    }

    private fun toggleHeatmap() {
        if (!::googleMap.isInitialized) return

        if (isHeatmapEnabled) {
            heatmapOverlay?.remove()
            heatmapOverlay = null
            isHeatmapEnabled = false
            updateHeatmapButtonText()
            Toast.makeText(this, "Heatmap View: OFF (Normal Map)", Toast.LENGTH_SHORT).show()
        } else {
            refreshHeatmap()
            if (heatmapOverlay != null) {
                isHeatmapEnabled = true
                updateHeatmapButtonText()
                Toast.makeText(
                    this,
                    "Heatmap View: ON (Showing wildlife & plant sighting density)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun refreshHeatmap() {
        if (!::googleMap.isInitialized) return
        heatmapOverlay?.remove()
        heatmapOverlay = null

        val points = getHeatmapPoints()
        if (points.isNotEmpty()) {
            val colors = intArrayOf(
                Color.rgb(102, 178, 255), // Light Blue: low density / rare sightings
                Color.rgb(102, 225, 0),   // Green: mild density
                Color.rgb(255, 255, 0),   // Yellow: moderate activity
                Color.rgb(255, 128, 0),   // Orange: high activity
                Color.rgb(255, 0, 0)      // Red: highest density / popular hotspots
            )
            val startPoints = floatArrayOf(0.1f, 0.3f, 0.5f, 0.75f, 1.0f)
            val gradient = Gradient(colors, startPoints)

            val provider = HeatmapTileProvider.Builder()
                .data(points)
                .gradient(gradient)
                .radius(45)
                .opacity(0.75)
                .build()

            heatmapOverlay = googleMap.addTileOverlay(
                TileOverlayOptions().tileProvider(provider)
            )
        }
    }

    private fun updateHeatmapButtonText() {
        if (isHeatmapEnabled) {
            findViewById<Button>(R.id.heatmapToggleButton)?.text = "Hide Wildlife Heatmap"
        } else {
            findViewById<Button>(R.id.heatmapToggleButton)?.text = "Show Wildlife Heatmap"
        }
    }

    private fun getHeatmapPoints(): List<LatLng> {
        val points = mutableListOf<LatLng>()

        userMarkers.forEach { marker ->
            val pos = marker.position
            points.add(pos)
            repeat(15) {
                val offsetLat = (Math.random() - 0.5) * 0.0006
                val offsetLng = (Math.random() - 0.5) * 0.0006
                points.add(LatLng(pos.latitude + offsetLat, pos.longitude + offsetLng))
            }
        }

        return points
    }
}