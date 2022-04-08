package com.benzorom.systemui.qs.tiles

import android.content.Intent
import android.content.om.OverlayInfo
import android.content.om.OverlayManager
import android.content.pm.PackageManager
import android.os.Build.IS_DEBUGGABLE
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.service.quicksettings.Tile
import android.util.Slog
import android.view.View
import com.android.internal.logging.MetricsLogger
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import kotlin.collections.List
import javax.inject.Inject

class OverlayToggleTile @Inject constructor(
    host: QSHost,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    om: OverlayManager
) : QSTileImpl<QSTile.BooleanState>(
    host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
    statusBarStateController, activityStarter, qsLogger
) {

    companion object {
        const val SYSUI_PACKAGE = "com.android.systemui"
    }

    val om: OverlayManager
    private var overlayLabel: CharSequence? = null
    private var overlayPackage: String? = null

    override fun newTileState(): QSTile.BooleanState {
        return QSTile.BooleanState()
    }

    override fun isAvailable(): Boolean {
        return IS_DEBUGGABLE
    }

    override fun getLongClickIntent(): Intent? {
        return null
    }

    override fun getMetricsCategory(): Int {
        return -1
    }

    override fun getTileLabel(): CharSequence {
        return "Overlay"
    }

    override fun handleLongClick(view: View?) {}

    override fun handleClick(view: View?) {
        if (overlayPackage != null) {
            val enabled = state.state != Tile.STATE_UNAVAILABLE
            Slog.v(TAG, "Setting enable state of $overlayPackage to $enabled")
            om.setEnabled(overlayPackage, enabled, UserHandle.CURRENT)
            refreshState("Restarting...")
            Thread.sleep(250L)
            Slog.v(TAG, "Restarting System UI to react to overlay changes")
            Process.killProcess(Process.myPid())
        }
    }

    override fun handleUpdateState(state: QSTile.BooleanState, arg: Any?) {
        val packageManager: PackageManager = mContext.getPackageManager()
        state.state = Tile.STATE_UNAVAILABLE
        state.label = "No overlay"
        var overlayInfo: OverlayInfo? = null
        val overlayInfosForTarget: List<OverlayInfo>? = om.getOverlayInfosForTarget(
                SYSUI_PACKAGE, UserHandle.CURRENT
        )
        if (overlayInfosForTarget != null) {
            for (currentOverlay in overlayInfosForTarget) {
                if (currentOverlay.packageName.startsWith("com.google.")) {
                    overlayInfo = currentOverlay
                    break
                }
            }
            if (overlayInfo != null) {
                if (overlayPackage != overlayInfo.packageName) {
                    val name: String = overlayInfo.packageName
                    overlayPackage = name
                    overlayLabel =
                        packageManager.getPackageInfo(name, 0).applicationInfo.loadLabel(
                            packageManager
                        )
                }
                state.value = overlayInfo.isEnabled()
                state.state = if (state.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                state.icon = ResourceIcon.get(com.android.internal.R.drawable.stat_sys_adb)
                state.label = overlayLabel
                when {
                    arg != null             -> state.secondaryLabel = "$arg"
                    overlayInfo.isEnabled() -> state.secondaryLabel = "Enabled"
                    else                    -> state.secondaryLabel = "Disabled"
                }
            }
        }
    }

    init {
        this.om = om
    }
}
