package com.example.music_box
import java.io.Serializable

data class MusicInfo(
    var music_title: String? = null,
    var music_name: String? = null,
    var music_path: String? = null,
    var music_artist: String? = null,
    var music_duration:Int = 0
):Serializable