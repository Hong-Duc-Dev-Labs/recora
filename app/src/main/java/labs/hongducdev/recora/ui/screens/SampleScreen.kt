package labs.hongducdev.recora.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import labs.hongducdev.recora.ui.components.PullTitleHeader
import labs.hongducdev.recora.ui.components.rememberPullTitleHeaderState
import labs.hongducdev.recora.ui.core.cards.FeatureCard
import labs.hongducdev.recora.ui.core.cards.SettingsItem
import labs.hongducdev.recora.ui.core.containers.RoundedCardContainer

/**
 * Reference screen in the Essentials layout: pull-to-expand title, grouped 24dp cards,
 * 150dp bottom padding so the floating toolbar never hides content.
 * Copy this as the starting point for every new screen.
 */
@Composable
fun SampleScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val headerState = rememberPullTitleHeaderState()
    val listState = rememberLazyListState()
    var featureOn by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().nestedScroll(headerState.nestedScrollConnection),
        contentPadding =
            PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = 150.dp,
                start = 16.dp,
                end = 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PullTitleHeader(title = "Sample", caption = "Essentials style", state = headerState) }
        item {
            RoundedCardContainer(title = "Features") {
                FeatureCard(
                    title = "Haptics",
                    description = "Vibration feedback on every control",
                    icon = Icons.Rounded.Vibration,
                    isEnabled = featureOn,
                    onToggle = { featureOn = it },
                    onClick = {},
                )
            }
        }
        item {
            RoundedCardContainer(title = "Settings") {
                SettingsItem(title = "Appearance", description = "Pitch black and dynamic colour", icon = Icons.Rounded.Palette)
            }
        }
    }
}
