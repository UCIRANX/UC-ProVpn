package com.ucprovpn.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface
import com.ucprovpn.ui.components.ConnectionSliderBar
import com.ucprovpn.ui.components.ConnectionState
import com.ucprovpn.ui.components.HeroConnectButton
import com.ucprovpn.ui.components.CountryFlagIcon
import com.ucprovpn.ui.components.SubscriptionDetailsDialog
import com.ucprovpn.ui.components.SubscriptionProviderCard
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.delay

@Composable
internal fun SelectionTopBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onExportSelected: () -> Unit,
    onPingSelected: () -> Unit,
    // Null on screens that do not offer custom groups; the action is then hidden.
    onMoveSelected: (() -> Unit)? = null
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancel",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$selectedCount ${strings.selected}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onMoveSelected != null) {
                IconButton(onClick = onMoveSelected) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = strings.moveToGroup,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onExportSelected) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = strings.exportSelected,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onPingSelected) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = strings.ping,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** Shared group header: used by the dashboard and by the Servers tab. */
@Composable
fun SubscriptionHeaderTile(
    group: HomeServerGroup,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRefreshSubscription: () -> Unit,
    onDeleteSubscription: () -> Unit,
    onPingGroup: () -> Unit = {},
    onShowProperties: (SubscriptionUiModel) -> Unit = {},
    onEditSubscription: ((SubscriptionUiModel) -> Unit)? = null,
    onExportAll: (String?) -> Unit = {},
    // Custom groups only, and only where the screen can host the dialogs. Null hides
    // the entry, which is what the dashboard wants: groups are managed on Servers.
    onRenameGroup: (() -> Unit)? = null,
    onDeleteGroup: (() -> Unit)? = null
) {
    val strings = LocalStrings.current
    var showMenu by remember { mutableStateOf(false) }
    // Subscriptions always carry the traffic block underneath, so their title
    // tile keeps square bottom corners and the two read as one card.
    val tileShape = if (isExpanded || group.isSubscription) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    } else {
        RoundedCornerShape(18.dp)
    }
    val primaryColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tileShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), tileShape),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = tileShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (group.isSubscription) "${group.title} (${group.nodes.size})" else group.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (group.isSubscription) "${strings.serverCountLabel(group.nodes.size)} | ${strings.autoUpdateOneHour}" else strings.serverCountLabel(group.nodes.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            // Header action buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPingGroup,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.NetworkCheck,
                        contentDescription = "Ping",
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (group.isSubscription) {
                    IconButton(
                        onClick = onRefreshSubscription,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    LumenMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        Column {
                            if (group.isSubscription) {
                                // Subscription menu: Edit, Properties, Export all, Delete.
                                if (onEditSubscription != null) {
                                    DropdownMenuItem(
                                        text = { Text(strings.edit, color = MaterialTheme.colorScheme.onSurface) },
                                        trailingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                                        onClick = {
                                            showMenu = false
                                            group.subscription?.let(onEditSubscription)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(strings.subscriptionProperties, color = MaterialTheme.colorScheme.onSurface) },
                                    trailingIcon = { Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                                    onClick = {
                                        showMenu = false
                                        group.subscription?.let { onShowProperties(it) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.exportAll, color = MaterialTheme.colorScheme.onSurface) },
                                    trailingIcon = { Icon(Icons.Filled.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                                    onClick = {
                                        showMenu = false
                                        onExportAll(group.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.deleteSubscription, color = MaterialTheme.colorScheme.error) },
                                    trailingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                                    onClick = {
                                        showMenu = false
                                        onDeleteSubscription()
                                    }
                                )
                            } else {
                                // Default / custom group menu: Export all, Ping, and for a
                                // custom group the rename and delete actions on top.
                                DropdownMenuItem(
                                    text = { Text(strings.exportAll, color = MaterialTheme.colorScheme.onSurface) },
                                    trailingIcon = { Icon(Icons.Filled.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                                    onClick = {
                                        showMenu = false
                                        onExportAll(if (group.isCustom) group.id else null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.ping, color = MaterialTheme.colorScheme.onSurface) },
                                    trailingIcon = { Icon(Icons.Filled.NetworkCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                                    onClick = {
                                        showMenu = false
                                        onPingGroup()
                                    }
                                )
                                if (group.isCustom && onRenameGroup != null) {
                                    DropdownMenuItem(
                                        text = { Text(strings.renameGroup, color = MaterialTheme.colorScheme.onSurface) },
                                        trailingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                                        onClick = {
                                            showMenu = false
                                            onRenameGroup()
                                        }
                                    )
                                }
                                if (group.isCustom && onDeleteGroup != null) {
                                    DropdownMenuItem(
                                        text = { Text(strings.deleteGroup, color = MaterialTheme.colorScheme.error) },
                                        trailingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                                        onClick = {
                                            showMenu = false
                                            onDeleteGroup()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Shared traffic/premium bar: used by the dashboard and by the Servers tab. */
@Composable
fun SubscriptionInfoBar(
    sub: SubscriptionUiModel,
    // Cancels the list spacing above so the bar sticks to the title tile.
    pullUp: androidx.compose.ui.unit.Dp = 6.dp,
    roundedBottom: Boolean = true,
    // Covers the list spacing below so the rows continue the same tile.
    extraBottom: androidx.compose.ui.unit.Dp = 0.dp
) {
    val strings = LocalStrings.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val infoShape = if (roundedBottom) {
        RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
    } else {
        RoundedCornerShape(0.dp)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = -pullUp)
            .clip(infoShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp + extraBottom)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = sub.trafficSummary ?: "— / ∞",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = sub.expiryDaysLeft?.let { "$it ${strings.daysRemaining}" } ?: strings.expiresUnlimited,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            // Real usage ratio from subscription-userinfo; unlimited plans stay empty.
            progress = { sub.trafficRatio ?: 0f },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = primaryColor,
            trackColor = primaryColor.copy(alpha = 0.2f)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = buildString {
                append(strings.serverCountLabel(sub.nodeCount))
                sub.updateIntervalHours?.let { append("  |  \u21bb ${it}h") }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        // The announcement is not drawn here: SubscriptionProviderCard lays it out
        // below the bar, where it can wrap over several lines next to the provider
        // buttons instead of being squeezed into one 11sp line.
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ServerTileRow(
    node: NodeUiModel,
    isSelectionMode: Boolean,
    isNodeSelected: Boolean,
    isPinging: Boolean = false,
    supportingText: String? = null,
    modern: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEditNode: () -> Unit,
    onPingNode: () -> Unit,
    onCopyLink: () -> Unit,
    onExportQr: () -> Unit,
    onDeleteNode: () -> Unit,
    // Null on screens that do not offer custom groups; the entry is then hidden.
    onMoveToGroup: (() -> Unit)? = null
) {
    val strings = LocalStrings.current
    val selected = node.isSelected
    val primaryColor = MaterialTheme.colorScheme.primary
    // Tiles always carry the palette accent; AMOLED only makes the tint slightly stronger.
    val amoled = MaterialTheme.colorScheme.background.luminance() < 0.02f
    val rowBg = when {
        isSelectionMode && isNodeSelected -> primaryColor.copy(alpha = if (amoled) 0.24f else 0.18f)
        selected -> primaryColor.copy(alpha = if (amoled) 0.20f else 0.14f)
        else -> primaryColor.copy(alpha = if (amoled) 0.08f else 0.06f)
            .compositeOver(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    }
    var showActionMenu by remember { mutableStateOf(false) }

    // modern = reworked look; false restores the original compact row.
    val tileShape = RoundedCornerShape(if (modern) 18.dp else 12.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tileShape)
            .border(
                if (modern && selected) 2.dp else 1.dp,
                if (selected) primaryColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                tileShape
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = rowBg,
        shape = tileShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (modern) 12.dp else 14.dp, vertical = if (modern) 13.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (modern) {
                // Accent rail marks the active server without an extra row.
                Box(
                    Modifier
                        .width(3.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(primaryColor.copy(alpha = if (selected) 1f else 0f))
                )
                Spacer(Modifier.width(10.dp))
            }
            // Checkbox in selection mode
            if (isSelectionMode) {
                Checkbox(
                    checked = isNodeSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = primaryColor,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
            }

            // Larger flags on the dashboard; unresolved countries use a neutral tile.
            CountryFlagIcon(
                countryCode = node.countryCode,
                width = 34.dp,
                height = 23.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) primaryColor else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (modern) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Per-protocol badge colour, shared with the Servers tab.
                        val badgeColor = protocolColor(node.protocol)
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeColor.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = node.displayProtocol.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (node.isAutoNode && node.displayProtocol.equals("AUTO", true)) {
                                strings.autoNodeDescriptionLabel
                            } else if (node.isAutoNode && node.displayProtocol.endsWith("/WARP", true)) {
                                "WARP"
                            } else {
                                "${node.server}:${node.port}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = node.displayProtocol,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                supportingText?.takeIf { it.isNotBlank() }?.let { detail ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isPinging) {
                // Spinner replaces the (already cleared) ping value while the probe runs.
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = primaryColor
                )
                Spacer(Modifier.width(8.dp))
            } else {
                node.pingMs?.let { ping ->
                    if (modern) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(pingColor(ping).copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (ping >= 0) "$ping ms" else "—",
                                style = MaterialTheme.typography.bodySmall,
                                color = pingColor(ping),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            text = if (ping >= 0) "$ping ms" else "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = pingColor(ping),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }

            // Trailing overflow button: same three-dot menu as the Servers tab.
            if (!isSelectionMode) {
                Box {
                    IconButton(
                        onClick = { showActionMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = null,
                            tint = if (selected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    LumenMenu(
                        expanded = showActionMenu,
                        onDismissRequest = { showActionMenu = false }
                    ) {
                        Column {
                            DropdownMenuItem(
                                text = { Text(strings.edit, color = MaterialTheme.colorScheme.onSurface) },
                                trailingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showActionMenu = false
                                    onEditNode()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.copyLink, color = MaterialTheme.colorScheme.onSurface) },
                                trailingIcon = { Icon(Icons.Filled.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showActionMenu = false
                                    onCopyLink()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.exportQrCode, color = MaterialTheme.colorScheme.onSurface) },
                                trailingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showActionMenu = false
                                    onExportQr()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.ping, color = MaterialTheme.colorScheme.onSurface) },
                                trailingIcon = { Icon(Icons.Filled.NetworkCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showActionMenu = false
                                    onPingNode()
                                }
                            )
                            if (onMoveToGroup != null) {
                                DropdownMenuItem(
                                    text = { Text(strings.moveToGroup, color = MaterialTheme.colorScheme.onSurface) },
                                    trailingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showActionMenu = false
                                        onMoveToGroup()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(strings.delete, color = MaterialTheme.colorScheme.error) },
                                trailingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showActionMenu = false
                                    onDeleteNode()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
/** Compact human readable speed, e.g. "637.8 KB/s". */
private fun formatHeroDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, secs)
    }
}

/** Server tile caption: region only, without the transport type. */
private fun heroServerLabel(countryCode: String?, protocol: String?, fallbackName: String?): String {
    val region = countryCode
        ?.takeIf { it.length == 2 }
        ?.let { code ->
            java.util.Locale("", code.uppercase(java.util.Locale.US))
                .getDisplayCountry(java.util.Locale.getDefault())
                .takeIf { it.isNotBlank() }
        }
        ?: fallbackName?.trim()?.takeIf { it.isNotBlank() }
    return region?.takeIf { it.isNotBlank() } ?: "\u2014"
}

/**
 * Dashboard hero: round/centered controls or a bottom slider, live throughput
 * and the shared server/session tiles.
 */
/**
 * "Check" action shared by every dashboard style. Instead of a loose button under
 * the hero it is a third stat tile next to "Server" and "Session": tapping it
 * measures the currently connected server with the ping method chosen in settings
 * and replaces its own value with the result (also delivered as a toast).
 */
@Composable
private fun HeroPingTile(
    connectedPing: String?,
    isChecking: Boolean,
    onCheckPing: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val s = LocalStrings.current
    val shape = RoundedCornerShape(if (compact) 13.dp else 18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), shape)
            .clickable(enabled = !isChecking, onClick = onCheckPing)
            .padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 5.dp else 12.dp
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = s.checkPing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (compact) 9.sp else 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.NetworkCheck,
                contentDescription = s.checkPing,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                modifier = Modifier.size(if (compact) 10.dp else 12.dp)
            )
        }
        Spacer(Modifier.height(if (compact) 1.dp else 3.dp))
        if (isChecking) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (compact) 12.dp else 16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                text = if (connectedPing.isNullOrBlank()) "—" else connectedPing,
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (connectedPing.isNullOrBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                fontSize = if (compact) 12.sp else 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun HomeConnectBar(
    connectionState: ConnectionState,
    serverName: String?,
    serverCountryCode: String?,
    serverProtocol: String?,
    connectedPing: String? = null,
    isCheckingPing: Boolean = false,
    onCheckPing: () -> Unit = {},
    onToggleConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    val connected = connectionState == ConnectionState.Connected
    var sessionSeconds by remember { mutableStateOf(0L) }

    // The session start timestamp is persisted, so reopening the app keeps
    // counting from the real connection time.
    val heroContext = androidx.compose.ui.platform.LocalContext.current
    val heroPrefs = remember {
        heroContext.getSharedPreferences("lumen_prefs", android.content.Context.MODE_PRIVATE)
    }
    LaunchedEffect(connected) {
        if (!connected) {
            sessionSeconds = 0L
            return@LaunchedEffect
        }
        while (true) {
            val startedAt = heroPrefs.getLong("session_started_at", 0L)
            sessionSeconds = if (startedAt > 0L) {
                ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)
            } else {
                0L
            }
            delay(1_000)
        }
    }

    // One fixed strip: three stat tiles plus the round tap-to-toggle button.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HeroStatTile(
            label = s.serverLabel,
            value = heroServerLabel(serverCountryCode, serverProtocol, serverName),
            modifier = Modifier.weight(1f),
            compact = true
        )
        HeroStatTile(
            label = s.sessionLabel,
            value = formatHeroDuration(sessionSeconds),
            modifier = Modifier.weight(1f),
            compact = true
        )
        HeroPingTile(
            connectedPing = connectedPing,
            isChecking = isCheckingPing,
            onCheckPing = onCheckPing,
            modifier = Modifier.weight(0.72f),
            compact = true
        )
        HeroConnectButton(
            state = connectionState,
            onConnectClick = onToggleConnection,
            buttonSize = 64.dp,
            compact = true
        )
    }
}

@Composable
private fun HeroStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(if (compact) 13.dp else 18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), shape)
            .padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 5.dp else 12.dp
            )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = if (compact) 9.sp else 11.sp
        )
        Spacer(Modifier.height(if (compact) 1.dp else 3.dp))
        Text(
            text = value,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = if (compact) 12.sp else 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
