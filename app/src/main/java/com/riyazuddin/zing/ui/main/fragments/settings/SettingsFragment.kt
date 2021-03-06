package com.riyazuddin.zing.ui.main.fragments.settings

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.riyazuddin.zing.R
import com.riyazuddin.zing.data.entities.User
import com.riyazuddin.zing.databinding.FragmentSettingsBinding
import com.riyazuddin.zing.other.Constants.PRIVATE
import com.riyazuddin.zing.other.Constants.PUBLIC
import com.riyazuddin.zing.other.EventObserver
import com.riyazuddin.zing.other.snackBar
import com.riyazuddin.zing.ui.auth.AuthActivity
import com.riyazuddin.zing.ui.bottomsheet.AccountPrivacyBottomSheetFragment
import com.riyazuddin.zing.ui.dialogs.CustomDialog
import com.riyazuddin.zing.ui.main.fragments.HomeFragment
import com.riyazuddin.zing.ui.main.viewmodels.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    companion object {
        const val TAG = "SettingsFragment"
    }

    private lateinit var binding: FragmentSettingsBinding
    private val viewModel: SettingsViewModel by viewModels()

    private var currentUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Firebase.auth.uid?.let {
            Log.i(TAG, "onCreate: Calling")
            viewModel.getUserProfile(it)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentSettingsBinding.bind(view)

        binding.switchPrivateAccount.isClickable = currentUser != null

        subscribeToObservers()
        setClickListeners()


        binding.switchPrivateAccount.setOnClickListener {
            binding.switchPrivateAccount.isChecked = !binding.switchPrivateAccount.isChecked
            currentUser?.let {
                AccountPrivacyBottomSheetFragment(
                    if (it.privacy == PUBLIC) PRIVATE
                    else PUBLIC
                ).apply {
                    onClickListener {
                        viewModel.toggleAccountPrivacy(
                            it.uid,
                            if (it.privacy == PUBLIC) PRIVATE
                            else PUBLIC
                        )
                        dismiss()
                    }
                }.show(parentFragmentManager, tag)
            }

        }
        val version = "Version : ${com.riyazuddin.zing.BuildConfig.VERSION_NAME}"
        binding.tvVersion.text = version
    }

    private fun subscribeToObservers() {
        viewModel.userProfileStatus.observe(viewLifecycleOwner, EventObserver(
            onError = {
                Log.e(TAG, "subscribeToObservers: $it")
            }
        ) {
            binding.switchPrivateAccount.isClickable = true
            currentUser = it
            binding.switchPrivateAccount.isChecked = it.privacy != PUBLIC
        })

        viewModel.togglePrivacyStatus.observe(viewLifecycleOwner, EventObserver(
            onError = {
                snackBar(it)
                Log.e(TAG, "subscribeToObservers: $it")
            },
            onLoading = {
                Toast.makeText(requireContext(), "Changing Account Privacy", Toast.LENGTH_SHORT)
                    .show()
            }
        ) { privacy ->
            currentUser?.let {
                it.privacy = privacy
            }
            binding.switchPrivateAccount.isChecked = privacy == PRIVATE
        })

        viewModel.removeDeviceTokeStatus.observe(viewLifecycleOwner, EventObserver(
            onError = {
                snackBar(it)
                Log.e(HomeFragment.TAG, "subscribeToObservers: $it")
            },
            onLoading = {
                Toast.makeText(requireContext(), "Logging Out", Toast.LENGTH_SHORT).show()
            }
        ) {
            if (it) {
                Firebase.auth.signOut()
                Intent(requireActivity(), AuthActivity::class.java).apply {
                    startActivity(this)
                    requireActivity().finish()
                }
            } else {
                snackBar("can't logout. Try again")
            }
        })
    }

    private fun checkForUpdate() {
        val appVersion = getAppVersion()
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val latestVersion = remoteConfig.getString("latest_version_of_app")
        if (latestVersion.isNotEmpty() && appVersion.isNotEmpty()) {
            val appVersionWithoutAlphaNumeric = appVersion.replace(".", "")
            val latestVersionWithoutAlphaNumeric = latestVersion.replace(".", "")
            try {
                val appVersionInt = appVersionWithoutAlphaNumeric.toInt()
                val latestVersionInt = latestVersionWithoutAlphaNumeric.toInt()
                if (latestVersionInt > appVersionInt) {
                    MaterialAlertDialogBuilder(
                        requireContext(),
                        R.style.MaterialAlertDialog_Round
                    ).apply {
                        setIcon(R.drawable.ic_update)
                        setTitle("New Update Available")
                        setMessage("Update the app to the latest version for new features and bug fix. Note: UnInstall current app and install new apk from Downloads Directory")
                        setPositiveButton("Update") { _, _ ->
                            val newVersionUrl =
                                remoteConfig.getString("new_version_url")

                            val request = DownloadManager.Request(Uri.parse(newVersionUrl))
                            val title = "Zing-${latestVersionWithoutAlphaNumeric}.apk"
                            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            request.setDestinationInExternalPublicDir(
                                Environment.DIRECTORY_DOWNLOADS,
                                title
                            )

                            val manager =
                                requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                            manager.enqueue(request)
                            Toast.makeText(
                                requireContext(),
                                "Downloading...",
                                Toast.LENGTH_SHORT
                            )
                                .show()

                        }
                        setNegativeButton("Cancel") { dialogInterface, _ ->
                            dialogInterface.dismiss()
                        }
                    }.show()
                } else {
                    Toast.makeText(requireContext(), "App is up-to date", Toast.LENGTH_SHORT)
                        .show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "checkForUpdate: ", e)
            }
        }
    }

    private fun getAppVersion(): String {
        var result: String? = null
        try {
            result = requireContext().packageManager.getPackageInfo(
                requireContext().packageName,
                0
            ).versionName
        } catch (e: Exception) {
            Log.e(TAG, "getApVersion: ", e)
        }
        return result ?: ""
    }

    private fun setClickListeners() {
        binding.tvEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_profileInfo)
        }
        binding.tvnChangePassword.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_currentPasswordVerification)
        }
        binding.tvCheckForUpdates.setOnClickListener {
            checkForUpdate()
        }
        binding.btnLogout.setOnClickListener {
            CustomDialog(getString(R.string.log_out), getString(R.string.log_out_message)).apply {
                setPositiveListener {
                    viewModel.removeDeviceToken(Firebase.auth.uid!!)
                }
            }.show(parentFragmentManager, null)
        }
        binding.tvPrivacyPolicy.setOnClickListener {
            findNavController().navigate(
                R.id.action_settingsFragment_to_privacyPolicyAndTermsAndConditionsFragment,
                Bundle().apply {
                    putString("type", "PRIVACY_POLICY")
                }
            )
        }
        binding.tvTermsAndConditions.setOnClickListener {
            findNavController().navigate(
                R.id.action_settingsFragment_to_privacyPolicyAndTermsAndConditionsFragment,
                Bundle().apply {
                    putString("type", "TERMS_AND_CONDITIONS")
                }
            )
        }
    }
}