package com.purityguard.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class BlockedActivity : ComponentActivity() {
    private var currentDomain by mutableStateOf("this domain")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentDomain = extractDomain(intent)

        setContent {
            val settings = SettingsStore(this@BlockedActivity)
            val uriHandler = LocalUriHandler.current
            val verse = MotivationVerses.pick(settings.getInspirationMode())

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(brush = Brush.verticalGradient(listOf(Color(0xFF020202), Color(0xFF0B0B0B), Color(0xFF000000))))
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.purityguardlogo),
                                contentDescription = "PurityGuard",
                                modifier = Modifier.size(76.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("Access blocked", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                color = Color(0xFF0E0E0E),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(999.dp))
                            ) {
                                Text(
                                    text = currentDomain,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text("PurityGuard blocked this domain using local VPN DNS enforcement.", color = Color(0xFFBBBBBB))

                            if (verse != null) {
                                Spacer(Modifier.height(16.dp))
                                Surface(
                                    color = Color(0xFF101010),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.border(1.dp, Color(0xFF232323), RoundedCornerShape(12.dp))
                                ) {
                                    Text(
                                        text = verse,
                                        color = Color(0xFFE1E1E1),
                                        softWrap = true,
                                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(28.dp))
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { uriHandler.openUri("https://ko-fi.com/outcome") }
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Favorite,
                                        contentDescription = "Ko-fi",
                                        tint = Color(0xFFA855F7),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text("SUPPORT ON KO-FI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentDomain = extractDomain(intent)
    }

    private fun extractDomain(intent: Intent?): String {
        return intent?.getStringExtra(EXTRA_DOMAIN).orEmpty().ifBlank { "this domain" }
    }

    companion object {
        const val EXTRA_DOMAIN = "blocked_domain"
    }
}
