package protect.card_locker.shiroikuma

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * shiroikuma-nekokan fork — toasts in the black-yellow look: a panel in the toast ground with
 * the toast frame round it and the toast text inside, each a role of its own.
 *
 * A drop-in for [Toast.makeText]. The toast is made by the platform call and then given a view
 * of ours, so it keeps its text for anything that reads it. The platform shows a toast with a
 * view only while the app is in front; with no activity started the stock toast is returned.
 */
object SkToast {
    private var started = 0

    /** Counts started activities, to know whether a toast with a view of ours will be shown. */
    @JvmStatic
    fun watch(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { started++ }
            override fun onActivityStopped(activity: Activity) { if (started > 0) started-- }
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    @JvmStatic
    fun makeText(context: Context, @StringRes resId: Int, duration: Int): Toast =
        makeText(context, context.getText(resId), duration)

    @JvmStatic
    fun makeText(context: Context, text: CharSequence?, duration: Int): Toast {
        val toast = Toast.makeText(context, text, duration)
        if (started <= 0) return toast
        val density = context.resources.displayMetrics.density
        val horizontal = (20 * density).toInt()
        val vertical = (12 * density).toInt()
        val view = TextView(context).apply {
            this.text = text
            setTextColor(SkTheme.color(context, SkSlot.TOAST_TEXT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            background = SkPill.panel(
                context,
                SkTheme.color(context, SkSlot.TOAST_GROUND),
                SkTheme.color(context, SkSlot.TOAST_FRAME),
                24f,
            )
            setPadding(horizontal, vertical, horizontal, vertical)
        }
        @Suppress("DEPRECATION")
        toast.view = view
        return toast
    }
}
