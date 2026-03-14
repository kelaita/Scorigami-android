package com.scorigami.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scorigami.app.R
import com.scorigami.app.data.ScoreDetails
import com.scorigami.app.ui.components.BottomControls
import com.scorigami.app.ui.components.HeatmapView
import com.scorigami.app.viewmodel.ScorigamiViewModel

@Composable
fun ScorigamiApp() {
    val vm: ScorigamiViewModel = viewModel()
    var showAbout by remember { mutableStateOf(false) }
    var selectedScore by remember { mutableStateOf<ScoreDetails?>(null) }
    var resetRequestId by remember { mutableIntStateOf(0) }

    if (vm.uiState.isLoading) {
        LoadingScreen()
        return
    }

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.scorigami_title),
                        contentDescription = "Scorigami",
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                    )
                    IconButton(onClick = { showAbout = true }, modifier = Modifier.size(42.dp)) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "About",
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(0.dp))
            }
        },
        bottomBar = {
            BottomControls(
                uiState = vm.uiState,
                onGradientChange = vm::setGradientType,
                onToggleColorMap = vm::toggleColorMapType,
                onRecencyStartYearChange = vm::updateRecencyStartYear,
                onFrequencyRangeChange = vm::updateFrequencyRange,
                onResetView = { resetRequestId += 1 },
                minLegend = vm.getMinLegendValue(),
                maxLegend = vm.getMaxLegendValue()
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            when {
                vm.uiState.error != null -> {
                    Text(
                        text = vm.uiState.error ?: "Unknown error",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        textAlign = TextAlign.Center
                    )
                }

                else -> {
                    HeatmapView(
                        uiState = vm.uiState,
                        resetRequestId = resetRequestId,
                        onCellTapped = { cell ->
                            selectedScore = vm.toScoreDetails(cell)
                        },
                        getCellColor = vm::getCellColor,
                        getCellTextColor = vm::getCellTextColor
                    )
                }
            }
        }
    }

    if (selectedScore != null) {
        ScoreSheet(
            details = selectedScore!!,
            isRecencyFilterActive = vm.isRecencyFilterActive(),
            onDismiss = { selectedScore = null }
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.scorigami_launch),
            contentDescription = "Scorigami Loading",
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            contentScale = ContentScale.FillWidth
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScoreSheet(details: ScoreDetails, isRecencyFilterActive: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xE84A4A4A),
        scrimColor = Color.Black.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = details.score,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (details.occurrences > 0) {
                if (!isRecencyFilterActive) {
                    Text(
                        text = "This score has happened ${details.occurrences} time${details.plural}.",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(text = "Most recent game:", color = Color(0xFFB0B0B0), fontSize = 14.sp)
                Text(
                    text = details.lastGame,
                    color = Color.White,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(details.gamesUrl)))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Text("View games")
                }
            } else {
                Text(text = "SCORIGAMI!", color = Color(0xFFFFA500), fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(text = "No game has ever ended with this score...yet.", color = Color.White, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
