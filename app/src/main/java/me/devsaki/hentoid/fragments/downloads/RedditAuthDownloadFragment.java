package me.devsaki.hentoid.fragments.downloads;

import static androidx.core.view.ViewCompat.requireViewById;
import static io.reactivex.android.schedulers.AndroidSchedulers.mainThread;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.annimon.stream.Stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.retrofit.RedditOAuthApiServer;
import me.devsaki.hentoid.util.OauthSessionManager;
import me.devsaki.hentoid.util.download.ContentQueueManager;
import me.devsaki.hentoid.util.image.ImageHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

public class RedditAuthDownloadFragment extends Fragment {

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private TextView imgCount;

    private Content currentContent = null;
    private List<ImageFile> imageSet = null; // Set of existing and new images of the Reddit album
    private CollectionDAO db = null;


    static RedditAuthDownloadFragment newInstance() {
        RedditAuthDownloadFragment f = new RedditAuthDownloadFragment();

        Bundle args = new Bundle();
        f.setArguments(args);

        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.fragment_reddit_download_auth, container, false);
    }

    @Override
    public void onDestroyView() {
        compositeDisposable.clear();
        super.onDestroyView();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        imgCount = requireViewById(view, R.id.reddit_auth_img_count);

        View button = requireViewById(view, R.id.reddit_auth_action);
        button.setOnClickListener(v -> onDownloadClick());

        // Load saved items
        OauthSessionManager.OauthSession session = OauthSessionManager.getInstance().getSessionBySite(Site.REDDIT);
        if (session != null) {
            compositeDisposable.add(
                    RedditOAuthApiServer.API.getUserSavedPosts(session.getUserName(), "bearer " + session.getAccessToken())
                            .observeOn(mainThread())
                            .subscribe(t -> onSavedItemsSuccess(t.toImageList()),
                                    this::onSavedItemsError)
            );
        } else Timber.e("Session has not been initialized");
    }

    private boolean isImageSupported(String imgUrl) {
        String extension = HttpHelper.getExtensionFromUri(imgUrl);
        return ImageHelper.INSTANCE.isImageExtensionSupported(extension);
    }

    private void onSavedItemsSuccess(List<String> savedUrls) { // TODO don't display placeholder when load is not complete - use a "loading..." image
        if (savedUrls.isEmpty()) {
            imgCount.setText(R.string.reddit_auth_noimg);
            return;
        }

        // Remove duplicates and unsupported files from saved URLs
        savedUrls = Stream.of(savedUrls).distinct().withoutNulls().filter(this::isImageSupported).toList();
        // Reverse the list as Reddit puts most recent first and Hentoid does the opposite
        Collections.reverse(savedUrls);

        int newImageNumber = 0;

        db = new ObjectBoxDAO(requireContext());
        Content contentDB = db.selectContentBySourceAndUrl(Site.REDDIT, "", "");

        if (null == contentDB) {    // The book has just been detected -> finalize before saving in DB

            // Create a new image set based on saved Urls, adding the cover in the process
            String coverUrl = savedUrls.isEmpty() ? "" : savedUrls.get(0);
            List<ImageFile> newImages = ParseHelper.urlsToImageFiles(savedUrls, coverUrl, StatusContent.SAVED);

            Timber.d("Reddit : new content created (%s pages)", newImages.size());
            currentContent = new Content().setSite(Site.REDDIT).setUrl("").setTitle("Reddit");
            currentContent.setStatus(StatusContent.SAVED);
            db.insertContent(currentContent);
            imageSet = newImages;
            newImageNumber = newImages.size() - 1; // Don't count the cover
        } else { // TODO duplicated code with BaseWebActivity
            // Create a new image set based on saved Urls, ignoring the cover that should already be there
            List<ImageFile> newImages = ParseHelper.urlsToImageFiles(savedUrls, null, StatusContent.SAVED);
            // Ignore the images that are already contained in the central booru book
            List<ImageFile> existingImages = contentDB.getImageFiles();
            if (existingImages != null) {
                if (!existingImages.isEmpty()) {
                    newImages.removeAll(existingImages);
                    Timber.d("Reddit : adding %s new pages to existing content (%s pages)", newImages.size(), existingImages.size());

                    // Recompute the name of existing images to align them with the formatting of the new ones
                    Collections.sort(existingImages, ImageFile.ORDER_COMPARATOR);
                    int order = 0;
                    for (ImageFile img : existingImages) {
                        img.setOrder(order++);
                        img.computeNameFromOrder();
                    }
                    // Reindex new images according to their future position in the existing album
                    for (ImageFile img : newImages) {
                        img.setOrder(order++);
                        img.computeNameFromOrder();
                    }
                }

                imageSet = new ArrayList<>(existingImages);
                imageSet.addAll(newImages);

                newImageNumber = newImages.size();
            }
            currentContent = contentDB;
        }
        Timber.d("Reddit : final image set : %s pages", imageSet.size());

        // Display size of new images on screen
        if (newImageNumber > 0)
            imgCount.setText(String.format(requireContext().getString(R.string.reddit_auth_img_count), newImageNumber + ""));
        else
            imgCount.setText(R.string.reddit_auth_noimg);
    }

    private void onSavedItemsError(Throwable t) {
        Timber.e(t, "Error fetching Reddit saved items");
    }

    private void onDownloadClick() {
        // Save new images to DB
        currentContent.setQtyPages(imageSet.size() - 1); // Don't count the cover
        currentContent.setStatus(StatusContent.DOWNLOADING);
        long contentId = db.insertContent(currentContent);

        for (ImageFile img : imageSet) img.setStatus(StatusContent.SAVED);
        db.replaceImageList(contentId, imageSet);

        List<QueueRecord> queue = db.selectQueue();
        int lastIndex = 1;
        if (!queue.isEmpty()) {
            lastIndex = queue.get(queue.size() - 1).getRank() + 1;
        }
        db.insertQueue(contentId, lastIndex);

        ContentQueueManager.INSTANCE.resumeQueue(requireContext());
        viewQueue();
    }

    private void viewQueue() {
        Intent intent = new Intent(requireContext(), QueueActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        requireContext().startActivity(intent);
        requireActivity().finish();
    }
}
