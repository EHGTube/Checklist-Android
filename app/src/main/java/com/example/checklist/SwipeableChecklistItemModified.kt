@Composable
fun SwipeableChecklistItem(
    item: ChecklistItem,
    onItemCompleted: (Boolean) -> Unit,
    onItemDeleted: () -> Unit,
    onItemCrossToggle: () -> Unit,
    onSettingsClicked: (ChecklistItem) -> Unit
) {
    val dismissState = rememberDismissState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                DismissValue.DismissedToEnd -> {
                    // Right to left swipe - toggle crossed out state
                    onItemCrossToggle()
                    false // Don't dismiss the item, just handle the action
                }
                DismissValue.DismissedToStart -> {
                    // Left to right swipe - delete item
                    onItemDeleted()
                    true
                }
                else -> false
            }
        }
    )

    // Color for the background based on swipe direction
    val color by animateColorAsState(
        when (dismissState.targetValue) {
            DismissValue.Default -> MaterialTheme.colorScheme.surfaceVariant
            DismissValue.DismissedToEnd -> Color(0xFF4CAF50) // Green for toggling mark complete
            DismissValue.DismissedToStart -> Color(0xFFE91E63) // Red for delete
        },
        label = "background color"
    )

    SwipeToDismiss(
        state = dismissState,
        directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
        background = {
            val direction = dismissState.dismissDirection ?: return@SwipeToDismiss
            val alignment = when (direction) {
                DismissDirection.StartToEnd -> Alignment.CenterStart
                DismissDirection.EndToStart -> Alignment.CenterEnd
            }
            val icon = when (direction) {
                DismissDirection.StartToEnd -> if (item.isMarkedComplete) Icons.Default.Refresh else Icons.Default.Done
                DismissDirection.EndToStart -> Icons.Default.Delete
            }
            val scale by animateFloatAsState(
                if (dismissState.targetValue == DismissValue.Default) 0.75f else 1f,
                label = "icon scale"
            )
            val iconDescription = when (direction) {
                DismissDirection.StartToEnd -> if (item.isMarkedComplete) "Mark as not done" else "Mark as done"
                DismissDirection.EndToStart -> "Delete item"
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconDescription,
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