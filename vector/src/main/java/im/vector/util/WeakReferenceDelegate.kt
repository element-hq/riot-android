package im.vector.util

import java.lang.ref.WeakReference
import kotlin.reflect.KProperty

fun <T> weak() = WeakReferenceDelegate<T>()
fun <T> weak(value: T) = WeakReferenceDelegate(value)

class WeakReferenceDelegate<T> {
    private var weakReference: WeakReference<T>? = null

    constructor()
    constructor(value: T) {
        weakReference = WeakReference(value)
    }

    operator fun getValue(thisRef: Any, property: KProperty<*>): T? = weakReference?.get()
    operator fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        weakReference = WeakReference(value)
    }
}