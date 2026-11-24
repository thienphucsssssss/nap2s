package vn.twosmc.nap2s.utils

enum class TopType(val keyExtractor: (String) -> String) {
    ALL({ "${it}_ALL" }),
    WEEK({ "${it}_WEEK" }),
    MONTH({ "${it}_MONTH" }),
    DAILY({ "${it}_DAILY" }),
    ;

    fun parseKey(key: String) = keyExtractor(key)
}