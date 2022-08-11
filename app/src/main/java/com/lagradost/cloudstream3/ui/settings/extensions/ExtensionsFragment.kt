package com.lagradost.cloudstream3.ui.settings.extensions

import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.plugins.PREBUILT_REPOSITORIES
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import kotlinx.android.synthetic.main.add_repo_input.*
import kotlinx.android.synthetic.main.add_repo_input.apply_btt
import kotlinx.android.synthetic.main.add_repo_input.cancel_btt
import kotlinx.android.synthetic.main.fragment_extensions.*

class ExtensionsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_extensions, container, false)
    }

    private val extensionViewModel: ExtensionsViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //context?.fixPaddingStatusbar(extensions_root)

        setUpToolbar(R.string.extensions)

        repo_recycler_view?.adapter = RepoAdapter(PREBUILT_REPOSITORIES, false, {
            findNavController().navigate(
                R.id.navigation_settings_extensions_to_navigation_settings_plugins,
                Bundle().apply {
                    putString(PLUGINS_BUNDLE_NAME, it.name)
                    putString(PLUGINS_BUNDLE_URL, it.url)
                })
        }, { repo ->
            // Prompt user before deleting repo
            main {
                val builder = AlertDialog.Builder(context ?: view.context)
                val dialogClickListener =
                    DialogInterface.OnClickListener { _, which ->
                        when (which) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                ioSafe {
                                    RepositoryManager.removeRepository(view.context, repo)
                                    extensionViewModel.loadRepositories()
                                }
                            }
                            DialogInterface.BUTTON_NEGATIVE -> {}
                        }
                    }

                builder.setTitle(R.string.delete_repository)
                    .setMessage(
                        context?.getString(R.string.delete_repository_plugins)
                    )
                    .setPositiveButton(R.string.delete, dialogClickListener)
                    .setNegativeButton(R.string.cancel, dialogClickListener)
                    .show()
            }
        })

        observe(extensionViewModel.repositories) {
            (repo_recycler_view?.adapter as? RepoAdapter)?.repositories = it
            (repo_recycler_view?.adapter as? RepoAdapter)?.notifyDataSetChanged()
        }

        add_repo_button?.setOnClickListener {
            val builder =
                AlertDialog.Builder(context ?: return@setOnClickListener, R.style.AlertDialogCustom)
                    .setView(R.layout.add_repo_input)

            val dialog = builder.create()
            dialog.show()
            (activity?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager?)?.primaryClip?.getItemAt(
                0
            )?.text?.toString()?.let { copy ->
                // Fix our own repo links and only paste the text if it's a link.
                if (copy.startsWith("http")) {
                    val fixedUrl = if (copy.startsWith("https://cs.repo")) {
                        "https://" + copy.substringAfter("?")
                    } else {
                        copy
                    }
                    dialog.repo_url_input?.setText(fixedUrl)
                }
            }

//            dialog.text2?.text = provider.name
            dialog.apply_btt?.setOnClickListener secondListener@{
                val name = dialog.repo_name_input?.text?.toString()
                val url = dialog.repo_url_input?.text?.toString()
                if (url.isNullOrBlank()) {
                    showToast(activity, R.string.error_invalid_data, Toast.LENGTH_SHORT)
                    return@secondListener
                }

                ioSafe {
                    val fixedName = if (!name.isNullOrBlank()) name
                    else RepositoryManager.parseRepository(url)?.name ?: "No name"

                    val newRepo = RepositoryData(fixedName, url)
                    RepositoryManager.addRepository(newRepo)
                    extensionViewModel.loadRepositories()
                }
                dialog.dismissSafe(activity)
            }
            dialog.cancel_btt?.setOnClickListener {
                dialog.dismissSafe(activity)
            }
        }


        extensionViewModel.loadRepositories()
    }
}