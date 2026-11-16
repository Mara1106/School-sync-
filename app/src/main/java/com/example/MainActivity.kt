package com.example

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.onGoogleSignInClicked
import com.example.auth.signOutUser
import com.example.data.model.Course
import com.example.data.model.StudyItem
import com.example.data.model.SubjectGrade
import com.example.data.model.UiState
import com.example.data.model.UserPreferences
import com.example.data.repository.LocalStudyRepository
import com.example.data.repository.StudyRepository
import com.example.notifications.NotificationHelper
import com.example.ui.StudyViewModel
import com.example.ui.components.CourseEditorDialog
import com.example.ui.components.StudyItemEditorSheet
import com.example.ui.screens.AlertsAndSettingsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.CoursesScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.GradesScreen
import com.example.ui.screens.StudentProfileScreen
import com.example.ui.screens.SubjectGradeEditorSheet
import com.example.ui.theme.StudySyncTheme
import com.example.ui.theme.ThemePreferencesStore
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

enum class StudyTab(
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  SCHEDULE(R.string.nav_today, Icons.Filled.Today, Icons.Outlined.Today),
  CALENDAR(R.string.nav_calendar, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
  COURSES(R.string.nav_courses, Icons.Filled.School, Icons.Outlined.School),
  GRADES(R.string.nav_grades, Icons.Filled.Grade, Icons.Outlined.Grade),
  ALERTS(R.string.nav_alerts, Icons.Filled.Notifications, Icons.Outlined.Notifications),
}

private const val SESSION_PREFS = "studysync_session"
private const val KEY_GUEST_MODE = "guest_mode_enabled"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    NotificationHelper.ensureChannels(this)
    ThemePreferencesStore.initialize(this)
    enableEdgeToEdge()
    setContent {
      val themeState by ThemePreferencesStore.state.collectAsStateWithLifecycle()
      StudySyncTheme(themeState = themeState) {
        AppNavigation()
      }
    }
  }
}

@Composable
fun AppNavigation() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val sessionPrefs = remember(context) {
    context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
  }

  var guestModeEnabled by remember {
    mutableStateOf(sessionPrefs.getBoolean(KEY_GUEST_MODE, false))
  }

  val currentUser by Firebase.auth.authStateFlow().collectAsStateWithLifecycle(
    initialValue = Firebase.auth.currentUser
  )

  fun setGuestMode(enabled: Boolean) {
    guestModeEnabled = enabled
    sessionPrefs.edit().putBoolean(KEY_GUEST_MODE, enabled).apply()
  }

  LaunchedEffect(Unit) {
    if (Firebase.auth.currentUser == null && !guestModeEnabled) {
      attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = {},
        onUnauthenticated = {},
        scope = scope,
      )
    }
  }

  // Migrate local guest items into Firestore if the user signs in with Google later
  val user = currentUser
  LaunchedEffect(user?.uid) {
    val uid = user?.uid ?: return@LaunchedEffect
    setGuestMode(false)
    val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    LocalStudyRepository(context).migrateGuestDataToCloud(StudyRepository(db), uid)
  }

  when {
    user != null -> {
      key(user.uid) {
        StudySyncHome(
          currentUserId = user.uid,
          isGuestMode = false,
          userDisplayName = user.displayName,
          userEmail = user.email,
          onRequestGoogleSignIn = {},
          onExitGuestMode = {},
          onSignOut = {
            setGuestMode(false)
            signOutUser(credentialManager = credentialManager, scope = scope)
          },
        )
      }
    }
    guestModeEnabled -> {
      key(LocalStudyRepository.LOCAL_USER_ID) {
        StudySyncHome(
          currentUserId = LocalStudyRepository.LOCAL_USER_ID,
          isGuestMode = true,
          userDisplayName = "Student",
          userEmail = null,
          onRequestGoogleSignIn = { onError ->
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = { setGuestMode(false) },
              onAuthError = onError,
              scope = scope,
            )
          },
          onExitGuestMode = { setGuestMode(false) },
          onSignOut = { setGuestMode(false) },
        )
      }
    }
    else -> {
      AuthScreen(
        onAuthSuccess = { setGuestMode(false) },
        onContinueWithoutSignIn = { setGuestMode(true) },
      )
    }
  }
}

