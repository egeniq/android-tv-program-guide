/*
 * Copyright (c) 2020, Egeniq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.egeniq.androidtvprogramguide.entity

import android.text.Spanned

/**
 * A channel which may be associated with multiple programmes.
 * Channels are displayed on the left side of the screen, and display the image you have defined in the URL,
 * and the name to the right of the image. ID is only used for identification purposes, and should be unique.
 */
interface ProgramGuideChannel {
    val id: String
    val name: Spanned?
    val imageUrl: String?
}