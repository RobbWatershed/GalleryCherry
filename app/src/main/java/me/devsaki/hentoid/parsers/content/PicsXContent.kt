package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.parsers.images.PicsXParser;
import pl.droidsonroids.jspoon.annotation.Selector;

public class PicsXContent extends BaseContentParser {

    @Selector(value = "head [property=og:title]", attr = "content", defValue = "")
    private String title;

    @Selector(value = ".image-container img")
    private List<Element> images;

    @Selector(value = "#images-container script")
    private List<Element> imageData;

    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.PICS_X);
        content.setUrl(url.replace(Site.PICS_X.getUrl() + "gallery", ""));
        content.setTitle(title);

        if (updateImages) {
            List<ImageFile> imageFiles = new ArrayList<>();
            List<String> imgUrls = PicsXParser.parseImages(images, imageData);
            if (!imgUrls.isEmpty())
                imageFiles.addAll(ParseHelper.urlsToImageFiles(imgUrls, imgUrls.get(0), StatusContent.SAVED));

            content.setImageFiles(imageFiles);
            content.setQtyPages(imageFiles.size() - 1);
        }

        return content;
    }
}
