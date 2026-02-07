package me.devsaki.hentoid.parsers

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.content.BabeTodayContent
import me.devsaki.hentoid.parsers.content.BestGirlSexyContent
import me.devsaki.hentoid.parsers.content.ContentParser
import me.devsaki.hentoid.parsers.content.CosplayTeleContent
import me.devsaki.hentoid.parsers.content.FapalityContent
import me.devsaki.hentoid.parsers.content.FoamGirlContent
import me.devsaki.hentoid.parsers.content.GirlsTopContent
import me.devsaki.hentoid.parsers.content.JjgirlsContent
import me.devsaki.hentoid.parsers.content.KemonoContent
import me.devsaki.hentoid.parsers.content.LusciousContent
import me.devsaki.hentoid.parsers.content.PicsXContent
import me.devsaki.hentoid.parsers.content.PornPicsContent
import me.devsaki.hentoid.parsers.content.SmartContent
import me.devsaki.hentoid.parsers.content.SxypixContent
import me.devsaki.hentoid.parsers.content.XhamsterContent
import me.devsaki.hentoid.parsers.content.XiutakuContent
import me.devsaki.hentoid.parsers.content.XnxxContent
import me.devsaki.hentoid.parsers.images.DummyParser
import me.devsaki.hentoid.parsers.images.FapalityParser
import me.devsaki.hentoid.parsers.images.FoamGirlParser
import me.devsaki.hentoid.parsers.images.ImageListParser
import me.devsaki.hentoid.parsers.images.KemonoParser
import me.devsaki.hentoid.parsers.images.LusciousParser
import me.devsaki.hentoid.parsers.images.PicsXParser
import me.devsaki.hentoid.parsers.images.SxypixParser
import me.devsaki.hentoid.parsers.images.XhamsterParser
import me.devsaki.hentoid.parsers.images.XiutakuParser


object ContentParserFactory {

    fun getContentParserClass(site: Site): Class<out ContentParser> {
        return when (site) {
            Site.PORNPICS -> PornPicsContent::class.java
            Site.XHAMSTER -> XhamsterContent::class.java
            Site.XNXX -> XnxxContent::class.java
            Site.JJGIRLS -> JjgirlsContent::class.java
            Site.BABETODAY -> BabeTodayContent::class.java
            Site.FAPALITY -> FapalityContent::class.java
            Site.LUSCIOUS -> LusciousContent::class.java
            Site.SXYPIX -> SxypixContent::class.java
            Site.PICS_X -> PicsXContent::class.java
            Site.COSPLAYTELE -> CosplayTeleContent::class.java
            Site.JAPBEAUTIES, Site.REDDIT, Site.LINK2GALLERIES, Site.NEXTPICTUREZ, Site.PORNPICGALLERIES -> SmartContent::class.java
            Site.COOMER -> KemonoContent::class.java
            Site.GIRLSTOP -> GirlsTopContent::class.java
            Site.BESTGIRLSEXY -> BestGirlSexyContent::class.java
            Site.FOAMGIRL -> FoamGirlContent::class.java
            Site.XIUTAKU -> XiutakuContent::class.java
            else -> SmartContent::class.java
        }
    }

    fun getImageListParser(content: Content?): ImageListParser {
        return if (null == content) DummyParser() else getImageListParser(content.site)
    }

    fun getImageListParser(site: Site): ImageListParser {
        return when (site) {
            Site.XHAMSTER -> XhamsterParser()
            Site.LUSCIOUS -> LusciousParser()
            Site.FAPALITY -> FapalityParser()
            Site.SXYPIX -> SxypixParser()
            Site.PICS_X -> PicsXParser()
            Site.COOMER -> KemonoParser()
            Site.FOAMGIRL -> FoamGirlParser()
            Site.XIUTAKU -> XiutakuParser()
            else -> DummyParser()
        }
    }
}