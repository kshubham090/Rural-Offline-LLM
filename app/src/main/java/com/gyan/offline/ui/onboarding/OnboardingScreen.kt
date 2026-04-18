package com.gyan.offline.ui.onboarding

import androidx.compose.foundation.background
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
import com.gyan.offline.ui.theme.*

@Composable
fun OnboardingScreen(onContinue: (adConsent: Boolean) -> Unit) {
    var adConsentChecked by remember { mutableStateOf(true) }

    GyanTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(GyanBackground)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(48.dp))

            // Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "ज्ञान",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = GyanGreen
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Unlimited AI for Rural India",
                    style = MaterialTheme.typography.titleMedium,
                    color = GyanGrey,
                    textAlign = TextAlign.Center
                )
            }

            // Feature list
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FeatureRow("🌾", "Agriculture", "Crop advice, pest control, government schemes — PM-KISAN, PMFBY, eNAM")
                FeatureRow("📚", "UPSC Prep", "History, Geography, Polity, Economy, Environment — full static syllabus")
                FeatureRow("🏦", "Banking Exams", "IBPS, SBI PO, SSC CGL, RRB NTPC — with step-by-step solutions")
                FeatureRow("📵", "100% Offline", "Works with zero internet. No limits. No subscription. Free forever.")
                FeatureRow("🎙️", "Voice + Hindi", "Speak or type in Hindi or English — get answers in your language")
            }

            // Consent + Continue
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Privacy note
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GyanSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "This app stores everything on your device. Your questions never leave your phone when offline. " +
                        "Ads are shown only when internet is available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GyanGrey,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = adConsentChecked,
                        onCheckedChange = { adConsentChecked = it },
                        colors = CheckboxDefaults.colors(checkedColor = GyanGreen)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Show ads when internet is available (keeps this app free)",
                        style = MaterialTheme.typography.bodySmall,
                        color = GyanGrey
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { onContinue(adConsentChecked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GyanGreen)
                ) {
                    Text(
                        "Get Started — शुरू करें",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    "By continuing you agree to our Privacy Policy (DPDPA 2023 compliant)",
                    style = MaterialTheme.typography.labelSmall,
                    color = GyanGrey.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FeatureRow(emoji: String, title: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(emoji, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = GyanGreenDark)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = GyanGrey)
        }
    }
}
