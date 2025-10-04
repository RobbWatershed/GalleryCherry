package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Content
import pl.droidsonroids.jspoon.annotation.Selector

const val NO_TITLE = "<no title>"

abstract class BaseContentParser : ContentParser {
    @Selector(value = "head [rel=canonical]", attr = "href", defValue = "")
    override lateinit var canonicalUrl: String


    override fun toContent(url: String): Content {
        return update(Content(), url, true)
    }

    abstract override fun update(content: Content, url: String, updateImages: Boolean): Content
}