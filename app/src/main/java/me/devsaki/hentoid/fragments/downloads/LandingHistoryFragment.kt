package me.devsaki.hentoid.fragments.downloads

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.ObjectBoxDB
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.LandingRecord
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.viewContentGalleryPage
import me.devsaki.hentoid.viewholders.TextItem
import java.util.Date

private const val SITE = "SITE"
private const val LANDING_HISTORY = "LANDING_HISTORY"
private const val DEFAULT_URL = "DEFAULT_URL"

class LandingHistoryFragmentK : Fragment() {

    private lateinit var site: Site

    private var input: EditText? = null

    private val itemAdapter = ItemAdapter<TextItem<String>>()
    private val fastAdapter: FastAdapter<TextItem<String>> = FastAdapter.with(itemAdapter)

    companion object {
        fun newInstance(
            context: Context?,
            site: Site,
            defaultUrl: String?
        ): LandingHistoryFragmentK {
            val f = LandingHistoryFragmentK()

            val landingHistory = ObjectBoxDB.selectLandingRecords(site)

            val urlHistory = ArrayList<String>()
            for (r in landingHistory) urlHistory.add(r.url)

            val args = Bundle()
            args.putStringArrayList(LANDING_HISTORY, urlHistory)
            args.putLong(SITE, site.code.toLong())
            args.putString(DEFAULT_URL, defaultUrl)

            f.setArguments(args)

            return f
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_landing_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.let { args ->
            val defaultUrl = args.getString(DEFAULT_URL)

            val siteCode = args.getLong(SITE)
            site = Site.searchByCode(siteCode.toInt())

            val foundSitesList = args.getStringArrayList(LANDING_HISTORY)
            requireNotNull(foundSitesList) { "Landing history not found" }


            val items = foundSitesList
                .map { TextItem(it, it, false) }
                .toMutableList()

            // Add default page if empty
            if (items.isEmpty()) items.add(TextItem<String?>(defaultUrl!!, defaultUrl, false))
            itemAdapter.add(items)

            val recyclerView =
                ViewCompat.requireViewById<RecyclerView?>(view, R.id.landing_history_list)
            recyclerView?.setAdapter(fastAdapter)

            // Item click listener
            fastAdapter.onClickListener = { _, _, i, _ ->
                onItemClick(i)
            }

            val okBtn: View = ViewCompat.requireViewById(view, R.id.landing_history_ok)
            okBtn.setOnClickListener { this.onOkClick(it) }

            input = ViewCompat.requireViewById(view, R.id.landing_history_input)
            input?.setText(defaultUrl)
        }
    }

    private fun onOkClick(view: View?) {
        var url = input?.getText().toString().trim()
        // Remove spaces added around /'s by dumb phone keyboards
        url = url.replace(" /", "/").replace("/ ", "/")

        recordUrlInDb(url)
        launchWebActivity(url)
    }

    private fun onItemClick(item: TextItem<String>): Boolean {
        if (null == item.tag) return false

        recordUrlInDb(item.getObject() ?: "")
        launchWebActivity(item.getObject() ?: "")
        return true
    }

    private fun recordUrlInDb(relativeUrl: String) {
        if (null == activity) return

        var record: LandingRecord? = ObjectBoxDB.selectLandingRecord(site, relativeUrl)
        if (null == record) record = LandingRecord(site, relativeUrl)
        record.lastAccessDate = Date().time
        ObjectBoxDB.insertLandingRecord(record)
    }

    private fun launchWebActivity(relativeUrl: String) {
        if (null == activity) return

        var completeUrl = site.url
        if (!completeUrl.endsWith("/") && !relativeUrl.startsWith("/")) completeUrl += "/"
        completeUrl += relativeUrl

        val content = Content()
        content.site = Site.REDDIT
        content.url = completeUrl
        viewContentGalleryPage(requireContext(), content, true)
    }
}