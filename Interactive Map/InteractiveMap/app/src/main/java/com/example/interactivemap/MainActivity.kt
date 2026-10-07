package com.example.interactivemap

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var map: MapView
    private lateinit var title: TextView
    private lateinit var detail: TextView
    private var temporaryPin: Marker? = null

    // Approximate center of CSU Channel Islands' main campus.
    private val campus = GeoPoint(34.1621, -119.0436)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPrefs = getPreferences(MODE_PRIVATE)
        Configuration.getInstance().load(this, sharedPrefs)
        Configuration.getInstance().userAgentValue =
            applicationContext.packageName + "/1.0 (campus demo)"

        setContentView(R.layout.activity_main)
        map = findViewById(R.id.mapView)
        title = findViewById(R.id.placeTitle)
        detail = findViewById(R.id.placeDetail)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true
        map.controller.setZoom(17.0)
        map.controller.setCenter(campus)

        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(point: GeoPoint): Boolean = false

            override fun longPressHelper(point: GeoPoint): Boolean {
                dropPin(point)
                return true
            }
        }))

        val campusMarker = Marker(map).apply {
            position = campus
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            this.title = "CSU Channel Islands"
            snippet = "1 University Drive, Camarillo, CA 93012"
            setOnMarkerClickListener { marker, _ ->
                marker.showInfoWindow()
                showCampus()
                true
            }
        }
        map.overlays.add(campusMarker)

        findViewById<Button>(R.id.resetButton).setOnClickListener {
            temporaryPin?.let { map.overlays.remove(it) }
            temporaryPin = null
            map.controller.setZoom(17.0)
            map.controller.animateTo(campus)
            showCampus()
            map.invalidate()
        }

        findViewById<Button>(R.id.officialMapButton).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.csuci.edu/"))
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "No browser is installed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCampus() {
        title.text = "CSU Channel Islands"
        detail.text = "1 University Drive, Camarillo, CA 93012. Long press to drop a pin."
    }

    private fun dropPin(point: GeoPoint) {
        temporaryPin?.let { map.overlays.remove(it) }
        temporaryPin = Marker(map).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            this.title = "Your pin"
            snippet = String.format(
                Locale.US, "%.5f, %.5f", point.latitude, point.longitude
            )
            setOnMarkerClickListener { marker, _ ->
                marker.showInfoWindow()
                showPin(point)
                true
            }
        }
        map.overlays.add(temporaryPin)
        showPin(point)
        map.invalidate()
    }

    private fun showPin(point: GeoPoint) {
        title.text = "Your pin"
        detail.text = String.format(
            Locale.US,
            "Latitude %.5f · Longitude %.5f",
            point.latitude,
            point.longitude
        )
    }

    override fun onResume() {
        super.onResume()
        if (::map.isInitialized) map.onResume()
    }

    override fun onPause() {
        if (::map.isInitialized) map.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::map.isInitialized) map.onDetach()
        super.onDestroy()
    }
}