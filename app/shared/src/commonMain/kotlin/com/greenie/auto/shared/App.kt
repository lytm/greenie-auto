package com.greenie.auto.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
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
private const val LOCAL_REFRESH_DELAY_MS = 2_000L
private const val FIREBASE_REFRESH_DELAY_MS = 10_000L
private const val MONTHLY_STATS_REFRESH_DELAY_MS = 30_000L

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
    var monthlyStats by remember { mutableStateOf(MonthlyStats(monthKey = "", days = emptyList())) }
    var monthlyError by remember { mutableStateOf<String?>(null) }
    var showMonthlyGrid by remember { mutableStateOf(false) }
    var showPumpHistory by remember { mutableStateOf(false) }
    var pumpHistory by remember { mutableStateOf(PumpHistory(entries = emptyList())) }
    var lowSoilAlert by remember { mutableStateOf(20) }
    var highTempAlert by remember { mutableStateOf(35f) }
    var mockSensorCount by remember { mutableStateOf((repository as? MockSoilRepository)?.getMockActiveSensors() ?: 1) }
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
            val nextDelay = when {
                isMock -> LOCAL_REFRESH_DELAY_MS
                repository is AutoSoilRepository && repository.lastSuccessfulSource == ConnectionSource.Firebase -> FIREBASE_REFRESH_DELAY_MS
                else -> LOCAL_REFRESH_DELAY_MS
            }
            delay(nextDelay)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            repository.fetchMonthlyStats()
                .onSuccess {
                    monthlyStats = it
                    monthlyError = null
                }
                .onFailure {
                    if (monthlyStats.days.isEmpty()) {
                        monthlyError = "Chưa có dữ liệu biểu đồ tháng."
                    }
                    logError("SoilRepository", "monthlyStats: ${it.message}")
                }
            delay(MONTHLY_STATS_REFRESH_DELAY_MS)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            repository.fetchPumpHistory()
                .onSuccess { pumpHistory = it }
                .onFailure { logError("SoilRepository", "pumpHistory: ${it.message}") }
            delay(30_000L)
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
                if (showPumpHistory) {
                    PumpHistoryScreen(
                        pumpHistory = pumpHistory,
                        onBack = { showPumpHistory = false }
                    )
                    return@Box
                }
                if (showMonthlyGrid) {
                    MonthlyTrendGridScreen(
                        monthlyStats = monthlyStats,
                        error = monthlyError,
                        onBack = { showMonthlyGrid = false }
                    )
                    return@Box
                }

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
                        if (isMock && repository is MockSoilRepository) {
                            MockSensorControlCard(
                                count = mockSensorCount,
                                onChange = { target ->
                                    val next = target.coerceIn(0, 6)
                                    repository.setMockActiveSensors(next)
                                    mockSensorCount = next
                                    scope.launch { applyFetchResult(repository.fetchData()) }
                                }
                            )
                            Spacer(Modifier.height(12.dp))
                        }

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
                            monthlyStats = monthlyStats,
                            monthlyError = monthlyError,
                            onOpenMonthlyGrid = { showMonthlyGrid = true },
                            onOpenPumpHistory = { showPumpHistory = true },
                            lowSoilAlert = lowSoilAlert,
                            highTempAlert = highTempAlert,
                            onLowSoilAlertChange = { lowSoilAlert = it },
                            onHighTempAlertChange = { highTempAlert = it },
                            repository = repository,
                            scope = scope,
                            onFailure = {
                                warning = toUserFacingMessage(it, hasCachedData = true)
                            }
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (repository is AutoSoilRepository && repository.lastSuccessfulSource == ConnectionSource.Firebase)
                            "Đang dùng Firebase: làm mới 10 giây/lần"
                        else
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
private fun MockSensorControlCard(count: Int, onChange: (Int) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2D56)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("🧪 Mock cảm biến", color = AppTextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = { onChange(count - 1) }, enabled = count > 0) {
                    Text("-1")
                }
                Text("Đang bật: $count / 6", color = AppTextPrimary, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { onChange(count + 1) }, enabled = count < 6) {
                    Text("+1")
                }
            }
        }
    }
}

