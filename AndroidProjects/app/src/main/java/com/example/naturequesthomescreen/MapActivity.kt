package com.example.naturequesthomescreen

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageView
import android.view.View
import com.example.natureobservation.feature.posts.ObservationPost
import kotlinx.coroutines.ensureActive
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.natureobservation.feature.posts.ObservationRepository
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.launch
import java.util.Locale

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var placeTitle: TextView
    private lateinit var placeDetail: TextView
    private lateinit var pinPhoto: ImageView
    private var photoLoadJob: Job? = null
    private var requestedPinFocused = false
    private var mapFeedListener: ListenerRegistration? = null

    private val userMarkers = mutableListOf<Marker>()
    private val repository by lazy { ObservationRepository(this) }

    // Approximate center of CSU Channel Islands.
    private val campus = LatLng(34.1621, -119.0436)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        placeTitle = findViewById(R.id.placeTitle)
        placeDetail = findViewById(R.id.placeDetail)
        pinPhoto = findViewById(R.id.pinPhoto)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment

        mapFragment.getMapAsync(this)
        showCampus()

        findViewById<Button>(R.id.addPinButton).setOnClickListener {
            if (::googleMap.isInitialized) {
                promptAndAddPin(googleMap.cameraPosition.target)
            }
        }

        findViewById<Button>(R.id.resetButton).setOnClickListener {
            if (::googleMap.isInitialized) {
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(campus, 17f)
                )

                showCampus()
            }
        }

        findViewById<Button>(R.id.backToHomeButton)?.setOnClickListener {
            finish()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(campus, 17f)
        )

        val campusMarker = googleMap.addMarker(
            MarkerOptions()
                .position(campus)
                .title("CSU Channel Islands")
                .snippet("CSU Channel Islands Campus")
        )

        googleMap.setOnMarkerClickListener { marker ->
            marker.showInfoWindow()

            if (marker == campusMarker) {
                showCampus()
            } else {
                showPin(marker)
            }

            true
        }

        googleMap.setOnMapClickListener { point ->
            promptAndAddPin(point)
        }

        googleMap.setOnMapLongClickListener { point ->
            promptAndAddPin(point)
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
        mapFeedListener = repository.observeFeed(
            onUpdate = { posts ->
                posts.forEach { post ->
                    val lat = post.latitude
                    val lng = post.longitude

                    if (lat != null && lng != null) {
                        val existingMarker = userMarkers.firstOrNull {
                            (it.tag as? ObservationPost)?.id == post.id
                        } ?: userMarkers.firstOrNull {
                            it.tag == null &&
                                    it.position.latitude == lat &&
                                    it.position.longitude == lng
                        }

                        if (existingMarker != null) {
                            existingMarker.tag = post
                            existingMarker.title = post.title
                            existingMarker.snippet = post.description
                        } else {
                            val marker = googleMap.addMarker(
                                MarkerOptions()
                                    .position(LatLng(lat, lng))
                                    .title(post.title)
                                    .snippet(post.description)
                                    .draggable(true)
                            )

                            if (marker != null) {
                                marker.tag = post
                                userMarkers.add(marker)
                            }
                        }
                    }
                }

                focusRequestedPin()
            },
            onError = { error ->
                Toast.makeText(
                    this@MapActivity,
                    error.localizedMessage ?: "Could not load map pins.",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun showCampus() {
        photoLoadJob?.cancel()
        pinPhoto.setImageDrawable(null)
        pinPhoto.visibility = View.GONE

        placeTitle.text = "CSU Channel Islands"
        placeDetail.text = "Tap map or 'Add Pin Here' to create a pin."
    }

    private fun promptAndAddPin(point: LatLng) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val paddingPx = (16 * resources.displayMetrics.density).toInt()
            setPadding(paddingPx, (8 * resources.displayMetrics.density).toInt(), paddingPx, 0)
        }

        val titleInput = EditText(this).apply {
            hint = "Title (e.g. Library, Food Court)"
            setSingleLine()
        }

        val descriptionInput = EditText(this).apply {
            hint = "Description (e.g. Quiet study spot)"
            isSingleLine = false
            minLines = 2
            maxLines = 4
        }

        container.addView(titleInput)
        container.addView(descriptionInput)

        AlertDialog.Builder(this)
            .setTitle("Create Custom Pin")
            .setMessage(
                String.format(
                    Locale.US,
                    "Location: %.5f, %.5f",
                    point.latitude,
                    point.longitude
                )
            )
            .setView(container)
            .setPositiveButton("Add Pin") { _, _ ->
                val pinTitle = titleInput.text.toString().ifBlank {
                    "User Pin"
                }
                val pinDescription = descriptionInput.text.toString().trim()

                addPin(point, pinTitle, pinDescription)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addPin(point: LatLng, pinTitle: String, pinDescription: String) {
        val newMarker = googleMap.addMarker(
            MarkerOptions()
                .position(point)
                .title(pinTitle)
                .snippet(pinDescription)
                .draggable(true)
        )

        if (newMarker != null) {
            userMarkers.add(newMarker)
            newMarker.showInfoWindow()
            showPin(newMarker)
        }

        // Link map pin to observation feed
        lifecycleScope.launch {
            try {
                repository.publishPin(
                    title = pinTitle,
                    description = pinDescription,
                    latitude = point.latitude,
                    longitude = point.longitude
                )
                Toast.makeText(
                    this@MapActivity,
                    "Pin added to Observation Feed!",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (_: Exception) {
            }
        }
    }

    private fun showPin(marker: Marker) {
        photoLoadJob?.cancel()
        pinPhoto.setImageDrawable(null)
        pinPhoto.visibility = View.GONE

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

        val post = marker.tag as? ObservationPost ?: return

        if (post.storagePath.isBlank()) {
            return
        }

        photoLoadJob = lifecycleScope.launch {
            val bitmap = repository.loadPreview(post.storagePath)

            // Ignore a previous photo request if another pin was selected.
            kotlinx.coroutines.currentCoroutineContext().ensureActive()

            if (bitmap != null) {
                pinPhoto.setImageBitmap(bitmap)
                pinPhoto.contentDescription = "Photo for ${post.title}"
                pinPhoto.visibility = View.VISIBLE
            } else {
                Toast.makeText(
                    this@MapActivity,
                    "Could not load this pin's photo. Tap the pin to retry.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    private fun focusRequestedPin() {
        if (requestedPinFocused) return

        val pinId = intent.getStringExtra("pin_id") ?: return

        val marker = userMarkers.firstOrNull {
            (it.tag as? ObservationPost)?.id == pinId
        } ?: return

        requestedPinFocused = true

        googleMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                marker.position,
                18f
            )
        )

        marker.showInfoWindow()
        showPin(marker)
    }
    override fun onDestroy() {
        mapFeedListener?.remove()
        photoLoadJob?.cancel()
        super.onDestroy()
    }
}