package com.greenie.auto.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
    var showScheduleSettings by remember { mutableStateOf(false) }
    var showAlertSettings by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var pumpHistory by remember { mutableStateOf(PumpHistory(entries = emptyList())) }
    var lowSoilAlert by remember { mutableStateOf(20) }
    var highTempAlert by remember { mutableStateOf(35f) }
    var waterSchedule by remember { mutableStateOf(WaterSchedule()) }
    var scheduleSaving by remember { mutableStateOf(false) }
    var scheduleMessage by remember { mutableStateOf<String?>(null) }
    var scheduleDirty by remember { mutableStateOf(false) }
    var mockSensorCount by remember { mutableStateOf((repository as? MockSoilRepository)?.getMockActiveSensors() ?: 1) }
    var loading by remember { mutableStateOf(true) }
    var sourceLabel by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    fun openPanel(panel: String) {
        showMonthlyGrid = false
        showPumpHistory = false
        showScheduleSettings = false
        showAlertSettings = false
        if (panel != "schedule") {
            scheduleDirty = false
        }
        when (panel) {
            "monthly" -> showMonthlyGrid = true
            "history" -> showPumpHistory = true
            "schedule" -> {
                scheduleDirty = false
                showScheduleSettings = true
            }
            "alert" -> showAlertSettings = true
        }
    }

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

    LaunchedEffect(Unit) {
        while (true) {
            repository.fetchWaterSchedule()
                .onSuccess {
                    if (!showScheduleSettings || !scheduleDirty) {
                        waterSchedule = it
                        scheduleDirty = false
                    }
                    if (scheduleMessage == null) {
                        scheduleMessage = "Đã tải lịch tưới"
                    }
                }
                .onFailure {
                    logError("SoilRepository", "waterSchedule: ${it.message}")
                    if (scheduleMessage == null) {
                        scheduleMessage = "Chưa tải được lịch tưới"
                    }
                }
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
                    ),
                    actions = {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Text("⋮", color = AppTextPrimary, fontSize = 20.sp)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("📈 Biểu đồ tháng") },
                                    onClick = {
                                        menuExpanded = false
                                        openPanel("monthly")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("📋 Lịch sử tưới") },
                                    onClick = {
                                        menuExpanded = false
                                        openPanel("history")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("⏰ Lịch tưới tự động") },
                                    onClick = {
                                        menuExpanded = false
                                        openPanel("schedule")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("🔔 Cảnh báo ngưỡng") },
                                    onClick = {
                                        menuExpanded = false
                                        openPanel("alert")
                                    }
                                )
                            }
                        }
                    }
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
                if (showScheduleSettings) {
                    ScheduleSettingsScreen(
                        schedule = waterSchedule,
                        runningNow = soilData?.scheduleRunning == true,
                        saving = scheduleSaving,
                        message = scheduleMessage,
                        onChange = {
                            waterSchedule = it
                            scheduleDirty = true
                        },
                        onSave = { scheduleToSave ->
                            scheduleSaving = true
                            scheduleMessage = null
                            val normalized = normalizeScheduleForSave(scheduleToSave)
                            scope.launch {
                                repository.setWaterSchedule(normalized)
                                    .onSuccess {
                                        scheduleSaving = false
                                        scheduleDirty = false
                                        waterSchedule = normalized
                                        scheduleMessage = "✅ Đã lưu lịch: ${normalized.timesCsv.ifBlank { "06:00" }} • ${normalized.durationMin} phút"
                                    }
                                    .onFailure { err ->
                                        scheduleSaving = false
                                        val msg = err.message ?: "Không lưu được lịch tưới"
                                        scheduleMessage = "❌ $msg"
                                        warning = toUserFacingMessage(msg, hasCachedData = true)
                                    }
                            }
                        },
                        onBack = { showScheduleSettings = false }
                    )
                    return@Box
                }

                if (showAlertSettings) {
                    AlertSettingsScreen(
                        lowSoilAlert = lowSoilAlert,
                        highTempAlert = highTempAlert,
                        onLowSoilChange = { lowSoilAlert = it },
                        onHighTempChange = { highTempAlert = it },
                        onBack = { showAlertSettings = false }
                    )
                    return@Box
                }

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
                            lowSoilAlert = lowSoilAlert,
                            highTempAlert = highTempAlert,
                            repository = repository,
                            scope = scope,
                            onError = {
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
    lowSoilAlert: Int,
    highTempAlert: Float,
    repository: SoilRepository,
    scope: CoroutineScope,
    onError: (String) -> Unit,
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
                        onError(it.message ?: "Không gửi được lệnh bơm")
                        logError("SoilRepository", it.message)
                    }
            }
        }
    )
}

