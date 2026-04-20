package com.stugram.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.remote.model.ProfileModel
import com.stugram.app.data.remote.model.UpdateProfileRequest
import com.stugram.app.data.repository.ProfileRepository
import com.stugram.app.data.repository.UploadOutcome
import com.stugram.app.data.repository.toUploadOutcome
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val regionOptions = listOf(
    "Tashkent City",
    "Tashkent",
    "Samarkand",
    "Andijan",
    "Bukhara",
    "Fergana",
    "Namangan"
)

private val districtOptions = mapOf(
    "Tashkent City" to listOf("Yunusabad", "Chilonzor", "Mirzo Ulugbek", "Olmazor"),
    "Tashkent" to listOf("Zangiota", "Qibray", "Chirchiq", "Yangiyul"),
    "Samarkand" to listOf("Samarkand City", "Urgut", "Pastdargom", "Jomboy"),
    "Andijan" to listOf("Andijan City", "Asaka", "Shahrixon", "Baliqchi"),
    "Bukhara" to listOf("Bukhara City", "Gijduvon", "Kogon", "Romitan"),
    "Fergana" to listOf("Fergana City", "Kokand", "Margilan", "Rishton"),
    "Namangan" to listOf("Namangan City", "Chust", "Kosonsoy", "Pop")
)

private val schoolOptions = mapOf(
    "Yunusabad" to listOf("School No. 5", "School No. 17", "School No. 220", "Westminster Academic Lyceum"),
    "Chilonzor" to listOf("School No. 90", "School No. 173", "School No. 200", "Chilonzor IT School"),
    "Mirzo Ulugbek" to listOf("School No. 121", "School No. 142", "School No. 187", "International House School"),
    "Olmazor" to listOf("School No. 31", "School No. 94", "School No. 243", "Al-Beruniy School"),
    "Samarkand City" to listOf("School No. 1", "School No. 8", "School No. 33", "Samarkand Academic Lyceum"),
    "Andijan City" to listOf("School No. 4", "School No. 14", "School No. 35", "Andijan Presidential School"),
    "Fergana City" to listOf("School No. 2", "School No. 12", "School No. 28", "Fergana State Lyceum"),
    "Namangan City" to listOf("School No. 3", "School No. 10", "School No. 18", "Namangan IT School")
)

private val gradeOptions = listOf(
    "7-grade",
    "8-grade",
    "9-grade",
    "10-grade",
    "11-grade",
    "1-course",
    "2-course",
    "3-course",
    "4-course"
)

private val groupOptions = listOf("A", "B", "C", "D", "101", "102", "201", "202")

private val birthdayDisplayFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
private val birthdayApiFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

private enum class EditSelectionSheet {
    Birthday,
    Region,
    District,
    School,
    Grade,
    Group
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    isDarkMode: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenManager = remember { TokenManager(context.applicationContext) }
    val profileRepository = remember { ProfileRepository() }
    val currentUser by tokenManager.currentUser.collectAsState(initial = null)
    val snackbarHostState = remember { SnackbarHostState() }

