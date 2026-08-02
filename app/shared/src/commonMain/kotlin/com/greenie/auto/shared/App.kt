package com.greenie.auto.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api

private val AppBg = Color(0xFF12172B)
private val AppCard = Color(0xFF1A2342)
private val AppAccent = Color(0xFF17E4BE)
private val AppTextPrimary = Color(0xFFE8ECF7)
private val AppTextSecondary = Color(0xFF9CA7C2)
private val AppTrack = Color(0xFF313C63)

@Composable
fun GreenieApp() {
    var connected by remember { mutableStateOf(false) }
    var useMock by remember { mutableStateOf(false) }
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(900)
        showSplash = false
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBg)
        ) {
            GreenieAppContent(
                connected = connected,
                useMock = useMock,
                onConnect = { mock ->
                    useMock = mock
                    connected = true
                }
            )

            if (showSplash) {
                SplashOverlay()
            }
        }
    }
}

@Composable
private fun GreenieAppContent(
    connected: Boolean,
    useMock: Boolean,
    onConnect: (Boolean) -> Unit,
) {
    if (!connected) {
        IpInputScreen(onConnect = onConnect)
        return
    }

    val repository = remember(useMock) {
        if (useMock) MockSoilRepository() else AutoSoilRepository()
    }
    DashboardScreen(repository = repository, isMock = useMock)
}

