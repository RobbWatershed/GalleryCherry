package me.devsaki.hentoid.parsers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadPreparationEvent;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Helper;

public class ParseHelper {

    private ParseHelper() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Remove counters from given string (e.g. "Futanari (2660)" => "Futanari")
     *
     * @param s String brackets have to be removed
     * @return String with removed brackets
     */
    private static String removeBrackets(String s) {
        int bracketPos = s.lastIndexOf('(');
        if (bracketPos > 1 && ' ' == s.charAt(bracketPos - 1)) bracketPos--;
        if (bracketPos > -1) {
            return s.substring(0, bracketPos);
        }

        return s;
    }

    public static void parseAttributes(
            @NonNull AttributeMap map,
            @NonNull AttributeType type,
            List<Element> elements,
            boolean filterCount,
            @NonNull Site site) {
        if (elements != null)
            for (Element a : elements) parseAttribute(map, type, a, filterCount, site);
    }

    public static void parseAttributes(
            @NonNull AttributeMap map,
            @NonNull AttributeType type,
            List<Element> elements,
            boolean filterCount,
            @NonNull String childElementClass,
            @NonNull Site site) {
        if (elements != null)
            for (Element a : elements)
                parseAttribute(map, type, a, filterCount, childElementClass, site);
    }

    public static void parseAttribute(
            @NonNull AttributeMap map,
            @NonNull AttributeType type,
            @NonNull Element element,
            boolean filterCount,
            @NonNull Site site) {
        parseAttribute(map, type, element, filterCount, null, site, "");
    }

    public static void parseAttribute(
            @NonNull AttributeMap map,
            @NonNull AttributeType type,
            @NonNull Element element,
            boolean filterCount,
            @NonNull String childElementClass,
            @NonNull Site site) {
        parseAttribute(map, type, element, filterCount, childElementClass, site, "");
    }

    public static void parseAttribute(
            @NonNull AttributeMap map,
            @NonNull AttributeType type,
            @NonNull Element element,
            boolean filterCount,
            @Nullable String childElementClass,
            @NonNull Site site,
            @NonNull final String prefix) {
        String name;
        if (null == childElementClass) {
            name = element.text();
        } else {
            name = element.selectFirst("." + childElementClass).text();
        }
        name = Helper.removeNonPrintableChars(name);
        if (filterCount) name = removeBrackets(name);
        if (name.isEmpty() || name.equals("-") || name.equals("/")) return;

        if (!prefix.isEmpty()) name = prefix + ":" + name;
        Attribute attribute = new Attribute(type, name, element.attr("href"), site);

        map.add(attribute);
    }

    public static ImageFile urlToImageFile(@Nonnull String imgUrl, int order, int nbPages, @NonNull final StatusContent status) {
        ImageFile result = new ImageFile();

        int nbMaxDigits = (int) (Math.floor(Math.log10(nbPages)) + 1);
        String name = String.format(Locale.US, "%0" + nbMaxDigits + "d", order);
        result.setName(name).setOrder(order).setUrl(imgUrl).setStatus(status);

        return result;
    }

    public static List<ImageFile> urlsToImageFiles(
            @Nonnull List<String> imgUrls,
            @NonNull String coverUrl,
            @NonNull final StatusContent status
    ) {
        List<ImageFile> result = new ArrayList<>();

        // Cover
        result.add(ImageFile.newCover(coverUrl, status));
        // Images
        result.addAll(urlsToImageFiles(imgUrls, status));

        return result;
    }

    public static List<ImageFile> urlsToImageFiles(
            @Nonnull List<String> imgUrls,
            @NonNull final StatusContent status
    ) {
        List<ImageFile> result = new ArrayList<>();

        // Images
        int order = 1;
        for (String s : imgUrls) result.add(urlToImageFile(s, order++, imgUrls.size(), status));

        return result;
    }

    public static void signalProgress(int current, int max) {
        EventBus.getDefault().post(new DownloadPreparationEvent(current, max));
    }
}
