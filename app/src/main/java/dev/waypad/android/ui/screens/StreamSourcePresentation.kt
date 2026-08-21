package dev.waypad.android.ui.screens

import dev.waypad.android.core.model.ScreenSource

/**
 * How capture sources are grouped for the picker.
 *
 * The daemon offers the same two monitors through three backends that differ by an order of
 * magnitude in speed, and used to present them as one flat list labelled with backend names. That
 * is a trap: picking the wrong row silently drops the stream from 60 fps of hardware H.264 to six
 * frames a second of JPEG, with nothing on screen to explain why. Grouping by speed puts the
 * consequence in front of the choice.
 */
enum class SourceGroup(val title: String, val caption: String) {
    Recommended(
        title = "Recommended",
        caption = "Hardware encoding, full frame rate",
    ),
    Fallback(
        title = "Fallbacks",
        caption = "Much slower — only if the recommended source fails",
    ),
}

/** How one capture backend is described to the user. */
private data class BackendDescriptor(
    val group: SourceGroup,
    /** Appended to the monitor name; `null` when the source is already uniquely named. */
    val tag: String?,
    val quality: String,
    val fast: Boolean,
)

/** A capture source as the picker shows it. */
data class SourcePresentation(
    val id: String,
    val group: SourceGroup,
    val title: String,
    val detail: String,
    val fast: Boolean,
)

/** Human-readable summary of one capture source. */
fun presentSource(source: ScreenSource): SourcePresentation {
    val resolution = if (source.width > 0 && source.height > 0) {
        "${source.width} × ${source.height}"
    } else {
        null
    }
    val descriptor = when (source.backend) {
        "wayland-screencast-portal" ->
            BackendDescriptor(SourceGroup.Recommended, null, "Hardware H.264, up to 60 fps", true)
        "hyprland-grim" ->
            BackendDescriptor(SourceGroup.Fallback, "Screenshot", "Screenshot capture, about 6 fps", false)
        "x11-ffmpeg" ->
            BackendDescriptor(SourceGroup.Fallback, "X11", "X11 and JPEG, no approval needed", false)
        else -> BackendDescriptor(SourceGroup.Fallback, source.backend, source.backend, false)
    }
    // The same monitor appears once per fallback backend, so the method has to be part of the
    // name: two rows both reading "eDP-1" are impossible to tell apart at a glance.
    val title = listOfNotNull(sourceTitle(source), descriptor.tag).joinToString(" · ")
    return SourcePresentation(
        id = source.id,
        group = descriptor.group,
        title = title,
        detail = listOfNotNull(descriptor.quality, resolution).joinToString(" · "),
        fast = descriptor.fast,
    )
}

/**
 * The source name without the parenthetical the daemon appends.
 *
 * Those parentheses carry the backend's frame rate, which the detail line now states properly, so
 * repeating it in the title just makes every row look the same at a glance.
 */
internal fun sourceTitle(source: ScreenSource): String {
    val label = source.label.ifBlank { source.id }
    val withoutSuffix = label.substringBefore(" (").trim()
    return withoutSuffix.ifBlank { label }
}

/** Sources grouped for display, recommended first, each group keeping the daemon's order. */
fun groupSources(sources: List<ScreenSource>): List<Pair<SourceGroup, List<SourcePresentation>>> {
    val presented = sources.map(::presentSource)
    return SourceGroup.entries
        .map { group -> group to presented.filter { it.group == group } }
        .filter { (_, items) -> items.isNotEmpty() }
}

/**
 * The source to stream when the user has not chosen one.
 *
 * Always the fastest available: someone who opens the app to share a screen wants it to work, not
 * to learn the difference between PipeWire and X11 first.
 */
fun defaultSourceId(sources: List<ScreenSource>): String? =
    sources.firstOrNull { presentSource(it).fast }?.id ?: sources.firstOrNull()?.id
