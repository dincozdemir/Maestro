package device

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import hierarchy.ViewHierarchy
import xcuitest.api.DeviceInfo
import okio.Sink
import okio.buffer
import okio.source
import org.slf4j.LoggerFactory
import util.IOSLaunchArguments.toIOSLaunchArguments
import util.LocalSimulatorUtils
import xcuitest.installer.LocalXCTestInstaller
import java.io.File
import java.io.InputStream
import java.nio.channels.Channels
import java.nio.file.Files

class SimctlIOSDevice(
    override val deviceId: String,
) : IOSDevice {

    companion object {
        private val logger = LoggerFactory.getLogger(SimctlIOSDevice::class.java)
    }

    private var screenRecording: LocalSimulatorUtils.ScreenRecording? = null

    override fun open() {
        TODO("Not yet implemented")
    }

    override fun deviceInfo(): DeviceInfo {
        TODO("Not yet implemented")
    }

    override fun viewHierarchy(excludeKeyboardElements: Boolean): ViewHierarchy {
        TODO("Not yet implemented")
    }

    override fun tap(x: Int, y: Int) {
        TODO("Not yet implemented")
    }

    override fun longPress(x: Int, y: Int, durationMs: Long) {
        TODO("Not yet implemented")
    }

    override fun pressKey(name: String) {
        TODO("Not yet implemented")
    }

    override fun pressButton(name: String) {
        TODO("Not yet implemented")
    }

    override fun scroll(xStart: Double, yStart: Double, xEnd: Double, yEnd: Double, duration: Double) {
        TODO("Not yet implemented")
    }

    override fun input(text: String) {
        TODO("Not yet implemented")
    }

    override fun install(stream: InputStream) {
        LocalSimulatorUtils.install(deviceId, stream)
    }

    override fun uninstall(id: String) {
        LocalSimulatorUtils.uninstall(deviceId, id)
    }

    override fun clearAppState(id: String) {
        LocalSimulatorUtils.clearAppState(deviceId, id)
    }

    override fun clearKeychain(): Result<Unit, Throwable> {
        return runCatching {
            LocalSimulatorUtils.clearKeychain(deviceId)
        }
    }

    override fun launch(
        id: String,
        launchArguments: Map<String, Any>,
    ) {
        val iOSLaunchArguments = launchArguments.toIOSLaunchArguments()
        LocalSimulatorUtils.launch(
            deviceId = deviceId,
            bundleId = id,
            launchArguments = iOSLaunchArguments,
        )
    }

    override fun stop(id: String) {
        LocalSimulatorUtils.terminate(deviceId, bundleId = id)
    }

    override fun isKeyboardVisible(): Boolean {
        error("Not Supported")
    }

    override fun openLink(link: String): Result<Unit, Throwable> {
        return runCatching {
            LocalSimulatorUtils.openURL(deviceId, link)
        }
    }

    override fun takeScreenshot(out: Sink, compressed: Boolean) {
        TODO("Not yet implemented")
    }

    override fun startScreenRecording(out: Sink): Result<IOSScreenRecording, Throwable> {
        return runCatching {
            val screenRecording = LocalSimulatorUtils.startScreenRecording(deviceId)
            this.screenRecording = screenRecording

            object : IOSScreenRecording {
                override fun close() {
                    val file = stopScreenRecording() ?: return
                    val byteChannel = Files.newByteChannel(file.toPath())
                    val source = Channels.newInputStream(byteChannel).source().buffer()
                    val buffer = out.buffer()

                    buffer.writeAll(source)

                    byteChannel.close()
                    buffer.close()
                    file.delete()
                }
            }
        }
    }

    private fun stopScreenRecording(): File? {
        return screenRecording
            ?.let { LocalSimulatorUtils.stopScreenRecording(it) }
            .also { screenRecording = null }
    }

    override fun addMedia(path: String) {
        LocalSimulatorUtils.addMedia(deviceId, path)
    }

    override fun setLocation(latitude: Double, longitude: Double): Result<Unit, Throwable> {
        return runCatching {
            LocalSimulatorUtils.setLocation(deviceId, latitude, longitude)
        }
    }

    override fun setOrientation(orientation: String) {
        TODO("Not yet implemented")
    }

    override fun isShutdown(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isScreenStatic(): Boolean {
        TODO("Not yet implemented")
    }

    override fun setPermissions(id: String, permissions: Map<String, String>) {
        val formattedPermissions = permissions.entries.joinToString(separator = ", ") { "${it.key}=${it.value}" }

        runCatching {
            logger.info("[Start] Setting permissions $formattedPermissions through applesimutils")
            LocalSimulatorUtils.setAppleSimutilsPermissions(deviceId, id, permissions)
            logger.info("[Done] Setting permissions through applesimutils")
        }.onFailure {
            logger.error("Failed setting permissions $permissions via applesimutils", it)
        }

        logger.info("[Start] Setting Permissions $formattedPermissions through simctl")
        LocalSimulatorUtils.setSimctlPermissions(deviceId, id, permissions)
        logger.info("[Done] Setting Permissions $formattedPermissions through simctl")
    }

    override fun eraseText(charactersToErase: Int) {
        TODO("Not yet implemented")
    }

    override fun close() {
        stopScreenRecording()

        logger.info("[Start] Stop and uninstall the runner app")
        stop(id = LocalXCTestInstaller.UI_TEST_RUNNER_APP_BUNDLE_ID)
        uninstall(id = LocalXCTestInstaller.UI_TEST_RUNNER_APP_BUNDLE_ID)
        logger.info("[Done] Stop and uninstall the runner app")
    }

}