@Composable
fun StudySyncHome(
  currentUserId: String,
  isGuestMode: Boolean,
  userDisplayName: String?,
  userEmail: String?,
  onRequestGoogleSignIn: (onError: (String) -> Unit) -> Unit,
  onExitGuestMode: () -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val viewModel: StudyViewModel = viewModel(
    key = "$currentUserId:$isGuestMode",
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        val dataSource =
          if (isGuestMode) {
            LocalStudyRepository(app)
          } else {
            val databaseId = app.getString(R.string.firestore_database_id)
            StudyRepository(FirebaseFirestore.getInstance(databaseId))
          }
        StudyViewModel(
          repository = dataSource,
          currentUserId = currentUserId,
          isGuestMode = isGuestMode,
          appContext = app,
        )
      }
    },
  )

  val coursesState by viewModel.coursesState.collectAsStateWithLifecycle()
  val itemsState by viewModel.itemsState.collectAsStateWithLifecycle()
  val filteredItemsState by viewModel.filteredItemsState.collectAsStateWithLifecycle()
  val preferencesState by viewModel.preferencesState.collectAsStateWithLifecycle()
  val gradesState by viewModel.gradesState.collectAsStateWithLifecycle()
  val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
  val selectedCourseCode by viewModel.selectedCourseCode.collectAsStateWithLifecycle()
  val calendarState by viewModel.calendarState.collectAsStateWithLifecycle()

  var currentTab by remember { mutableStateOf(StudyTab.SCHEDULE) }
  var showSettingsMenu by remember { mutableStateOf(false) }
  var showProfileScreen by remember { mutableStateOf(false) }
  var expandManualInSettings by remember { mutableStateOf(false) }
  var editingItem by remember { mutableStateOf<StudyItem?>(null) }
  var showNewItemSheet by remember { mutableStateOf(false) }
  var editingCourse by remember { mutableStateOf<Course?>(null) }
  var showNewCourseDialog by remember { mutableStateOf(false) }
  var editingGrade by remember { mutableStateOf<SubjectGrade?>(null) }
  var showGradeSheet by remember { mutableStateOf(false) }

  var hasNotificationPermission by remember {
    mutableStateOf(NotificationHelper.hasNotificationPermission(context))
  }

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasNotificationPermission = granted || NotificationHelper.hasNotificationPermission(context)
    }

  val calendarConsentLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
      viewModel.onCalendarConsentResult(result.data)
    }

  val snackbarHostState = remember { SnackbarHostState() }

  fun triggerSignInUpgrade() {
    onRequestGoogleSignIn { errorMsg ->
      scope.launch { snackbarHostState.showSnackbar(errorMsg) }
    }
  }

  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  LaunchedEffect(viewModel) {
    viewModel.pendingCalendarConsent.collect { pendingIntent ->
      calendarConsentLauncher.launch(
        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
      )
    }
  }

  LaunchedEffect(viewModel) {
    viewModel.snackbarMessages.collect { message ->
      snackbarHostState.showSnackbar(message)
    }
  }

  if (currentTab != StudyTab.SCHEDULE) {
    BackHandler { currentTab = StudyTab.SCHEDULE }
  }

  val prefsSnapshot = (preferencesState as? UiState.Success)?.data ?: UserPreferences()
  val resolvedStudentName =
    prefsSnapshot.studentName.takeIf { it.isNotBlank() } ?: userDisplayName ?: "Student"
  val avatarInitial = resolvedStudentName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S"

  if (showProfileScreen) {
    val coursesCount = (coursesState as? UiState.Success)?.data?.size ?: 0
    val gradesCount = (gradesState as? UiState.Success)?.data?.size ?: 0
    StudentProfileScreen(
      preferences = prefsSnapshot,
      fallbackDisplayName = userDisplayName,
      userEmail = userEmail,
      isGuestMode = isGuestMode,
      coursesCount = coursesCount,
      gradesCount = gradesCount,
      onSaveProfile = viewModel::updateStudentProfile,
      onBack = { showProfileScreen = false },
    )
    return
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 600.dp

    Scaffold(
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      contentWindowInsets = WindowInsets.navigationBars,
      topBar = {
        @OptIn(ExperimentalMaterial3Api::class)
        TopAppBar(
          windowInsets = WindowInsets.statusBars,
          title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "StudySync",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
              ) {
                Text(
                  text = stringResource(currentTab.labelRes),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
            }
          },
          actions = {
            // Quick Profile Avatar pill
            Surface(
              onClick = { showProfileScreen = true },
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = CircleShape,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("top_profile_avatar_button"),
            ) {
              Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = avatarInitial,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
            }

            // Menu Button for Settings & Profile
            Box {
              IconButton(
                onClick = { showSettingsMenu = true },
                modifier = Modifier.testTag("settings_menu_button"),
              ) {
                Icon(
                  imageVector = Icons.Default.Menu,
                  contentDescription = stringResource(R.string.menu_settings),
                )
              }

              DropdownMenu(
                expanded = showSettingsMenu,
                onDismissRequest = { showSettingsMenu = false },
                modifier = Modifier.testTag("settings_menu_dropdown"),
              ) {
                DropdownMenuItem(
                  text = {
                    Column {
                      Text(
                        text = resolvedStudentName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text =
                          prefsSnapshot.studentBio.takeIf { it.isNotBlank() }
                            ?: prefsSnapshot.studentMajor.takeIf { it.isNotBlank() }
                            ?: "Student Profile & Bio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  },
                  leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                  },
                  onClick = {
                    showSettingsMenu = false
                    showProfileScreen = true
                  },
                  modifier = Modifier.testTag("menu_item_profile"),
                )
                HorizontalDivider()
                DropdownMenuItem(
                  text = { Text("Theme (Suggested & Manual)") },
                  leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                  onClick = {
                    showSettingsMenu = false
                    expandManualInSettings = false
                    currentTab = StudyTab.ALERTS
                  },
                  modifier = Modifier.testTag("menu_item_theme"),
                )
                DropdownMenuItem(
                  text = { Text("All Settings & Alerts") },
                  leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                  onClick = {
                    showSettingsMenu = false
                    expandManualInSettings = false
                    currentTab = StudyTab.ALERTS
                  },
                  modifier = Modifier.testTag("menu_item_settings"),
                )
                DropdownMenuItem(
                  text = { Text("StudySync Manual & Guide") },
                  leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                  },
                  onClick = {
                    showSettingsMenu = false
                    expandManualInSettings = true
                    currentTab = StudyTab.ALERTS
                  },
                  modifier = Modifier.testTag("menu_item_manual"),
                )
                HorizontalDivider()
                DropdownMenuItem(
                  text = {
                    Text(if (isGuestMode) "Sign in with Google" else stringResource(R.string.sign_out))
                  },
                  leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                  },
                  onClick = {
                    showSettingsMenu = false
                    if (isGuestMode) {
                      triggerSignInUpgrade()
                    } else {
                      onSignOut()
                    }
                  },
                  modifier = Modifier.testTag("menu_item_account"),
                )
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
      },
      floatingActionButton = {
        if (currentTab == StudyTab.SCHEDULE || currentTab == StudyTab.CALENDAR) {
          FloatingActionButton(
            onClick = {
              editingItem = null
              showNewItemSheet = true
            },
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.testTag("add_study_item_fab"),
          ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_study_item))
          }
        }
      },
      bottomBar = {
        if (!useNavRail) {
          NavigationBar(windowInsets = WindowInsets.navigationBars) {
            StudyTab.entries.forEach { tab ->
              val selected = currentTab == tab
              NavigationBarItem(
                selected = selected,
                onClick = { currentTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(tab.labelRes),
                  )
                },
                label = { Text(stringResource(tab.labelRes)) },
                modifier = Modifier.testTag("nav_${tab.name.lowercase()}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useNavRail) {
          NavigationRail {
            StudyTab.entries.forEach { tab ->
              val selected = currentTab == tab
              NavigationRailItem(
                selected = selected,
                onClick = { currentTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(tab.labelRes),
                  )
                },
                label = { Text(stringResource(tab.labelRes)) },
                modifier = Modifier.testTag("rail_${tab.name.lowercase()}"),
              )
            }
          }
        }

        when (currentTab) {
          StudyTab.SCHEDULE -> {
            DashboardScreen(
              userDisplayName = userDisplayName,
              isGuestMode = isGuestMode,
              allItemsState = itemsState,
              filteredItemsState = filteredItemsState,
              coursesState = coursesState,
              preferencesState = preferencesState,
              selectedFilter = selectedFilter,
              selectedCourseCode = selectedCourseCode,
              calendarState = calendarState,
              onSelectFilter = viewModel::setFilter,
              onSelectCourse = viewModel::setCourseFilter,
              onToggleComplete = viewModel::toggleCompleted,
              onEditItem = { item ->
                editingItem = item
                showNewItemSheet = true
              },
              onOpenNewItem = {
                editingItem = null
                showNewItemSheet = true
              },
              onCheckInStreak = viewModel::recordDailyStudyCheckIn,
              onSyncCalendarClick = {
                if (isGuestMode) triggerSignInUpgrade() else viewModel.startGoogleCalendarSync()
              },
              onOpenAlertsClick = { currentTab = StudyTab.ALERTS },
            )
          }
          StudyTab.CALENDAR -> {
            CalendarScreen(
              isGuestMode = isGuestMode,
              itemsState = itemsState,
              preferencesState = preferencesState,
              calendarState = calendarState,
              onSyncNow = viewModel::startGoogleCalendarSync,
              onSignInForCalendar = ::triggerSignInUpgrade,
              onToggleAutoSync = { enabled ->
                viewModel.updatePreferences { it.copy(googleCalendarAutoSync = enabled) }
              },
              onToggleComplete = viewModel::toggleCompleted,
              onEditItem = { item ->
                editingItem = item
                showNewItemSheet = true
              },
              onImportRemoteEvent = viewModel::importGoogleCalendarEvent,
            )
          }
          StudyTab.COURSES -> {
            CoursesScreen(
              coursesState = coursesState,
              itemsState = itemsState,
              onAddCourse = {
                editingCourse = null
                showNewCourseDialog = true
              },
              onEditCourse = { course ->
                editingCourse = course
                showNewCourseDialog = true
              },
              onDeleteCourse = viewModel::deleteCourse,
            )
          }
          StudyTab.GRADES -> {
            GradesScreen(
              gradesState = gradesState,
              onAddGrade = {
                editingGrade = null
                showGradeSheet = true
              },
              onEditGrade = { grade ->
                editingGrade = grade
                showGradeSheet = true
              },
              onDeleteGrade = viewModel::deleteGrade,
            )
          }
          StudyTab.ALERTS -> {
            AlertsAndSettingsScreen(
              isGuestMode = isGuestMode,
              userEmail = userEmail,
              userName = userDisplayName,
              hasNotificationPermission = hasNotificationPermission,
              preferencesState = preferencesState,
              itemsState = itemsState,
              expandManualOnLaunch = expandManualInSettings,
              onOpenProfile = { showProfileScreen = true },
              onRequestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
              },
              onUpdatePreferences = viewModel::updatePreferences,
              onUpdateTheme = viewModel::updateThemeSettings,
              onApplySuggestedTheme = viewModel::applySuggestedTheme,
              onSendTestNotification = viewModel::sendTestNotification,
              onSignInWithGoogle = ::triggerSignInUpgrade,
              onExitGuestMode = onExitGuestMode,
              onSignOut = onSignOut,
            )
          }
        }
      }
    }
  }

  if (showNewItemSheet) {
    val prefs = (preferencesState as? UiState.Success)?.data
    StudyItemEditorSheet(
      initialItem = editingItem,
      courses = (coursesState as? UiState.Success)?.data.orEmpty(),
      defaultHomeworkLead = prefs?.defaultHomeworkReminderMinutes ?: 60L,
      defaultExamLead = prefs?.defaultExamReminderMinutes ?: 1440L,
      onDismiss = {
        showNewItemSheet = false
        editingItem = null
      },
      onSave = viewModel::saveStudyItem,
      onDelete = viewModel::deleteStudyItem,
      onQuickCreateCourse = {
        editingCourse = null
        showNewCourseDialog = true
      },
    )
  }

  if (showNewCourseDialog) {
    CourseEditorDialog(
      initialCourse = editingCourse,
      onDismiss = {
        showNewCourseDialog = false
        editingCourse = null
      },
      onSave = viewModel::saveCourse,
    )
  }

  if (showGradeSheet) {
    SubjectGradeEditorSheet(
      initialGrade = editingGrade,
      courses = (coursesState as? UiState.Success)?.data.orEmpty(),
      onDismiss = {
        showGradeSheet = false
        editingGrade = null
      },
      onSave = viewModel::saveGrade,
      onDelete = viewModel::deleteGrade,
    )
  }
}
