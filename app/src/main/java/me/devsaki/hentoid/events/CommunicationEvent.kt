package me.devsaki.hentoid.events

class CommunicationEvent(val type: Type, val recipient: Recipient = Recipient.ALL, val message: String = "") {

    enum class Type {
        SEARCH,
        SEARCH_NO_HISTORY,
        ADVANCED_SEARCH,
        UPDATE,
        UPDATE_TOOLBAR,
        CLOSE_DRAWER,
        CLOSED,
        ENABLE,
        DISABLE,
        UNSELECT,
        BROADCAST,
        UPDATE_EDIT_MODE,
        SCROLL_TOP,
        SIGNAL_SITE,
        CANCEL,
        APK_AVAILABLE,
        RELOAD
    }

    enum class Recipient {
        ALL,
        LIBRARY_LIST,
        LIBRARY_GROUPS,
        LIBRARY_CONTENTS,
        LIBRARY_FOLDERS,
        QUEUE_QUEUE,
        QUEUE_ERRORS,
        DRAWER,
        DUPLICATE_MAIN,
        DUPLICATE_DETAILS,
        SETTINGS,
        UPDATE_WORKER,
        TRANSFORM_ALL
    }
}