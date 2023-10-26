package com.example.music_box

import android.Manifest
import android.Manifest.permission.*
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.music_box.databinding.ActivityMainBinding
import kotlinx.coroutines.*


class MainActivity : AppCompatActivity() {
    private lateinit var binding:ActivityMainBinding
    private lateinit var mManager:MusicService.MusicBinder
    private var musicList = mutableListOf<MusicInfo>()
    private lateinit var mlistAdapter: ListAdapter
    private lateinit var mListView: ListView
    private lateinit var btnPrevious: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var btnPlay: ImageView
    private lateinit var listTitle: TextView
    private lateinit var playingName: TextView
    private lateinit var musicSeekBar: SeekBar
    private lateinit var playModel: ImageView
    private var isServiceBound = false


    companion object {
        const val STATUS_PLAY = 0
        const val STATUS_NEXT = 1
        const val STATUS_PREV = 2
    }

    private var music_playing = false
    val coroutineScope = CoroutineScope(Dispatchers.Main)

    // 定义播放模式的枚举
    enum class PlayMode {
        REPEAT_ALL,
        REPEAT_ONE,
        SHUFFLE
    }

    var currentPlayMode = PlayMode.REPEAT_ALL // 默认播放模式为列表循环

    fun togglePlayMode() {
        // 切换到下一个播放模式
        when (currentPlayMode) {
            PlayMode.REPEAT_ALL -> {
                mManager?.setPlayMode(MusicService.PlayMode.SINGLE_LOOP)
                currentPlayMode = PlayMode.REPEAT_ONE
            }
            PlayMode.REPEAT_ONE -> {
                mManager?.setPlayMode(MusicService.PlayMode.SHUFFLE)
                currentPlayMode = PlayMode.SHUFFLE
            }
            PlayMode.SHUFFLE ->{
                mManager?.setPlayMode(MusicService.PlayMode.LIST_LOOP)
                currentPlayMode = PlayMode.REPEAT_ALL
            }
        }

    }

    fun updatePlayModeIcon() {
        when (currentPlayMode) {
            PlayMode.REPEAT_ALL -> playModel.setImageResource(R.drawable.repeat_all)
            PlayMode.REPEAT_ONE -> playModel.setImageResource(R.drawable.repeat_one)
            PlayMode.SHUFFLE -> playModel.setImageResource(R.drawable.shuffle)
        }
    }


