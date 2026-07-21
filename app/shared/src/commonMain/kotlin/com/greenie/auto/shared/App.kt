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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GreenieApp() {
    var espIp by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    var useMock by remember { mutableStateOf(false) }
    
    if (!connected) {
        IpInputScreen(
            onConnect = { ip, mock ->
                espIp = ip
                useMock = mock
                connected = true
            }
        )
    } else {
        DashboardScreen(espIp, useMock)
    }
}

@Composable
private fun IpInputScreen(onConnect: (String, Boolean) -> Unit) {
    var ip by remember { mutableStateOf("") }
    var useMock by remember { mutableStateOf(false) }
    
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                "🌱 greenie-auto",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00D4AA)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (useMock) "🧪 Chế độ Mock (Test)" else "Nhập IP của ESP32",
                color = Color(0xFFAAAAAA),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(24.dp))
            
            if (!useMock) {
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Ví dụ: 192.168.1.105", color = Color(0xFFAAAAAA)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.White)
                )
                Spacer(Modifier.height(16.dp))
            }
            
            Button(
                onClick = { onConnect(ip.ifBlank { "mock" }, useMock) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = useMock || ip.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (useMock) "🧪 Test Mock" else "Kết nối", fontWeight = FontWeight.Bold)
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
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00D4AA))
                )
            }
            
            Spacer(Modifier.height(4.dp))
            Text(
                if (useMock) "✅ Dùng dữ liệu giả lập (test mà không cần ESP32)"
                else "Lấy IP từ Serial Monitor ESP32 sau khi upload",
                color = Color(0xFF666666),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun DashboardScreen(espIp: String, useMock: Boolean) {
    var soilData by remember { mutableStateOf<SoilData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val repository = remember { 
        if (useMock) MockSoilRepository() else SoilRepository("http://$espIp")
    }
    
    LaunchedEffect(Unit) {
        scope.launch {
            while (true) {
                repository.fetchData()
                    .onSuccess { soilData = it; error = null; loading = false }
                    .onFailure {
                        error = it.message
                        loading = false
                        logError("SoilRepository", it.message)
                    }
                delay(2000)
            }
        }
    }
    
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A2E))
                .padding(16.dp)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "🌱 greenie-auto",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00D4AA),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                
                when {
                    loading -> CircularProgressIndicator(color = Color(0xFF00D4AA))
                    error != null -> ErrorCard(error!!)
                    soilData != null -> SensorCards(soilData!!, repository, scope)
                }
                
                Spacer(Modifier.height(16.dp))
                Text(
                    "Tự động làm mới 2 giây/lần",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SensorCards(data: SoilData, repository: SoilRepository, scope: CoroutineScope) {
    var pumpLoading by remember { mutableStateOf(false) }
    
    SensorCard("Cảm biến 1", data.sensor1)
    Spacer(Modifier.height(12.dp))
    SensorCard("Cảm biến 2", data.sensor2)
    Spacer(Modifier.height(12.dp))
    AverageCard(data.average)
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
                        logError("SoilRepository", it.message)
                    }
            }
        }
    )
}

@Composable
private fun SensorCard(title: String, pct: Int) {
    val color = when {
        pct < 20 -> Color(0xFFE74C3C)
        pct < 60 -> Color(0xFFF39C12)
        else -> Color(0xFF27AE60)
    }
    val label = when {
        pct < 20 -> "🔴 Khô — Cần tưới!"
        pct < 60 -> "🟡 Vừa — Độ ẩm tốt"
        else -> "🟢 Ướt — Đủ nước"
    }
    
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = Color(0xFFAAAAAA), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text("$pct%", color = color, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { pct / 100f },
                Modifier.fillMaxWidth().height(8.dp),
                color = color,
                trackColor = Color(0xFF2C2C4E)
            )
            Spacer(Modifier.height(6.dp))
            Text(label, color = Color(0xFFCCCCCC), fontSize = 13.sp)
        }
    }
}

@Composable
private fun AverageCard(avg: Int) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))
    ) {
        Row(
            Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Trung bình", color = Color(0xFFAAAAAA))
            Text("$avg%", color = Color(0xFF00D4AA), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PumpCard(isRunning: Boolean, loading: Boolean, onToggle: (Boolean) -> Unit) {
    val btnColor = if (isRunning) Color(0xFFE74C3C) else Color(0xFF27AE60)
    val btnLabel = if (isRunning) "Tắt bơm" else "Bật bơm"
    
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Máy bơm", color = Color(0xFFAAAAAA))
                Text(
                    if (isRunning) "⚙️ Đang chạy" else "⏸ Đứng",
                    color = if (isRunning) Color(0xFF27AE60) else Color(0xFFAAAAAA),
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
private fun ErrorCard(message: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3D1515))
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("❌ Lỗi kết nối", color = Color(0xFFE74C3C), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Color(0xFFCCCCCC), fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text("Kiểm tra ESP32 đã bật và cùng WiFi", color = Color(0xFF888888), fontSize = 12.sp)
        }
    }
}
