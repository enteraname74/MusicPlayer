package com.example.musicplayer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.ObjectInputStream


class MusicSelectionActivity : Tools(), MusicListSelection.OnMusicListener {
    private var musics = ArrayList<Music>()
    private lateinit var adapter : MusicListSelection
    private var selectedMusicsPositions = ArrayList<Int>()
    private var menuRecyclerView : RecyclerView? = null
    private var mediaPlayer = MyMediaPlayer.getInstance
    private val savedMusicsFile = "allMusics.musics"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music_selection)

        menuRecyclerView = findViewById(R.id.all_songs_list)
        musics = readAllMusicsFromFile(savedMusicsFile)

        adapter = MusicListSelection(musics,selectedMusicsPositions,applicationContext,this)

        menuRecyclerView?.layoutManager = LinearLayoutManager(this)
        menuRecyclerView?.adapter = adapter

        val pausePlay = findViewById<ImageView>(R.id.pause_play)
        val nextBtn = findViewById<ImageView>(R.id.next)
        val previousBtn = findViewById<ImageView>(R.id.previous)

        val noSongPlaying = findViewById<TextView>(R.id.no_song_playing)
        val infoSongPlaying = findViewById<RelativeLayout>(R.id.info_song_playing)
        val songTitleInfo = findViewById<TextView>(R.id.song_title_info)
        val bottomInfos = findViewById<LinearLayout>(R.id.bottom_infos)
        val albumCoverInfo = findViewById<ImageView>(R.id.album_cover_info)

        if (MyMediaPlayer.currentIndex == -1){
            noSongPlaying.visibility = View.VISIBLE
            infoSongPlaying.visibility = View.GONE
        } else {
            noSongPlaying.visibility = View.GONE
            infoSongPlaying.visibility = View.VISIBLE
            songTitleInfo?.text = MyMediaPlayer.currentPlaylist[MyMediaPlayer.currentIndex].name
            pausePlay?.setOnClickListener(View.OnClickListener{pausePlay()})
            nextBtn?.setOnClickListener(View.OnClickListener { playNextSong() })
            previousBtn?.setOnClickListener(View.OnClickListener { playPreviousSong() })
            bottomInfos.setOnClickListener(View.OnClickListener {onBottomMenuClick(MyMediaPlayer.currentIndex) })
            songTitleInfo?.setSelected(true)
        }
        // Lorsqu'une musique se finit, on passe à la suivante automatiquement :
        mediaPlayer.setOnCompletionListener { playNextSong() }

        val validateButton = findViewById<Button>(R.id.validate)
        val cancelButton = findViewById<Button>(R.id.cancel)
        validateButton.setOnClickListener(View.OnClickListener { onValidateButtonClick() })
        cancelButton.setOnClickListener(View.OnClickListener { onCancelButtonClick() })
    }

    override fun onResume() {
        super.onResume()

        val infoSongPlaying = findViewById<RelativeLayout>(R.id.info_song_playing)
        val songTitleInfo = findViewById<TextView>(R.id.song_title_info)
        val albumCoverInfo = findViewById<ImageView>(R.id.album_cover_info)

        if (MyMediaPlayer.currentIndex != -1){
            infoSongPlaying.visibility = View.VISIBLE
            songTitleInfo.text = MyMediaPlayer.currentPlaylist[MyMediaPlayer.currentIndex].name
            if (MyMediaPlayer.currentPlaylist[MyMediaPlayer.currentIndex].albumCover != null){
                // Passons d'abord notre byteArray en bitmap :
                val bytes = MyMediaPlayer.currentPlaylist[MyMediaPlayer.currentIndex].albumCover
                var bitmap: Bitmap? = null
                if (bytes != null && bytes.isNotEmpty()) {
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                albumCoverInfo.setImageBitmap(bitmap)
            } else {
                albumCoverInfo.setImageResource(R.drawable.michael)
            }

            songTitleInfo?.setSelected(true)
        }
    }

    private fun onBottomMenuClick(position : Int){
        Log.d("MUSIC POSITION", position.toString())
        var sameMusic = true

        if (position != MyMediaPlayer.currentIndex) {
            MyMediaPlayer.getInstance.reset()
            sameMusic = false
        }
        MyMediaPlayer.currentIndex = position
        Log.d("MEDIA POSITION", MyMediaPlayer.currentIndex.toString())
        val intent = Intent(this@MusicSelectionActivity,MusicPlayerActivity::class.java)

        /*On fait passer notre liste de musiques dans notre nouvelle activité pour
        récupérer les données des musiques
         */

        intent.putExtra("SAME MUSIC", sameMusic)
        intent.putExtra("POSITION", position)

        startActivity(intent)
    }

    private fun playNextSong(){
        if(MyMediaPlayer.currentIndex==(MyMediaPlayer.currentPlaylist.size)-1){
            MyMediaPlayer.currentIndex = 0
        } else {
            MyMediaPlayer.currentIndex+=1
        }
        playMusic()
    }

    private fun playPreviousSong(){
        if(MyMediaPlayer.currentIndex==0){
            MyMediaPlayer.currentIndex = (MyMediaPlayer.currentPlaylist.size)-1
        } else {
            MyMediaPlayer.currentIndex-=1
        }
        playMusic()
    }

    override fun pausePlay(){
        val pausePlay = findViewById<ImageView>(R.id.pause_play)
        if(mediaPlayer.isPlaying){
            mediaPlayer.pause()
            pausePlay?.setImageResource(R.drawable.ic_baseline_play_circle_outline_24)
        } else {
            mediaPlayer.start()
            pausePlay?.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24)
        }
    }

    override fun onMusicClick(position: Int) {

        if (position in selectedMusicsPositions){
            selectedMusicsPositions.remove(position)
        } else {
            selectedMusicsPositions.add(position)
        }

        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
    }

    private fun onValidateButtonClick(){
        val returnIntent = Intent()
        returnIntent.putExtra("addedSongs", selectedMusicsPositions)
        setResult(RESULT_OK, returnIntent)
        finish()
    }

    private fun onCancelButtonClick(){
        val returnIntent = Intent()
        setResult(RESULT_CANCELED, returnIntent)
        finish()
    }
}