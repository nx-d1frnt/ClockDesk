package com.nxd1frnt.clockdesk2.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.nxd1frnt.clockdesk2.BuildConfig
import com.nxd1frnt.clockdesk2.R

class AboutFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE

        // Version text in header card
        view.findViewById<TextView>(R.id.about_version).text =
            getString(R.string.about_version_format, versionName)

        // Version text in info row
        view.findViewById<TextView>(R.id.about_version_value).text =
            getString(R.string.about_version_code_format, versionName, versionCode)

        // Footer
        view.findViewById<TextView>(R.id.about_footer).text =
            getString(R.string.about_footer, versionName)

        // GitHub button
        view.findViewById<MaterialButton>(R.id.about_btn_github).setOnClickListener {
            openUrl(getString(R.string.about_github_url))
        }

        // License button
        view.findViewById<MaterialButton>(R.id.about_btn_license).setOnClickListener {
            openUrl(getString(R.string.about_license_url))
        }

        // Source row tappable
        view.findViewById<View>(R.id.about_row_source).setOnClickListener {
            openUrl(getString(R.string.about_github_url))
        }

        // License row tappable
        view.findViewById<View>(R.id.about_row_license).setOnClickListener {
            openUrl(getString(R.string.about_license_url))
        }
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}
