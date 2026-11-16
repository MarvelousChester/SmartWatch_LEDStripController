package com.example.myled.tile

import android.content.ComponentName
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.tile
import com.example.myled.data.SettingsStore
import com.example.myled.presentation.MainActivity

/**
 * Mirrors the app's control view (colour wheel with marker, brightness arc, power button) from the
 * last saved state. Tiles can't take drags, so it is drawn as one image: tapping the power button
 * toggles power via [TileToggleActivity]; tapping anywhere else opens the app.
 *
 * The strip's real state can't be read back over BLE, so this reflects what the watch last sent.
 */
class StripTileService : Material3TileService() {

    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val settings = SettingsStore(applicationContext).load()
        val hasDevice = settings.lastDeviceAddress.isNotEmpty()

        val openApp = clickable(
            ActionBuilders.launchAction(ComponentName(applicationContext, MainActivity::class.java)),
        )
        val powerAction = if (hasDevice) {
            ActionBuilders.launchAction(ComponentName(applicationContext, TileToggleActivity::class.java))
        } else {
            ActionBuilders.launchAction(ComponentName(applicationContext, MainActivity::class.java))
        }

        val widthDp = deviceConfiguration.screenWidthDp.toFloat()
        val heightDp = deviceConfiguration.screenHeightDp.toFloat()
        val rendered = renderControlView(widthDp, heightDp, deviceConfiguration.screenDensity, settings, hasDevice)

        val view = LayoutElementBuilders.Image.Builder(protoLayoutScope)
            .setImageResource(
                ResourceBuilders.ImageResource.Builder()
                    .setInlineResource(
                        ResourceBuilders.InlineImageResource.Builder()
                            .setData(rendered.pixels)
                            .setWidthPx(rendered.widthPx)
                            .setHeightPx(rendered.heightPx)
                            .setFormat(ResourceBuilders.IMAGE_FORMAT_ARGB_8888)
                            .build(),
                    )
                    .build(),
            )
            .setWidth(dp(widthDp))
            .setHeight(dp(heightDp))
            .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FIT)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(openApp)
                    .setSemantics(describe("LED strip controls. Tap to open the app."))
                    .build(),
            )
            .build()

        // Invisible touch target over the drawn power button (48dp minimum), at the bottom centre.
        val powerTarget = LayoutElementBuilders.Box.Builder()
            .setWidth(dp(POWER_TARGET_DP))
            .setHeight(dp(POWER_TARGET_DP))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable(powerAction))
                    .setSemantics(describe("Toggle power"))
                    .build(),
            )
            .build()

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
            .addContent(view)
            .addContent(powerTarget)
            .build()

        return tile(timeline = Timeline.fromLayoutElement(root))
    }

    private fun describe(text: String) =
        ModifiersBuilders.Semantics.Builder()
            .setContentDescription(TypeBuilders.StringProp.Builder(text).build())
            .build()

    private companion object {
        const val POWER_TARGET_DP = 48f
    }
}
