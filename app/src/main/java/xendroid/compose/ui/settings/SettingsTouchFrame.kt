package xendroid.compose.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import xendroid.compose.R
import xendroid.compose.settings.SettingGroup
import xendroid.compose.ui.design.*

/** Presentation destinations only; the underlying section IDs and settings remain unchanged. */
internal enum class SettingsHubCategory(val key: String, val titleRes: Int, val subtitleRes: Int, val sectionIds: List<String>) {
    GRAPHICS("graphics", R.string.zephyr_settings_graphics, R.string.zephyr_settings_graphics_note,
        listOf("set:IMAGE", "set:DRIVER", "set:PERFORMANCE")),
    AUDIO("audio", R.string.xd_group_audio, R.string.zephyr_settings_audio_note, listOf("set:AUDIO")),
    INPUT("input", R.string.zephyr_settings_input, R.string.zephyr_settings_input_note, listOf("set:INPUT")),
    STORAGE("storage", R.string.zephyr_settings_storage, R.string.zephyr_settings_storage_note, listOf(SettingsSectionIds.DATA)),
    SYSTEM("system", R.string.zephyr_settings_system, R.string.zephyr_settings_system_note,
        listOf(SettingsSectionIds.UI, SettingsSectionIds.LANGUAGE, "set:SYSTEM", SettingsSectionIds.UPDATES, SettingsSectionIds.ABOUT)),
    ADVANCED("advanced", R.string.zephyr_settings_advanced, R.string.zephyr_settings_advanced_note,
        listOf("set:COMPAT", "set:DEBUG", SettingsSectionIds.DIAGNOSTICS, SettingsSectionIds.COMMUNITY, SettingsSectionIds.SUMMARY));

    val destination: String get() = if (sectionIds.size == 1) sectionIds.single() else "hub:$key"
    fun icon(): ImageVector = when (this) {
        GRAPHICS -> XdIcons.monitor
        AUDIO -> groupIcon(SettingGroup.AUDIO)
        INPUT -> XdIcons.gamepad
        STORAGE -> XdIcons.save
        SYSTEM -> XdIcons.gear
        ADVANCED -> XdIcons.sliders
    }
}

/** Unmapped future sections remain reachable under Advanced; unavailable optional sections disappear. */
internal fun settingsHubSections(category: SettingsHubCategory, sections: List<XdSection>): List<XdSection> {
    val real = sections.filter { it.id != SettingsSectionIds.HOME && !it.id.startsWith("hub:") && it.goTo == null && it.content != null }
    val known = SettingsHubCategory.entries.flatMap { it.sectionIds }.toSet()
    val assigned = category.sectionIds.mapNotNull { id -> real.firstOrNull { it.id == id } }
    return assigned + if (category == SettingsHubCategory.ADVANCED) real.filter { it.id !in known } else emptyList()
}

/** One static corner cut establishes the same structural language as the navigation blades. */
private val settingsCut = GenericShape { size, _ ->
    val cut = minOf(size.height * 0.18f, size.width * 0.06f)
    moveTo(0f, 0f); lineTo(size.width - cut, 0f); lineTo(size.width, cut)
    lineTo(size.width, size.height); lineTo(0f, size.height); close()
}

