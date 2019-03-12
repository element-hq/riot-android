package im.vector.activity;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import im.vector.R;

public class AboutSabaActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Saba creation: displays activity containing info about Saba app
        setContentView(R.layout.activity_about_saba);
    }
}