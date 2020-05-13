package im.vector.widgets.model

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class JitsiWidgetPropertiesTest {
    private lateinit var properties: JitsiWidgetProperties

    @Before
    fun setup() {
        properties = JitsiWidgetProperties(WIDGET_URI)
    }

    @Test
    fun testParseConferenceDomain() {
        assertEquals("jitsi.riot.im", properties.domain)
    }

    @Test
    fun testParseDisplayName() {
        assertEquals("jitsier", properties.displayName)
    }

    @Test
    fun testParseAvatarUrl() {
        assertEquals("mxc://synapse.matrix.org/OMGWTFBBQ", properties.avatarUrl)
    }
}

private const val WIDGET_URI = "https://riot.im/jitsi.html?confId=JitsiConferencePointlessCleanPot" +
        "#conferenceDomain=jitsi.riot.im&conferenceId=JitsiConferenceEvilCleanMasher&isAudioOnly=\$isAudioOnly&displayName=jitsier" +
        "&avatarUrl=mxc://synapse.matrix.org/OMGWTFBBQ&userId=@jitsier:matrix.org"