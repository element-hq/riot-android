package im.vector.activity

import android.support.v7.app.AppCompatActivity
import android.os.Bundle

import im.vector.R
import android.widget.TextView
import butterknife.BindView
import butterknife.ButterKnife
import im.vector.BuildConfig


/**
 * Display a activity containing info about Saba Messenger
 */
class AboutSabaActivity : AppCompatActivity() {
    @BindView(R.id.version_text)
    lateinit var versionTextView: TextView

    @BindView(R.id.security_level_text)
    lateinit var securityLevelTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_saba)
        ButterKnife.bind(this)
        versionTextView.text = getString(R.string.saba_about_version_info, BuildConfig.VERSION_NAME)
        securityLevelTextView.text = getString(R.string.saba_about_security_level_info, "1")
    }
}