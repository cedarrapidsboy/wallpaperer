package com.moosedrive.wallpaperer;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import java.text.MessageFormat;
import java.util.Locale;

import mehdi.sakout.aboutpage.AboutPage;
import mehdi.sakout.aboutpage.Element;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Element storageElement = new Element();
        String freeSpace = String.format(Locale.US, "%1.2f", (this.getFilesDir().getFreeSpace() - MainActivity.MINIMUM_REQUIRED_FREE_SPACE) / (1024.0 * 1000000));
        String totalSpace = String.format(Locale.US, "%1.2f", (this.getFilesDir().getTotalSpace() - MainActivity.MINIMUM_REQUIRED_FREE_SPACE) / (1024.0 * 1000000));
        String pattern = getString(R.string.about_storage_details);
        MessageFormat formatter = new MessageFormat(pattern, Locale.US);

        storageElement.setTitle(formatter.format(new Object[] {freeSpace, totalSpace}));
        Intent storageIntent = new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        if (storageIntent.resolveActivity(getPackageManager()) != null)
            storageElement.setIntent(storageIntent);

        View aboutPage = new AboutPage(this)
                .isRTL(false)
                .setDescription(getString(R.string.about_app_description))
                .setImage(R.drawable.ic_launcher)
                .addItem(new Element().setTitle(getString(R.string.app_simple_name) + " " + BuildConfig.VERSION_NAME))
                .addGroup(getString(R.string.about_storage_title))
                .addItem(storageElement)
                .addGroup(getString(R.string.about_social_title))
                .addGitHub(getString(R.string.about_github_user))
                .create();

        setContentView(aboutPage);
    }
}