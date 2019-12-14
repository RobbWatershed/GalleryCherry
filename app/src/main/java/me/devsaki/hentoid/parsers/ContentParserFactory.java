package me.devsaki.hentoid.parsers;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.HellpornoContent;
import me.devsaki.hentoid.parsers.content.JjgirlsContent;
import me.devsaki.hentoid.parsers.content.JpegworldContent;
import me.devsaki.hentoid.parsers.content.NextpicturezContent;
import me.devsaki.hentoid.parsers.content.PornPicsContent;
import me.devsaki.hentoid.parsers.content.SmartContent;
import me.devsaki.hentoid.parsers.content.XhamsterContent;
import me.devsaki.hentoid.parsers.content.XnxxContent;
import me.devsaki.hentoid.parsers.images.ASMHentaiParser;
import me.devsaki.hentoid.parsers.images.DummyParser;
import me.devsaki.hentoid.parsers.images.EHentaiParser;
import me.devsaki.hentoid.parsers.images.FakkuParser;
import me.devsaki.hentoid.parsers.images.HentaiCafeParser;
import me.devsaki.hentoid.parsers.images.HitomiParser;
import me.devsaki.hentoid.parsers.images.ImageListParser;
import me.devsaki.hentoid.parsers.images.LusciousParser;
import me.devsaki.hentoid.parsers.images.NexusParser;
import me.devsaki.hentoid.parsers.images.PururinParser;
import me.devsaki.hentoid.parsers.images.TsuminoParser;

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
            case JJGIRLS:
                return JjgirlsContent.class;
            case REDDIT:
            case LINK2GALLERIES:
            case PORNPICGALLERIES:
            default:
                return SmartContent.class;
        }
    }

    public ImageListParser getImageListParser(Site site) {
        switch (site) {
            case XHAMSTER:
                return new XhamsterParser();
            default:
                return new DummyParser();
        }
    }
}
