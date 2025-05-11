package com.example.checklist

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.*
import com.example.checklist.ui.theme.ChecklistTheme
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import android.util.Log
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

class MainActivity : ComponentActivity(), CoroutineScope by MainScope() {
    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
            showPermissionGrantedSnackbar()
        } else {
            // Permission denied
            showPermissionDeniedSnackbar()
        }
    }

    private lateinit var snackbarHostState: SnackbarHostState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create notification channel
        createNotificationChannel()

        setContent {
            snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            ChecklistTheme {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    content = { padding ->
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            val checklistViewModel: ChecklistViewModel = viewModel()
                            ChecklistApp(
                                viewModel = checklistViewModel,
                                onNeedPermission = {
                                    checkAndRequestNotificationPermission()
                                }
                            )
                        }
                    }
                )
            }
        }

        // Check for notification permission on startup
        checkAndRequestNotificationPermission()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(1000, 1000, 1000, 1000)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission is already granted
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show rationale and request permission
                    showPermissionRationaleSnackbar()
                }
                else -> {
                    // Directly request permission
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun showPermissionRationaleSnackbar() {
        launch {
            val result = snackbarHostState.showSnackbar(
                message = "Notifications permission is needed for reminders",
                actionLabel = "Grant",
                duration = SnackbarDuration.Long
            )

            if (result == SnackbarResult.ActionPerformed) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showPermissionGrantedSnackbar() {
        launch {
            snackbarHostState.showSnackbar(
                message = "Notification permission granted",
                duration = SnackbarDuration.Short
            )
        }
    }

    private fun showPermissionDeniedSnackbar() {
        launch {
            val result = snackbarHostState.showSnackbar(
                message = "Notifications won't work without permission",
                actionLabel = "Settings",
                duration = SnackbarDuration.Long
            )

            if (result == SnackbarResult.ActionPerformed) {
                // Open app settings
                openAppSettings()
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "checklist_reminders"
    }
}

// Add the missing DismissValue and DismissDirection enums
enum class DismissValue {
    Default,
    DismissedToStart,
    DismissedToEnd
}

enum class DismissDirection {
    StartToEnd,
    EndToStart
}

// Add the missing DismissState class
@Composable
fun rememberDismissState(
    initialValue: DismissValue = DismissValue.Default,
    confirmValueChange: (DismissValue) -> Boolean = { true }
): DismissState {
    return remember {
        DismissState(
            initialValue = initialValue,
            confirmValueChange = confirmValueChange
        )
    }
}

class DismissState(
    initialValue: DismissValue,
    val confirmValueChange: (DismissValue) -> Boolean
) {
    var currentValue by mutableStateOf(initialValue)
    var targetValue by mutableStateOf(initialValue)
    var dismissDirection by mutableStateOf<DismissDirection?>(null)
}

@Composable
fun SwipeToDismiss(
    state: DismissState,
    directions: Set<DismissDirection> = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
    background: @Composable () -> Unit,
    dismissContent: @Composable () -> Unit
) {
    val offsetX = remember { mutableFloatStateOf(0f) }
    val screenWidth = LocalContext.current.resources.displayMetrics.widthPixels
    val thresholdPx = screenWidth * 0.25f // 25% of screen width as threshold

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.floatValue.roundToInt(), 0) }
            .draggable(
                state = rememberDraggableState { delta ->
                    // Only update offset if the direction is allowed
                    val canDragRight = directions.contains(DismissDirection.StartToEnd)
                    val canDragLeft = directions.contains(DismissDirection.EndToStart)

                    when {
                        delta > 0 && canDragRight -> { // Dragging to the right
                            val newValue = (offsetX.floatValue + delta).coerceAtMost(thresholdPx)
                            offsetX.floatValue = newValue

                            // Set dismiss direction for visual indication
                            if (newValue > 0) {
                                state.dismissDirection = DismissDirection.StartToEnd
                                state.targetValue = if (newValue > thresholdPx / 2)
                                    DismissValue.DismissedToEnd else DismissValue.Default
                            }
                        }
                        delta < 0 && canDragLeft -> { // Dragging to the left
                            val newValue = (offsetX.floatValue + delta).coerceAtLeast(-thresholdPx)
                            offsetX.floatValue = newValue

                            // Set dismiss direction for visual indication
                            if (newValue < 0) {
                                state.dismissDirection = DismissDirection.EndToStart
                                state.targetValue = if (newValue < -thresholdPx / 2)
                                    DismissValue.DismissedToStart else DismissValue.Default
                            }
                        }
                    }
                },
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    // When drag stops, check if we crossed the threshold
                    val isDismissedRight = offsetX.floatValue > thresholdPx / 2
                    val isDismissedLeft = offsetX.floatValue < -thresholdPx / 2

                    when {
                        isDismissedRight -> {
                            // Handle right swipe action
                            val canDismiss = state.confirmValueChange(DismissValue.DismissedToEnd)
                            if (canDismiss) {
                                state.currentValue = DismissValue.DismissedToEnd
                                // Keep it dismissed visually only if the swipe is allowed to complete
                            } else {
                                // Reset position if not allowed
                                offsetX.floatValue = 0f
                                state.dismissDirection = null
                                state.targetValue = DismissValue.Default
                                state.currentValue = DismissValue.Default
                            }
                        }
                        isDismissedLeft -> {
                            // Handle left swipe action
                            val canDismiss = state.confirmValueChange(DismissValue.DismissedToStart)
                            if (canDismiss) {
                                state.currentValue = DismissValue.DismissedToStart
                                // Keep it dismissed visually
                            } else {
                                // Reset position if not allowed
                                offsetX.floatValue = 0f
                                state.dismissDirection = null
                                state.targetValue = DismissValue.Default
                                state.currentValue = DismissValue.Default
                            }
                        }
                        else -> {
                            // Not swiped far enough, reset position
                            offsetX.floatValue = 0f
                            state.dismissDirection = null
                            state.targetValue = DismissValue.Default
                            state.currentValue = DismissValue.Default
                        }
                    }
                }
            )
    ) {
        background()
        dismissContent()
    }
}

