package com.trackit.expense.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing a simplified location result.
 */
data class TrackItLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null
)

/**
 * Helper class for fetching device location and performing reverse geocoding.
 *
 * Uses Google Play Services [FusedLocationProviderClient] for efficient location retrieval.
 */
@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    /**
     * Gets the last known location of the device.
     *
     * @return [TrackItLocation] containing coordinates and optionally an address, or null if unavailable.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): TrackItLocation? = withContext(Dispatchers.IO) {
        try {
            // Task-based API converted to coroutine-friendly wait
            val locationTask = fusedLocationClient.lastLocation
            val location: Location? = Tasks.await(locationTask, 5, TimeUnit.SECONDS)

            location?.let {
                val address = getAddressFromLocation(it.latitude, it.longitude)
                TrackItLocation(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    address = address
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Performs reverse geocoding to get a human-readable address from coordinates.
     */
    private suspend fun getAddressFromLocation(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                // Construct a simple one-line address: "FeatureName, Locality" or similar
                val parts = mutableListOf<String>()
                address.featureName?.let { parts.add(it) }
                address.locality?.let { parts.add(it) }
                address.adminArea?.let { parts.add(it) }
                
                parts.distinct().take(2).joinToString(", ")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
