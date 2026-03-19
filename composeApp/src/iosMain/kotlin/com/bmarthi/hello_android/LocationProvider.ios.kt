package com.bmarthi.hello_android

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.*
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume

class IosLocationProvider : LocationProvider {
    override fun isAvailable(): Boolean = true

    override suspend fun getCurrentLocation(): LatLng? {
        return suspendCancellableCoroutine { cont ->
            val manager = CLLocationManager()
            var resumed = false

            val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                    if (!resumed) {
                        resumed = true
                        val location = didUpdateLocations.lastOrNull() as? CLLocation
                        if (location != null) {
                            cont.resume(LatLng(location.coordinate.useContents { latitude }, location.coordinate.useContents { longitude }))
                        } else {
                            cont.resume(null)
                        }
                    }
                    manager.stopUpdatingLocation()
                }

                override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                    if (!resumed) {
                        resumed = true
                        cont.resume(null)
                    }
                }

                override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                    when (manager.authorizationStatus) {
                        kCLAuthorizationStatusAuthorizedWhenInUse,
                        kCLAuthorizationStatusAuthorizedAlways -> {
                            manager.requestLocation()
                        }
                        kCLAuthorizationStatusDenied,
                        kCLAuthorizationStatusRestricted -> {
                            if (!resumed) {
                                resumed = true
                                cont.resume(null)
                            }
                        }
                        else -> {} // Still waiting for user decision
                    }
                }
            }

            manager.delegate = delegate
            manager.desiredAccuracy = kCLLocationAccuracyBest

            val status = manager.authorizationStatus
            if (status == kCLAuthorizationStatusNotDetermined) {
                manager.requestWhenInUseAuthorization()
            } else if (status == kCLAuthorizationStatusAuthorizedWhenInUse ||
                       status == kCLAuthorizationStatusAuthorizedAlways) {
                manager.requestLocation()
            } else {
                cont.resume(null)
            }

            cont.invokeOnCancellation {
                manager.stopUpdatingLocation()
            }
        }
    }
}
