package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import pl.droidsonroids.jspoon.annotation.Selector;
import timber.log.Timber;

public class XhamsterContent extends BaseContentParser {

    private static final String GALLERY_FOLDER = "/photos/gallery/";
    private static final Pattern TITLE_NUMBER_PATTERN = Pattern.compile(".* - (\\d+) .*amster.*"); // e.g. "Big bewbs - 50 Pics | xHamster.com"

    @Selector(value = "head meta[name='twitter:url']", attr = "content", defValue = "")
    private String galleryUrl;
    @Selector(value = "img.thumb", attr = "src", defValue = "")
    private List<String> thumbs;
    @Selector(value = "h1.page-title", defValue = "")
    private String title1;
    @Selector(value = "head [property=og:title]", attr = "content", defValue = "")
    private String title2;
    @Selector("head title")
    private String headTitle;
    @Selector(value = ".categories_of_pictures .categories-container__item")
    private List<Element> tags;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.XHAMSTER);

        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;
        int galleryLocation = theUrl.indexOf(GALLERY_FOLDER) + GALLERY_FOLDER.length();
        content.setUrl(theUrl.substring(galleryLocation));
        if (thumbs != null)
            content.setCoverImageUrl(thumbs.isEmpty() ? "" : thumbs.get(0));
        if (!title1.isEmpty())
            content.setTitle(title1);
        else
            content.setTitle(title2);

        if (updateImages) {
            Matcher matcher = TITLE_NUMBER_PATTERN.matcher(headTitle);

            Timber.d("Match found? %s", matcher.find());

            if (matcher.groupCount() > 0) {
                String results = matcher.group(1);
                if (results != null)
                    content.setQtyPages(Integer.parseInt(results));
            }
        }

        AttributeMap attributes = new AttributeMap();

        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true, Site.XHAMSTER);

        content.addAttributes(attributes);

        content.setStatus(StatusContent.SAVED);

        return content;
    }
}
