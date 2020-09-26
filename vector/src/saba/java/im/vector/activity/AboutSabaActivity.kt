package im.vector.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import butterknife.BindView
import butterknife.ButterKnife
import im.vector.BuildConfig
import im.vector.R


/**
 * Display a activity containing info about Messenger
 */
class AboutSabaActivity : AppCompatActivity() {
    @BindView(R.id.version_text)
    lateinit var versionTextView: TextView

    @BindView(R.id.security_level_text)
    lateinit var securityLevelTextView: TextView

    @BindView(R.id.button)
    lateinit var aboutsababutton: Button

    @BindView(R.id.button2)
    lateinit var freesoftwarebutton: Button

    @BindView(R.id.button3)
    lateinit var privacybutton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_saba)
        ButterKnife.bind(this)
        versionTextView.text = getString(R.string.saba_about_version_info, BuildConfig.VERSION_NAME)
        securityLevelTextView.text = getString(R.string.saba_about_security_level_info, "1")
        val homeServerUrl = getString(R.string.default_hs_server_url)
        aboutsababutton.setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(homeServerUrl))
            startActivity(i)
        }
        freesoftwarebutton.setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("$homeServerUrl/legal"))
            startActivity(i)
        }
        privacybutton.setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("$homeServerUrl/privacy"))
            startActivity(i)
        }
    }
}