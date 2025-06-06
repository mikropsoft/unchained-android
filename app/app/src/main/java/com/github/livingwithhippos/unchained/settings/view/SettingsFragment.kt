package com.github.livingwithhippos.unchained.settings.view

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.method.DigitsKeyListener
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.github.livingwithhippos.unchained.R
import com.github.livingwithhippos.unchained.settings.viewmodel.SettingEvent
import com.github.livingwithhippos.unchained.settings.viewmodel.SettingsViewModel
import com.github.livingwithhippos.unchained.utilities.FEEDBACK_URL
import com.github.livingwithhippos.unchained.utilities.GPLV3_URL
import com.github.livingwithhippos.unchained.utilities.extension.getThemeList
import com.github.livingwithhippos.unchained.utilities.extension.openExternalWebPage
import com.github.livingwithhippos.unchained.utilities.extension.showToast
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

/**
 * A simple [PreferenceFragmentCompat] subclass. Manages the interactions with the items in the
 * preferences menu
 */
@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {
    @Inject lateinit var preferences: SharedPreferences

    private val viewModel: SettingsViewModel by activityViewModels()

    private val pickDirectoryLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            if (it != null) {
                Timber.d("User has picked a folder $it")

                // permanent permissions
                val contentResolver = requireContext().contentResolver

                val takeFlags: Int =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                contentResolver.takePersistableUriPermission(it, takeFlags)

                viewModel.setDownloadFolder(it)
            } else {
                Timber.d("User has not picked a folder")
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        val dayNightPreference = findPreference<ListPreference>(KEY_DAY_NIGHT)

        dayNightPreference?.setOnPreferenceChangeListener { oldValue, newValue ->
            if (oldValue != newValue) {
                when (newValue) {
                    THEME_AUTO ->
                        AppCompatDelegate.setDefaultNightMode(
                            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        )
                    THEME_DAY ->
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    THEME_NIGHT ->
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }
            }
            true
        }

        findPreference<Preference>("user_logout")?.setOnPreferenceClickListener {
            viewModel.userLogout()
            true
        }

        findPreference<EditTextPreference>("filter_size_mb")?.setOnBindEditTextListener {
            it.keyListener = DigitsKeyListener.getInstance("0123456789")
        }

        setupTheme()

        setupKodi()

        setupVersion()

        findPreference<Preference>("download_folder_key")?.setOnPreferenceClickListener {
            pickDirectoryLauncher.launch(null)
            true
        }

        findPreference<Preference>("manage_remote_devices")?.setOnPreferenceClickListener {
            val action =
                SettingsFragmentDirections.actionSettingsFragmentToRemoteDeviceListFragment()
            findNavController().navigate(action)
            true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {

        viewModel.kodiLiveData.observe(viewLifecycleOwner) {
            when (it.getContentIfNotHandled()) {
                true -> {
                    context?.showToast(R.string.kodi_connection_successful)
                }
                false -> {
                    context?.showToast(R.string.kodi_connection_error)
                }
                null -> {}
            }
        }

        viewModel.eventLiveData.observe(viewLifecycleOwner) {
            when (it.getContentIfNotHandled()) {
                SettingEvent.Logout -> {
                    context?.showToast(R.string.user_logged_out)
                    activity?.finishAffinity()
                }

                SettingEvent.LogoutNoCredentials -> {
                    context?.showToast(R.string.no_credentials_found)
                }
                null -> {
                    // do nothing
                }
            }
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    private fun setupTheme() {
        findPreference<Preference>("selected_theme")?.setOnPreferenceClickListener {
            openThemePickerDialog()
            true
        }
        val themeRes =
            preferences.getInt(KEY_THEME_NEW, R.style.Theme_Unchained_Material3_Green_One)
        val currentTheme: ThemeItem? =
            requireContext().getThemeList().find { it.themeID == themeRes }
        findPreference<Preference>("selected_theme")?.summary = currentTheme?.name
    }

    private fun setupKodi() {

        findPreference<Preference>("kodi_remote_control_info")?.setOnPreferenceClickListener {
            context?.openExternalWebPage("https://kodi.wiki/view/Settings/Services/Control") == true
        }
        // todo: sistema per kodi
        val ipPreference = findPreference<EditTextPreference>("kodi_ip_address")
        val portPreference = findPreference<EditTextPreference>("kodi_port")

        // todo: aside from ips are domains accepted? remove this in that case
        ipPreference?.setOnBindEditTextListener {
            it.keyListener = DigitsKeyListener.getInstance("0123456789.")
        }
        portPreference?.setOnBindEditTextListener {
            it.keyListener = DigitsKeyListener.getInstance("0123456789")
        }

        portPreference?.setOnPreferenceChangeListener { _, newValue ->
            val portVal: Int? = newValue.toString().toIntOrNull()
            if (portVal != null && portVal > 0 && portVal <= 65535) {
                true
            } else {
                context?.showToast(R.string.port_range_error)
                false
            }
        }
    }

    private fun setupVersion() {
        val pi =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context
                    ?.packageManager
                    ?.getPackageInfo(
                        requireContext().packageName,
                        PackageManager.PackageInfoFlags.of(0),
                    )
            } else {
                context?.packageManager?.getPackageInfo(requireContext().packageName, 0)
            }
        val version = pi?.versionName
        val versionPreference = findPreference<Preference>("app_version")
        versionPreference?.summary = version
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "feedback" -> context?.openExternalWebPage(FEEDBACK_URL)
            "license" -> context?.openExternalWebPage(GPLV3_URL)
            "credits" -> openCreditsDialog()
            "terms" -> openTermsDialog()
            "privacy" -> openPrivacyDialog()
            "update_regexps" -> {
                viewModel.updateRegexps()
                context?.showToast(R.string.updating_link_matcher)
            }
            "test_kodi" -> testKodiConnection()
            "delete_external_plugins" -> {
                val removedPlugins = viewModel.removeAllPlugins(requireContext())
                if (removedPlugins >= 0)
                    context?.showToast(getString(R.string.plugin_removed, removedPlugins))
                else context?.showToast(getString(R.string.error))
            }
            else -> return super.onPreferenceTreeClick(preference)
        }

        return true
    }

    private fun testKodiConnection() {
        val ipPreference = findPreference<EditTextPreference>("kodi_ip_address")
        val portPreference = findPreference<EditTextPreference>("kodi_port")
        val usernamePreference = findPreference<EditTextPreference>("kodi_username")
        val passwordPreference = findPreference<EditTextPreference>("kodi_password")

        val ip = ipPreference?.text
        val port = portPreference?.text?.toIntOrNull() ?: -1
        val username = usernamePreference?.text
        val password = passwordPreference?.text

        if (ip.isNullOrBlank() || port <= 0)
            context?.showToast(R.string.kodi_credentials_incomplete)
        else {
            viewModel.testKodi(ip, port, username, password)
        }
    }

    private fun openThemePickerDialog() {
        val dialog = ThemePickerDialog()
        dialog.show(parentFragmentManager, "ThemePickerDialogFragment")
    }

    private fun openCreditsDialog() {
        val dialog = HtmlDialogFragment(R.string.credits_title, R.string.credits_text)
        dialog.show(parentFragmentManager, "CreditsDialogFragment")
    }

    private fun openTermsDialog() {
        val dialog = HtmlDialogFragment(R.string.terms_title, R.string.terms_text)
        dialog.show(parentFragmentManager, "TermsDialogFragment")
    }

    private fun openPrivacyDialog() {
        val dialog = HtmlDialogFragment(R.string.privacy_policy_title, R.string.privacy_text)
        dialog.show(parentFragmentManager, "TermsDialogFragment")
    }

    companion object {
        // these must match the ones used in [xml/settings.xml]
        const val KEY_DAY_NIGHT = "day_night_theme"
        const val KEY_THEME_NEW = "new_current_theme"
        const val KEY_TORRENT_NOTIFICATIONS = "notification_torrent_key"
        const val KEY_REFERRAL_ASKED = "referral_asked_key"
        const val KEY_REFERRAL_USE = "use_referral_key"
        const val KEY_USE_DOH = "use_doh_key"
        const val THEME_AUTO = "auto"
        const val THEME_NIGHT = "night"
        const val THEME_DAY = "day"
    }
}
