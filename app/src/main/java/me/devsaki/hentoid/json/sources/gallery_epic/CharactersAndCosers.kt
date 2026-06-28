package me.devsaki.hentoid.json.sources.gallery_epic

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CharactersAndCosers(
    val coser: Coser?,
    val character: Character?
) {
    val title: String?
        get() = if (null == character) coser?.displayName
        else if (character.parody != null) "${character.parody.name} - ${character.name} "
        else character.name

    val characterName: String?
        get() = character?.name

    val coserName: String?
        get() = coser?.displayName

    val parodyName: String?
        get() = character?.parody?.name
}

@JsonClass(generateAdapter = true)
data class Coser(
    val name: String,
    val nameEnglish: String?
) {
    val displayName: String
        get() = nameEnglish ?: name
}

@JsonClass(generateAdapter = true)
data class Character(
    val character: String,
    val characterEnglish: String?,
    val parody: Parody?
) {
    val name: String
        get() = characterEnglish ?: character
}

@JsonClass(generateAdapter = true)
data class Parody(
    val parody: String,
    val parodyEnglish: String?
) {
    val name: String
        get() = parodyEnglish ?: parody
}