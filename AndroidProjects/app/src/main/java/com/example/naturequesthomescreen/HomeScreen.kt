package com.example.naturequesthomescreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onCameraClick: () -> Unit,
    onMapClick: () -> Unit,
    onUploadClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Nature Quest")

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = onCameraClick) {
            Text("Open Camera")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = onMapClick) {
            Text("Open Map")
        }

        Button(onClick = onUploadClick) {
            Text("Upload/Post Picture")
        }

        Spacer(modifier = Modifier.height(12.dp))

    }
}