package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.WeightLossViewModel
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Ensure Layout Direction is always Right-To-Left for native Arabic interface
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        WeightLossApp()
                    }
                }
            }
        }
    }
}

@Composable
fun WeightLossApp(viewModel: WeightLossViewModel = viewModel()) {
    val profileState by viewModel.userProfile.collectAsStateWithLifecycle()
    val progressState by viewModel.allDailyProgress.collectAsStateWithLifecycle()
    val weightLogsState by viewModel.allWeightLogs.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf("dashboard") }
    var detailDay by remember { mutableIntStateOf(1) }

    // Onboarding if profile is empty or hasn't been set up
    val profile = profileState
    if (profile == null || profile.startingWeight == 0.0) {
        OnboardingScreen(onSetupPlan = { w, h, a, g, act ->
            viewModel.setupUserProfile(w, h, a, g, act)
        })
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    tonalElevation = 8.dp,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = activeTab == "dashboard",
                        onClick = { activeTab = "dashboard" },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = "30 يوم") },
                        label = { Text("برنامج 30 يوم", fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("tab_dashboard")
                    )
                    NavigationBarItem(
                        selected = activeTab == "weight",
                        onClick = { activeTab = "weight" },
                        icon = { Icon(Icons.Default.Info, contentDescription = "وزني") },
                        label = { Text("منحنى الوزن", fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("tab_weight")
                    )
                    NavigationBarItem(
                        selected = activeTab == "tips",
                        onClick = { activeTab = "tips" },
                        icon = { Icon(Icons.Default.Star, contentDescription = "نصائح") },
                        label = { Text("نصائح عراقية", fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("tab_tips")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // Faint elegant radial background glow as guided by frontend-design skill matches
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0x1500C853), Color.Transparent),
                                center = Offset(size.width * 0.7f, size.height * 0.2f),
                                radius = size.minDimension * 0.6f
                            )
                        )
                    }
            ) {
                when (activeTab) {
                    "dashboard" -> DashboardScreen(
                        profile = profile,
                        progressList = progressState,
                        selectedDay = detailDay,
                        onDaySelected = { detailDay = it },
                        onMealSelected = { day, type, idx -> viewModel.selectMeal(day, type, idx) },
                        onMealDeselected = { day, type -> viewModel.deselectMeal(day, type) },
                        onExerciseToggled = { day, exId -> viewModel.toggleExercise(day, exId) },
                        onWaterChanged = { day, amt -> viewModel.changeWaterCups(day, amt) },
                        onWeightLogged = { day, w -> viewModel.logWeightForDay(day, w) },
                        onToggleDayComplete = { day -> viewModel.toggleDayCompleted(day) }
                    )
                    "weight" -> WeightHistoryScreen(
                        profile = profile,
                        logs = weightLogsState,
                        onAddManualLog = { label, w -> viewModel.addManualWeightLog(label, w) },
                        onDeleteLog = { log -> viewModel.deleteWeightLog(log) },
                        onResetAll = { viewModel.resetJourney() }
                    )
                    "tips" -> HealthyTipsScreen()
                }
            }
        }
    }
}

