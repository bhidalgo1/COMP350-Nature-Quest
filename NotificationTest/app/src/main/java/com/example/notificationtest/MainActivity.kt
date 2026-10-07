package com.example.notificationtest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.example.notificationtest.ui.theme.NotificationTestTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import java.lang.Thread.sleep

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val counterService = CounterNotificationService(applicationContext)
        val timerService = TimerNotificationService(applicationContext)
        var time = Timer.value
        enableEdgeToEdge()
        setContent {
            NotificationTestTheme {
                Surface (modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Button(onClick = { counterService.showNotification(Counter.value) }) {
                            Text(text = "Show notification")
                        }
                    }

                    Column(Modifier) {
                        Text(text = "Timer", modifier = Modifier.padding(16.dp))
                        while (time > 0) {
                            sleep(1000)
                            time--
                        }
                        if (time <= 0) timerService.showNotification()
                    }
                }
            }
        }
    }
}