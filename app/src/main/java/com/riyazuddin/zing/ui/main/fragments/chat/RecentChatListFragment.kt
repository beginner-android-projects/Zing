package com.riyazuddin.zing.ui.main.fragments.chat

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.riyazuddin.zing.R
import com.riyazuddin.zing.adapters.LastMessageAdapter
import com.riyazuddin.zing.data.entities.User
import com.riyazuddin.zing.databinding.FragmentRecentChatListBinding
import com.riyazuddin.zing.other.EventObserver
import com.riyazuddin.zing.other.snackBar
import com.riyazuddin.zing.ui.main.viewmodels.ChatViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RecentChatListFragment : Fragment(R.layout.fragment_recent_chat_list) {

    private var _binding: FragmentRecentChatListBinding? = null
    private val binding get() = _binding!!
    private val args: RecentChatListFragmentArgs by navArgs()

    private val viewModel: ChatViewModel by viewModels()

    @Inject
    lateinit var lastMessageAdapter: LastMessageAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentRecentChatListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        binding = FragmentRecentChatListBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        binding.ibNewChat.setOnClickListener {
            val bundle = Bundle().apply {
                putSerializable("currentUser", args.currentUser)
            }
            findNavController().navigate(
                R.id.action_recentChatListFragment_to_newChatFragment,
                bundle
            )
        }

        subscribeToObservers()
        setupRecyclerView()
        viewModel.getLastMessageFirstQuery(Firebase.auth.uid!!)

        lastMessageAdapter.setOnItemClickListener { lastMessage, user ->
            val bundle = Bundle().apply {
                putSerializable("otherEndUser", user)
                putSerializable("currentUser", args.currentUser)
            }
            findNavController().navigate(R.id.action_recentChatListFragment_to_chatFragment, bundle)
        }
    }

    private fun subscribeToObservers() {
        viewModel.recentMessagesList.observe(viewLifecycleOwner, EventObserver(
            onError = {
                snackBar(it)
                binding.progressBar.isVisible = false
            },
            onLoading = {
                Log.i(TAG, "subscribeToObservers: Loading")
                binding.progressBar.isVisible = true
            }
        ) {
            for (e in it)
                Log.i(TAG, "subscribeToObservers: $e")
            lastMessageAdapter.lastMessages = it
            binding.progressBar.isVisible = false
            lastMessageAdapter.notifyDataSetChanged()
        })
    }

    private fun setupRecyclerView() {
        binding.rvRecentChatList.apply {
            adapter = lastMessageAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    override fun onDestroyView() {
        Log.i(TAG, "onDestroyView: ")
        viewModel.clearRecentMessagesList()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "RecentChatFrag"
    }
}