    // 创建一个挂起函数来执行更新任务
    suspend fun updateMusicSeekBar() {
        while (true) {
            mManager?.let {
                try {
                    if (!mManager.isCompleted()) {
                        if (it.isPlaying()) {
                            var duration = it.getDuration()
                            var currentPos = it.getCurrentPosition()
                            withContext(Dispatchers.Main) {
                                musicSeekBar.max = duration
                                musicSeekBar.progress = currentPos
                            }
                        }
                    }
                    if (mManager.isCompleted()) {
                        updateState(STATUS_NEXT)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            kotlinx.coroutines.delay(100) // 每秒执行十次更新
        }
    }


    fun updateMusicInfo() {
        val index = mManager?.getCurrentIndex() ?: 0
        mlistAdapter.setFocusItemPos(index)
        val currentMusicName = musicList[index].music_title
        val currentArtist = musicList[index].music_artist
        val musicInfo = "$currentMusicName\n$currentArtist"
        playingName.text = musicInfo
    }


    fun updateState(status: Int) {
//        Log.e("MainActivity", "updateState里的play,currentIndex=${mManager.getCurrentIndex()}")
        when (status) {
            STATUS_PLAY -> {
                if (!music_playing) {
                    if (mManager?.getCurrentIndex() == -1) {
                        mManager?.setCurrentIndex(0)
                        mlistAdapter.setFocusItemPos(0)
                        mManager?.Play()
                    } else {
                        mManager?.setCurrentIndex(mManager?.getCurrentIndex() ?: 0)
                        mlistAdapter.setFocusItemPos(mManager?.getCurrentIndex() ?: 0)
                        mManager?.Play()
                    }
                    mManager?.Resume()
                    music_playing = true
                    btnPlay.setImageResource(R.drawable.pause)
                    updateMusicInfo()
                    return
                }
                if (music_playing) {
                    mManager?.Pause()
                    music_playing = false
                    btnPlay.setImageResource(R.drawable.play)
                    updateMusicInfo()
                }
                return
            }

            STATUS_NEXT -> {
                mManager?.PlayNext()
                mlistAdapter.setFocusItemPos(mManager?.getCurrentIndex() ?: 0)
                updateMusicInfo()
                if (music_playing) {
                    btnPlay.setImageResource(R.drawable.pause)
                } else {
                    btnPlay.setImageResource(R.drawable.play)
                }
                mListView.setSelection(mManager?.getCurrentIndex() ?: 0)
            }

            STATUS_PREV -> {
                mManager?.playPrevious()
                mlistAdapter.setFocusItemPos(mManager?.getCurrentIndex() ?: 0)
                updateMusicInfo()
                if (music_playing) {
                    btnPlay.setImageResource(R.drawable.pause)
                } else {
                    btnPlay.setImageResource(R.drawable.play)
                }
                mListView.setSelection(mManager?.getCurrentIndex() ?: 0)
            }

            else -> {
                throw IllegalStateException("错误的状态: $status")
            }
        }
    }

    private val connection=object:ServiceConnection{
        override fun onServiceConnected(name:ComponentName?, service: IBinder?) {
            mManager=service as MusicService.MusicBinder
            isServiceBound=true
            initializeUI()
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            TODO("Not yet implemented")
        }

    }

    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val command = intent?.getStringExtra("command")
            if (command != null) {
                when (command) {
                    "PLAY" -> {
                        // 处理播放按钮点击
//                        Log.e("BroadReceiver","onReceive里面的play")
                        updateState(STATUS_PLAY)
                    }
                    "PAUSE" -> {
                        // 处理暂停按钮点击
//                        Log.e("BroadReceiver","onReceive里面的pause")
                        updateState(STATUS_PLAY)
                    }
                    "NEXT" -> {
                        // 处理下一首按钮点击
//                        Log.e("BroadReceiver","onReceive里面的next")
                        updateState(STATUS_NEXT)
                    }
                    "PREVIOUS" -> {
                        // 处理上一首按钮点击
//                        Log.e("BroadReceiver","onReceive里面的previous")
                        updateState(STATUS_PREV)
                    }

                }
            }
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindMusicService()//绑定Service

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this, arrayOf<String>(
                    READ_MEDIA_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS
                ),0)

        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf<String>(
                    READ_EXTERNAL_STORAGE
                ), 1)

        }

        val filter=IntentFilter("com.example.music_box.ACTION_CONTROL")
        registerReceiver(controlReceiver,filter)

    }

    private fun initializeUI() {

        listTitle = findViewById(R.id.music_list_title)
            playingName = findViewById(R.id.music_name)
            musicSeekBar = findViewById(R.id.music_seek_bar)
            btnPrevious = findViewById(R.id.btn_previous)
            btnNext = findViewById(R.id.btn_next)
            btnPlay = findViewById(R.id.btn_play)
            playModel = findViewById(R.id.btn_play_model)

            musicList = (mManager?.getMusicList() ?: mutableListOf()).toMutableList()
            val title = "本地音乐(总数:${mManager?.getTotalMusic() ?: 0})"
            listTitle.text = title

            mlistAdapter = ListAdapter(this, musicList)
            mListView = findViewById(R.id.music_list)

            mListView.adapter = mlistAdapter

            coroutineScope.launch {
                updateMusicSeekBar()
            }

            mListView.setOnItemClickListener { parent, view, position, id ->
                mManager?.setCurrentIndex(position)
                mlistAdapter.setFocusItemPos(position)
                mManager?.Reset()
                music_playing = false
                updateState(STATUS_PLAY)
            }

            btnPrevious.setOnClickListener {
                updateState(STATUS_PREV)
            }

            btnPlay.setOnClickListener {
                updateState(STATUS_PLAY)
            }

            btnNext.setOnClickListener {
                updateState(STATUS_NEXT)
            }
            playModel.setOnClickListener {
                // 在这里切换播放模式和更改图标
                togglePlayMode() // 编写切换播放模式的方法
                updatePlayModeIcon() // 编写更新图标的方法
            }

            musicSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    mManager?.let {
                        try {
                            it.seekTo(seekBar?.progress ?: 0)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            })

    }


    // 在 onDestroy 方法中取消注册广播接收器
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(controlReceiver)
        coroutineScope.cancel()
    }
}