@Composable
private fun SplashOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "🌱",
                fontSize = 56.sp,
            )

            Text(
                "greenie-auto",
                color = AppAccent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )

            CircularProgressIndicator(color = AppAccent)

            Text(
                "Đang khởi động...",
                color = AppTextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun IpInputScreen(onConnect: (Boolean) -> Unit) {
    var useMock by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val setupUrl = "http://192.168.4.1"
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "🌱 greenie-auto",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBg,
                    titleContentColor = AppAccent
                )
            )
        },
        containerColor = AppBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBg)
                .padding(innerPadding)
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (useMock) "🧪 Chế độ Mock (Test)" else "Thiết lập WiFi cho ESP32",
                    color = AppTextSecondary,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(24.dp))
                
                if (!useMock) {
                    Button(
                        onClick = { uriHandler.openUri(setupUrl) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F80ED))
                    ) {
                        Text("Mở trang setup WiFi ESP32", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                
                Button(
                    onClick = { onConnect(useMock) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = true,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (useMock) "🧪 Test Mock" else "Vào Dashboard", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                
                // Toggle Mock Mode
                Row(
                    Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Chế độ Mock:", color = Color(0xFFAAAAAA))
                    Spacer(Modifier.width(12.dp))
                    Checkbox(
                        checked = useMock,
                        onCheckedChange = { useMock = it },
                        colors = CheckboxDefaults.colors(checkedColor = AppAccent)
                    )
                }
                
                Spacer(Modifier.height(4.dp))
                Text(
                    if (useMock) "✅ Dùng dữ liệu giả lập (test mà không cần ESP32)"
                    else "Cùng WiFi: đọc ESP32 trực tiếp. Khác WiFi: tự fallback Firebase.",
                    color = AppTextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DashboardScreen(repository: SoilRepository, isMock: Boolean) {
    var soilData by remember { mutableStateOf<SoilData?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var sourceLabel by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    fun applyFetchResult(result: Result<SoilData>) {
        result
            .onSuccess {
                soilData = it
                sourceLabel = when {
                    isMock -> "Mock dữ liệu"
                    repository is AutoSoilRepository -> repository.lastSuccessfulSource?.label
                    else -> null
                }
                warning = null
                error = null
                loading = false
            }
            .onFailure {
                val raw = when (repository) {
                    is AutoSoilRepository -> repository.lastFailureSummary ?: it.message
                    else -> it.message
                }
                val message = toUserFacingMessage(raw, hasCachedData = soilData != null)
                loading = false
                if (soilData == null) {
                    error = message
                } else {
                    warning = message
                }
                logError("SoilRepository", message)
            }
    }
    
    LaunchedEffect(Unit) {
        while (true) {
            applyFetchResult(repository.fetchData())
            delay(2000)
        }
    }
    
    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "🌱 greenie-auto",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppBg,
                        titleContentColor = AppAccent
                    )
                )
            },
            containerColor = AppBg
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(AppBg)
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (loading && soilData == null && error == null) {
                        CircularProgressIndicator(color = AppAccent)
                    } else if (soilData == null && error != null) {
                        ErrorCard(
                            message = error!!,
                            onRetry = {
                                scope.launch { applyFetchResult(repository.fetchData()) }
                            },
                            onOpenSetup = {
                                uriHandler.openUri("http://192.168.4.1")
                            }
                        )
                    } else if (soilData != null) {
                        sourceLabel?.let {
                            ConnectionBanner(it)
                            Spacer(Modifier.height(12.dp))
                        }

                        warning?.let {
                            WarningBanner(it)
                            Spacer(Modifier.height(12.dp))
                        }

                        SensorCards(
                            data = soilData!!,
                            repository = repository,
                            scope = scope,
                            onFailure = {
                                warning = toUserFacingMessage(it, hasCachedData = true)
                            }
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Tự động làm mới 2 giây/lần",
                        color = AppTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorCards(
    data: SoilData,
    repository: SoilRepository,
    scope: CoroutineScope,
    onFailure: (String) -> Unit,
) {
    var pumpLoading by remember { mutableStateOf(false) }

    // ─ Cảm biến không khí — chỉ hiện khi DHT đang cắm
    if (data.airTemp >= 0f) {
        AirSensorCard(temp = data.airTemp, humidity = data.airHumidity)
        Spacer(Modifier.height(12.dp))
    }

    // ─ Cảm biến độ ẩm đất — chỉ hiện cái nào đang cắm (giá trị ≥ 0)
    val sensors = data.sensors
        .mapIndexed { index, pct -> index + 1 to pct }
        .filter { (_, pct) -> pct >= 0 }

    sensors.forEachIndexed { idx, (label, pct) ->
        SensorCard("Cảm biến đất $label", pct)
        if (idx != sensors.lastIndex) {
            Spacer(Modifier.height(12.dp))
        }
    }

    if (sensors.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
    }

    AverageCard(data.average, activeCount = sensors.size, totalCount = data.sensorCount)
    Spacer(Modifier.height(12.dp))
    PumpCard(
        isRunning = data.pump,
        loading = pumpLoading,
        onToggle = { target ->
            pumpLoading = true
            scope.launch {
                repository.setPump(target)
                    .onSuccess { pumpLoading = false }
                    .onFailure {
                        pumpLoading = false
                        onFailure(it.message ?: "Không gửi được lệnh bơm")
                        logError("SoilRepository", it.message)
                    }
            }
        }
    )
}

@Composable
private fun AirSensorCard(temp: Float, humidity: Float) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("🌡️ Cảm biến không khí", color = AppTextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(temp * 10).toInt() / 10.0}°C",
                        color = Color(0xFFE67E22),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Nhiệt độ", color = Color(0xFF888888), fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(humidity * 10).toInt() / 10.0}%",
                        color = Color(0xFF3498DB),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Độ ẩm KK", color = AppTextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SensorCard(title: String, pct: Int) {
    val color = when {
        pct < 20 -> Color(0xFFE74C3C)
        pct < 60 -> Color(0xFFF39C12)
        else -> Color(0xFF27AE60)
    }
    val label = when {
        pct < 20 -> "Khô — Cần tưới"
        pct < 60 -> "Vừa — Độ ẩm tốt"
        else -> "Ướt — Đủ nước"
    }
    
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = AppTextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text("$pct%", color = color, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { pct / 100f },
                Modifier.fillMaxWidth().height(8.dp),
                color = color,
                trackColor = AppTrack
            )
            Spacer(Modifier.height(6.dp))
            Text(label, color = AppTextPrimary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AverageCard(avg: Int, activeCount: Int, totalCount: Int) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Trung bình", color = AppTextSecondary)
                if (activeCount < totalCount) {
                    Text(
                        "⚠️ $activeCount/$totalCount cảm biến có tín hiệu",
                        color = Color(0xFFF39C12),
                        fontSize = 11.sp
                    )
                }
            }
            Text("$avg%", color = AppAccent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PumpCard(isRunning: Boolean, loading: Boolean, onToggle: (Boolean) -> Unit) {
    val btnColor = if (isRunning) Color(0xFFE74C3C) else Color(0xFF27AE60)
    val btnLabel = if (isRunning) "Tắt bơm" else "Bật bơm"
    
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Máy bơm", color = AppTextSecondary)
                Text(
                    if (isRunning) "⚙️ Đang chạy" else "⏸ Đứng",
                    color = if (isRunning) Color(0xFF27AE60) else AppTextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onToggle(!isRunning) },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = btnColor)
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                else Text(btnLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ConnectionBanner(source: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF133A33))
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("✅ Nguồn dữ liệu", color = AppTextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(source, color = AppAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun WarningBanner(message: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2C10))
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("⚠️ Cảnh báo", color = Color(0xFFF5B041), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(message, color = AppTextPrimary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3D1515))
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("❌ Lỗi kết nối", color = Color(0xFFE74C3C), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = AppTextPrimary, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Thử lại", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenSetup,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Mở trang setup ESP32", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun toUserFacingMessage(raw: String?, hasCachedData: Boolean): String {
    val text = raw?.lowercase().orEmpty()

    val reason = when {
        text.contains("401") || text.contains("403") ->
            "Firebase đang từ chối truy cập."
        text.contains("timeout") || text.contains("timed out") ->
            "Kết nối đang bị timeout."
        text.contains("unknownhost") || text.contains("unresolved") || text.contains("unable to resolve") ->
            "Không tìm thấy ESP32 trong mạng hiện tại."
        text.contains("network") || text.contains("offline") || text.contains("failed to connect") ->
            "Thiết bị đang mất mạng hoặc WiFi không ổn định."
        else ->
            "Chưa lấy được dữ liệu cảm biến."
    }

    val nextStep = if (hasCachedData) {
        "App đang giữ dữ liệu cũ. Kiểm tra mạng rồi bấm Thử lại."
    } else {
        "Hãy kiểm tra WiFi (cùng mạng với ESP32) hoặc bật mạng Internet để dùng Firebase, rồi bấm Thử lại."
    }

    return "$reason\n$nextStep"
}