@Composable
private fun SensorCards(
    data: SoilData,
    monthlyStats: MonthlyStats,
    monthlyError: String?,
    onOpenMonthlyGrid: () -> Unit,
    onOpenPumpHistory: () -> Unit,
    lowSoilAlert: Int,
    highTempAlert: Float,
    onLowSoilAlertChange: (Int) -> Unit,
    onHighTempAlertChange: (Float) -> Unit,
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

    // ─ Cảnh báo ngưỡng
    val dryAlertActive = sensors.isNotEmpty() && data.average in 1..99 && data.average < lowSoilAlert && !data.pump
    val hotAlertActive = data.airTemp >= 0f && data.airTemp > highTempAlert
    if (dryAlertActive) {
        AlertBanner("🚰 Đất khô! Độ ẩm TB ${data.average}% dưới ngưỡng cảnh báo ${lowSoilAlert}%")
        Spacer(Modifier.height(12.dp))
    }
    if (hotAlertActive) {
        AlertBanner("🌡️ Nhiệt độ ${((data.airTemp * 10).toInt() / 10.0)}°C vượt ngưỡng cảnh báo ${((highTempAlert * 10).toInt() / 10.0)}°C!")
        Spacer(Modifier.height(12.dp))
    }

    MonthlyTrendPreviewCard(
        monthlyStats = monthlyStats,
        error = monthlyError,
        onOpenMonthlyGrid = onOpenMonthlyGrid,
    )
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
    Spacer(Modifier.height(12.dp))
    AlertSettingsCard(
        lowSoilAlert = lowSoilAlert,
        highTempAlert = highTempAlert,
        onLowSoilChange = onLowSoilAlertChange,
        onHighTempChange = onHighTempAlertChange,
        onOpenPumpHistory = onOpenPumpHistory,
    )
}

