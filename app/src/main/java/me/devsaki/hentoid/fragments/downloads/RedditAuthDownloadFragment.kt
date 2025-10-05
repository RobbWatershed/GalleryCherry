package me.devsaki.hentoid.fragments.downloads

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.QueueActivity
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.retrofit.RedditOAuthApiServer
import me.devsaki.hentoid.util.OAuthSessionManager
import me.devsaki.hentoid.util.download.ContentQueueManager.resumeQueue
import me.devsaki.hentoid.util.image.isSupportedImage
import timber.log.Timber
import java.util.Collections

class RedditAuthDownloadFragment : Fragment() {
    private var imgCount: TextView? = null

    private var currentContent: Content? = null

    // Set of existing and new images of the Reddit album
    private var imageSet: MutableList<ImageFile>? = null
    private var db: CollectionDAO? = null


    companion object {
        fun newInstance(): RedditAuthDownloadFragment {
            val f = RedditAuthDownloadFragment()

            val args = Bundle()
            f.setArguments(args)

            return f
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_reddit_download_auth, container, false)
    }

    override fun onDestroyView() {
        db?.cleanup()
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imgCount = ViewCompat.requireViewById<TextView?>(view, R.id.reddit_auth_img_count)

        val button: View = ViewCompat.requireViewById(view, R.id.reddit_auth_action)
        button.setOnClickListener { v -> onDownloadClick() }

        // Load saved items
        val session = OAuthSessionManager.getSessionBySite(Site.REDDIT)
        if (session != null) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        RedditOAuthApiServer.API.getUserSavedPosts(
                            session.userName,
                            "bearer ${session.accessToken}"
                        ).execute().body()?.let {
                            onSavedItemsSuccess(it.toImageList())
                        }
                    } catch (t: Throwable) {
                        onSavedItemsError(t)
                    }
                }
            }
        } else Timber.e("Session has not been initialized")
    }

    private fun isImageSupported(imgUrl: String): Boolean {
        return isSupportedImage(imgUrl)
    }

    private fun onSavedItemsSuccess(savedUrls: MutableList<String>) { // TODO don't display placeholder when load is not complete - use a "loading..." image
        var savedUrls = savedUrls
        if (savedUrls.isEmpty()) {
            imgCount?.setText(R.string.reddit_auth_noimg)
            return
        }

        // Remove duplicates and unsupported files from saved URLs
        savedUrls = savedUrls.toList().distinct().filter { isImageSupported(it) }.toMutableList()

        // Reverse the list as Reddit puts most recent first and Hentoid does the opposite
        savedUrls.reverse()

        var newImageNumber: Int

        db = ObjectBoxDAO()
        val contentDB = db!!.selectContentBySourceAndUrl(Site.REDDIT, "", "")

        if (null == contentDB) {    // The book has just been detected -> finalize before saving in DB

            // Create a new image set based on saved Urls, adding the cover in the process

            val coverUrl = if (savedUrls.isEmpty()) "" else savedUrls[0]
            val newImages =
                urlsToImageFiles(savedUrls, coverUrl, StatusContent.SAVED).toMutableList()

            Timber.d("Reddit : new content created (%s pages)", newImages.size)
            currentContent = Content(site = Site.REDDIT, dbUrl = "", title = "Reddit")
            currentContent!!.status = StatusContent.SAVED
            db?.insertContent(currentContent!!)
            imageSet = newImages
            newImageNumber = newImages.size - 1 // Don't count the cover
        } else { // TODO duplicated code with BaseWebActivity
            // Create a new image set based on saved Urls, ignoring the cover that should already be there
            val newImages = urlsToImageFiles(savedUrls, "", StatusContent.SAVED).toMutableList()

            // Ignore the images that are already contained in the central booru book
            val existingImages: MutableList<ImageFile> = contentDB.imageFiles
            if (!existingImages.isEmpty()) {
                newImages.removeAll(existingImages)
                Timber.d(
                    "Reddit : adding %s new pages to existing content (%s pages)",
                    newImages.size,
                    existingImages.size
                )

                // Recompute the name of existing images to align them with the formatting of the new ones
                Collections.sort<ImageFile?>(existingImages, ImageFile.ORDER_COMPARATOR)
                var order = 0
                for (img in existingImages) {
                    img.order = order++
                    img.computeName(3)
                }
                // Reindex new images according to their future position in the existing album
                for (img in newImages) {
                    img.order = order++
                    img.computeName(3)
                }
            }

            imageSet = ArrayList<ImageFile>(existingImages)
            imageSet!!.addAll(newImages)

            newImageNumber = newImages.size
            currentContent = contentDB
        }
        Timber.d("Reddit : final image set : %s pages", imageSet!!.size)

        // Display size of new images on screen
        if (newImageNumber > 0) imgCount?.text = String.format(
            requireContext().getString(R.string.reddit_auth_img_count),
            newImageNumber.toString()
        )
        else imgCount?.setText(R.string.reddit_auth_noimg)
    }

    private fun onSavedItemsError(t: Throwable?) {
        Timber.e(t, "Error fetching Reddit saved items")
    }

    private fun onDownloadClick() {
        // Save new images to DB
        currentContent!!.qtyPages = imageSet!!.size - 1 // Don't count the cover
        currentContent!!.status = StatusContent.DOWNLOADING
        val contentId = db!!.insertContent(currentContent!!)

        imageSet?.let { ims ->
            ims.forEach { it.status = StatusContent.SAVED }
            db?.replaceImageList(contentId, ims)
        }

        val queue = db!!.selectQueue()
        var lastIndex = 1
        if (!queue.isEmpty()) {
            lastIndex = queue[queue.size - 1].rank + 1
        }
        db?.insertQueue(contentId, lastIndex)

        resumeQueue(requireContext())
        viewQueue()
    }

    private fun viewQueue() {
        val intent = Intent(requireContext(), QueueActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        requireContext().startActivity(intent)
        requireActivity().finish()
    }
}