@Composable
fun AlarmButton(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable { onToggle(!enabled) }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // The alarm icon
        Icon(
            imageVector = Icons.Default.Alarm,
            contentDescription = if (enabled) "Notifications enabled" else "Notifications disabled",
            tint = if (enabled) Color(0xFF4CAF50) else Color.Gray
        )

        // Diagonal line when disabled
        if (!enabled) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, size.height)
                }

                drawPath(
                    path = path,
                    color = Color(0xFFE91E63),
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
fun SwipeableChecklistItem(
    item: ChecklistItem,
    onItemCompleted: (Boolean) -> Unit,
    onItemDeleted: () -> Unit,
    onItemUndone: () -> Unit,
    onSettingsClicked: (ChecklistItem) -> Unit
) {
    val scope = rememberCoroutineScope()

    val dismissState = rememberDismissState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                DismissValue.DismissedToEnd -> {
                    // Right-to-left swipe (for marking as crossed out)
                    if (item.isMarkedComplete) {
                        // If already marked complete, we treat this as an "undone" action
                        onItemUndone()
                    } else {
                        // Otherwise, mark it as complete (crossed out)
                        scope.launch {
                            // Ensure notification is enabled
                            if (!item.isCompleted) {
                                onItemCompleted(true)
                                delay(100) // Small delay to ensure notification state is set first
                            }
                            // Now toggle the marked state
                            onItemCompleted(true) // Just in case
                            onItemUndone() // This will update the marked complete state
                        }
                    }
                    false // Don't dismiss, just handle the action
                }
                DismissValue.DismissedToStart -> {
                    // Left-to-right swipe (for deletion)
                    onItemDeleted()
                    true // Allow dismiss animation
                }
                else -> false
            }
        }
    )

    // Color for the background based on swipe direction
    val color by animateColorAsState(
        when (dismissState.targetValue) {
            DismissValue.Default -> MaterialTheme.colorScheme.surfaceVariant
            DismissValue.DismissedToEnd -> if (item.isMarkedComplete)
                Color(0xFF2196F3) // Blue for undo
            else
                Color(0xFF4CAF50) // Green for marking complete
            DismissValue.DismissedToStart -> Color(0xFFE91E63) // Red for delete
        },
        label = "background color"
    )

    // Set up swipe directions - allow both directions
    val directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart)

    SwipeToDismiss(
        state = dismissState,
        directions = directions,
        background = {
            val direction = dismissState.dismissDirection ?: return@SwipeToDismiss
            val alignment = when (direction) {
                DismissDirection.StartToEnd -> Alignment.CenterStart
                DismissDirection.EndToStart -> Alignment.CenterEnd
            }
            val icon = when (direction) {
                DismissDirection.StartToEnd -> if (item.isMarkedComplete)
                    Icons.Default.Refresh
                else
                    Icons.Default.Done
                DismissDirection.EndToStart -> Icons.Default.Delete
            }
            val scale by animateFloatAsState(
                if (dismissState.targetValue == DismissValue.Default) 0.75f else 1f,
                label = "icon scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = when (direction) {
                        DismissDirection.StartToEnd -> if (item.isMarkedComplete)
                            "Undo completion"
                        else
                            "Mark as completed"
                        DismissDirection.EndToStart -> "Delete item"
                    },
                    modifier = Modifier.scale(scale),
                    tint = Color.White
                )
            }
        },
        dismissContent = {
            ChecklistItemContent(
                item = item,
                onCheckedChange = onItemCompleted,
                onSettingsClicked = {
                    onSettingsClicked(item)
                }
            )
        }
    )
}

