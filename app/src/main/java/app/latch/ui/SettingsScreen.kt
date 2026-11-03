package app.latch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.latch.data.LatchSettings
import app.latch.ui.theme.LatchColors
import app.latch.ui.theme.LatchType
import app.latch.ui.theme.TextShadow

/** Callbacks from the settings screen to the root. */
interface SettingsActions {
    fun setAppsPerDrawer(count: Int)
    fun setGrayIcons(enabled: Boolean)

    /** Close the settings screen, e.g. after a tap on empty space. */
    fun close()
}

/**
 * Latch's own settings: a centered title, then one collapsible section per settings category,
 * opened and closed like drawers on home. Tapping empty space closes the screen.
 */
@Composable
fun SettingsScreen(settings: LatchSettings, actions: SettingsActions, modifier: Modifier = Modifier) {
    var openSection by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LatchColors.OverlayScrim)
            // Taps on empty space close settings instead of reaching home underneath.
            .pointerInput(actions) { detectTapGestures { actions.close() } }
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp)
            .testTag("settings"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Latch settings",
            style = LatchType.SectionHeader,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
        )
        SettingsSection(
            title = "Appearance",
            expanded = openSection == SECTION_APPEARANCE,
            onToggle = { openSection = if (openSection == SECTION_APPEARANCE) null else SECTION_APPEARANCE }
        ) {
            Stepper(
                label = "Apps per drawer",
                value = settings.appsPerDrawer,
                range = LatchSettings.APPS_PER_DRAWER_RANGE,
                onChange = actions::setAppsPerDrawer,
                tag = "appsPerDrawer"
            )
            Toggle(
                label = "Gray icons",
                checked = settings.grayIcons,
                onChange = actions::setGrayIcons,
                tag = "grayIcons"
            )
        }
    }
}

private const val SECTION_APPEARANCE = "appearance"

@Composable
private fun SettingsSection(title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = LatchType.CategoryName.copy(fontSize = 24.sp, lineHeight = 30.sp),
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .testTag("settingsSection:$title")
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // Taps between controls belong to the section, not to the empty space.
                    .pointerInput(Unit) { detectTapGestures {} }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) { content() }
        }
    }
}

private val SettingLabel = TextStyle(color = LatchColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, shadow = TextShadow)

/** A "− value +" control for a whole number within [range]. */
@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit, tag: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = SettingLabel)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", enabled = value > range.first, tag = "$tag:minus") { onChange(value - 1) }
            Text(
                text = value.toString(),
                style = SettingLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(44.dp).testTag("$tag:value")
            )
            StepButton("+", enabled = value < range.last, tag = "$tag:plus") { onChange(value + 1) }
        }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, style = TextStyle(color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold))
    }
}

/** A labelled on/off switch. */
@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = SettingLabel)
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.2f),
                uncheckedBorderColor = Color.White
            )
        )
    }
}
