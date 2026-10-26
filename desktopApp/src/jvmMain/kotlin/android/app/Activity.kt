package android.app

import android.content.ContextWrapper

/** Stand-in so `CommonActivity.activity` has a type, there is never an Activity on desktop */
open class Activity : ContextWrapper()
