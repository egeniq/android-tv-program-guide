package com.egeniq.androidtvprogramguide.entity

import android.text.Spanned


interface ProgramGuideChannel {
    val id: String
    val name: Spanned?
    val imageUrl: String?
}