package me.devsaki.hentoid.fragments.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.databinding.DialogLibraryUpdateSuccessBinding
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.json.GithubRelease
import me.devsaki.hentoid.retrofit.GithubServer
import me.devsaki.hentoid.viewholders.GitHubReleaseItem
import timber.log.Timber

/**
 * "update success" dialog
 */
class UpdateSuccessDialogFragment : BaseDialogFragment<Nothing>() {

    companion object {
        fun invoke(parent: Fragment) {
            invoke(parent, UpdateSuccessDialogFragment())
        }

        fun invoke(parent: FragmentActivity) {
            invoke(parent, UpdateSuccessDialogFragment())
        }
    }

    // UI
    private var binding: DialogLibraryUpdateSuccessBinding? = null

    // === VARIABLES
    private val itemAdapter: ItemAdapter<GitHubReleaseItem> = ItemAdapter()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View? {
        binding = DialogLibraryUpdateSuccessBinding.inflate(inflater, container, false)

        val releaseItemAdapter = FastAdapter.with(itemAdapter)
        binding?.recyclerView?.adapter = releaseItemAdapter

        return binding?.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        getReleases()
    }

    private fun getReleases() {
        lifecycleScope.launch {
            var response: GithubRelease? = null
            withContext(Dispatchers.IO) {
                try {
                    response = GithubServer.api.latestRelease.execute().body()
                } catch (e: Exception) {
                    onCheckError(e)
                }
            }
            response.let {
                if (null == it) Timber.w("Error fetching GitHub latest release data (empty response)")
                else onCheckSuccess(it)
            }
        }
    }

    private fun onCheckSuccess(latestReleaseInfo: GithubRelease) {
        itemAdapter.add(GitHubReleaseItem(latestReleaseInfo))
    }

    private fun onCheckError(t: Throwable) {
        Timber.w(t, "Error fetching GitHub latest release data")
    }
}