@Composable
private fun MonthlyTrendPreviewCard(
    monthlyStats: MonthlyStats,
    error: String?,
    onOpenMonthlyGrid: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("📈 Biểu đồ tháng", color = AppTextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))

            if (monthlyStats.days.isEmpty()) {
                Text(error ?: "Đang chờ dữ liệu tháng...", color = AppTextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenMonthlyGrid,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Mở view biểu đồ")
                }
                return@Column
            }

            val monthLabel = monthlyStats.monthKey.ifBlank { "N/A" }
            Text("Tháng $monthLabel", color = AppTextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))

            val last = monthlyStats.days.last()
            Text("Hôm nay (${last.day}): ${((last.avgTemp * 10).toInt() / 10.0)}°C • Bơm ${last.pumpOnCount} lần", color = AppTextPrimary, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onOpenMonthlyGrid,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Mở view biểu đồ dạng grid", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MonthlyTrendGridScreen(
    monthlyStats: MonthlyStats,
    error: String?,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onBack, shape = RoundedCornerShape(10.dp)) {
                Text("← Quay lại")
            }
            Text(
                "📊 Grid tháng ${monthlyStats.monthKey.ifBlank { "N/A" }}",
                color = AppAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        if (monthlyStats.days.isEmpty()) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppCard)
            ) {
                Text(
                    error ?: "Chưa có dữ liệu biểu đồ tháng.",
                    color = AppTextSecondary,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 13.sp
                )
            }
            return@Column
        }

        MonthlyLineChartCard(monthlyStats = monthlyStats)
        Spacer(Modifier.height(12.dp))

        val maxPump = (monthlyStats.days.maxOfOrNull { it.pumpOnCount } ?: 1).coerceAtLeast(1)
        val maxTemp = (monthlyStats.days.maxOfOrNull { it.avgTemp } ?: 1f).coerceAtLeast(1f)

        monthlyStats.days.chunked(2).forEach { rowDays ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowDays.forEach { day ->
                    DayGridItem(
                        modifier = Modifier.weight(1f),
                        day = day,
                        maxTemp = maxTemp,
                        maxPump = maxPump
                    )
                }
                if (rowDays.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MonthlyLineChartCard(monthlyStats: MonthlyStats) {
    val days = monthlyStats.days
    val tempValues = days.map { it.avgTemp }
    val minTempRaw = tempValues.minOrNull() ?: 0f
    val maxTempRaw = tempValues.maxOrNull() ?: 1f
    val minTemp = if (minTempRaw == maxTempRaw) minTempRaw - 1f else minTempRaw
    val maxTemp = if (minTempRaw == maxTempRaw) maxTempRaw + 1f else maxTempRaw

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Biểu đồ đường tháng", color = AppTextPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🟧 Nhiệt độ", color = Color(0xFFE67E22), fontSize = 12.sp)
                Text("🟦 Độ ẩm đất", color = Color(0xFF3498DB), fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            ) {
                val left = 10f
                val right = size.width - 10f
                val top = 12f
                val bottom = size.height - 16f
                val chartWidth = (right - left).coerceAtLeast(1f)
                val chartHeight = (bottom - top).coerceAtLeast(1f)
                val count = days.size
                val stepX = if (count <= 1) 0f else chartWidth / (count - 1)

                drawLine(
                    color = AppTrack,
                    start = Offset(left, bottom),
                    end = Offset(right, bottom),
                    strokeWidth = 2f
                )

                for (i in 1..3) {
                    val y = top + (chartHeight * i / 4f)
                    drawLine(
                        color = AppTrack.copy(alpha = 0.5f),
                        start = Offset(left, y),
                        end = Offset(right, y),
                        strokeWidth = 1f
                    )
                }

                var prevTemp: Offset? = null
                var prevSoil: Offset? = null

                days.forEachIndexed { index, day ->
                    val x = left + index * stepX
                    val tempRatio = ((day.avgTemp - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)
                    val tempY = bottom - (tempRatio * chartHeight)
                    val tempPoint = Offset(x, tempY)

                    val soilPoint = if (day.avgSoil >= 0f) {
                        val soilRatio = (day.avgSoil / 100f).coerceIn(0f, 1f)
                        val soilY = bottom - (soilRatio * chartHeight)
                        Offset(x, soilY)
                    } else {
                        null
                    }

                    prevTemp?.let {
                        drawLine(
                            color = Color(0xFFE67E22),
                            start = it,
                            end = tempPoint,
                            strokeWidth = 3f
                        )
                    }
                    if (soilPoint != null) {
                        prevSoil?.let {
                            drawLine(
                                color = Color(0xFF3498DB),
                                start = it,
                                end = soilPoint,
                                strokeWidth = 3f
                            )
                        }
                    }

                    drawCircle(Color(0xFFE67E22), radius = 3.5f, center = tempPoint)
                    soilPoint?.let {
                        drawCircle(Color(0xFF3498DB), radius = 3.5f, center = it)
                    }

                    prevTemp = tempPoint
                    prevSoil = soilPoint
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Nhiệt độ scale theo min-max tháng (${(minTemp * 10).toInt() / 10.0}°C → ${(maxTemp * 10).toInt() / 10.0}°C), độ ẩm đất scale 0-100%.",
                color = AppTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun DayGridItem(
    modifier: Modifier,
    day: MonthlyDayStat,
    maxTemp: Float,
    maxPump: Int,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Ngày ${day.day}", color = AppTextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text("${((day.avgTemp * 10).toInt() / 10.0)}°C", color = Color(0xFFE67E22), fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
                progress = { (day.avgTemp / maxTemp).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Color(0xFFE67E22),
                trackColor = AppTrack,
            )
            Spacer(Modifier.height(8.dp))
            Text("Đất: ${((day.avgSoil * 10).toInt() / 10.0)}%", color = Color(0xFF17E4BE), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (day.avgSoil / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Color(0xFF17E4BE),
                trackColor = AppTrack,
            )
            Spacer(Modifier.height(8.dp))
            Text("Bơm: ${day.pumpOnCount} lần", color = Color(0xFF3498DB), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            LinearProgressIndicator(
                progress = { (day.pumpOnCount.toFloat() / maxPump).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Color(0xFF3498DB),
                trackColor = AppTrack,
            )
        }
    }
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

@Composable
private fun AlertBanner(message: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3D1515))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = Color(0xFFE74C3C), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AlertSettingsCard(
    lowSoilAlert: Int,
    highTempAlert: Float,
    onLowSoilChange: (Int) -> Unit,
    onHighTempChange: (Float) -> Unit,
    onOpenPumpHistory: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("🔔 Cảnh báo & Lịch sử", color = AppTextSecondary, fontSize = 14.sp)

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenPumpHistory,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("📋 Lịch sử tưới", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (expanded) "Thu gọn" else "⚙️ Sửa", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3D1515))
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Đất khô", color = Color(0xFFEAA7A7), fontSize = 11.sp)
                        Text("< ${lowSoilAlert}%", color = Color(0xFFE74C3C), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2812))
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Nhiệt cao", color = Color(0xFFF4C789), fontSize = 11.sp)
                        Text("> ${((highTempAlert * 10).toInt() / 10.0)}°C", color = Color(0xFFE67E22), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Text("Cảnh báo đất khô — dưới: ${lowSoilAlert}%", color = AppTextPrimary, fontSize = 13.sp)
                Slider(
                    value = lowSoilAlert.toFloat(),
                    onValueChange = { onLowSoilChange(it.toInt()) },
                    valueRange = 5f..50f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFE74C3C), activeTrackColor = Color(0xFFE74C3C))
                )
                Spacer(Modifier.height(4.dp))
                Text("Cảnh báo nhiệt độ cao — trên: ${((highTempAlert * 10).toInt() / 10.0)}°C", color = AppTextPrimary, fontSize = 13.sp)
                Slider(
                    value = highTempAlert,
                    onValueChange = onHighTempChange,
                    valueRange = 25f..45f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFE67E22), activeTrackColor = Color(0xFFE67E22))
                )
            }
        }
    }
}

