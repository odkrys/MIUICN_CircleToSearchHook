// GoogleAssistantHook.kt
package com.test.googleassistanthook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class GoogleAssistantHook : IXposedHookLoadPackage {

    @Throws(Throwable::class)
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        XposedBridge.log("$TAG: Hooking in package: ${lpparam.packageName}")

        if (lpparam.packageName == "android") {
            XposedBridge.log("$TAG: Targeting system_server process for MIUI specific hooks.")

            try {
                val shortCutActionsUtilsClass = XposedHelpers.findClass(
                    "com.miui.server.util.ShortCutActionsUtils",
                    lpparam.classLoader
                )

                XposedHelpers.findAndHookMethod(
                    shortCutActionsUtilsClass,
                    "triggerFunction",
                    String::class.java,   // function
                    String::class.java,   // shortcut
                    Bundle::class.java,   // extras
                    Boolean::class.javaPrimitiveType, // hapticFeedback
                    String::class.java,   // effectKey
                    object : XC_MethodHook() {
                        @Throws(Throwable::class)
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val functionName = param.args[0] as String

                            if (functionName == "launch_voice_assistant") {
                                XposedBridge.log("$TAG: Intercepted launch_voice_assistant")

                                param.result = true

                                try {
                                    val context = XposedHelpers.callStaticMethod(
                                        XposedHelpers.findClass("android.app.ActivityThread", null),
                                        "currentApplication"
                                    ) as Context

                                    // ✅ [1] voice_interaction_service 설정 시도
                                    try {
                                        val desiredService =
                                            "$GOOGLE_ASSISTANT_PACKAGE/$GOOGLE_ASSISTANT_CLASS"
                                        val currentSetting = Settings.Secure.getString(
                                            context.contentResolver,
                                            "voice_interaction_service"
                                        )

                                        if (currentSetting != desiredService) {
                                            val success = Settings.Secure.putString(
                                                context.contentResolver,
                                                "voice_interaction_service",
                                                desiredService
                                            )
                                            if (success) {
                                                XposedBridge.log("$TAG: voice_interaction_service set to $desiredService")
                                            } else {
                                                XposedBridge.log("$TAG: Failed to set voice_interaction_service")
                                            }
                                        } else {
                                            XposedBridge.log("$TAG: voice_interaction_service already set correctly.")
                                        }
                                    } catch (e: Throwable) {
                                        XposedBridge.log("$TAG: Error setting voice_interaction_service: ${Log.getStackTraceString(e)}")
                                    }

                                    // ✅ [2] 어시스턴트 실행
                                    val assistantIntent = Intent(Intent.ACTION_ASSIST).apply {
                                        component = ComponentName(CTS_ASSISTANT_PACKAGE, CTS_ASSISTANT_CLASS)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }

                                    context.startActivity(assistantIntent)
                                    XposedBridge.log("$TAG: Launched CTS Assistant: ${assistantIntent.component}")
                                } catch (e: Exception) {
                                    XposedBridge.log("$TAG: Failed to launch assistant: ${Log.getStackTraceString(e)}")
                                }
                            }
                        }
                    }
                )
            } catch (e: XposedHelpers.ClassNotFoundError) {
                XposedBridge.log("$TAG: ShortCutActionsUtils not found.")
            } catch (e: NoSuchMethodError) {
                XposedBridge.log("$TAG: triggerFunction signature mismatch.")
            } catch (e: Exception) {
                XposedBridge.log("$TAG: Error hooking triggerFunction: ${Log.getStackTraceString(e)}")
            }
        }
    }

    companion object {
        private const val TAG = "GoogleAssistantHook"
        private const val GOOGLE_ASSISTANT_PACKAGE = "com.google.android.googlequicksearchbox"
        private const val GOOGLE_ASSISTANT_CLASS = "com.google.android.voiceinteraction.GsaVoiceInteractionService"
        private const val CTS_ASSISTANT_PACKAGE = "com.parallelc.micts"
        private const val CTS_ASSISTANT_CLASS = "com.parallelc.micts.ui.activity.MainActivity"
    }
}