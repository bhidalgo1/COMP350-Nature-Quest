package com.example.naturequesthomescreen

import android.Manifest
import android.content.Intent
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
import androidx.core.content.ContextCompat
import com.example.natureobservation.feature.posts.ObservationRepository
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

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

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

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

        if (initialTargetLocation != null) {
            val target = initialTargetLocation!!
            val targetTitle = intent.getStringExtra("pin_title")?.ifBlank { null }
            val targetDescription = intent.getStringExtra("pin_description")?.ifBlank { null }

            val title = targetTitle ?: "User Pin"
            val snippet = targetDescription ?: String.format(
                Locale.US,
                "%.5f, %.5f",
                target.latitude,
                target.longitude
            )

            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(target)
                    .title(title)
                    .snippet(snippet)
                    .draggable(true)
            )
            if (marker != null) {
                userMarkers.add(marker)
                marker.showInfoWindow()
                showPin(marker)
            }
        }

        googleMap.setOnMarkerDragListener(
            object : GoogleMap.OnMarkerDragListener {
                override fun onMarkerDragStart(marker: Marker) {}

                override fun onMarkerDrag(marker: Marker) {
                    showPin(marker)
                }

                override fun onMarkerDragEnd(marker: Marker) {
                    showPin(marker)
                }
            }
        )

        // Sync feed items to map
        repository.observeFeed(
            onUpdate = { posts ->
                val targetId = intent.getStringExtra("pin_id")
                posts.forEach { post ->
                    val lat = post.latitude
                    val lng = post.longitude
                    if (lat != null && lng != null) {
                        val pos = LatLng(lat, lng)
                        val existingMarker = userMarkers.find {
                            it.position.latitude == lat && it.position.longitude == lng
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