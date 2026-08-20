package dev.waypad.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.waypad.android.core.video.RemoteVideoRenderer
import dev.waypad.android.core.video.WaypadVideoView
import dev.waypad.android.ui.theme.WaypadOnVideoLetterbox
import dev.waypad.android.ui.theme.WaypadOnVideoLetterboxMuted
import dev.waypad.android.ui.theme.WaypadTheme

/**
 * Video output of the remote desktop.
 *
 * Hands the decoder a [android.view.Surface] and stays out of the way: frames are rendered by
 * MediaCodec straight into the surface, so no bitmap is ever allocated on the UI side and no
 * recomposition happens per frame. [WaypadVideoView] measures itself to the source aspect ratio,
 * and the parent box centres unaligned children, so no sizing modifier is needed.
 *
 * The letterboxing it performs is the same fit-centre used by `core.video.VideoLayout.fitCentre`,
 * which is what [remoteDisplayGestures] assumes when it maps touches onto desktop coordinates.
 * The view never consumes pointer input; gestures belong to the parent box.
 */
@Composable
fun RemoteDisplayVideoSurface(
    renderer: RemoteVideoRenderer?,
    modifier: Modifier = Modifier,
) {
    if (renderer == null) {
        Box(modifier)
        return
    }
    AndroidView(
        factory = { WaypadVideoView(it) },
        // Attaching belongs in `update`, not `factory`: the view is reused across recompositions
        // and would otherwise keep pointing at a renderer from a previous session.
        update = { view -> view.attachRenderer(renderer) },
        onRelease = { view -> view.detachRenderer() },
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * Centred empty state shown while no frame has been received.
 *
 * Drawn on the black letterbox, so it uses the theme-independent video content colours instead of
 * the scheme's `onSurface` roles.
 */
@Composable
fun BoxScope.RemoteDisplayPlaceholder(
    statusText: String,
    sourceLabel: String?,
) {
    Column(
        Modifier
            .align(Alignment.Center)
            .padding(WaypadTheme.spacing.hero),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.Computer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(WaypadTheme.spacing.emptyStateIconSize),
        )
        Spacer(Modifier.height(WaypadTheme.spacing.lg))
        Text(
            statusText,
            style = MaterialTheme.typography.titleMedium,
            color = WaypadOnVideoLetterbox,
        )
        Text(
            sourceLabel ?: "Select a source and start streaming",
            style = MaterialTheme.typography.bodySmall,
            color = WaypadOnVideoLetterboxMuted,
        )
    }
}
