package com.gyan.offline.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyan.offline.download.DownloadProgress
import com.gyan.offline.ui.theme.*

@Composable
fun DownloadScreen(vm: DownloadViewModel = viewModel(), onComplete: () -> Unit) {
    val state by vm.uiState.collectAsState()

    LaunchedEffect(state.allDone) {
        if (state.allDone) onComplete()
    }

    GyanTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Downloading AI Models", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = GyanGreen)
            Spacer(Modifier.height(8.dp))
            Text(
                "One-time download — ~8.5 GB. After this, everything works offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = GyanGrey,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            if (!state.isDownloading && !state.allDone) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GyanSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "⚠️  We recommend Wi-Fi. This download is ~8.5 GB.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GyanGrey,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { vm.startDownload() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GyanGreen)
                ) {
                    Text("Start Download", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }

            if (state.isDownloading || state.allDone) {
                state.progresses.forEach { prog ->
                    DownloadRow(prog)
                    Spacer(Modifier.height(12.dp))
                }
            }

            state.error?.let { err ->
                Spacer(Modifier.height(16.dp))
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.startDownload() }) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(prog: DownloadProgress) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                prog.modelName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = GyanGreenDark
            )
            Text(
                if (prog.isDone) "✓ Done" else "${prog.percent}%",
                style = MaterialTheme.typography.bodySmall,
                color = if (prog.isDone) GyanGreen else GyanGrey
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (prog.totalBytes > 0) prog.percent / 100f else 0f },
            modifier = Modifier.fillMaxWidth(),
            color = if (prog.isDone) GyanGreen else GyanGreenLight,
            trackColor = GyanLightGrey
        )
    }
}