@Composable
fun ChecklistItemContent(
    item: ChecklistItem,
    onCheckedChange: (Boolean) -> Unit,
    onSettingsClicked: () -> Unit
) {
    var showSettingsDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Replace Checkbox with AlarmButton
            AlarmButton(
                enabled = item.isCompleted,
                onToggle = onCheckedChange
            )

            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.isMarkedComplete) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )

            // Settings icon
            IconButton(onClick = { showSettingsDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Item Settings"
                )
            }
        }
    }

    if (showSettingsDialog) {
        ItemSettingsDialog(
            item = item,
            onDismiss = { showSettingsDialog = false },
            onConfirm = { updatedItem ->
                onSettingsClicked()
                showSettingsDialog = false
            }
        )
    }
}

@Composable
fun ItemSettingsDialog(
    item: ChecklistItem,
    onDismiss: () -> Unit,
    onConfirm: (ChecklistItem) -> Unit
) {
    var notificationInterval by remember { mutableStateOf(item.notificationIntervalMinutes.toString()) }
    var selectedRepeatType by remember { mutableStateOf(item.repeatType) }
    var repeatHour by remember { mutableStateOf(item.repeatHour) }
    var repeatMinute by remember { mutableStateOf(item.repeatMinute) }

    val hourOptions = (0..23).toList()
    val minuteOptions = (0..55 step 5).toList()

    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notification Settings") },
        text = {
            Column(modifier = Modifier.padding(8.dp)) {
                // Notification interval setting
                Text(
                    text = "Notification Interval (minutes)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = notificationInterval,
                    onValueChange = {
                        // Only allow numbers
                        if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                            notificationInterval = it
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Repeat settings
                Text(
                    text = "Repeat Notifications",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column(modifier = Modifier.selectableGroup()) {
                    RepeatTypeOption(
                        text = "None",
                        selected = selectedRepeatType == RepeatType.NONE,
                        onClick = { selectedRepeatType = RepeatType.NONE }
                    )
                    RepeatTypeOption(
                        text = "Daily",
                        selected = selectedRepeatType == RepeatType.DAILY,
                        onClick = { selectedRepeatType = RepeatType.DAILY }
                    )
                    RepeatTypeOption(
                        text = "Weekly",
                        selected = selectedRepeatType == RepeatType.WEEKLY,
                        onClick = { selectedRepeatType = RepeatType.WEEKLY }
                    )
                    RepeatTypeOption(
                        text = "Monthly",
                        selected = selectedRepeatType == RepeatType.MONTHLY,
                        onClick = { selectedRepeatType = RepeatType.MONTHLY }
                    )
                    RepeatTypeOption(
                        text = "Yearly",
                        selected = selectedRepeatType == RepeatType.YEARLY,
                        onClick = { selectedRepeatType = RepeatType.YEARLY }
                    )
                }

                // Time picker section
                if (selectedRepeatType != RepeatType.NONE) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Time: ",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = String.format("%02d:%02d", repeatHour, repeatMinute),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .clickable { showTimePicker = true }
                                .padding(8.dp)
                        )
                    }
                }

                // Time picker dialog
                if (showTimePicker) {
                    AlertDialog(
                        onDismissRequest = { showTimePicker = false },
                        title = { Text("Set Time") },
                        text = {
                            Column {
                                // Hour picker
                                Text("Hour")
                                LazyColumn(
                                    modifier = Modifier.height(150.dp)
                                ) {
                                    items(hourOptions) { hour ->
                                        Text(
                                            text = String.format("%02d", hour),
                                            style = MaterialTheme.typography.bodyLarge,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { repeatHour = hour }
                                                .background(
                                                    if (hour == repeatHour)
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    else
                                                        Color.Transparent
                                                )
                                                .padding(8.dp)
                                        )
                                    }
                                }

                                // Minute picker
                                Text("Minute")
                                LazyColumn(
                                    modifier = Modifier.height(150.dp)
                                ) {
                                    items(minuteOptions) { minute ->
                                        Text(
                                            text = String.format("%02d", minute),
                                            style = MaterialTheme.typography.bodyLarge,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { repeatMinute = minute }
                                                .background(
                                                    if (minute == repeatMinute)
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    else
                                                        Color.Transparent
                                                )
                                                .padding(8.dp)
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = { showTimePicker = false }) {
                                Text("OK")
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val intervalValue = notificationInterval.toIntOrNull() ?: 10
                    onConfirm(item.copy(
                        notificationIntervalMinutes = intervalValue,
                        repeatType = selectedRepeatType,
                        repeatHour = repeatHour,
                        repeatMinute = repeatMinute
                    ))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RepeatTypeOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null // null because we handle click on the row
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun ChecklistApp(
    viewModel: ChecklistViewModel,
    onNeedPermission: () -> Unit
) {
    val items by viewModel.allItems.collectAsState(initial = emptyList())
    val context = LocalContext.current

    // Check notification permission before scheduling
    val hasNotificationPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not needed before Android 13
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "My Checklist",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn {
            items(items) { item ->
                SwipeableChecklistItem(
                    item = item,
                    onItemCompleted = { isCompleted ->
                        viewModel.updateItem(item.copy(
                            isCompleted = isCompleted,
                            isMarkedComplete = if (!isCompleted) false else item.isMarkedComplete
                        ))

                        if (isCompleted && !item.isMarkedComplete) {
                            if (hasNotificationPermission) {
                                scheduleNotification(context, item)
                            } else {
                                onNeedPermission()
                            }
                        } else if (!isCompleted) {
                            cancelNotification(context, item.id)
                        }
                    },
                    onItemDeleted = {
                        viewModel.deleteItem(item)
                        cancelNotification(context, item.id)
                    },
                    onItemUndone = {
                        if (item.isMarkedComplete) {
                            viewModel.updateItem(item.copy(
                                isMarkedComplete = false
                            ))

                            // Restart notifications if the alarm is still enabled
                            if (item.isCompleted) {
                                if (hasNotificationPermission) {
                                    scheduleNotification(context, item)
                                } else {
                                    onNeedPermission()
                                }
                            }
                        } else {
                            // If not marked complete, now we mark it as complete
                            viewModel.updateItem(item.copy(
                                isMarkedComplete = true
                            ))

                            // Cancel notifications since we're marking as complete
                            cancelNotification(context, item.id)
                        }
                    },
                    onSettingsClicked = { updatedItem ->
                        viewModel.updateItem(updatedItem)

                        // If the item is completed, reschedule notifications with new settings
                        if (updatedItem.isCompleted && !updatedItem.isMarkedComplete && hasNotificationPermission) {
                            cancelNotification(context, updatedItem.id)
                            scheduleNotification(context, updatedItem)
                        } else if (updatedItem.isCompleted && !hasNotificationPermission) {
                            onNeedPermission()
                        }
                    }
                )
            }

            // Add new item placeholder
            item {
                AddNewItemRow(
                    onAddItem = { newItemText ->
                        if (newItemText.isNotBlank()) {
                            viewModel.insert(ChecklistItem(
                                text = newItemText,
                                isCompleted = false,
                                isMarkedComplete = false
                            ))
                        }
                    }
                )
            }
        }

        // Show permission banner if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            NotificationPermissionBanner(onRequestPermission = onNeedPermission)
        }
    }
}

@Composable
fun NotificationPermissionBanner(onRequestPermission: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Enable notifications for reminders",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Enable")
            }
        }
    }
}

// Updated notification scheduling function
fun scheduleNotification(context: Context, item: ChecklistItem) {
    // Only schedule if we have permission (double-check)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("ChecklistApp", "Notification permission not granted")
            return // Exit if no permission
        }
    }

    val workManager = WorkManager.getInstance(context)

    // Cancel any existing notifications for this item
    cancelNotification(context, item.id)

    Log.d("ChecklistApp", "Scheduling notification for item ${item.id}: ${item.text}")

    // Create notification data
    val inputData = Data.Builder()
        .putInt("item_id", item.id)
        .putString("item_text", item.text)
        .putInt("repeat_type", item.repeatType.ordinal)
        .putInt("repeat_hour", item.repeatHour)
        .putInt("repeat_minute", item.repeatMinute)
        .build()

    // For basic interval notifications (when item has notifications enabled but not marked complete)
    if (item.isCompleted && !item.isMarkedComplete) {
        val intervalMinutes = if (item.notificationIntervalMinutes > 0) item.notificationIntervalMinutes else 10

        Log.d("ChecklistApp", "Creating periodic work with interval: $intervalMinutes minutes")

        // Create a periodic work request that repeats based on the item's interval
        val notificationWork = PeriodicWorkRequestBuilder<NotificationWorker>(
            intervalMinutes.toLong(), TimeUnit.MINUTES
        )
            .setInputData(inputData)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .addTag("notification_${item.id}")
            .build()

        // Enqueue the work with REPLACE policy to ensure we don't have multiple workers
        workManager.enqueueUniquePeriodicWork(
            "notification_${item.id}",
            ExistingPeriodicWorkPolicy.REPLACE,
            notificationWork
        )

        Log.d("ChecklistApp", "Periodic work enqueued successfully")
    }

    // For scheduled repeat notifications (based on repeat type)
    if (item.repeatType != RepeatType.NONE) {
        Log.d("ChecklistApp", "Scheduling ${item.repeatType} notification at ${item.repeatHour}:${item.repeatMinute}")

        // Create a one-time worker for immediate testing/feedback
        val immediateWork = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInputData(inputData)
            .setInitialDelay(10, TimeUnit.SECONDS) // Show a test notification quickly
            .addTag("notification_immediate_${item.id}")
            .build()

        workManager.enqueueUniqueWork(
            "notification_immediate_${item.id}",
            ExistingWorkPolicy.REPLACE,
            immediateWork
        )
    }
}

// Updated function to cancel notifications
private fun cancelNotification(context: Context, itemId: Int) {
    val workManager = WorkManager.getInstance(context)

    // Cancel all notification work for this item
    workManager.cancelAllWorkByTag("notification_${itemId}")
    workManager.cancelAllWorkByTag("notification_immediate_${itemId}")
    workManager.cancelAllWorkByTag("notification_once_${itemId}")

    // Also cancel any active notifications
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancel(itemId)

    Log.d("ChecklistApp", "Cancelled all notifications for item $itemId")
}

@Composable
fun AddNewItemRow(onAddItem: (String) -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    var newItemText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.small,
        color = if (isEditing) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
    ) {
        if (isEditing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Use a grayed-out AlarmButton for consistency with other items
                AlarmButton(
                    enabled = false,
                    onToggle = { /* Not functional for new items */ }
                )

                TextField(
                    value = newItemText,
                    onValueChange = { newItemText = it },
                    placeholder = { Text("Enter new item") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onAddItem(newItemText)
                            newItemText = ""
                            isEditing = false
                        }
                    )
                )

                IconButton(
                    onClick = {
                        onAddItem(newItemText)
                        newItemText = ""
                        isEditing = false
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = "Add Item"
                    )
                }
            }

            LaunchedEffect(isEditing) {
                if (isEditing) {
                    focusRequester.requestFocus()
                }
            }
        } else {
            // Greyed out placeholder row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isEditing = true }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(12.dp)) // Space for alarm icon

                Text(
                    text = "Add new item...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(start = 32.dp, top = 8.dp, bottom = 8.dp)
                )
            }
        }
    }
}