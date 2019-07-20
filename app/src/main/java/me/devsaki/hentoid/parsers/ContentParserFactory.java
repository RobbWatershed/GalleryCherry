package me.devsaki.hentoid.parsers;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.HellpornoContent;
import me.devsaki.hentoid.parsers.content.JpegworldContent;
import me.devsaki.hentoid.parsers.content.NextpicturezContent;
import me.devsaki.hentoid.parsers.content.PornPicsContent;
import me.devsaki.hentoid.parsers.content.SmartContent;
import me.devsaki.hentoid.parsers.content.XhamsterContent;
import me.devsaki.hentoid.parsers.content.XnxxContent;
import me.devsaki.hentoid.retrofit.XnxxGalleryServer;

public class ContentParserFactory {

    private static final ContentParserFactory mInstance = new ContentParserFactory();

    private ContentParserFactory() {
    }

    public static ContentParserFactory getInstance() {
        return mInstance;
    }


    public Class getContentParserClass(Site site) {
        switch (site) {
            case HELLPORNO:
                return HellpornoContent.class;
            case JPEGWORLD:
                return JpegworldContent.class;
            case NEXTPICTUREZ:
                return NextpicturezContent.class;
            case PORNPICS:
                return PornPicsContent.class;
            case XHAMSTER:
                return XhamsterContent.class;
            case XNXX:
                return XnxxContent.class;
            default:
                return SmartContent.class;
        }
    }

    public ImageListParser getImageListParser(Content content) {
        return (null == content) ? new DummyParser() : getImageListParser(content.getSite());
    }

    private ImageListParser getImageListParser(Site site) {
        switch (site) {
            case XHAMSTER:
                return new XhamsterParser();
            default:
                return new DummyParser();
        }
    }
}
