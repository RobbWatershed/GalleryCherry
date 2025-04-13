package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import pl.droidsonroids.jspoon.annotation.Selector;

public class SxypixContent extends BaseContentParser {

    @Selector(value = "head meta[name='keywords']", attr = "content", defValue = "")
    private String title;

    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.SXYPIX);
        content.setUrl(url);
        content.setTitle(title);

        AttributeMap attributes = new AttributeMap();

        String[] parts = title.split(" - ");
        String model = (parts.length > 1) ? parts[1] : "";

        if (!model.isBlank()) {
            attributes.add(new Attribute(AttributeType.MODEL, model, model.replace(" ", "-"), Site.SXYPIX));
        }

        content.addAttributes(attributes);

        if (updateImages) {
            List<ImageFile> images = new ArrayList<>();
            content.setImageFiles(images);
            content.setQtyPages(images.size());
        }

        return content;
    }
}