// ----------------------------------------------------
// ONBOARDING SCREEN
// ----------------------------------------------------
@Composable
fun OnboardingScreen(
    onSetupPlan: (weight: Double, height: Double, age: Int, gender: String, activity: String) -> Unit
) {
    var weightInput by remember { mutableStateOf("85") }
    var heightInput by remember { mutableStateOf("170") }
    var ageInput by remember { mutableStateOf("28") }
    var gender by remember { mutableStateOf("ذكر") }
    var activityLevel by remember { mutableStateOf("متوسط") }
    var errorMessage by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        // App Icon Mockup inside UI
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    Brush.sweepGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.primary
                        )
                    ), CircleShape
                )
                .padding(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "رشاقة عائلية",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Text(
            text = "تحدي إنقاص 4 كيلو في 30 يوم للياقة والوزن المثالي",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "أدخل بياناتك لحساب سعراتك الذكية:",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Current Weight Field
                Column {
                    Text("الوزن الحالي (كيلوغرام):", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it; errorMessage = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboard_weight_input"),
                        singleLine = true,
                        trailingIcon = { Text("كغم", modifier = Modifier.padding(end = 8.dp)) }
                    )
                }

                // Height Field
                Column {
                    Text("الطول (سنتيمتر):", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = { heightInput = it; errorMessage = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboard_height_input"),
                        singleLine = true,
                        trailingIcon = { Text("سم", modifier = Modifier.padding(end = 8.dp)) }
                    )
                }

                // Age Field
                Column {
                    Text("العمر (بالسنوات):", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = ageInput,
                        onValueChange = { ageInput = it; errorMessage = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboard_age_input"),
                        singleLine = true,
                        trailingIcon = { Text("سنة", modifier = Modifier.padding(end = 8.dp)) }
                    )
                }

                // Gender Buttons
                Column {
                    Text("الجنس:", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf("ذكر", "أنثى").forEach { g ->
                            val isSelected = gender == g
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { gender = g }
                                    .testTag("onboard_gender_$g"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = g,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Activity Level Selector
                Column {
                    Text("مستوى النشاط اليومي المتوقع:", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    listOf(
                        "خامل" to "قليل الحركة (عمل مكتبي، جلوس مستمر)",
                        "متوسط" to "متوسط النشاط (مشي طفيف، حركة يومية بالمكتب)",
                        "نشط جداً" to "نشط جداً (تمارين رياضية يومية شاقة أو حركة مستمرة)"
                    ).forEach { (level, desc) ->
                        val isSelected = activityLevel == level
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { activityLevel = level }
                                .padding(12.dp)
                                .testTag("onboard_activity_$level"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = isSelected, onClick = { activityLevel = level })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(level, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Button(
                    onClick = {
                        val w = weightInput.toDoubleOrNull() ?: 0.0
                        val h = heightInput.toDoubleOrNull() ?: 0.0
                        val a = ageInput.toIntOrNull() ?: 0

                        if (w <= 30.0 || w > 250.0) {
                            errorMessage = "يرجى إدخال وزن حقيقي وصحيح بين (30 - 250) كغم"
                        } else if (h <= 100.0 || h > 240.0) {
                            errorMessage = "يرجى إدخال قياس طول واقعي بين (100 - 240) سم"
                        } else if (a <= 10 || a > 100) {
                            errorMessage = "يرجى إدخال عمر صحيح بين (10 - 100) سنة"
                        } else {
                            onSetupPlan(w, h, a, gender, activityLevel)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("setup_plan_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("احسب سعراتي وابدأ رحلتي الآن 🚀", fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

// ----------------------------------------------------
// CORE DASHBOARD SCREEN
// ----------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    profile: UserProfileEntity,
    progressList: List<DailyProgressEntity>,
    selectedDay: Int,
    onDaySelected: (Int) -> Unit,
    onMealSelected: (day: Int, type: String, idx: Int) -> Unit,
    onMealDeselected: (day: Int, type: String) -> Unit,
    onExerciseToggled: (day: Int, id: Int) -> Unit,
    onWaterChanged: (day: Int, amt: Int) -> Unit,
    onWeightLogged: (day: Int, weight: Double) -> Unit,
    onToggleDayComplete: (day: Int) -> Unit
) {
    val dayProgress = progressList.find { it.day == selectedDay } ?: DailyProgressEntity(day = selectedDay)

    // Calculate completed days out of 30
    val completedDays = progressList.count { it.isCompleted }
    val progressPercent = completedDays / 30.0f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- HEADER LOG ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("برنامج الـ 30 يوم 🏆", fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Text("هدف الشهر: خسارة 4 كيلوغرام بصحة", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "يوم ${completedDays}/30 منجز",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { progressPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("الافتتاحي: ${profile.startingWeight} كغم", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("الحالي: ${profile.currentWeight} كغم", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("المستهدف: ${profile.targetWeight} كغم", fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- DAYS TRACKER ROW ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("اختر يوم المتابعة:", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items((1..30).toList()) { d ->
                        val isSelected = selectedDay == d
                        val isDayDone = progressList.find { it.day == d }?.isCompleted == true
                        
                        Card(
                            modifier = Modifier
                                .width(78.dp)
                                .height(82.dp)
                                .clickable { onDaySelected(d) }
                                .testTag("day_button_$d"),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isDayDone -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "اليوم",
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    d.toString(),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                if (isDayDone) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "تم",
                                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- CALORIES SUMMARY BOX ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val netCalories = dayProgress.caloriesEaten - dayProgress.caloriesBurned
                val statusExceeded = netCalories > profile.dailyCalorieTarget
                
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "ميزان السعرات لليوم $selectedDay",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Math logic UI
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(dayProgress.caloriesEaten.toString(), fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            Text("المأخوذة (طعام)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Text("-", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(dayProgress.caloriesBurned.toString(), fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary)
                            Text("المحروقة (رياضة)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Text("=", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                netCalories.toString(),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = if (statusExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            Text("صافي السعرات", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Recommended Line Budget
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("المسموح كحد أعلى لليوم:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${profile.dailyCalorieTarget} سعرة", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            val isWithin = netCalories <= profile.dailyCalorieTarget
                            Text(
                                text = if (isWithin) {
                                    "🍏 أنت في النطاق الآمن لإنقاص الوزن! حافظ على العجز الحراري."
                                } else {
                                    "⚠️ السعرات تجاوزت الحد اليومي! حاول زيادة تمارين الرياضة للتعويض."
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isWithin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // --- FOOD CHOICE MANAGER ---
        item {
            Column(modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)) {
                Text(
                    "الوجبات العراقية الدايت المقترحة:",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "✨ قائمة متجددة ومختلفة لليوم $selectedDay لكي لا تشعر بالملل وتخسر الوزن بمرونة تامة!",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Breakfast, Lunch, Dinner, Snack Sub-sections with fallback safety
        item {
            val rawBreakfast = DietAndWorkoutProgram.getBreakfastOptionsForDay(selectedDay)
            val bIdx = dayProgress.breakfastChoiceIdx
            val breakfastList = if (bIdx >= 0 && rawBreakfast.none { it.id == bIdx }) {
                val selectedItem = DietAndWorkoutProgram.masterBreakfastOptions.find { it.id == bIdx }
                if (selectedItem != null) rawBreakfast + selectedItem else rawBreakfast
            } else {
                rawBreakfast
            }
            MealSectionWidget(
                title = "الريوك (الفطور الصحّي) 🍳",
                options = breakfastList,
                selectedIdx = bIdx,
                onSelect = { idx -> onMealSelected(selectedDay, "BREAKFAST", idx) },
                onDeselect = { onMealDeselected(selectedDay, "BREAKFAST") },
                tagId = "breakfast"
            )
        }

        item {
            val rawLunch = DietAndWorkoutProgram.getLunchOptionsForDay(selectedDay)
            val lIdx = dayProgress.lunchChoiceIdx
            val lunchList = if (lIdx >= 0 && rawLunch.none { it.id == lIdx }) {
                val selectedItem = DietAndWorkoutProgram.masterLunchOptions.find { it.id == lIdx }
                if (selectedItem != null) rawLunch + selectedItem else rawLunch
            } else {
                rawLunch
            }
            MealSectionWidget(
                title = "الغداء العراقي الدايت 🥘",
                options = lunchList,
                selectedIdx = lIdx,
                onSelect = { idx -> onMealSelected(selectedDay, "LUNCH", idx) },
                onDeselect = { onMealDeselected(selectedDay, "LUNCH") },
                tagId = "lunch"
            )
        }

        item {
            val rawDinner = DietAndWorkoutProgram.getDinnerOptionsForDay(selectedDay)
            val dIdx = dayProgress.dinnerChoiceIdx
            val dinnerList = if (dIdx >= 0 && rawDinner.none { it.id == dIdx }) {
                val selectedItem = DietAndWorkoutProgram.masterDinnerOptions.find { it.id == dIdx }
                if (selectedItem != null) rawDinner + selectedItem else rawDinner
            } else {
                rawDinner
            }
            MealSectionWidget(
                title = "العشاء الخفيف الدايت 🥗",
                options = dinnerList,
                selectedIdx = dIdx,
                onSelect = { idx -> onMealSelected(selectedDay, "DINNER", idx) },
                onDeselect = { onMealDeselected(selectedDay, "DINNER") },
                tagId = "dinner"
            )
        }

        item {
            val rawSnack = DietAndWorkoutProgram.getSnackOptionsForDay(selectedDay)
            val sIdx = dayProgress.snackChoiceIdx
            val snackList = if (sIdx >= 0 && rawSnack.none { it.id == sIdx }) {
                val selectedItem = DietAndWorkoutProgram.masterSnackOptions.find { it.id == sIdx }
                if (selectedItem != null) rawSnack + selectedItem else rawSnack
            } else {
                rawSnack
            }
            MealSectionWidget(
                title = "السناك اليومي الصحي ☕",
                options = snackList,
                selectedIdx = sIdx,
                onSelect = { idx -> onMealSelected(selectedDay, "SNACK", idx) },
                onDeselect = { onMealDeselected(selectedDay, "SNACK") },
                tagId = "snack"
            )
        }

        // --- DAILY EXERCISES SECTION ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("exercise_section"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تمارين التخسيس الرياضية لليوم:", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "المستهدف: ${profile.dailyCalorieBurnTarget} سعرة",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val exercises = DietAndWorkoutProgram.getExercisesForDay(selectedDay)
                    val exercisesDone = if (dayProgress.exerciseDoneIds.isEmpty()) {
                        emptyList()
                    } else {
                        dayProgress.exerciseDoneIds.split(",").mapNotNull { it.toIntOrNull() }
                    }

                    exercises.forEach { ex ->
                        val isDone = exercisesDone.contains(ex.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(
                                    if (isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                .clickable { onExerciseToggled(selectedDay, ex.id) }
                                .padding(12.dp)
                                .testTag("exercise_item_${ex.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isDone) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ex.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${ex.description} (${ex.durationMinutes} دقيقة)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "+${ex.caloriesBurned} سعرة",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }

        // --- WATER TRACKER SECTION ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("water_section"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("متابع شرب الماء 💧", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("الماء يزيد من معدلات الحرق ويطرد الدهون", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF00B0FF).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "المستهدف: 8 أكواب",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0091EA)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "${dayProgress.waterCups} / 8 أكواب",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0091EA)
                    )

                    // Cups Water Status Text in Baghdad idiom
                    val statusText = when {
                        dayProgress.waterCups == 0 -> "لم تشرب الماء اليوم! ابدأ فوراً 🥤"
                        dayProgress.waterCups in 1..3 -> "خطوة جيدة، جسمك يحتاج دبل هذا!"
                        dayProgress.waterCups in 4..7 -> "جيد جداً، واظب لتصل إلى الهدف التام!"
                        else -> "رائع وممتاز! 🌟 جسمك رطب ومستعد لحرق الدهون بكفاءة عالية!"
                    }
                    Text(
                        statusText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { onWaterChanged(selectedDay, -1) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.width(72.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }

                        Button(
                            onClick = { onWaterChanged(selectedDay, 1) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0091EA)),
                            modifier = Modifier.width(100.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("اشرب كأس", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // --- SCALE WEIGHT LOG SYSTEM FOR SELECTED DAY ---
        item {
            var inputWeightText by remember { mutableStateOf("") }
            Card(
                modifier = Modifier.fillMaxWidth().testTag("day_weight_logger"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("سجل وزنك اليومي لليوم $selectedDay:", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("مراقبة الوزن يومياً تصنع الالتزام وتقيس النتائج بدقة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputWeightText,
                            onValueChange = { inputWeightText = it },
                            placeholder = { Text("مثلاً: 84.5") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).height(54.dp),
                            singleLine = true,
                            trailingIcon = { Text("كغم", modifier = Modifier.padding(end = 8.dp)) }
                        )

                        Button(
                            onClick = {
                                val dW = inputWeightText.toDoubleOrNull()
                                if (dW != null && dW > 20.0) {
                                    onWeightLogged(selectedDay, dW)
                                    inputWeightText = ""
                                }
                            },
                            modifier = Modifier.height(54.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("حفظ والتقييد", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (dayProgress.weightLogged > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "الوزن المسجل لليوم $selectedDay: ${dayProgress.weightLogged} كيلوغرام",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // --- COMPLETE DAY OR NOT CHECKBOX BUTTON ---
        item {
            val isSelectedDone = dayProgress.isCompleted
            Button(
                onClick = { onToggleDayComplete(selectedDay) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("toggle_complete_day_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelectedDone) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isSelectedDone) "اليوم $selectedDay تم إنجازه بنجاح! مجدداً؟ 🔄" else "تسجيل إكمال وإنهاء اليوم $selectedDay بالكامل! 🌟",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isSelectedDone) MaterialTheme.colorScheme.primary else Color.White
                )
            }
        }
    }
}

// Subordinate widgets for Meal Sections
@Composable
fun MealSectionWidget(
    title: String,
    options: List<MealOption>,
    selectedIdx: Int,
    onSelect: (Int) -> Unit,
    onDeselect: () -> Unit,
    tagId: String
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("meal_section_$tagId"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (selectedIdx >= 0) Icons.Default.Check else Icons.Default.Star,
                        contentDescription = null,
                        tint = if (selectedIdx >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedIdx >= 0) {
                        val meal = options.find { it.id == selectedIdx }
                        if (meal != null) {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "${meal.calories} سعرة",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "توسيع"
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { item ->
                        val isMealSelected = selectedIdx == item.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isMealSelected) onDeselect() else onSelect(item.id)
                                }
                                .testTag("meal_option_${tagId}_${item.id}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMealSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isMealSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Checkbox(
                                    checked = isMealSelected,
                                    onCheckedChange = { if (isMealSelected) onDeselect() else onSelect(item.id) },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1.0f)) {
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(item.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "${item.calories} سعرة",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isMealSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// WEIGHT HISTORY & GRAPH SCREEN
// ----------------------------------------------------
@Composable
fun WeightHistoryScreen(
    profile: UserProfileEntity,
    logs: List<WeightLogEntity>,
    onAddManualLog: (String, Double) -> Unit,
    onDeleteLog: (WeightLogEntity) -> Unit,
    onResetAll: () -> Unit
) {
    var isResetDialogShown by remember { mutableStateOf(false) }
    var inputLabel by remember { mutableStateOf("") }
    var inputWeight by remember { mutableStateOf("") }

    val sortedLogs = logs.sortedBy { it.id }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- SUMMARY STATISTICS ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("weight_summary_stats"),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("إحصائيات تقدم الوزن الحالي:", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(profile.startingWeight.toString(), fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Text("الافتتاحي", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(profile.currentWeight.toString(), fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            Text("الوزن الحالي", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val lossCurrent = profile.startingWeight - profile.currentWeight
                            Text(
                                String.format(java.util.Locale.US, "%.1f", lossCurrent),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = if (lossCurrent >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Text("لقد خسرت", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(profile.targetWeight.toString(), fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary)
                            Text("المستهدف", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // --- CUSTOM CANVAS WEIGHT GRAPH ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("weight_canvas_graph"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("منحنى تغير الوزن (كغم):", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("رسم بياني يوضح مسار انخفاض وزنك للوصول للهدف", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))

                    if (sortedLogs.size >= 2) {
                        WeightTimelineGraph(logs = sortedLogs, targetWeight = profile.targetWeight)
                    } else {
                        // Graph Placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "تحتاج إلى تسجيل قرائتي وزن على الأقل لكي يرسم المنحنى! سجل وزن اليوم للبدء.",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- MANUAL LOG INTAKE ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("manual_weight_logger"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("تسجيل قراءة وزن يدوية بالتوثيق:", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputLabel,
                            onValueChange = { inputLabel = it },
                            placeholder = { Text("مثلاً: الأسبوع 1") },
                            modifier = Modifier.weight(1.2f).height(54.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = inputWeight,
                            onValueChange = { inputWeight = it },
                            placeholder = { Text("83.2") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.8f).height(54.dp),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                val wVal = inputWeight.toDoubleOrNull()
                                if (wVal != null && wVal > 20 && inputLabel.isNotEmpty()) {
                                    onAddManualLog(inputLabel, wVal)
                                    inputLabel = ""
                                    inputWeight = ""
                                }
                            },
                            modifier = Modifier.height(54.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("أضف", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- WEIGHT LOG ENTRIES LIST ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("تاريخ القراءات المسجلة للوزن:", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (logs.isNotEmpty()) {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        onClick = { isResetDialogShown = true }
                    ) {
                        Text("بدء الخطة من جديد 🔄")
                    }
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("لم تقم بتسجيل أوزان في السجل بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(logs) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("weight_log_entry"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(entry.date, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("سجل كغم", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${entry.weight} كغم",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            IconButton(onClick = { onDeleteLog(entry) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "حذف القيد",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isResetDialogShown) {
        AlertDialog(
            onDismissRequest = { isResetDialogShown = false },
            title = { Text("إعادة تعيين الرحلة بالكامل؟") },
            text = { Text("هل أنت متأكد من رغبتك في حذف كل تاريخ الأوزان والوجبات لبرنامج الـ 30 يوم وإعادة ضبط ملفك الشخصي بالكامل للبدء مجدداً؟") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        onResetAll()
                        isResetDialogShown = false
                    }
                ) {
                    Text("نعم، احذف وابدأ من جديد 🔄", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isResetDialogShown = false }) {
                    Text("إلغاء وتراجع")
                }
            }
        )
    }
}

// Custom-drawn responsive canvas vector line chart of weight loss progress
@Composable
fun WeightTimelineGraph(logs: List<WeightLogEntity>, targetWeight: Double) {
    val maxWeight = logs.maxOf { it.weight }.coerceAtLeast(targetWeight).toFloat()
    val minWeight = logs.minOf { it.weight }.coerceAtMost(targetWeight).toFloat()
    val weightRange = if (maxWeight == minWeight) 10f else (maxWeight - minWeight)

    val colorScheme = MaterialTheme.colorScheme

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(top = 16.dp, bottom = 24.dp, start = 24.dp, end = 12.dp)
    ) {
        val width = size.width
        val height = size.height

        val paddingOffset = 15f
        val effectiveWidth = width - (2 * paddingOffset)
        val effectiveHeight = height - (2 * paddingOffset)

        // Draw scale grid lines horizontal
        val gridCount = 4
        for (i in 0..gridCount) {
            val y = paddingOffset + (effectiveHeight * i / gridCount)
            val currentGridWeight = maxWeight - (weightRange * i / gridCount)
            drawLine(
                color = Color(0x1F7D5260),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        // Build continuous coordinates for log coordinates
        val points = logs.mapIndexed { idx, log ->
            val x = paddingOffset + (effectiveWidth * idx / (logs.size - 1))
            val ratio = (log.weight.toFloat() - minWeight) / weightRange
            val y = paddingOffset + (effectiveHeight * (1f - ratio))
            Offset(x, y)
        }

        // Create shaded path beneath the progress graph line
        val path = Path().apply {
            moveTo(points.first().x, height)
            points.forEach { pt ->
                lineTo(pt.x, pt.y)
            }
            lineTo(points.last().x, height)
            close()
        }

        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(colorScheme.primary.copy(alpha = 0.35f), Color.Transparent)
            )
        )

        // Draw line connecting log weight points
        val strokePath = Path().apply {
            points.forEachIndexed { idx, pt ->
                if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
            }
        }

        drawPath(
            path = strokePath,
            color = colorScheme.primary,
            style = Stroke(width = 6f)
        )

        // Highlight actual recorded point circles
        points.forEachIndexed { idx, pt ->
            // Point glow ring
            drawCircle(
                color = colorScheme.tertiary,
                radius = 8f,
                center = pt
            )
            drawCircle(
                color = Color.White,
                radius = 4f,
                center = pt
            )
        }
    }
}

// ----------------------------------------------------
// TIPS SCREEN (SURVIVAL GUIDE TO IRAQI FEASTS)
// ----------------------------------------------------
@Composable
fun HealthyTipsScreen() {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("tips_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header inside Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "دليل الرشاقة العراقي 🇮🇶🍏",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "كيف تحافظ على العجز الحراري وتلتزم بإنقاص 4 كيلو وتتجنب السمنة المفرطة، حتى مع العزائم العراقية والتشريب!",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Text(
            "نصائح المطبخ العراقي دايت وضغط السعرات:",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Tip Card 1
        TipDetailCard(
            title = "أسرار تناول الأرز (التمن البسمتي) العراقي",
            description = "الأرز ليس عدوك! لكن طريقة تحضيره بالدهن الحر ترفع السعرات بشكل مخيف. استعمل طريقة التمن المبزول بالماء فقط وصبه بدون زيت مضاف. 6 ملاعق من التمن المبزول تحتوي على 140 سعرة فقط وتكفي جداً كوجبة كربوهيدرات ممتازة مع المرق.",
            icon = Icons.Default.Check,
            badgeColor = MaterialTheme.colorScheme.primary
        )

        // Tip Card 2
        TipDetailCard(
            title = "عزائم الأهل والتشريب والمطباخ العراقي",
            description = "عند زيارة الأقارب وعند وجود تشريب اللحم: تجنب نقع الخبز الحار الأبيض بمرق اللحم الممتلئ بالدهن والشحم. تناول قطعة اللحم الحمراء بعد التخلص من الدهن تماماً، واشرب كوباً كبيراً من الماء البارد قبل الأكل بـ 15 دقيقة، واستعمل رذاذ الليمون الطازج لتعزيز الشبع وحرق الدهون.",
            icon = Icons.Default.Warning,
            badgeColor = MaterialTheme.colorScheme.tertiary
        )

        // Tip Card 3
        TipDetailCard(
            title = "بدائل ذكية للخبز والصمون الحار",
            description = "استبدل الصمون الأبيض والخبز العراقي الاعتيادي بخبز الشعير العراقي أو خبز النخالة الأسمر المتوفر بالأسواق. يحتوي الشعير على نسب عالية من الألياف المعقدة التي تساعد في حرق دهون البطن (الكرش) وتسريع خروج الفضلات وترطيب المعدة.",
            icon = Icons.Default.Refresh,
            badgeColor = Color(0xFF00E676)
        )

        // Tip Card 4
        TipDetailCard(
            title = "عشق السمك المسكوف العراقي",
            description = "يعتبر السمك المسكوف العراقي بالفرن أو المشوي على الحطب أفضل وجبة ريجيم بروتينية على الإطلاق بفضل الأوميغا-3 المفيد للشرايين والقلب ومحفز الأيض لحرق الشحوم. فقط تأكد من عدم دهن السمك بالزيوت عند الشواء وعصر الليمون الحامض والثوم بكثافة.",
            icon = Icons.Default.Favorite,
            badgeColor = Color(0xFFFF1744)
        )

        // Tip Card 5
        TipDetailCard(
            title = "الشاي العراقي المهيل والحلويات",
            description = "من عاداتنا شرب استكان شاي بعد كل وجبة. لإنقاص الوزن عجل، توقف تماماً عن استخدام السكر الأبيض في شاي الهيل. استبدله بمحليات طبيعية كأوراق الاستيفيا أو خذه مراً دافئاً فصنع حموضة المعدة وسهولة الهضم وحفظ مستوى الأنسولين.",
            icon = Icons.Default.Favorite,
            badgeColor = Color(0xFF00B0FF)
        )

        // Tip Card 6
        TipDetailCard(
            title = "شرب الماء بكثافة وحرارة الصيف",
            description = "بسبب حرارة جو العراق العالية، يحبس الجسم السوائل للدفاع عن نفسه فتجد وزنك ثابتاً. لتسهيل نزول الـ 4 كيلوغرامات بمرونة تامة، التزم بشرب 8 إلى 12 كوب ماء يومياً، وبخاصة بارداً، فالجسم يستهلك طاقة لتعديل حرارة الماء البارد فيسرع حرق السعرات الحرارية تلقائياً!",
            icon = Icons.Default.Star,
            badgeColor = Color(0xFF29B6F6)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun TipDetailCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(badgeColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    title,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
