package com.woowa.di.activity

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.LifecycleOwner
import com.woowa.di.DIActivity
import kotlin.reflect.KClass
import kotlin.reflect.full.hasAnnotation

class ActivityLifecycleListener : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        if (activity::class.hasAnnotation<DIActivity>() && activity is LifecycleOwner) {
            val component =
                ActivityComponentManager.getComponentInstance(activity::class) as ActivityComponent<*>
            activity.lifecycle.addObserver(component)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is LifecycleOwner && activity.isFinishing && activity::class.hasAnnotation<DIActivity>()) {
            ActivityComponent.getInstance(activity::class as KClass<out ComponentActivity>)
                .deleteAllDIInstance(activity::class)
        }
    }

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) {
    }
}