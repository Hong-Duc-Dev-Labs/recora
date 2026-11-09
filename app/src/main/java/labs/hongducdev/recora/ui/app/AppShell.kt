package labs.hongducdev.recora.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import labs.hongducdev.recora.ui.components.EssentialsFloatingToolbar
import labs.hongducdev.recora.ui.components.ToolbarItem
import labs.hongducdev.recora.utils.HapticUtil
import kotlinx.coroutines.launch
import kotlin.math.abs

/** One bottom-toolbar destination and the screen it shows. */
data class ShellTab(
    val title: String,
    val icon: ImageVector,
    val content: @Composable (contentPadding: PaddingValues) -> Unit,
)

/**
 * Essentials-style app shell: swipeable pager, floating pill toolbar, bucketed rumble haptics
 * while swiping and a heavy tick when a new page settles.
 * Call it inside `RecoraTheme { }` after `enableEdgeToEdge()`.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EssentialsAppShell(
    tabs: List<ShellTab>,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { tabs.size })

    val pageSpring =
        spring<Float>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0, animationSpec = pageSpring) }
    }

    var lastBucket by remember { mutableIntStateOf(0) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPageOffsetFraction }.collect { offset ->
            val bucket = (abs(offset) * 10f).toInt()
            if (bucket != lastBucket) {
                lastBucket = bucket
                HapticUtil.performSliderHaptic(view)
            }
        }
    }
    LaunchedEffect(pagerState) {
        var first = true
        snapshotFlow { pagerState.currentPage }.collect {
            if (first) first = false else HapticUtil.performHeavyHaptic(view)
        }
    }

    val contentPadding = PaddingValues(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())

    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.surfaceContainer) { _ ->
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                tabs[page].content(contentPadding)
            }
            EssentialsFloatingToolbar(
                items =
                    tabs.mapIndexed { index, tab ->
                        ToolbarItem(
                            icon = tab.icon,
                            label = tab.title,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index, animationSpec = pageSpring) }
                            },
                        )
                    },
                selectedIndex = pagerState.currentPage,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
