package im.vector.activity

//import android.support.v7.app.AppCompatActivity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button

import im.vector.R
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import butterknife.BindView
import butterknife.ButterKnife
import im.vector.BuildConfig


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
        aboutsababutton.setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.sabaos.com"))
            startActivity(i)
        }
        freesoftwarebutton.setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.sabaos.com/legal"))
            startActivity(i)
        }
        privacybutton.setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.sabaos.com/privacy"))
            startActivity(i)
        }

    }
}