    val backgroundColor = if (isDarkMode) Color(0xFF0F0F0F) else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val accentBlue = Color(0xFF00A3FF)
    val fieldColor = if (isDarkMode) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)

    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var birthdayValue by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var school by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }

    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var bannerUrl by remember { mutableStateOf<String?>(null) }
    var avatarPreviewUri by remember { mutableStateOf<Uri?>(null) }
    var bannerPreviewUri by remember { mutableStateOf<Uri?>(null) }

    var isSaving by remember { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var isUploadingBanner by remember { mutableStateOf(false) }
    var selectionSheet by remember { mutableStateOf<EditSelectionSheet?>(null) }
    var showBirthdayPicker by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser?.id) {
        fullName = currentUser?.fullName.orEmpty()
        username = currentUser?.username.orEmpty()
        bio = currentUser?.bio.orEmpty()
        birthdayValue = currentUser?.birthday.orEmpty()
        region = currentUser?.region.orEmpty()
        district = currentUser?.district.orEmpty()
        school = currentUser?.school.orEmpty()
        grade = currentUser?.grade.orEmpty()
        group = currentUser?.group.orEmpty()
        avatarUrl = currentUser?.avatar
        bannerUrl = currentUser?.banner
        avatarPreviewUri = null
        bannerPreviewUri = null
    }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val bannerHeight = screenWidth * 500f / 1080f
    val avatarSize = 110.dp

    val districtList = districtOptions[region].orEmpty()
    val schoolList = schoolOptions[district].orElseEmpty()
    val saveEnabled = fullName.trim().length >= 2 && username.trim().length >= 3 && !isSaving && !isUploadingAvatar && !isUploadingBanner

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            avatarPreviewUri = uri
            scope.launch {
                isUploadingAvatar = true
                try {
                    val response = profileRepository.uploadAvatar(context, uri)
                    when (val outcome = response.toUploadOutcome("Avatar upload failed")) {
                        is UploadOutcome.Success -> {
                            tokenManager.updateCurrentUser(outcome.data)
                            avatarUrl = outcome.data.avatar
                            avatarPreviewUri = null
                            snackbarHostState.showSnackbar(outcome.message ?: "Avatar updated")
                        }
                        is UploadOutcome.Failure -> {
                            avatarPreviewUri = null
                            snackbarHostState.showSnackbar(outcome.message)
                        }
                    }
                } catch (e: Exception) {
                    avatarPreviewUri = null
                    snackbarHostState.showSnackbar(e.localizedMessage ?: "Avatar upload failed")
                }
                isUploadingAvatar = false
            }
        }
    }

    val bannerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            bannerPreviewUri = uri
            scope.launch {
                isUploadingBanner = true
                try {
                    val response = profileRepository.uploadBanner(context, uri)
                    when (val outcome = response.toUploadOutcome("Banner upload failed")) {
                        is UploadOutcome.Success -> {
                            tokenManager.updateCurrentUser(outcome.data)
                            bannerUrl = outcome.data.banner
                            bannerPreviewUri = null
                            snackbarHostState.showSnackbar(outcome.message ?: "Banner updated")
                        }
                        is UploadOutcome.Failure -> {
                            bannerPreviewUri = null
                            snackbarHostState.showSnackbar(outcome.message)
                        }
                    }
                } catch (e: Exception) {
                    bannerPreviewUri = null
                    snackbarHostState.showSnackbar(e.localizedMessage ?: "Banner upload failed")
                }
                isUploadingBanner = false
            }
        }
    }

    fun locationSummary(): String = listOf(district.takeIf { it.isNotBlank() }, region.takeIf { it.isNotBlank() })
        .filterNotNull()
        .joinToString(", ")

    fun submitProfileUpdate() {
        if (!saveEnabled) return
        val normalizedUsername = username.trim().removePrefix("@")
        if (!normalizedUsername.matches(Regex("^[A-Za-z0-9._]{3,30}$"))) {
            scope.launch { snackbarHostState.showSnackbar("Username can only use letters, numbers, dots, and underscores.") }
            return
        }

        scope.launch {
            isSaving = true
            val response = runCatching {
                profileRepository.updateProfile(
                    UpdateProfileRequest(
                        fullName = fullName.trim(),
                        username = normalizedUsername,
                        bio = bio.trim().ifBlank { "" },
                        birthday = birthdayValue.ifBlank { null },
                        location = locationSummary().ifBlank { null },
                        school = school.ifBlank { null },
                        region = region.ifBlank { null },
                        district = district.ifBlank { null },
                        grade = grade.ifBlank { null },
                        group = group.ifBlank { null }
                    )
                )
            }.getOrNull()

            val updatedProfile = response?.body()?.data
            if (response?.isSuccessful == true && updatedProfile != null) {
                tokenManager.updateCurrentUser(updatedProfile)
                snackbarHostState.showSnackbar("Profile updated")
                onSave()
            } else {
                snackbarHostState.showSnackbar(response?.body()?.message ?: "Could not save profile changes")
            }
            isSaving = false
        }
    }

    if (showBirthdayPicker) {
        BirthdayPickerDialog(
            initialValue = birthdayValue,
            onDismiss = { showBirthdayPicker = false },
            onConfirm = {
                birthdayValue = it
                showBirthdayPicker = false
            },
            accentBlue = accentBlue
        )
    }

    selectionSheet?.let { sheet ->
        SelectionBottomSheet(
            title = when (sheet) {
                EditSelectionSheet.Region -> "Select region"
                EditSelectionSheet.District -> "Select district"
                EditSelectionSheet.School -> "Select school"
                EditSelectionSheet.Grade -> "Select grade"
                EditSelectionSheet.Group -> "Select group"
                EditSelectionSheet.Birthday -> "Select birthday"
            },
            options = when (sheet) {
                EditSelectionSheet.Region -> regionOptions
                EditSelectionSheet.District -> districtList
                EditSelectionSheet.School -> schoolList
                EditSelectionSheet.Grade -> gradeOptions
                EditSelectionSheet.Group -> groupOptions
                EditSelectionSheet.Birthday -> emptyList()
            },
            selectedValue = when (sheet) {
                EditSelectionSheet.Region -> region
                EditSelectionSheet.District -> district
                EditSelectionSheet.School -> school
                EditSelectionSheet.Grade -> grade
                EditSelectionSheet.Group -> group
                EditSelectionSheet.Birthday -> birthdayValue
            },
            accentBlue = accentBlue,
            onDismiss = { selectionSheet = null },
            onOptionSelected = { selected ->
                when (sheet) {
                    EditSelectionSheet.Region -> {
                        region = selected
                        district = ""
                        school = ""
                    }
                    EditSelectionSheet.District -> {
                        district = selected
                        school = ""
                    }
                    EditSelectionSheet.School -> school = selected
                    EditSelectionSheet.Grade -> group = group.takeIf { it in groupOptions }.orEmpty().also { grade = selected }
                    EditSelectionSheet.Group -> group = selected
                    EditSelectionSheet.Birthday -> Unit
                }
                selectionSheet = null
            }
        )
    }

    Scaffold(
        containerColor = backgroundColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    TextButton(onClick = ::submitProfileUpdate, enabled = saveEnabled) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accentBlue)
                        } else {
                            Text("Done", color = accentBlue, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = contentColor,
                    navigationIconContentColor = contentColor
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bannerHeight + (avatarSize / 2))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bannerHeight)
                        .background(fieldColor)
                ) {
                    if (bannerPreviewUri != null) {
                        AsyncImage(
                            model = bannerPreviewUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        AppBanner(
                            imageModel = bannerUrl,
                            title = fullName,
                            modifier = Modifier.fillMaxSize(),
                            isDarkMode = isDarkMode
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(0.4f))
                            .clickable(enabled = !isUploadingBanner) {
                                bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isUploadingBanner) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Edit Cover", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(avatarSize)
                        .background(backgroundColor, CircleShape)
                        .padding(4.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize().clip(CircleShape)) {
                        if (avatarPreviewUri != null) {
                            AsyncImage(
                                model = avatarPreviewUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            AppAvatar(
                                imageModel = avatarUrl,
                                name = fullName,
                                username = username,
                                modifier = Modifier.fillMaxSize(),
                                isDarkMode = isDarkMode,
                                fontSize = 30.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(0.4f))
                                .clickable(enabled = !isUploadingAvatar) {
                                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isUploadingAvatar) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("Public Information", color = accentBlue, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))

                EditField(label = "Name", value = fullName, onValueChange = { fullName = it }, isDarkMode = isDarkMode)
                Spacer(modifier = Modifier.height(16.dp))
                EditField(
                    label = "Username",
                    value = username,
                    onValueChange = { username = it.removePrefix("@") },
                    isDarkMode = isDarkMode,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
                )
                Spacer(modifier = Modifier.height(16.dp))
                EditField(label = "Bio", value = bio, onValueChange = { bio = it }, isDarkMode = isDarkMode, singleLine = false)

                Spacer(modifier = Modifier.height(32.dp))

                Text("Personal Details", color = accentBlue, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))

                SelectionField(
                    label = "Birthday",
                    value = birthdayValue.toBirthdayDisplay(),
                    placeholder = "Select birthday",
                    isDarkMode = isDarkMode,
                    icon = Icons.Default.Event,
                    onClick = { showBirthdayPicker = true }
                )
                Spacer(modifier = Modifier.height(16.dp))
                SelectionField(
                    label = "Region / Viloyat",
                    value = region,
                    placeholder = "Select region",
                    isDarkMode = isDarkMode,
                    icon = Icons.Default.LocationCity,
                    onClick = { selectionSheet = EditSelectionSheet.Region }
                )
                Spacer(modifier = Modifier.height(16.dp))
                SelectionField(
                    label = "District / Tuman",
                    value = district,
                    placeholder = if (region.isBlank()) "Select region first" else "Select district",
                    isDarkMode = isDarkMode,
                    icon = Icons.Default.LocationCity,
                    enabled = region.isNotBlank(),
                    onClick = { selectionSheet = EditSelectionSheet.District }
                )
                Spacer(modifier = Modifier.height(16.dp))
                SelectionField(
                    label = "School / Maktab",
                    value = school,
                    placeholder = if (district.isBlank()) "Select district first" else "Select school",
                    isDarkMode = isDarkMode,
                    icon = Icons.Default.School,
                    enabled = district.isNotBlank() && schoolList.isNotEmpty(),
                    onClick = { selectionSheet = EditSelectionSheet.School }
                )
                Spacer(modifier = Modifier.height(16.dp))
                SelectionField(
                    label = "Grade / Class / Kurs",
                    value = grade,
                    placeholder = "Select grade",
                    isDarkMode = isDarkMode,
                    icon = Icons.Default.School,
                    onClick = { selectionSheet = EditSelectionSheet.Grade }
                )
                Spacer(modifier = Modifier.height(16.dp))
                SelectionField(
                    label = "Group",
                    value = group,
                    placeholder = "Select group",
                    isDarkMode = isDarkMode,
                    icon = Icons.Default.School,
                    onClick = { selectionSheet = EditSelectionSheet.Group }
                )

                Spacer(modifier = Modifier.height(16.dp))
                ReadOnlyInfoField(
                    label = "Location summary",
                    value = locationSummary().ifBlank { "Will be saved from region and district" },
                    isDarkMode = isDarkMode
                )

                Spacer(modifier = Modifier.height(40.dp))

                Button(
                    onClick = ::submitProfileUpdate,
                    enabled = saveEnabled,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentBlue,
                        disabledContainerColor = accentBlue.copy(alpha = 0.4f)
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text("Save Changes", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}

@Composable
fun EditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isDarkMode: Boolean,
    singleLine: Boolean = true,
    icon: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val fieldColor = if (isDarkMode) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)

    Column {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = icon?.let { { Icon(it, null, tint = Color.Gray, modifier = Modifier.size(20.dp)) } },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = fieldColor,
                unfocusedContainerColor = fieldColor,
                disabledContainerColor = fieldColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Color(0xFF00A3FF),
                focusedTextColor = contentColor,
                unfocusedTextColor = contentColor
            ),
            shape = RoundedCornerShape(16.dp),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            keyboardOptions = keyboardOptions
        )
    }
}

@Composable
private fun SelectionField(
    label: String,
    value: String,
    placeholder: String,
    isDarkMode: Boolean,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val fieldColor = if (isDarkMode) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
    val contentColor = if (isDarkMode) Color.White else Color.Black

    Column {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = fieldColor,
            onClick = onClick,
            enabled = enabled
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = value.ifBlank { placeholder },
                    color = if (value.isBlank()) contentColor.copy(alpha = 0.45f) else contentColor,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ArrowDropDown, null, tint = if (enabled) Color.Gray else Color.Gray.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun ReadOnlyInfoField(
    label: String,
    value: String,
    isDarkMode: Boolean
) {
    val fieldColor = if (isDarkMode) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
    val contentColor = if (isDarkMode) Color.White else Color.Black

    Column {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = fieldColor) {
            Text(
                text = value,
                color = contentColor.copy(alpha = 0.72f),
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBottomSheet(
    title: String,
    options: List<String>,
    selectedValue: String,
    accentBlue: Color,
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF151515)) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            options.forEach { option ->
                Surface(
                    onClick = { onOptionSelected(option) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (option == selectedValue) accentBlue.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(option, color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        if (option == selectedValue) {
                            Icon(Icons.Default.CameraAlt, null, tint = accentBlue, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayPickerDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    accentBlue: Color
) {
    val initialMillis = remember(initialValue) {
        runCatching { birthdayApiFormat.parse(initialValue)?.time }.getOrNull()
    }
    val datePickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onConfirm(birthdayApiFormat.format(Date(millis)))
                    }
                }
            ) {
                Text("Select", color = accentBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    ) {
        androidx.compose.material3.DatePicker(state = datePickerState)
    }
}

private fun List<String>?.orElseEmpty(): List<String> = this ?: emptyList()

private fun String.toBirthdayDisplay(): String {
    if (isBlank()) return ""
    return runCatching {
        val parsed = birthdayApiFormat.parse(this)
        if (parsed != null) birthdayDisplayFormat.format(parsed) else this
    }.getOrDefault(this)
}
