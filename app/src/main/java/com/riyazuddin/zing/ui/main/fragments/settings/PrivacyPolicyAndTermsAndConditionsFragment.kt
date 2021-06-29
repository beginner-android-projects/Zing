package com.riyazuddin.zing.ui.main.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.riyazuddin.zing.R
import com.riyazuddin.zing.databinding.FragmentPrivacyPolicyAndTermsAndConditionsBinding

class PrivacyPolicyAndTermsAndConditionsFragment :
    Fragment(R.layout.fragment_privacy_policy_and_terms_and_conditions) {

    companion object {
        const val TAG = "PP_TAC_FRAGMENT"
    }

    private lateinit var binding: FragmentPrivacyPolicyAndTermsAndConditionsBinding
    private val args: PrivacyPolicyAndTermsAndConditionsFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentPrivacyPolicyAndTermsAndConditionsBinding.inflate(layoutInflater)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.webView.apply {
            if (args.type == "PRIVACY_POLICY") {
                loadUrl("file:///android_asset/PrivacyPolicy.html")
                binding.toolbar.title = "Privacy Policy"
            }
            if (args.type == "TERMS_AND_CONDITIONS") {
                loadUrl("file:///android_asset/TermsAndConditions.html")
                binding.toolbar.title = "Terms And Conditions"
            }
        }
        binding.btnClose.setOnClickListener {
            findNavController().popBackStack()
        }
    }
}