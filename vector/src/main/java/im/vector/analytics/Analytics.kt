package im.vector.analytics

interface Analytics {

    fun trackScreen(screen: String, title: String? = null)

    fun trackEvent(event: Event)

    fun visitVariable(index: Int, name: String, value: String)

    fun forceDispatch()

}