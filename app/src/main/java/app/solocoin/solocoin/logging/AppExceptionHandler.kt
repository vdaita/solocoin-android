package app.solocoin.solocoin.logging

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Process
import timber.log.Timber
import kotlin.system.exitProcess

class AppExceptionHandler(private val systemHandler: Thread.UncaughtExceptionHandler,
                          private val crashlyticsHandler: Thread.UncaughtExceptionHandler,
                          application: Application) : Thread.UncaughtExceptionHandler {

    private var lastStartedActivity: Activity? = null

    private var startCount = 0

    init {
        application.registerActivityLifecycleCallbacks(
          object : Application.ActivityLifecycleCallbacks {
              override fun onActivityPaused(p0: Activity?) {
                  //Empty
              }

              override fun onActivityResumed(p0: Activity?) {
                  //Empty
              }

              override fun onActivityStarted(p0: Activity?) {
                  startCount++
                  lastStartedActivity = p0
              }

              override fun onActivityDestroyed(p0: Activity?) {
                  startCount--
                  if (startCount <= 0) {
                      lastStartedActivity = null
                  }
              }

              override fun onActivitySaveInstanceState(p0: Activity?, p1: Bundle?) {
                  //Empty
              }

              override fun onActivityStopped(p0: Activity?) {
                  //Empty
              }

              override fun onActivityCreated(p0: Activity?, p1: Bundle?) {
                  //Empty
              }

          })
    }


    override fun uncaughtException(t: Thread, e: Throwable) {
        Timber.e(e)

        lastStartedActivity?.let { activity ->
            val isRestarted = activity.intent.getBooleanExtra(RESTARTED, false)
                                  
            val lastException = activity.intent
                    .getSerializableExtra(LAST_EXCEPTION) as Throwable?

            if (!isRestarted || !isSameException(e, lastException)) {
                killThisProcess {
                    // signal exception to be logged by crashlytics
                    crashlyticsHandler.uncaughtException(t, e) 

                    val intent = activity.intent
                            .putExtra(RESTARTED, true)
                            .putExtra(LAST_EXCEPTION, e)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                      Intent.FLAG_ACTIVITY_CLEAR_TASK)

                    with(activity) {
                      finish()
                      startActivity(intent)
                    }
                }
            } else {
                Timber.d("The system exception handler will handle the caught exception.")
                killThisProcess { systemHandler.uncaughtException(t, e) }
            }
        } ?: killThisProcess {
            crashlyticsHandler.uncaughtException(t, e)
            systemHandler.uncaughtException(t, e)
        }
    }

/**
     * Not bullet-proof, but it works well.
     */

    private fun isSameException(originalException: Throwable, lastException: Throwable?): Boolean {
        if (lastException == null) return false

        return originalException.javaClass == lastException.javaClass &&
                originalException.stackTrace[0] == originalException.stackTrace[0] &&
                originalException.message == lastException.message
    }

    private fun killThisProcess(action: () -> Unit = {}) {
        action()
        Process.killProcess(Process.myPid())
        exitProcess(10)
    }

    companion object {
        private const val RESTARTED = "appExceptionHandler_restarted"
        private const val LAST_EXCEPTION = "appExceptionHandler_lastException"
    }
}
