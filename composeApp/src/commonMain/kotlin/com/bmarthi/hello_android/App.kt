package com.bmarthi.hello_android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun App(locationProvider: LocationProvider? = null) {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        val lookup = remember { SchoolLookup() }

        var isLoading by remember { mutableStateOf(true) }
        var latText by remember { mutableStateOf("") }
        var lngText by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<School>?>(null) }
        var lookingUp by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var locatingInProgress by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.Default) {
                lookup.load()
            }
            isLoading = false
        }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "School Attendance Zone Lookup",
                style = MaterialTheme.typography.headlineMedium
            )

            if (isLoading) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator()
                Text("Loading school data...")
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("Latitude") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it },
                        label = { Text("Longitude") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (locationProvider != null && locationProvider.isAvailable()) {
                    Button(
                        onClick = {
                            scope.launch {
                                locatingInProgress = true
                                errorMessage = null
                                val loc = locationProvider.getCurrentLocation()
                                if (loc != null) {
                                    latText = loc.latitude.toString()
                                    lngText = loc.longitude.toString()
                                } else {
                                    errorMessage = "Could not get current location"
                                }
                                locatingInProgress = false
                            }
                        },
                        enabled = !locatingInProgress
                    ) {
                        if (locatingInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Use Current Location")
                    }
                }

                Button(
                    onClick = {
                        val lat = latText.toDoubleOrNull()
                        val lng = lngText.toDoubleOrNull()
                        if (lat == null || lng == null) {
                            errorMessage = "Please enter valid latitude and longitude"
                            results = null
                            return@Button
                        }
                        errorMessage = null
                        lookingUp = true
                        scope.launch {
                            val found = withContext(Dispatchers.Default) {
                                lookup.findSchools(lat, lng)
                            }
                            results = found
                            lookingUp = false
                        }
                    },
                    enabled = !lookingUp
                ) {
                    if (lookingUp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Lookup")
                }

                if (errorMessage != null) {
                    Text(
                        errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (results != null) {
                    HorizontalDivider()
                    if (results!!.isEmpty()) {
                        Text(
                            "No schools found at this location.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        Text(
                            "Found ${results!!.size} school(s):",
                            style = MaterialTheme.typography.titleMedium
                        )
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(results!!) { school ->
                                Card(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            school.name,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            "NCES: ${school.ncessch}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "Level: ${school.level}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
