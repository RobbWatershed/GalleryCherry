package me.devsaki.hentoid.parsers;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;

public class ContentParserFactory {

    private static final ContentParserFactory mInstance = new ContentParserFactory();

    private ContentParserFactory() {
    }

    public static ContentParserFactory getInstance() {
        return mInstance;
    }

    public ContentParser getParser(Content content) {
        return (null == content) ? new DummyParser() : getParser(content.getSite());
    }

    private ContentParser getParser(Site site) {
        switch (site) {
            case XHAMSTER:
                return new XhamsterParser();
            default:
                return new DummyParser();
        }
    }
}
