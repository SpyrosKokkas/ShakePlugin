package com.s3nko.shake

import com.android.ddmlib.IDevice
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.io.BufferedReader
import java.io.InputStreamReader

class ShakeEmulator : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        val devices = AndroidSdkUtils.getDebugBridge(project)?.devices

        if (devices.isNullOrEmpty()) {
            project.showNotification("No Devices Connected", NotificationType.WARNING)
        } else {
            devices.forEach { device ->
                if (device.serialNumber.startsWith("emulator-")) {
                    executeShakeCommand(project, device)
                } else {
                    project.showNotification(
                        "Device ${device.name}: Cannot simulate shake on physical device",
                        NotificationType.WARNING
                    )
                }
            }
        }
    }


    /**
     * Executes the adb command directly to the emulator
     * @param project It defines the project instance
     * @param device It holds the device id so the command knows to which device to send the command
     *
     * First we get the adb path of the android sdk from the user configurations,
     * then, if the path is null, we show a notification so the user can go and configure the Android SDK
     * to the Android Studio.
     * If the adbPath is nonNull, we continue with the chained execution of the commands,
     * that replicate the shake with the emulator virtual sensors.
     *
     * If any of the commands fail, it shows a notification with the error message
     *
     * In case where there is an adbPath but for some reason if wrong/broken,
     * it displays an error notification
     */
    private fun executeShakeCommand(project: Project, device: IDevice) {
        val adbPath = getAdbPath(project)
        if (adbPath == null) {
            project.showNotification("Android SDK not configured. Cannot find 'adb' executable.", NotificationType.ERROR)
            return
        }
        val serial = device.serialNumber

        val firstCommand = arrayOf(
            adbPath, "-s", serial, "emu", "sensor", "set", "acceleration", "100:100:100"
        )
        val secondCommand = arrayOf(
            adbPath, "-s", serial, "emu", "sensor", "set", "acceleration", "0:0:0"
        )

        runCatching {
            val startProcess = Runtime.getRuntime().exec(firstCommand)
            val exitCodeFirst = startProcess.waitFor()

            val endProcess = Runtime.getRuntime().exec(secondCommand)
            val exitCodeSecond = endProcess.waitFor()

            if (exitCodeFirst == 0 && exitCodeSecond == 0) {
                /**
                 * Leaving this here for debugging if necessary later
                 */
//               project.showNotification(
//                  "Device ${device.name}: Shake (Sensor Manipulation) sent successfully.",
//                   NotificationType.INFORMATION
//                )
            } else {
                val errorOutput = BufferedReader(InputStreamReader(endProcess.errorStream)).readText()
                project.showNotification(
                    "Device ${device.name}: ADB Emu Command Failed. Output: $errorOutput",
                    NotificationType.ERROR
                )
            }
        }.onFailure { e ->
            project.showNotification(
                "Device ${device.name}: Error executing host command. Check if ADB is in PATH. Message: ${e.message}",
                NotificationType.ERROR)
        }
    }

    /**
     * Notification extension function
     * @param message It holds the message we want to display
     * @param type It defines the notification type to display (e.g., Type.INFORMATION)
     */
    private fun Project.showNotification(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("ShakeEmulator.Notifications")
            .createNotification("Shake", message, type)
            .notify(this)
    }

    /**
     * Retrieves the adb path using the AndroidSdkUtils
     */
    fun getAdbPath(project : Project): String? {
        return AndroidSdkUtils.findAdb(project).adbPath.toString()
    }
}