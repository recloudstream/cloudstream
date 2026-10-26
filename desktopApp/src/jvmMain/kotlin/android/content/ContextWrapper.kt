package android.content

/** Android's Activity extends ContextWrapper, kept so the class hierarchy extensions expect exists */
open class ContextWrapper : Context() {
    open fun getBaseContext(): Context = DesktopContext
}