@Composable
private fun ScheduleSettingsScreen(
    schedule: WaterSchedule,
    runningNow: Boolean,
    saving: Boolean,
    message: String?,
    onChange: (WaterSchedule) -> Unit,
    onSave: (WaterSchedule) -> Unit,
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
                "⏰ Lịch tưới tự động",
                color = AppAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        AutoWaterScheduleCard(
            schedule = schedule,
            runningNow = runningNow,
            saving = saving,
            message = message,
            onChange = onChange,
            onSave = onSave,
        )
    }
}

@Composable
private fun AlertSettingsScreen(
    lowSoilAlert: Int,
    highTempAlert: Float,
    onLowSoilChange: (Int) -> Unit,
    onHighTempChange: (Float) -> Unit,
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
                "🔔 Cảnh báo ngưỡng",
                color = AppAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        AlertSettingsCard(
            lowSoilAlert = lowSoilAlert,
            highTempAlert = highTempAlert,
            onLowSoilChange = onLowSoilChange,
            onHighTempChange = onHighTempChange,
        )
    }
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
    val allDays = monthlyStats.days
    var selectedDays by remember { mutableStateOf(30) }
    var panOffsetPx by remember { mutableStateOf(0f) }
    var zoomScale by remember { mutableStateOf(1f) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(selectedDays) {
        panOffsetPx = 0f
        zoomScale = 1f
    }

    val displayDays = allDays.takeLast(selectedDays.coerceAtMost(allDays.size.coerceAtLeast(1)))
    val tempValues = displayDays.map { it.avgTemp }
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
            // Title + day-range selector
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("📈 Biểu đồ đường", color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(7, 14, 30).forEach { n ->
                        val isSelected = selectedDays == n
                        Surface(
                            onClick = { selectedDays = n },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) AppAccent else AppTrack,
                            modifier = Modifier.height(26.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    "${n}N",
                                    color = if (isSelected) AppCard else AppTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🟧 Nhiệt độ", color = Color(0xFFE67E22), fontSize = 12.sp)
                Text("🟦 Độ ẩm đất", color = Color(0xFF3498DB), fontSize = 12.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Chụm/giãn để zoom • Kéo ngang để cuộn",
                color = AppTextSecondary.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
            Spacer(Modifier.height(8.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clipToBounds()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                            panOffsetPx += -pan.x
                        }
                    }
            ) {
                val yLabelW = 38f
                val left = yLabelW
                val right = size.width - 8f
                val top = 10f
                val xLabelH = 28f
                val bottom = size.height - xLabelH
                val chartWidth = (right - left).coerceAtLeast(1f)
                val chartHeight = (bottom - top).coerceAtLeast(1f)
                val count = displayDays.size

                val totalW = chartWidth * zoomScale
                val stepX = if (count <= 1) 0f else totalW / (count - 1).toFloat()
                val maxPan = (totalW - chartWidth).coerceAtLeast(0f)
                val pan = panOffsetPx.coerceIn(0f, maxPan)

                // Axes
                drawLine(AppTrack, Offset(left, bottom), Offset(right, bottom), 2f)
                drawLine(AppTrack, Offset(left, top), Offset(left, bottom), 2f)

                // Horizontal grid lines + Y-axis temp labels
                for (i in 0..4) {
                    val y = bottom - chartHeight * i / 4f
                    drawLine(
                        color = AppTrack.copy(alpha = if (i == 0) 0f else 0.4f),
                        start = Offset(left, y),
                        end = Offset(right, y),
                        strokeWidth = 1f
                    )
                    val tempVal = minTemp + (maxTemp - minTemp) * i / 4f
                    val labelStr = "${(tempVal * 10).toInt() / 10.0}"
                    drawText(
                        textMeasurer = textMeasurer,
                        text = labelStr,
                        topLeft = Offset(0f, y - 7f),
                        style = TextStyle(
                            color = Color(0xFFE67E22).copy(alpha = 0.75f),
                            fontSize = 8.5.sp
                        )
                    )
                }

                // Data lines + dots + X-axis labels
                var prevTemp: Offset? = null
                var prevSoil: Offset? = null
                val labelStep = when {
                    count <= 7 -> 1
                    count <= 14 -> 2
                    else -> 5
                }

                displayDays.forEachIndexed { index, day ->
                    val x = left + index * stepX - pan

                    val tempRatio = ((day.avgTemp - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)
                    val tempY = bottom - tempRatio * chartHeight
                    val tempPoint = Offset(x, tempY)

                    val soilPoint = if (day.avgSoil >= 0f) {
                        val soilY = bottom - (day.avgSoil / 100f).coerceIn(0f, 1f) * chartHeight
                        Offset(x, soilY)
                    } else null

                    // Draw lines (canvas clips at bounds)
                    prevTemp?.let { drawLine(Color(0xFFE67E22), it, tempPoint, 2.5f) }
                    soilPoint?.let { sp -> prevSoil?.let { drawLine(Color(0xFF3498DB), it, sp, 2.5f) } }

                    // Dots and labels only for visible area
                    if (x >= left - 4f && x <= right + 4f) {
                        drawCircle(Color(0xFFE67E22), radius = 4f, center = tempPoint)
                        soilPoint?.let { drawCircle(Color(0xFF3498DB), radius = 4f, center = it) }

                        if (index % labelStep == 0 && x >= left) {
                            val dayLabel = day.day.trimStart('0').ifEmpty { "0" }
                            val monthPart = monthlyStats.monthKey.substringAfter('-').takeIf { it.isNotEmpty() }
                            val label = if (monthPart != null) "$dayLabel/$monthPart" else dayLabel
                            drawText(
                                textMeasurer = textMeasurer,
                                text = label,
                                topLeft = Offset(x - 10f, bottom + 5f),
                                style = TextStyle(
                                    color = AppTextSecondary,
                                    fontSize = 8.5.sp
                                )
                            )
                        }
                    }

                    prevTemp = tempPoint
                    prevSoil = soilPoint
                }
            }

            Spacer(Modifier.height(4.dp))
            val monthLabel = monthlyStats.monthKey.ifBlank { "N/A" }
            Text(
                "$monthLabel • ${displayDays.size}/${allDays.size} ngày • Nhiệt: ${(minTemp * 10).toInt() / 10.0}–${(maxTemp * 10).toInt() / 10.0}°C",
                color = AppTextSecondary,
                fontSize = 10.sp
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
private fun AutoWaterScheduleCard(
    schedule: WaterSchedule,
    runningNow: Boolean,
    saving: Boolean,
    message: String?,
    onChange: (WaterSchedule) -> Unit,
    onSave: (WaterSchedule) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        val slotList = parseTimesCsv(schedule.timesCsv)
        val weekdays = parseWeekdaysCsv(schedule.weekdaysCsv)

        Column(Modifier.padding(20.dp)) {
            Text("⏰ Lịch tưới tự động", color = AppTextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (schedule.enabled) "Đang bật" else "Đang tắt",
                    color = if (schedule.enabled) Color(0xFF27AE60) else AppTextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = { onChange(schedule.copy(enabled = it)) }
                )
            }

            if (runningNow) {
                Spacer(Modifier.height(8.dp))
                Text("🚿 Đang tưới theo lịch", color = AppAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))
            Text("Ngày chạy (T2..CN)", color = AppTextPrimary, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val labels = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
                for (idx in labels.indices) {
                    val day = idx + 1
                    val selected = weekdays.contains(day)
                    OutlinedButton(
                        onClick = {
                            val next = weekdays.toMutableSet()
                            if (selected) next.remove(day) else next.add(day)
                            onChange(schedule.copy(weekdaysCsv = toWeekdaysCsv(next)))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) Color(0xFF244C3A) else Color.Transparent,
                            contentColor = if (selected) AppAccent else AppTextSecondary,
                        )
                    ) {
                        Text(labels[idx], fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Mốc giờ tưới (${slotList.size})", color = AppTextPrimary, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            slotList.forEachIndexed { index, slot ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101A35))
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mốc ${index + 1}: ${formatTimeSlot(slot)}", color = AppTextPrimary, fontWeight = FontWeight.SemiBold)
                            if (slotList.size > 1) {
                                OutlinedButton(
                                    onClick = {
                                        val next = slotList.toMutableList().apply { removeAt(index) }
                                        onChange(schedule.copy(timesCsv = toTimesCsv(next)))
                                    },
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Xóa", fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val next = slotList.toMutableList()
                                    next[index] = next[index].copy(hour = (next[index].hour + 23) % 24)
                                    onChange(schedule.copy(timesCsv = toTimesCsv(next)))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                            ) { Text("-1h", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                            OutlinedButton(
                                onClick = {
                                    val next = slotList.toMutableList()
                                    next[index] = next[index].copy(hour = (next[index].hour + 1) % 24)
                                    onChange(schedule.copy(timesCsv = toTimesCsv(next)))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                            ) { Text("+1h", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                            OutlinedButton(
                                onClick = {
                                    val next = slotList.toMutableList()
                                    val mins = (next[index].hour * 60 + next[index].minute + 24 * 60 - 10) % (24 * 60)
                                    next[index] = TimeSlot(hour = mins / 60, minute = mins % 60)
                                    onChange(schedule.copy(timesCsv = toTimesCsv(next)))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                            ) { Text("-10p", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                            OutlinedButton(
                                onClick = {
                                    val next = slotList.toMutableList()
                                    val mins = (next[index].hour * 60 + next[index].minute + 10) % (24 * 60)
                                    next[index] = TimeSlot(hour = mins / 60, minute = mins % 60)
                                    onChange(schedule.copy(timesCsv = toTimesCsv(next)))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                            ) { Text("+10p", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            OutlinedButton(
                onClick = {
                    val next = slotList.toMutableList()
                    if (next.size < 8) {
                        val base = next.lastOrNull() ?: TimeSlot(6, 0)
                        val mins = (base.hour * 60 + base.minute + 60) % (24 * 60)
                        next.add(TimeSlot(mins / 60, mins % 60))
                        onChange(schedule.copy(timesCsv = toTimesCsv(next)))
                    }
                },
                enabled = slotList.size < 8,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (slotList.isEmpty()) "+ Thêm mốc giờ đầu tiên" else "+ Thêm mốc giờ", fontSize = 12.sp)
            }

            Spacer(Modifier.height(4.dp))
            Text("Thời gian tưới: ${schedule.durationMin} phút", color = AppTextPrimary, fontSize = 13.sp)
            Slider(
                value = schedule.durationMin.toFloat(),
                onValueChange = { onChange(schedule.copy(durationMin = it.toInt().coerceIn(1, 60))) },
                valueRange = 1f..60f,
                steps = 59,
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSave(normalizeScheduleForSave(schedule)) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Text("Lưu lịch tưới", fontWeight = FontWeight.Bold)
                }
            }

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = if (it.startsWith("✅")) Color(0xFF2ECC71) else AppTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

private data class TimeSlot(val hour: Int, val minute: Int)

private fun parseTimesCsv(csv: String): List<TimeSlot> {
    val parsed = csv
        .split(',')
        .mapNotNull { token ->
            val clean = token.trim()
            if (!clean.contains(':')) return@mapNotNull null
            val hh = clean.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
            val mm = clean.substringAfter(':').toIntOrNull() ?: return@mapNotNull null
            if (hh !in 0..23 || mm !in 0..59) return@mapNotNull null
            TimeSlot(hh, mm)
        }
        .distinctBy { it.hour * 60 + it.minute }
        .sortedBy { it.hour * 60 + it.minute }

    return parsed
}

private fun toTimesCsv(slots: List<TimeSlot>): String =
    slots
        .distinctBy { it.hour * 60 + it.minute }
        .sortedBy { it.hour * 60 + it.minute }
        .joinToString(",") { formatTimeSlot(it) }

private fun formatTimeSlot(slot: TimeSlot): String =
    "${slot.hour.toString().padStart(2, '0')}:${slot.minute.toString().padStart(2, '0')}"

private fun parseWeekdaysCsv(csv: String): Set<Int> {
    val set = csv
        .split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it in 1..7 }
        .toSet()
    return if (set.isEmpty()) (1..7).toSet() else set
}

private fun toWeekdaysCsv(days: Set<Int>): String {
    val normalized = days.filter { it in 1..7 }.sorted()
    return if (normalized.isEmpty()) "1,2,3,4,5,6,7" else normalized.joinToString(",")
}

private fun normalizeScheduleForSave(schedule: WaterSchedule): WaterSchedule {
    val slots = parseTimesCsv(schedule.timesCsv)
    val safeTimes = if (slots.isEmpty()) "06:00" else toTimesCsv(slots)
    val safeDays = toWeekdaysCsv(parseWeekdaysCsv(schedule.weekdaysCsv))
    return schedule.copy(
        durationMin = schedule.durationMin.coerceIn(1, 60),
        timesCsv = safeTimes,
        weekdaysCsv = safeDays,
    )
}

@Composable
private fun AlertSettingsCard(
    lowSoilAlert: Int,
    highTempAlert: Float,
    onLowSoilChange: (Int) -> Unit,
    onHighTempChange: (Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("🔔 Cảnh báo ngưỡng", color = AppTextSecondary, fontSize = 14.sp)

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = { expanded = !expanded },
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
