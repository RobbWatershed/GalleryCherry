package me.devsaki.hentoid.parsers;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.AsianSisterContent;
import me.devsaki.hentoid.parsers.content.BabeTodayContent;
import me.devsaki.hentoid.parsers.content.ContentParser;
import me.devsaki.hentoid.parsers.content.FapalityContent;
import me.devsaki.hentoid.parsers.content.HellpornoContent;
import me.devsaki.hentoid.parsers.content.Jjgirls2Content;
import me.devsaki.hentoid.parsers.content.JjgirlsContent;
import me.devsaki.hentoid.parsers.content.JpegworldContent;
import me.devsaki.hentoid.parsers.content.PornPicsContent;
import me.devsaki.hentoid.parsers.content.SmartContent;
import me.devsaki.hentoid.parsers.content.XhamsterContent;
import me.devsaki.hentoid.parsers.content.XnxxContent;
import me.devsaki.hentoid.parsers.images.DummyParser;
import me.devsaki.hentoid.parsers.images.FapalityParser;
import me.devsaki.hentoid.parsers.images.ImageListParser;
import me.devsaki.hentoid.parsers.images.LusciousParser;
import me.devsaki.hentoid.parsers.images.XhamsterParser;

public class ContentParserFactory {

    private static final ContentParserFactory mInstance = new ContentParserFactory();

    private ContentParserFactory() {
    }

    public static ContentParserFactory getInstance() {
        return mInstance;
    }


    public Class<? extends ContentParser> getContentParserClass(Site site) {
        switch (site) {
            case HELLPORNO:
                return HellpornoContent.class;
            case JPEGWORLD:
                return JpegworldContent.class;
            case PORNPICS:
                return PornPicsContent.class;
            case XHAMSTER:
                return XhamsterContent.class;
            case XNXX:
                return XnxxContent.class;
            case JJGIRLS:
                return JjgirlsContent.class;
            case JJGIRLS2:
                return Jjgirls2Content.class;
            case BABETODAY:
                return BabeTodayContent.class;
            case FAPALITY:
                return FapalityContent.class;
            case ASIANSISTER:
                return AsianSisterContent.class;
            case REDDIT:
            case LINK2GALLERIES:
            case NEXTPICTUREZ:
            case PORNPICGALLERIES:
            default:
                return SmartContent.class;
        }
    }

    public ImageListParser getImageListParser(Content content) {
        return (null == content) ? new DummyParser() : getImageListParser(content.getSite());
    }

    public ImageListParser getImageListParser(Site site) {
        switch (site) {
            case XHAMSTER:
                return new XhamsterParser();
            case LUSCIOUS:
                return new LusciousParser();
            case FAPALITY:
                return new FapalityParser();
            default:
                return new DummyParser();
        }
    }
}
