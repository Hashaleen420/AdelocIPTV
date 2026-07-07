package com.adeloc.iptv.data.model

import com.google.gson.annotations.SerializedName

data class XtreamCategory(
    @SerializedName("category_id")
    val categoryId: String? = null,
    @SerializedName("category_name")
    val categoryName: String? = null,
    @SerializedName("parent_id")
    val parentId: Any? = null
)