@Composable
private fun PumpHistoryScreen(
    pumpHistory: PumpHistory,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onBack, shape = RoundedCornerShape(10.dp)) {
                Text("← Quay lại")
            }
            Text(
                "📋 Lịch sử tưới nước",
                color = AppAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.height(12.dp))

        if (pumpHistory.entries.isEmpty()) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppCard)
            ) {
                Text(
                    "Chưa có lịch sử tưới.\nESP32 sẽ ghi lại lên Firebase mỗi khi bơm hoàn tất một lần tưới.",
                    color = AppTextSecondary,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 13.sp
                )
            }
            return@Column
        }

        val grouped = pumpHistory.entries.groupBy { it.date }
        grouped.keys.sortedDescending().forEach { date ->
            Text(
                "📅 $date",
                color = AppTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            val sessions = grouped[date] ?: emptyList()
            sessions.forEachIndexed { idx, entry ->
                PumpHistoryEntryCard(entry)
                if (idx != sessions.lastIndex) Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PumpHistoryEntryCard(entry: PumpLogEntry) {
    val mins = entry.durationSeconds / 60
    val secs = entry.durationSeconds % 60
    val durationText = if (mins > 0) "${mins} phút ${secs}s" else "${secs} giây"
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D2137)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("💧 ${entry.startTime} → ${entry.endTime}", color = AppTextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    durationText,
                    color = AppAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Thời lượng: $durationText",
                color = AppTextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
