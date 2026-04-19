package com.lionfit.app.ui.sleep

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// Define local colors
val SleepBarColor = ComposeColor(0xFFBDA7EF)

class SleepFragment : Fragment(R.layout.fragment_sleeping) {

    // ใช้ Mock Data แทนเพื่อป้องกันแอปเด้งจากปัญหา Room Database_Impl
    private val mockSleepRecords = mutableStateListOf<SleepMockRecord>()
    private var currentWeekStart by mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)))

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ข้อมูลเริ่มต้นจำลอง
        if (mockSleepRecords.isEmpty()) {
            val today = LocalDate.now()
            mockSleepRecords.add(SleepMockRecord(today.minusDays(1), LocalTime.of(22, 30), LocalTime.of(6, 30)))
            mockSleepRecords.add(SleepMockRecord(today.minusDays(2), LocalTime.of(23, 0), LocalTime.of(7, 0)))
            mockSleepRecords.add(SleepMockRecord(today.minusDays(3), LocalTime.of(21, 0), LocalTime.of(5, 0)))
        }

        // ตั้งค่า ComposeView สำหรับกราฟ
        view.findViewById<ComposeView>(R.id.chart_compose_view)?.apply {
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

                    LaunchedEffect(mockSleepRecords.size, currentWeekStart) {
                        updateXmlStats(view, mockSleepRecords, currentWeekStart)
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        SleepChartContent(records = mockSleepRecords, startOfWeek = currentWeekStart)

                        if (showAddDialog) {
                            AddSleepDialog(
                                onDismiss = { showAddDialog = false },
                                onSave = { newRecord ->
                                    mockSleepRecords.add(newRecord)
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
        
        // ปุ่ม Info
        view.findViewById<ImageButton>(R.id.btn_info)?.setOnClickListener {
            showInfoDialog()
        }
        
        // เพิ่มการคลิกที่ tv_date_range เพื่อเปลี่ยนสัปดาห์
        view.findViewById<TextView>(R.id.tv_date_range)?.setOnClickListener {
            val picker = DatePickerDialog(requireContext(), { _, y, m, d ->
                val selectedDate = LocalDate.of(y, m + 1, d)
                currentWeekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            }, currentWeekStart.year, currentWeekStart.monthValue - 1, currentWeekStart.dayOfMonth)
            picker.show()
        }
    }
    
    private fun showInfoDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Information")
            .setMessage("เลือกเวลานอนของคุณ กราฟนี้จะแสดงชั่วโมงการนอนเฉลี่ยของคุณ 1 สัปดาห์")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun updateXmlStats(rootView: View, records: List<SleepMockRecord>, startOfWeek: LocalDate) {
        val endOfWeek = startOfWeek.plusDays(6)
        
        val recordsInWeek = records.filter {
            (it.startDate >= startOfWeek && it.startDate <= endOfWeek) ||
            (it.endDate >= startOfWeek && it.endDate <= endOfWeek)
        }
        
        val totalMinutes = recordsInWeek.sumOf { it.durationMinutes }
        val daysWithData = recordsInWeek.map { it.startDate }.distinct().size
        
        val avgTotalMinutes = if (daysWithData > 0) totalMinutes / daysWithData else 0L
        val displayHours = avgTotalMinutes / 60
        val displayMinutes = avgTotalMinutes % 60

        rootView.findViewById<TextView>(R.id.tv_avg_hours)?.text = displayHours.toString()
        rootView.findViewById<TextView>(R.id.tv_avg_minutes)?.text = displayMinutes.toString()
        rootView.findViewById<TextView>(R.id.tv_date_range)?.text = 
            "${startOfWeek.format(DateTimeFormatter.ofPattern("dd"))} - ${endOfWeek.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}"
    }

    private data class SleepMockRecord(
        val startDate: LocalDate,
        val startTime: LocalTime,
        val endTime: LocalTime
    ) {
        val endDate: LocalDate get() = if (endTime.isBefore(startTime)) startDate.plusDays(1) else startDate
        val durationMinutes: Long get() {
            val start = startDate.atTime(startTime)
            val end = endDate.atTime(endTime)
            return Duration.between(start, end).toMinutes()
        }
    }

    @Composable
    private fun SleepChartContent(records: List<SleepMockRecord>, startOfWeek: LocalDate) {
        val daysLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val scrollState = rememberScrollState()
        val hourHeight = 45.dp 
        
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(hourHeight * 24)
                    .background(ComposeColor.Transparent)
                    .padding(vertical = 16.dp, horizontal = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        for (i in 0..24) {
                            Box(modifier = Modifier.height(hourHeight)) {
                                HorizontalDivider(color = ComposeColor.Gray.copy(alpha = 0.08f), thickness = 1.dp)
                            }
                        }
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.width(40.dp)) {
                            for (i in 0..24) {
                                Box(modifier = Modifier.height(hourHeight), contentAlignment = Alignment.TopStart) {
                                    Text(String.format(Locale.getDefault(), "%02d:00", i), color = ComposeColor.LightGray, fontSize = 10.sp)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(hourHeight * 24),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            daysLabels.forEachIndexed { index, _ ->
                                val currentDate = startOfWeek.plusDays(index.toLong())
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    Box(modifier = Modifier.fillMaxHeight().width(0.5.dp).background(ComposeColor.Gray.copy(alpha = 0.05f)).align(Alignment.CenterStart))
                                    
                                    records.forEach { record ->
                                        if (record.startDate == currentDate && record.endDate == currentDate) {
                                            DrawSleepBar(record.startTime, record.endTime, hourHeight, Alignment.TopCenter)
                                        }
                                        else if (record.startDate == currentDate && record.endDate > currentDate) {
                                            DrawSleepBar(record.startTime, LocalTime.MAX, hourHeight, Alignment.TopCenter)
                                        }
                                        else if (record.startDate < currentDate && record.endDate == currentDate) {
                                            DrawSleepBar(LocalTime.MIN, record.endTime, hourHeight, Alignment.TopCenter)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                daysLabels.forEach { day -> 
                    Text(day, color = ComposeColor.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
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
        
        Box(modifier = Modifier
            .padding(top = startPos.dp)
            .width(24.dp)
            .height(durationHeight.dp)
            .align(alignment)
            .clip(RoundedCornerShape(6.dp))
            .background(SleepBarColor)
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AddSleepDialog(onDismiss: () -> Unit, onSave: (SleepMockRecord) -> Unit) {
        var startDate by remember { mutableStateOf(LocalDate.now()) }
        var startTime by remember { mutableStateOf(LocalTime.of(22, 0)) }
        var endDate by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
        var endTime by remember { mutableStateOf(LocalTime.of(7, 0)) }
        
        var showDatePicker by remember { mutableStateOf(false) }
        var showTimeWheel by remember { mutableStateOf(false) }
        var pickingStart by remember { mutableStateOf(true) }

        ComposeDialog(onDismissRequest = onDismiss) {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = ComposeColor.White)) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ADD TIME SLEEP", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Column(modifier = Modifier.fillMaxWidth().background(ComposeColor(0xFFF2F2F7), RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))) {
                            AddSleepRow("Starts", startDate, startTime) { isDate -> 
                                pickingStart = true
                                if (isDate) showDatePicker = true else showTimeWheel = true 
                            }
                            HorizontalDivider(color = ComposeColor.White, thickness = 1.dp)
                            AddSleepRow("Ends", endDate, endTime) { isDate -> 
                                pickingStart = false
                                if (isDate) showDatePicker = true else showTimeWheel = true 
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Button(
                            onClick = { 
                                onSave(SleepMockRecord(startDate, startTime, endTime))
                            },
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = ComposeColor.Black)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Save", tint = ComposeColor.White)
                        }
                    }
                    if (showTimeWheel) {
                        Box(modifier = Modifier.padding(top = 80.dp).align(Alignment.TopCenter)) {
                            Surface(modifier = Modifier.width(200.dp), color = ComposeColor.White, shape = RoundedCornerShape(16.dp), shadowElevation = 8.dp) {
                                TimeWheelSelector(initialTime = if (pickingStart) startTime else endTime, onTimeSelected = { 
                                    if (pickingStart) startTime = it else endTime = it
                                    showTimeWheel = false 
                                })
                            }
                        }
                    }
                }
            }
        }
        if (showDatePicker) {
            val ctx = requireContext()
            SideEffect {
                val currentPickerDate = if (pickingStart) startDate else endDate
                val picker = DatePickerDialog(ctx, { _, y, m, d ->
                    val date = LocalDate.of(y, m + 1, d)
                    if (pickingStart) startDate = date else endDate = date
                    showDatePicker = false
                }, currentPickerDate.year, currentPickerDate.monthValue - 1, currentPickerDate.dayOfMonth)
                picker.setOnCancelListener { showDatePicker = false }
                picker.show()
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
        Surface(onClick = onClick, color = ComposeColor(0xFFE5E5EA), shape = RoundedCornerShape(12.dp)) {
            Text(text, color = ComposeColor.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }

    @Composable
    private fun TimeWheelSelector(initialTime: LocalTime, onTimeSelected: (LocalTime) -> Unit) {
        var h by remember { mutableIntStateOf(initialTime.hour) }
        var m by remember { mutableIntStateOf(initialTime.minute) }
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.height(150.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Wheel(0..23, h) { h = it }; Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp)); Wheel(0..59, m) { m = it }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onTimeSelected(LocalTime.of(h, m)) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ComposeColor.Black), shape = RoundedCornerShape(12.dp)) {
                Text("Confirm", color = ComposeColor.White)
            }
        }
    }

    @Composable
    private fun Wheel(range: IntRange, current: Int, onValueChange: (Int) -> Unit) {
        val state = rememberLazyListState(initialFirstVisibleItemIndex = current)
        val firstVisibleIndex by remember { derivedStateOf { state.firstVisibleItemIndex } }
        
        LaunchedEffect(firstVisibleIndex) { onValueChange(firstVisibleIndex) }
        
        Box(modifier = Modifier.width(50.dp).height(150.dp)) {
            LazyColumn(state = state, contentPadding = PaddingValues(vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                items(range.toList()) { n ->
                    Text(
                        text = String.format(Locale.getDefault(), "%02d", n), 
                        fontSize = if (n == firstVisibleIndex) 24.sp else 16.sp, 
                        color = if (n == firstVisibleIndex) ComposeColor.Black else ComposeColor.LightGray, 
                        modifier = Modifier.padding(4.dp),
                        fontWeight = if (n == firstVisibleIndex) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
