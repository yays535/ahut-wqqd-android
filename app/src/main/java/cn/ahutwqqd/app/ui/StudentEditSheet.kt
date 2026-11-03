package cn.ahutwqqd.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cn.ahutwqqd.app.data.Crypto
import cn.ahutwqqd.app.data.LocationMode
import cn.ahutwqqd.app.data.SignStatus
import cn.ahutwqqd.app.data.Student
import cn.ahutwqqd.app.data.StudentStore
import cn.ahutwqqd.app.util.LocationHelper
import kotlinx.coroutines.launch
import java.util.UUID

private val fieldShape = RoundedCornerShape(14.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentEditSheet(
    initial: Student?,
    existing: List<Student>,
    onDismiss: () -> Unit,
    onSave: (Student) -> Unit,
    onDelete: (Student) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var idText by remember { mutableStateOf(initial?.studentId?.toString().orEmpty()) }
    var useDefaultPassword by remember { mutableStateOf(initial?.passwordCipher == null) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var locationMode by remember { mutableStateOf(initial?.locationMode ?: LocationMode.AUTO) }
    var latitude by remember { mutableStateOf(initial?.latitude) }
    var longitude by remember { mutableStateOf(initial?.longitude) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: false) }
    var fetching by remember { mutableStateOf(false) }
    var locationTip by remember { mutableStateOf<String?>(null) }
    var showManualDialog by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var lockedDeleteTip by remember { mutableStateOf(false) }

    val parsedId = idText.toLongOrNull()
    // 当日已签成功的学生，当日锁学号 + 锁删除，防止改学号后再签一次
    val lockedToday = initial != null &&
        initial.lastSuccessDate == cn.ahutwqqd.app.data.SignRepository.today()
    // 学号在列表内唯一：编辑自己时不能算作重复
    val duplicatedId = parsedId != null && existing.any {
        it.id != initial?.id && it.studentId == parsedId
    }
    // 选了「自定义」却没有坐标的话，保存下去等于还在用宿舍位置，界面却标着「自定义位置」，
    // 这种半截状态最难排查，直接挡在保存按钮上
    val locationReady = locationMode == LocationMode.AUTO ||
        (latitude != null && longitude != null)
    val canSave = parsedId != null && parsedId > 0 && !duplicatedId && locationReady &&
        (useDefaultPassword || password.isNotBlank() || initial?.passwordCipher != null)

    fun captureCurrentLocation() {
        scope.launch {
            fetching = true
            locationTip = null
            val point = LocationHelper.current(context)
            fetching = false
            if (point == null) {
                locationTip = "没有取到定位，请到空旷处重试，或手动输入经纬度"
            } else {
                latitude = point.first
                longitude = point.second
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            captureCurrentLocation()
        } else {
            locationTip = "未授予定位权限，可改用手动输入"
        }
    }

    fun requestCurrentLocation() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            captureCurrentLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (initial == null) "添加学生" else "编辑学生",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("姓名（可不填，签到后自动获取）") },
                singleLine = true,
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = idText,
                onValueChange = { input -> idText = input.filter { it.isDigit() }.take(12) },
                label = { Text("学号") },
                singleLine = true,
                enabled = !lockedToday,
                isError = idText.isNotBlank() && (parsedId == null || duplicatedId),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = when {
                    lockedToday -> {
                        { Text("已成功签到的学生当日不可修改学号，如需修改请明日再试") }
                    }
                    idText.isNotBlank() && parsedId == null -> {
                        { Text("学号必须为数字") }
                    }
                    duplicatedId -> {
                        { Text("这个学号已经在列表里了，不能重复添加") }
                    }
                    else -> null
                },
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "使用学校初始密码",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "没改过密码就保持开启",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "初始密码 ${StudentStore.DEFAULT_PASSWORD}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Switch(
                    checked = useDefaultPassword,
                    onCheckedChange = { useDefaultPassword = it }
                )
            }

            if (!useDefaultPassword) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (initial?.passwordCipher != null) "新密码（留空则不变）" else "密码") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (passwordVisible) KeyboardType.Text
                        else KeyboardType.Password
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            EyeIcon(
                                visible = passwordVisible,
                                modifier = Modifier.size(22.dp),
                                tint = if (passwordVisible) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (initial?.passwordCipher != null) "已保存过密码，留空则保持原密码不变"
                    else "密码用 Android Keystore 加密后存在本机，不会上传到任何服务器",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "签到位置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "通常情况下，自动获取即可",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "默认用学校登记的宿舍位置，每次签到在该位置 100 米内随机偏移",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = locationMode == LocationMode.AUTO,
                    onClick = {
                        locationMode = LocationMode.AUTO
                        locationTip = null
                    },
                    label = { Text("自动获取") }
                )
                FilterChip(
                    selected = locationMode == LocationMode.CUSTOM,
                    onClick = { locationMode = LocationMode.CUSTOM },
                    label = { Text("自定义") }
                )
            }

            if (locationMode == LocationMode.CUSTOM) {
                Spacer(Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (latitude != null && longitude != null) {
                                Text(
                                    formatPoint(latitude!!, longitude!!),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "签到时会以此为圆心偏移",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    "还没有选择位置",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (fetching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = ::requestCurrentLocation,
                        enabled = !fetching,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("使用当前位置")
                    }
                    OutlinedButton(
                        onClick = { showManualDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("手动输入")
                    }
                }

                TextButton(
                    onClick = {
                        locationMode = LocationMode.AUTO
                        latitude = null
                        longitude = null
                        locationTip = null
                    }
                ) {
                    Text("取消自定义，恢复自动获取")
                }

                locationTip?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (!locationReady) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "先取一次位置或手动输入经纬度，才能保存",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "参与自动签到",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "关闭后只在手动点「签到」时执行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (initial != null) {
                    // 当日已签的学生锁删除；否则删除会连密码和自定义位置一起丢掉，先问一句再动手
                    TextButton(
                        onClick = {
                            if (lockedToday) lockedDeleteTip = true
                            else confirmingDelete = true
                        }
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val cipher = when {
                            useDefaultPassword -> null
                            password.isNotBlank() -> Crypto.encrypt(password)
                            else -> initial?.passwordCipher
                        }
                        onSave(
                            Student(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                studentId = parsedId ?: return@Button,
                                passwordCipher = cipher,
                                locationMode = locationMode,
                                latitude = if (locationMode == LocationMode.CUSTOM) latitude else null,
                                longitude = if (locationMode == LocationMode.CUSTOM) longitude else null,
                                enabled = enabled,
                                status = initial?.status ?: SignStatus.NONE,
                                lastMessage = initial?.lastMessage.orEmpty(),
                                lastRunAt = initial?.lastRunAt ?: 0L,
                                lastSuccessDate = initial?.lastSuccessDate.orEmpty()
                            )
                        )
                    },
                    enabled = canSave,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
                ) {
                    Text("保存")
                }
            }
        }
    }

    if (showManualDialog) {
        ManualLocationDialog(
            initialLat = latitude,
            initialLng = longitude,
            onDismiss = { showManualDialog = false },
            onConfirm = { lat, lng ->
                latitude = lat
                longitude = lng
                locationTip = null
                showManualDialog = false
            }
        )
    }

    val deleting = initial
    if (lockedDeleteTip) {
        AlertDialog(
            onDismissRequest = { lockedDeleteTip = false },
            title = { Text("暂不可删除") },
            text = { Text("已成功签到的学生当日不可修改学号和删除，如需修改请明日再试") },
            confirmButton = {
                TextButton(onClick = { lockedDeleteTip = false }) { Text("知道了") }
            }
        )
    }
    if (confirmingDelete && deleting != null) {
        // 没填姓名时 displayName 就是学号，拼在一起会重复，所以先判断再去重
        val who = if (deleting.name.isBlank()) {
            "这个学生（学号 ${deleting.studentId}）"
        } else {
            "${deleting.name}（学号 ${deleting.studentId}）"
        }
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("删除这个学生？") },
            text = {
                Text(
                    "${who}的密码和位置配置会一起删除，签到历史里已有的记录仍会保留。此操作无法撤销。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete(deleting)
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("取消") }
            }
        )
    }
}

private fun formatPoint(lat: Double, lng: Double): String =
    String.format(java.util.Locale.US, "%.6f, %.6f", lat, lng)

@Composable
private fun ManualLocationDialog(
    initialLat: Double?,
    initialLng: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit
) {
    var latText by remember {
        mutableStateOf(initialLat?.let { String.format(java.util.Locale.US, "%.6f", it) }.orEmpty())
    }
    var lngText by remember {
        mutableStateOf(initialLng?.let { String.format(java.util.Locale.US, "%.6f", it) }.orEmpty())
    }
    val lat = latText.toDoubleOrNull()
    val lng = lngText.toDoubleOrNull()
    val valid = lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动输入经纬度") },
        text = {
            Column {
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it.trim() },
                    label = { Text("纬度，如 31.234567") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = lngText,
                    onValueChange = { lngText = it.trim() },
                    label = { Text("经度，如 117.123456") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (lat != null && lng != null) onConfirm(lat, lng) },
                enabled = valid
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
