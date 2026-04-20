package com.lionfit.app.ui.sleep

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog as ComposeDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.lionfit.app.R
import com.lionfit.app.data.database.AppDatabase
import com.lionfit.app.data.database.SupabaseManager
import com.lionfit.app.data.model.SleepRecord
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID

val SleepBarColor = ComposeColor(0xFFBDA7EF)

class SleepFragment : Fragment(R.layout.fragment_sleeping) {

    private val sleepRecords = mutableStateListOf<SleepRecord>()
    private var currentWeekStart by mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)))
    
    private val db by lazy { AppDatabase.getDatabase(requireContext()) }
    private val userId by lazy { SupabaseManager.client.auth.currentUserOrNull()?.id ?: "" }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // โหลดข้อมูลจาก Room Database (กรองตาม User ID)
        observeSleepData()

        val composeView = view.findViewById<ComposeView>(R.id.chart_compose_view)
        composeView?.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            
            @SuppressLint("ClickableViewAccessibility")
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        v.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }

            setContent {
                MaterialTheme {
                    var showAddDialog by remember { mutableStateOf(false) }

                    LaunchedEffect(sleepRecords.toList(), currentWeekStart) {
                        updateXmlStats(view, sleepRecords, currentWeekStart)
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        SleepChartContent(records = sleepRecords, startOfWeek = currentWeekStart)

                        if (showAddDialog) {
                            AddSleepDialog(
                                onDismiss = { showAddDialog = false },
                                onSave = { startDate, startTime, endTime ->
                                    createNewSleepRecord(startDate, startTime, endTime)
                                    showAddDialog = false
                                }
                            )
                        }
                    }
                    
                    DisposableEffect(Unit) {
                        val btnAdd = view.findViewById<View>(R.id.btn_add_sleep_xml)
                        btnAdd?.setOnClickListener { showAddDialog = true }
                        onDispose { btnAdd?.setOnClickListener(null) }
                    }
                }
            }
        }
        
        view.findViewById<ImageButton>(R.id.btn_prev_week)?.setOnClickListener {
            currentWeekStart = currentWeekStart.minusWeeks(1)
        }

        view.findViewById<ImageButton>(R.id.btn_next_week)?.setOnClickListener {
            currentWeekStart = currentWeekStart.plusWeeks(1)
        }

        view.findViewById<ImageButton>(R.id.btn_info)?.setOnClickListener { showInfoDialog() }
        
        view.findViewById<TextView>(R.id.tv_date_range)?.setOnClickListener {
            val picker = DatePickerDialog(requireContext(), { _, y, m, d ->
                val selectedDate = LocalDate.of(y, m + 1, d)
                currentWeekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            }, currentWeekStart.year, currentWeekStart.monthValue - 1, currentWeekStart.dayOfMonth)
            picker.show()
        }
    }

    private fun observeSleepData() {
        if (userId.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            // แก้ไข: เรียกใช้ getSleepRecordsByUser เพื่อกรองข้อมูลตาม User ID
            db.sleepDao().getSleepRecordsByUser(userId).collectLatest { records ->
                sleepRecords.clear()
                sleepRecords.addAll(records)
            }
        }
    }

    private fun createNewSleepRecord(startDate: LocalDate, startTime: LocalTime, endTime: LocalTime) {
        if (userId.isEmpty()) {
            Toast.makeText(requireContext(), "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        val startDT = startDate.atTime(startTime)
        var endDT = startDate.atTime(endTime)
        if (endTime.isBefore(startTime)) {
            endDT = endDT.plusDays(1)
        }

        val duration = Duration.between(startDT, endDT)
        val totalHours = duration.toMinutes() / 60.0

        val newRecord = SleepRecord(
            id = UUID.randomUUID().toString(),
            userId = userId,
            dateLogged = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            bedTimeInMillis = startDT.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            wakeTimeInMillis = endDT.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            totalHoursSlept = totalHours
        )

        checkOverlapAndSave(newRecord)
    }

    private fun checkOverlapAndSave(newRecord: SleepRecord) {
        // ค้นหาว่ามีรายการที่ทับซ้อนกันหรือไม่
        val overlapping = sleepRecords.filter { existing ->
            newRecord.bedTimeInMillis < existing.wakeTimeInMillis && newRecord.wakeTimeInMillis > existing.bedTimeInMillis
        }
        
        if (overlapping.isNotEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle("แจ้งเตือน")
                .setMessage("คุณได้เลือกเวลาดังกล่าวแล้ว ต้องการบันทึกต่อหรือไม่")
                .setPositiveButton("ตกลง") { _, _ ->
                    // ลบรายการเก่าที่ทับซ้อนทิ้ง แล้วบันทึกอันใหม่แทนที่
                    saveRecord(newRecord, overlapping)
                }
                .setNegativeButton("ยกเลิก") { d, _ -> d.dismiss() }
                .show()
        } else {
            saveRecord(newRecord, emptyList())
        }
    }

    private fun saveRecord(newRecord: SleepRecord, overlappingRecords: List<SleepRecord>) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. ลบรายการเก่าที่ทับซ้อนออก (ทั้ง Local และ Cloud)
                overlappingRecords.forEach { oldRecord ->
                    db.sleepDao().deleteSleepRecord(oldRecord)
                    SupabaseManager.deleteSleepRecord(oldRecord.id)
                }

                // 2. บันทึกรายการใหม่ลง Room (Local)
                db.sleepDao().insertSleepRecord(newRecord)

                // 3. ส่งรายการใหม่ขึ้น Supabase (Cloud)
                val success = SupabaseManager.saveSleepRecord(newRecord)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(requireContext(), "Updated successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Updated locally only", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error saving data", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateXmlStats(rootView: View, records: List<SleepRecord>, startOfWeek: LocalDate) {
        val endOfWeek = startOfWeek.plusDays(6)
        val weekStartMillis = startOfWeek.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val weekEndMillis = endOfWeek.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val recordsInWeek = records.filter {
            it.bedTimeInMillis < weekEndMillis && it.wakeTimeInMillis > weekStartMillis
        }

        var totalMinutesInWeek = 0L
        recordsInWeek.forEach { record ->
            val actualStart = maxOf(record.bedTimeInMillis, weekStartMillis)
            val actualEnd = minOf(record.wakeTimeInMillis, weekEndMillis)
            if (actualStart < actualEnd) {
                totalMinutesInWeek += (actualEnd - actualStart) / (1000 * 60)
            }
        }

        val avgTotalMinutes = if (recordsInWeek.isNotEmpty()) totalMinutesInWeek / 7 else 0
        rootView.findViewById<TextView>(R.id.tv_avg_hours)?.text = (avgTotalMinutes / 60).toString()
        rootView.findViewById<TextView>(R.id.tv_avg_minutes)?.text = (avgTotalMinutes % 60).toString()
        rootView.findViewById<TextView>(R.id.tv_date_range)?.text = 
            "${startOfWeek.format(DateTimeFormatter.ofPattern("dd"))} - ${endOfWeek.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}"
    }

    private fun showInfoDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Information")
            .setMessage("เลือกเวลานอนของคุณ กราฟนี้จะแสดงชั่วโมงการนอนเฉลี่ยของคุณ 1 สัปดาห์")
            .setPositiveButton("OK") { d, _ -> d.dismiss() }
            .show()
    }

    @Composable
    private fun SleepChartContent(records: List<SleepRecord>, startOfWeek: LocalDate) {
        val daysLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val hourHeight = 45.dp 
        
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Box(modifier = Modifier.fillMaxWidth().height(hourHeight * 24).padding(vertical = 16.dp, horizontal = 8.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        repeat(25) { Box(modifier = Modifier.height(hourHeight)) { HorizontalDivider(color = ComposeColor.Gray.copy(alpha = 0.1f)) } }
                    }
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.width(40.dp)) {
                            repeat(25) { i -> Box(modifier = Modifier.height(hourHeight)) { Text(String.format(Locale.getDefault(), "%02d:00", i), color = ComposeColor.LightGray, fontSize = 10.sp) } }
                        }
                        Row(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.SpaceBetween) {
                            daysLabels.forEachIndexed { index, _ ->
                                val currentDate = startOfWeek.plusDays(index.toLong())
                                val startOfDay = currentDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val endOfDay = currentDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    VerticalDivider(modifier = Modifier.align(Alignment.CenterStart), color = ComposeColor.Gray.copy(alpha = 0.05f))
                                    records.forEach { record ->
                                        // ตรวจสอบว่า record คาบเกี่ยววันปัจจุบันหรือไม่
                                        val drawStart = maxOf(record.bedTimeInMillis, startOfDay)
                                        val drawEnd = minOf(record.wakeTimeInMillis, endOfDay)
                                        
                                        if (drawStart < drawEnd) {
                                            val startLT = LocalDateTime.ofInstant(Instant.ofEpochMilli(drawStart), ZoneId.systemDefault()).toLocalTime()
                                            val endLT = LocalDateTime.ofInstant(Instant.ofEpochMilli(drawEnd), ZoneId.systemDefault()).toLocalTime()
                                            
                                            DrawSleepBar(
                                                startTime = startLT, 
                                                endTime = if (drawEnd == endOfDay) LocalTime.MAX else endLT, 
                                                hourHeight = hourHeight, 
                                                alignment = Alignment.TopCenter
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).padding(start = 40.dp)) {
                daysLabels.forEach { Text(it, color = ComposeColor.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        }
    }

    @Composable
    private fun BoxScope.DrawSleepBar(startTime: LocalTime, endTime: LocalTime, hourHeight: androidx.compose.ui.unit.Dp, alignment: Alignment) {
        val startMin = startTime.toSecondOfDay() / 60f
        val endMin = if (endTime == LocalTime.MAX) 1440f else endTime.toSecondOfDay() / 60f
        val durationMin = endMin - startMin
        val startPos = (startMin / 60f) * hourHeight.value
        val durationHeight = (durationMin / 60f) * hourHeight.value
        Box(modifier = Modifier.padding(top = startPos.dp).width(20.dp).height(durationHeight.dp).align(alignment).clip(RoundedCornerShape(4.dp)).background(SleepBarColor))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AddSleepDialog(onDismiss: () -> Unit, onSave: (LocalDate, LocalTime, LocalTime) -> Unit) {
        var startDate by remember { mutableStateOf(LocalDate.now()) }
        var startTime by remember { mutableStateOf(LocalTime.of(22, 0)) }
        var endTime by remember { mutableStateOf(LocalTime.of(7, 0)) }
        var showDatePicker by remember { mutableStateOf(false) }
        var showTimeWheel by remember { mutableStateOf(false) }
        var pickingStart by remember { mutableStateOf(true) }

        ComposeDialog(onDismissRequest = onDismiss) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = ComposeColor.White)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ADD TIME SLEEP", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(modifier = Modifier.fillMaxWidth().background(ComposeColor(0xFFF2F2F7), RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))) {
                        AddSleepRow("Starts", startDate, startTime) { isDate -> pickingStart = true; if (isDate) showDatePicker = true else showTimeWheel = true }
                        HorizontalDivider(color = ComposeColor.White)
                        val endDate = if (endTime.isBefore(startTime)) startDate.plusDays(1) else startDate
                        AddSleepRow("Ends", endDate, endTime) { isDate -> pickingStart = false; if (isDate) showDatePicker = true else showTimeWheel = true }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(onClick = { 
                        onSave(startDate, startTime, endTime)
                    }, modifier = Modifier.size(56.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = ComposeColor.Black), contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Default.Check, contentDescription = "Save", tint = ComposeColor.White)
                    }
                }
                if (showTimeWheel) { TimeWheelSelector(initialTime = if (pickingStart) startTime else endTime, onTimeSelected = { if (pickingStart) startTime = it else endTime = it; showTimeWheel = false }) }
            }
        }
        if (showDatePicker) {
            val ctx = requireContext()
            SideEffect {
                DatePickerDialog(ctx, { _, y, m, d -> startDate = LocalDate.of(y, m + 1, d); showDatePicker = false }, startDate.year, startDate.monthValue - 1, startDate.dayOfMonth).show()
            }
        }
    }

    @Composable
    private fun AddSleepRow(label: String, date: LocalDate, time: LocalTime, onClick: (Boolean) -> Unit) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = ComposeColor.Black, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            PillButton(date.format(DateTimeFormatter.ofPattern("dd MMM"))) { onClick(true) }
            Spacer(modifier = Modifier.width(8.dp))
            PillButton(time.format(DateTimeFormatter.ofPattern("HH:mm"))) { onClick(false) }
        }
    }

    @Composable
    private fun PillButton(text: String, onClick: () -> Unit) {
        Surface(onClick = onClick, color = ComposeColor(0xFFE5E5EA), shape = RoundedCornerShape(8.dp)) {
            Text(text, color = ComposeColor.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }

    @Composable
    private fun TimeWheelSelector(initialTime: LocalTime, onTimeSelected: (LocalTime) -> Unit) {
        var h by remember { mutableIntStateOf(initialTime.hour) }
        var m by remember { mutableIntStateOf(initialTime.minute) }
        ComposeDialog(onDismissRequest = { }) {
            Surface(shape = RoundedCornerShape(16.dp), color = ComposeColor.White) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.height(150.dp), verticalAlignment = Alignment.CenterVertically) {
                        Wheel(0..23, h) { h = it }; Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold); Wheel(0..59, m) { m = it }
                    }
                    Button(onClick = { onTimeSelected(LocalTime.of(h, m)) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ComposeColor.Black)) {
                        Text("Confirm")
                    }
                }
            }
        }
    }

    @Composable
    private fun Wheel(range: IntRange, current: Int, onValueChange: (Int) -> Unit) {
        val state = rememberLazyListState(initialFirstVisibleItemIndex = current)
        val firstVisibleIndex by remember { derivedStateOf { state.firstVisibleItemIndex } }
        LaunchedEffect(firstVisibleIndex) { onValueChange(firstVisibleIndex % range.count()) }
        Box(modifier = Modifier.width(60.dp).height(150.dp)) {
            LazyColumn(state = state, contentPadding = PaddingValues(vertical = 60.dp)) {
                items(range.toList()) { n ->
                    Text(text = String.format(Locale.getDefault(), "%02d", n), fontSize = if (n == firstVisibleIndex % range.count()) 24.sp else 18.sp, color = if (n == firstVisibleIndex % range.count()) ComposeColor.Black else ComposeColor.LightGray, modifier = Modifier.padding(8.dp).fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}