@Composable
internal fun SettingsTouchFrame(
    sections: List<XdSection>, current: XdSection?, history: XdSectionHistory,
    query: String, onQuery: (String) -> Unit, onNavigate: (String) -> Unit, onBack: () -> Unit,
    searchResults: @Composable ColumnScope.() -> Unit,
) {
    val c = Xd.colors
    val focus = LocalFocusManager.current
    val navigate: (String) -> Unit = { focus.clearFocus(); onNavigate(it) }
    val home = current?.id == SettingsSectionIds.HOME
    val category = SettingsHubCategory.entries.firstOrNull { it.sectionIds.size > 1 && it.destination == current?.id }
    val back: () -> Unit = {
        focus.clearFocus()
        when {
            home -> onBack()
            history.previous != null -> history.back()
            else -> history.pick(SettingsSectionIds.HOME)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg).testTag("settings-touch-frame")) {
        val compact = maxHeight > maxWidth || maxWidth < 840.dp
        val body: @Composable (Modifier) -> Unit = { modifier ->
            CompositionLocalProvider(LocalXdButtonShape provides RoundedCornerShape(4.dp)) {
                Column(modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing.part(
                    top = !compact, bottom = true, start = compact, end = true))) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        XdIconButton(XdIcons.back, stringResource(R.string.xd_back), back,
                            Modifier.testTag("settings-hub-back"))
                        Text(if (home) stringResource(R.string.zephyr_settings_home) else current?.title.orEmpty(),
                            Modifier.weight(1f), style = XdText.h2, color = c.fg)
                        if (!home) XdIconButton(XdIcons.home, stringResource(R.string.zephyr_settings_home),
                            { focus.clearFocus(); history.pick(SettingsSectionIds.HOME) }, Modifier.testTag("settings-hub-home-button"))
                    }
                    val scroll = rememberSaveable(current?.id, saver = ScrollState.Saver) { ScrollState(0) }
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
                        .testTag("settings-hub-scroll").padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when {
                            home -> {
                                Text(stringResource(R.string.zephyr_settings_intro), style = XdText.body, color = c.fg2)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    XdSearchField(query, onQuery, stringResource(R.string.xd_set_search_everywhere),
                                        Modifier.weight(1f).testTag("settings-hub-search"), height = 48.dp)
                                    if (query.isNotBlank()) XdIconButton(XdIcons.xCircle, stringResource(R.string.lib_clear_search),
                                        { onQuery(""); focus.clearFocus() }, Modifier.testTag("settings-hub-clear-search"))
                                }
                                if (query.isBlank()) SettingsHubCards(sections, navigate)
                                else Column(Modifier.fillMaxWidth().testTag("settings-hub-search-results"), content = searchResults)
                            }
                            category != null -> {
                                Text(stringResource(category.subtitleRes), style = XdText.body, color = c.fg2)
                                settingsHubSections(category, sections).forEach { section ->
                                    SettingsHubRow(section.title, section.lead, section.icon, "settings-hub-section:${section.id}",
                                        section.badge) { navigate(section.id) }
                                }
                            }
                            current != null -> Column(Modifier.fillMaxWidth().testTag("settings-hub-content:${current.id}"),
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                current.heading?.takeIf { it != current.title }?.let { Text(it, style = XdText.h2, color = c.fg) }
                                current.lead?.let { Text(it, style = XdText.bodySm, color = c.fg2) }
                                current.content?.invoke(this)
                            }
                        }
                    }
                }
            }
        }
        if (compact) Column(Modifier.fillMaxSize()) { BTopAreaBar(XdArea.SETTINGS); body(Modifier.weight(1f)) }
        else Row(Modifier.fillMaxSize()) { BRail(XdArea.SETTINGS); body(Modifier.weight(1f)) }
    }
}

@Composable
private fun SettingsHubCards(sections: List<XdSection>, onNavigate: (String) -> Unit) {
    val categories = SettingsHubCategory.entries.filter { settingsHubSections(it, sections).isNotEmpty() }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("settings-hub-home")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            categories.chunked(if (maxWidth >= 640.dp) 2 else 1).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { category ->
                        Box(Modifier.weight(1f)) {
                            SettingsHubRow(stringResource(category.titleRes), stringResource(category.subtitleRes), category.icon(),
                                "settings-hub-category:${category.key}") { onNavigate(category.destination) }
                        }
                    }
                    if (maxWidth >= 640.dp && row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SettingsHubRow(title: String, subtitle: String?, icon: ImageVector, tag: String, badge: String? = null, onClick: () -> Unit) {
    val c = Xd.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).testTag(tag).focusRing(settingsCut).clip(settingsCut)
        .background(c.s1).border(1.dp, c.line2, settingsCut).clickable(role = Role.Button, onClick = onClick)
        .padding(start = 14.dp, end = 22.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(24.dp), tint = c.fg2)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = XdText.label, color = c.fg)
            subtitle?.let { Text(it, style = XdText.bodySm, color = c.fg2) }
        }
        badge?.let { Text(it, style = XdText.monoNum, color = c.acc) }
        Icon(XdIcons.chevR, null, Modifier.size(16.dp), tint = c.fg2)
    }
}
