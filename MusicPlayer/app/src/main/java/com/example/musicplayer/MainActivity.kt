package com.example.musicplayer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Resources
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.musicplayer.adapters.ShortcutList
import com.example.musicplayer.adapters.VpAdapter
import com.example.musicplayer.classes.Folder
import com.example.musicplayer.classes.MyMediaPlayer
import com.example.musicplayer.classes.Tools
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class MainActivity : Tools(), NavigationView.OnNavigationItemSelectedListener, ShortcutList.OnShortcutListener  {

    private var allMusicsBackup = ArrayList<Music>()
    private lateinit var tabLayout : com.google.android.material.tabs.TabLayout
    private lateinit var fetchingSongs : LinearLayout
    private lateinit var determinateProgressBar : ProgressBar
    private lateinit var indeterminateProgressBar : ProgressBar
    private lateinit var viewPager : ViewPager2

    private lateinit var pausePlayButton : ImageView

    private lateinit var bottomSheetLayout: LinearLayout
    lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>

    lateinit var shortcutAdapter : ShortcutList

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            if (intent.extras?.getBoolean("STOP") != null && intent.extras?.getBoolean("STOP") as Boolean) {
                pausePlayButton.setImageResource(R.drawable.ic_baseline_play_circle_outline_24)
            } else if (intent.extras?.getBoolean("STOP") != null && !(intent.extras?.getBoolean("STOP") as Boolean)){
                pausePlayButton.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24)
            }
            updateBottomPanel(findViewById(R.id.song_title_info),findViewById(R.id.song_artist_info),findViewById(R.id.album_cover_info))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d("MAIN ACTIVITY", "ON CREATE")

        if(savedInstanceState != null) {
            updateMusicNotification(!mediaPlayer.isPlaying)
        }

        pausePlayButton = findViewById(R.id.pause_play)
        fetchingSongs = findViewById(R.id.fetching_songs)
        tabLayout = findViewById(R.id.tab_layout)
        viewPager = findViewById(R.id.view_pager)
        determinateProgressBar = findViewById(R.id.determinate_bar)
        indeterminateProgressBar = findViewById(R.id.indeterminate_bar)

        if (SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                requestPermissionToWrite()
            }
        }

        if (!checkPermission()){
            requestPermission()
        }

        if (File(applicationContext.filesDir, saveAllMusicsFile).exists() && MyMediaPlayer.allMusics.size == 0){
            CoroutineScope(Dispatchers.Main).launch {
                MyMediaPlayer.allMusics = readAllMusicsFromFile(saveAllMusicsFile)
                allMusicsBackup = MyMediaPlayer.allMusics.map{ it.copy() } as ArrayList<Music> /* = java.util.ArrayList<com.example.musicplayer.Music> */
                fetchingSongs.visibility = View.GONE
                viewPager.visibility = View.VISIBLE
                readAllFoldersFromFile()

                CoroutineScope(Dispatchers.Main).launch {
                    generateArtists()
                    generateAlbums()
                }
            }
        } else if (MyMediaPlayer.allMusics.size != 0) {
            fetchingSongs.visibility = View.GONE
            viewPager.visibility = View.VISIBLE
        }

        viewPager.adapter = VpAdapter(this)

        TabLayoutMediator(tabLayout, viewPager){tab, index ->
            tab.text = when(index){
                0 -> {resources.getString(R.string.musics)}
                1 -> {resources.getString(R.string.playlists)}
                2 -> {resources.getString(R.string.albums)}
                3 -> {resources.getString(R.string.artists)}
                else -> { throw Resources.NotFoundException("Position not found")}
            }
        }.attach()

        findViewById<ImageView>(R.id.shuffle_button).setOnClickListener { playRandom(MyMediaPlayer.allMusics, this,"Main") }

        CoroutineScope(Dispatchers.IO).launch{ readPlaylistsAsync() }
        CoroutineScope(Dispatchers.IO).launch { readAllDeletedMusicsFromFile() }

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val openMenu = findViewById<ImageView>(R.id.open_menu)
        val navigationView = findViewById<NavigationView>(R.id.navigation_view)
        navigationView.setNavigationItemSelectedListener(this)

        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)

        openMenu.setOnClickListener { openNavigationMenu(drawerLayout) }

        bottomSheetLayout = findViewById(R.id.bottom_infos)
        sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout)
        if (File(applicationContext.filesDir, saveCurrentPlaylist).exists()) {
            CoroutineScope(Dispatchers.IO).launch {
                readCurrentPlaylistFromFile()

                if (MyMediaPlayer.currentIndex != -1 && MyMediaPlayer.currentPlaylist.size > 0) {
                    withContext(Dispatchers.Main) {
                        sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                        findViewById<ImageView>(R.id.next).setOnClickListener { playNextSong() }
                        loadLastCurrentPlaylist(findViewById(R.id.song_title_info),findViewById(R.id.song_artist_info),findViewById(R.id.album_cover_info))
                    }
                }
            }
        }

        sheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN || newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    Log.d("MAIN ACTIVITY", "MUSIC WILL STOP")
                    stopMusic()
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        findViewById<LinearLayout>(R.id.bottom_infos).setOnClickListener {
            onBottomMenuClick(
                MyMediaPlayer.currentIndex,
                this@MainActivity
            )
        }

        CoroutineScope(Dispatchers.Main).launch {
            if (File(applicationContext.filesDir, saveAllShortcuts).exists()) {
                readAllShortcutsFromFile()
            } else {
                writeAllShortcuts()
            }
            val shortcutRecyclerView = findViewById<RecyclerView>(R.id.shortcut_recycler_view)

            shortcutRecyclerView.layoutManager =
                LinearLayoutManager(
                    this@MainActivity,
                    LinearLayoutManager.HORIZONTAL,
                    false)
            shortcutAdapter =
                ShortcutList(
                    MyMediaPlayer.allShortcuts,
                    this@MainActivity,
                    this@MainActivity)
            shortcutRecyclerView.adapter = shortcutAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        // Si nous rentrons dans cette condition, c'est que l'utilisateur ouvre l'application pour la première fois
        // Si on a la permission et qu'on a pas encore de fichiers avec des musiques, alors on va chercher nos musiques :
        if (checkPermission() && !File(applicationContext.filesDir, saveAllMusicsFile).exists()){
            // Créons d'abord la playlist des favoris :
            CoroutineScope(Dispatchers.IO).launch {
                val favoritePlaylist = Playlist("Favorites",ArrayList(),null, true)
                MyMediaPlayer.allPlaylists = ArrayList<Playlist>()
                MyMediaPlayer.allPlaylists.add(favoritePlaylist)
                writeAllPlaylists()
                writeAllDeletedSong()
                retrieveAllFoldersUsed()
                writeAllFolders()
            }

            CoroutineScope(Dispatchers.IO).launch { fetchMusics() }
        }

        CoroutineScope(Dispatchers.Main).launch {
            shortcutAdapter.notifyDataSetChanged()
        }

        if (MyMediaPlayer.currentIndex != -1 && MyMediaPlayer.currentPlaylist.size != 0) {
            CoroutineScope(Dispatchers.Main).launch {
                sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                updateBottomPanel(findViewById(R.id.song_title_info), findViewById(R.id.song_artist_info), findViewById(R.id.album_cover_info))

                pausePlayButton.setOnClickListener { pausePlay(pausePlayButton) }
            }
        } else {
            CoroutineScope(Dispatchers.Main).launch {
                sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        if (!mediaPlayer.isPlaying) {
            pausePlayButton.setImageResource(R.drawable.ic_baseline_play_circle_outline_24)
        } else {
            pausePlayButton.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24)
        }

        registerReceiver(broadcastReceiver, IntentFilter("BROADCAST"))

        val serviceIntent = Intent(this, PlaybackService::class.java)
        startService(serviceIntent)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.parameters -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.find_new_songs -> {
                val intent = Intent(this,FindNewSongsActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.download_data -> {
                CoroutineScope(Dispatchers.Main).launch { retrieveAllMusicsFromApp() }
                true
            }
            R.id.set_data -> {
                val intent = Intent(this,SetDataActivity::class.java)
                startActivity(intent)
                true
            }
            else -> {
                true
            }
        }
    }

    private suspend fun fetchMusics() {
        // Pour éviter de potentiels crash de l'app :
        val shuffleButton = findViewById<ImageView>(R.id.shuffle_button)
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val openMenu = findViewById<ImageView>(R.id.open_menu)

        withContext(Dispatchers.Main) {
            shuffleButton.visibility = View.GONE
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            openMenu.visibility = View.GONE
        }

        // A "projection" defines the columns that will be returned for each row
        val projection: Array<String> = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Albums.ALBUM_ID
        )

        val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"

        // Does a query against the table and returns a Cursor object
        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,    // The content URI of the words table
            projection,                                     // The columns to return for each row
            selection,                                      // Either null, or the boolean that specifies the rows to retrieve
            null,
            null // The sort order for the returned rows
        )

        when (cursor?.count) {
            null -> {
                Toast.makeText(this, resources.getString(R.string.cannot_retrieve_files), Toast.LENGTH_SHORT).show()
            }
            else -> {
                var count = 0
                withContext(Dispatchers.Main) {
                    indeterminateProgressBar.visibility = View.GONE
                    determinateProgressBar.visibility = View.VISIBLE
                    determinateProgressBar.max = cursor.count
                }
                while (cursor.moveToNext()) {
                    val albumCover : ByteArray? = try {
                        val bitmap = ThumbnailUtils.createAudioThumbnail(File(cursor.getString(4)),Size(350,350),null)
                        bitmapToByteArray(bitmap)
                    } catch (error : IOException){
                        null
                    }
                    val music = Music(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        albumCover,
                        cursor.getLong(3),
                        cursor.getString(4)
                    )
                    if (File(music.path).exists()) {
                        MyMediaPlayer.allMusics.add(music)
                        if (MyMediaPlayer.allFolders.find { it.path == File(music.path).parent } == null) {
                            MyMediaPlayer.allFolders.add(Folder(File(music.path).parent as String))
                        }
                    }
                    withContext(Dispatchers.Main){
                        count+=1
                        determinateProgressBar.setProgress(count,true)
                    }
                }
                cursor.close()
                MyMediaPlayer.allMusics

                writeAllMusics()
                writeAllPlaylists()
                writeAllFolders()

                withContext(Dispatchers.Main){
                    fetchingSongs.visibility = View.GONE
                    viewPager.visibility = View.VISIBLE
                    shuffleButton.visibility = View.VISIBLE
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                    openMenu.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun checkPermission() : Boolean {
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }


    private fun requestPermission(){
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            69
        )
    }

    private fun requestPermissionToWrite(){
        val uri = Uri.parse("package:${BuildConfig.APPLICATION_ID}")

        if (SDK_INT >= 30) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    uri
                )
            )
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(broadcastReceiver)
    }

    override fun onLongShortcutClick(position: Int) {
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(R.layout.bottom_sheet_dialog_shortcuts)

        bottomSheetDialog.findViewById<LinearLayout>(R.id.delete_shortcut)?.setOnClickListener {
            MyMediaPlayer.allShortcuts.shortcutsList.remove(shortcutAdapter.shortcuts.shortcutsList[position])
            shortcutAdapter.notifyItemRemoved(position)
            CoroutineScope(Dispatchers.IO).launch { writeAllShortcuts() }
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }
}