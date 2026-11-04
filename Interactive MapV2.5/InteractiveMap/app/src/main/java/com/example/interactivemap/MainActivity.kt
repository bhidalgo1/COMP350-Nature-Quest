package com.example.interactivemap

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var googleMap: GoogleMap
    private lateinit var title: TextView
    private lateinit var detail: TextView
    private val userMarkers = mutableListOf<Marker>()

    // Approximate center of CSU Channel Islands' main campus.
    private val campus = LatLng(34.1621, -119.0436)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        title = findViewById(R.id.placeTitle)
        detail = findViewById(R.id.placeDetail)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        findViewById<Button>(R.id.addPinButton).setOnClickListener {
            if (::googleMap.isInitialized) {
                promptAndAddPin(googleMap.cameraPosition.target)
            }
        }

        findViewById<Button>(R.id.resetButton).setOnClickListener {
            if (::googleMap.isInitialized) {
                userMarkers.forEach { it.remove() }
                userMarkers.clear()
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(campus, 17f))
                showCampus()
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(campus, 17f))

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

        googleMap.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker) {}

            override fun onMarkerDrag(marker: Marker) {
                showPin(marker)
            }

            override fun onMarkerDragEnd(marker: Marker) {
                showPin(marker)
            }
        })
    }

    private fun showCampus() {
        title.text = "CSU Channel Islands"
        detail.text = "Tap map or 'Add Pin Here' to create a pin."
    }

    private fun promptAndAddPin(point: LatLng) {
        val input = EditText(this).apply {
            hint = "Pin Name (e.g. Library, Food Court)"
            setSingleLine()
        }

        AlertDialog.Builder(this)
            .setTitle("Create Custom Pin")
            .setMessage(String.format(Locale.US, "Location: %.5f, %.5f", point.latitude, point.longitude))
            .setView(input)
            .setPositiveButton("Add Pin") { _, _ ->
                val pinName = input.text.toString().ifBlank { "User Pin" }
                addPin(point, pinName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addPin(point: LatLng, pinTitle: String) {
        val newMarker = googleMap.addMarker(
            MarkerOptions()
                .position(point)
                .title(pinTitle)
                .snippet(String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude))
                .draggable(true)
        )
        if (newMarker != null) {
            userMarkers.add(newMarker)
            newMarker.showInfoWindow()
            showPin(newMarker)
        }
    }

    private fun showPin(marker: Marker) {
        title.text = marker.title ?: "User Pin"
        detail.text = String.format(
            Locale.US,
            "Latitude %.5f · Longitude %.5f (Draggable)",
            marker.position.latitude,
            marker.position.longitude
        )
    }
}