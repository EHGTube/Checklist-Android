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
                    onItemCrossToggle = {
                        // New swipe action to toggle marked complete state
                        val newMarkedState = !item.isMarkedComplete
                        viewModel.updateItem(item.copy(isMarkedComplete = newMarkedState))

                        // If marking as complete, cancel notifications
                        if (newMarkedState) {
                            cancelNotification(context, item.id)
                        } else if (item.isCompleted) {
                            // If unmarking and notifications should be active
                            if (hasNotificationPermission) {
                                scheduleNotification(context, item)
                            } else {
                                onNeedPermission()
                            }
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