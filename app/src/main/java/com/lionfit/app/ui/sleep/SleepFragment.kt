package com.lionfit.app.ui.sleep

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lionfit.app.R
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

val SleepBarColor = ComposeColor(0xFFBDA7EF)

class SleepFragment : Fragment(R.layout.fragment_sleeping) {

    private val sleepRecords = mutableStateListOf<SleepLocalRecord>()
    private var currentWeekStart by mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)))
    private val PREFS_NAME = "sleep_data_prefs"
    private val KEY_RECORDS = "sleep_records_list"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // โหลดข้อมูลจริงจาก SharedPreferences
        loadStoredData()

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

                    // ใช้ sleepRecords.toList() เพื่อให้ตรวจจับการเปลี่ยนแปลงภายในลิสต์ได้ (แม้ขนาดเท่าเดิม)
                    LaunchedEffect(sleepRecords.toList(), currentWeekStart) {
                        updateXmlStats(view, sleepRecords, currentWeekStart)
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        SleepChartContent(records = sleepRecords, startOfWeek = currentWeekStart)

                        if (showAddDialog) {
                            AddSleepDialog(
                                onDismiss = { showAddDialog = false },
                                onSave = { newRecord ->
                                    checkOverlapAndSave(newRecord)
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

    private fun loadStoredData() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RECORDS, null)
        if (json != null) {
            val type = object : TypeToken<List<SleepLocalRecord>>() {}.type
            val list: List<SleepLocalRecord> = Gson().fromJson(json, type)
            sleepRecords.clear()
            sleepRecords.addAll(list)
        } else {
            // ข้อมูลเริ่มต้นครั้งแรก
            val today = LocalDate.now()
            val initial = listOf(
                SleepLocalRecord(today.minusDays(1).toString(), "22:30", "06:30"),
                SleepLocalRecord(today.minusDays(2).toString(), "23:00", "07:00"),
                SleepLocalRecord(today.minusDays(3).toString(), "21:00", "05:00")
            )
            sleepRecords.addAll(initial)
            saveToPrefs()
        }
    }

    private fun saveToPrefs() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(sleepRecords.toList())
        prefs.edit().putString(KEY_RECORDS, json).apply()
    }

    private fun checkOverlapAndSave(newRecord: SleepLocalRecord) {
        val newStartDT = newRecord.getStartDT()
        val newEndDT = newRecord.getEndDT()

        val overlapping = sleepRecords.filter { existing ->
            newStartDT.isBefore(existing.getEndDT()) && newEndDT.isAfter(existing.getStartDT())
        }
        
        if (overlapping.isNotEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle("แจ้งเตือน")
                .setMessage("คุณเคยเลือกเวลานี้แล้วต้องการไปต่อไหม")
                .setPositiveButton("ไปต่อ") { _, _ ->
                    sleepRecords.removeAll(overlapping)
                    sleepRecords.add(newRecord)
                    saveToPrefs()
                }
                .setNegativeButton("ยกเลิก") { d, _ -> d.dismiss() }
                .show()
        } else {
            sleepRecords.add(newRecord)
            saveToPrefs()
        }
    }

    private fun updateXmlStats(rootView: View, records: List<SleepLocalRecord>, startOfWeek: LocalDate) {
        val endOfWeek = startOfWeek.plusDays(6)
        val weekStartDT = startOfWeek.atStartOfDay()
        val weekEndDT = endOfWeek.atTime(LocalTime.MAX)

        val recordsInWeek = records.filter {
            it.getStartDT().isBefore(weekEndDT) && it.getEndDT().isAfter(weekStartDT)
        }

        var totalMinutesInWeek = 0L
        recordsInWeek.forEach { record ->
            val actualStart = if (record.getStartDT().isBefore(weekStartDT)) weekStartDT else record.getStartDT()
            val actualEnd = if (record.getEndDT().isAfter(weekEndDT)) weekEndDT else record.getEndDT()
            if (actualStart.isBefore(actualEnd)) {
                totalMinutesInWeek += Duration.between(actualStart, actualEnd).toMinutes()
            }
        }

        val avgTotalMinutes = totalMinutesInWeek / 7
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

    // Data Class สำหรับเก็บข้อมูลลง Prefs (เป็น String เพื่อให้ Gson ทำงานง่าย)
    private data class SleepLocalRecord(
        val dateStr: String, // ISO Date
        val startTimeStr: String, // HH:mm
        val endTimeStr: String // HH:mm
    ) {
        fun getStartDate(): LocalDate = LocalDate.parse(dateStr)
        fun getStartTime(): LocalTime = LocalTime.parse(startTimeStr)
        fun getEndTime(): LocalTime = LocalTime.parse(endTimeStr)
        fun getStartDT(): LocalDateTime = getStartDate().atTime(getStartTime())
        fun getEndDT(): LocalDateTime {
            val endDT = getStartDate().atTime(getEndTime())
            return if (getEndTime().isBefore(getStartTime())) endDT.plusDays(1) else endDT
        }
    }

    @Composable
    private fun SleepChartContent(records: List<SleepLocalRecord>, startOfWeek: LocalDate) {
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
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    VerticalDivider(modifier = Modifier.align(Alignment.CenterStart), color = ComposeColor.Gray.copy(alpha = 0.05f))
                                    records.forEach { record ->
                                        if (record.getStartDate() == currentDate) {
                                            DrawSleepBar(record.getStartTime(), if (record.getEndTime().isBefore(record.getStartTime())) LocalTime.MAX else record.getEndTime(), hourHeight, Alignment.TopCenter)
                                        } else if (record.getStartDate().plusDays(1) == currentDate && record.getEndTime().isBefore(record.getStartTime())) {
                                            DrawSleepBar(LocalTime.MIN, record.getEndTime(), hourHeight, Alignment.TopCenter)
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
    private fun AddSleepDialog(onDismiss: () -> Unit, onSave: (SleepLocalRecord) -> Unit) {
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
                        onSave(SleepLocalRecord(startDate.toString(), startTime.toString(), endTime.toString()))
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
