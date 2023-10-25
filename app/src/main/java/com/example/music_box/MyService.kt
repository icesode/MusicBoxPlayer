package com.example.music_box

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.widget.RemoteViews
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import java.io.IOException

class MyService : Service() {

    private val musicList = mutableListOf<MusicInfo>()
    private var random = java.util.Random(System.currentTimeMillis())

    private lateinit var mPlayer:MediaPlayer
    private var seekLength = 0
    private var currentIndex = -1
    private var total_music = 0

    private var playMode:PlayMode=PlayMode.LIST_LOOP
    var completed = false
    private var context=MyApplication.context
    private lateinit var playButtonId:TextView
//    val musicProgressLiveData = MutableLiveData<MusicProgress>()
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    enum class PlayMode {
        LIST_LOOP,
        SINGLE_LOOP,
        SHUFFLE
    }
    private val mBinder=MusicBinder()


    override fun onCreate() {
        super.onCreate()
        Log.d("MyService", "onCreate executed")
        random = java.util.Random(System.currentTimeMillis())
        var mPlayer: MediaPlayer? = null
        resolveMusicToList()
        initPlayer()

        val manager=getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
            val channel=NotificationChannel("my_service","前台Service通知",
            NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)

        }
        val intent=Intent(this,MainActivity::class.java)
        val pi=PendingIntent.getActivity(this,0,intent, PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(this,"my_service")
            .setContentTitle("music title")
            .setContentText("music artist")
            .setSmallIcon(R.drawable.small_icon)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.large_icon))
            .setContentIntent(pi)
            .build()

        startForeground(1,notification)
        serviceScope.launch {
            updateNotificationWithControlsPeriodically()
        }
    }
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.e("MyService", "onStartCommand executed")
        when (intent.action) {
            "PLAY" -> {
                // 处理播放操作
//                mBinder.Play()
                sendControlCommand("PLAY")
                Log.e("MyService", "onStartCommand里的Play")
            }
            "PAUSE" -> {
                // 处理暂停操作
//                mBinder.Pause()
                sendControlCommand("PAUSE")
                Log.e("MyService", "onStartCommand里的PAUSE")
            }
            "PREVIOUS" -> {
//                mBinder.setCurrentIndex(currentIndex-1)
                // 处理上一首操作
//                mBinder.playPrevious()
                sendControlCommand("PREVIOUS")
                Log.e("MyService", "onStartCommand里的PREVIOUS,currentIndex=${currentIndex}")
            }
            "NEXT" -> {
                // 处理下一首操作
//                Log.e("MyService", "onStartCommand里的NEXT,currentIndex=${currentIndex}")
//                mBinder.setCurrentIndex(currentIndex+1)
//                mBinder.PlayNext()
                sendControlCommand("NEXT")
                Log.e("MyService", "onStartCommand里的NEXT,currentIndex=${currentIndex}")
            }
            else -> {
                // 处理其他操作或默认操作
            }
        }

        // 更新通知
        mBinder.updateNotificationWithControls(mBinder.getDuration(),mBinder.getCurrentPosition())
        return super.onStartCommand(intent, flags, startId)
    }
    override fun onDestroy() {
        super.onDestroy()
        Log.d("MyService", "onDestroy executed")
    }



    inner class MusicBinder() : Binder() {

//        fun getMusicProgressLiveData(): LiveData<MusicProgress> {
//            return musicProgressLiveData
//        }

        fun setPlayMode(mode: PlayMode) {
            playMode = mode
        }
        fun getPlayMode() :PlayMode{
            return playMode
        }

        fun release() {
            mPlayer?.reset()
            mPlayer?.stop()
            mPlayer?.release()
        }

        fun Pause() {
            if (mPlayer?.isPlaying == true) {
                mPlayer?.pause()
                seekLength = mPlayer?.currentPosition ?: 0
                updateNotificationWithControls(getDuration(),getCurrentPosition())
            }
        }

        fun Resume() {
            mPlayer?.let {
                completed = false
                it.seekTo(seekLength)
                it.start()
            }
        }

        fun SetSeekPos(seekPos: Int) {
            mPlayer?.seekTo(seekPos)
        }

        fun Reset() {
            seekLength = 0
            mPlayer?.let {
                completed = false
                it.seekTo(seekLength)
            }
        }


        fun Play() {
            if (mPlayer == null) {
                initPlayer() // 如果 MediaPlayer 为空，进行初始化
            }

            mPlayer?.reset()

            if (currentIndex < 0 || currentIndex >= musicList.size) {
                // 处理索引越界情况
                // 添加错误处理语句，例如：
                showError("Invalid music index")
                return
            }

            val musicInfo = musicList[currentIndex]
            val musicPath = musicInfo.music_path

            if (musicPath.isNullOrEmpty()) {
                // 处理音频文件路径为空的情况
                // 添加错误处理语句，例如：
                showError("Invalid music file path")
                return
            }

            try {
                mPlayer?.setDataSource(musicPath)
                mPlayer?.prepare()
                mPlayer?.seekTo(seekLength)
                completed = false
                mPlayer?.start()
                mPlayer?.setOnCompletionListener { mp ->
                    completed = true
                }
                GlobalScope.launch(Dispatchers.Main) {
                    updateNotificationWithControlsPeriodically()
                }
            } catch (e: IOException) {
                // 处理设置数据源和准备播放时的异常
                e.printStackTrace()
                // 添加错误处理语句，例如：
                showError("Error preparing music: " + e.message)
            }
            updateNotificationWithControls(getDuration(),getCurrentPosition())

        }

        fun showError(errorMessage: String) {
            // 在这里处理错误，例如，可以将错误消息显示给用户
            // 使用 Toast 来显示错误消息：
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        }


        // 在 MusicBinder 类中添加一个用于更新通知的方法
        fun updateNotificationWithControls(duration: Int, currentPosition: Int) {
//            Log.d("MyService", "updateNotificationWithControls executed")
            val musicInfo = getMusicInfo(getCurrentIndex())

            val playIntent = Intent(context, MyService::class.java)
            playIntent.action = "PLAY"
            val playPendingIntent = PendingIntent.getService(context, 0, playIntent,  PendingIntent.FLAG_IMMUTABLE)

            val pauseIntent = Intent(context, MyService::class.java)
            pauseIntent.action = "PAUSE"
            val pausePendingIntent = PendingIntent.getService(context, 1, pauseIntent,  PendingIntent.FLAG_IMMUTABLE)

            val previousIntent = Intent(context, MyService::class.java)
            previousIntent.action = "PREVIOUS"
            val previousPendingIntent = PendingIntent.getService(context, 2, previousIntent,  PendingIntent.FLAG_IMMUTABLE)

            val nextIntent = Intent(context, MyService::class.java)
            nextIntent.action = "NEXT"
            val nextPendingIntent = PendingIntent.getService(context, 3, nextIntent,  PendingIntent.FLAG_IMMUTABLE)

            val notificationLayout = RemoteViews(this@MyService.packageName, R.layout.notification_layout)
            notificationLayout.setTextViewText(R.id.notification_title, musicInfo.music_title)
            notificationLayout.setTextViewText(R.id.notification_artist, musicInfo.music_artist)
            notificationLayout.setProgressBar(R.id.notification_progress, getDuration(), getCurrentPosition(), false)
            if (isPlaying()) {
                // 如果正在播放，将通知中的播放图标设置为暂停图标
                notificationLayout.setOnClickPendingIntent(R.id.notification_play, pausePendingIntent)
                notificationLayout.setImageViewResource(R.id.notification_play, R.drawable.pause)
            } else {
                // 如果未播放，将通知中的暂停图标设置为播放图标
                notificationLayout.setOnClickPendingIntent(R.id.notification_play, playPendingIntent)
                notificationLayout.setImageViewResource(R.id.notification_play, R.drawable.play)
            }


            notificationLayout.setOnClickPendingIntent(R.id.notification_previous, previousPendingIntent)
            notificationLayout.setOnClickPendingIntent(R.id.notification_next, nextPendingIntent)


            val notification = NotificationCompat.Builder(context, "my_service")
                .setSmallIcon(R.drawable.small_icon)
                .setCustomBigContentView(notificationLayout)
                .setContentIntent(playPendingIntent)
                .setOngoing(true)
                .build()
//            musicProgressLiveData.postValue(MusicProgress(duration,currentPosition))
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1, notification)
        }

        fun PlayNext(){
            when(playMode){
                PlayMode.LIST_LOOP->{
                    // 列表循环，切换到下一首歌曲
                    // 检查是否已经是最后一首歌曲，如果是，切换到第一首歌曲
                    if (currentIndex==musicList.size-1){
                        currentIndex=0
                    }else{
                        currentIndex++
                    }
                }
                PlayMode.SINGLE_LOOP->{

                }
                PlayMode.SHUFFLE->{
                    currentIndex=random.nextInt(musicList.size)
                }

            }
            seekLength=0
            completed=false
            Play()
        }

        fun playPrevious() {
            currentIndex = currentIndex - 1
            if (currentIndex < 0) {
                currentIndex = musicList.size - 1
            }
            seekLength = 0
            completed = false
            mPlayer?.let {
                Play()
            }
        }

        fun isPlaying(): Boolean {
            return mPlayer?.isPlaying == true
        }

        fun getDuration(): Int {
            return mPlayer?.duration ?: 0
        }

        fun isCompleted(): Boolean {
            return completed
        }

        fun getCurrentPosition(): Int {
            return mPlayer?.currentPosition ?: 0
        }

        fun seekTo(length: Int) {
            seekLength = length
            mPlayer?.seekTo(length)
        }

        fun setCurrentIndex(index: Int) {
            currentIndex = index
        }

        fun getCurrentIndex(): Int {
            return currentIndex
        }

        fun getMusicInfo(index: Int): MusicInfo {
            return musicList[index]
        }

        fun getMusicList(): List<MusicInfo> {
            return musicList
        }

        fun getTotalMusic(): Int {
            return total_music
        }

    }

    private suspend fun updateNotificationWithControlsPeriodically() {
        while (mBinder.isPlaying()&&!mBinder.isCompleted()){
            var duration=mBinder.getDuration()
            var currentPosition=mBinder.getCurrentPosition()

            withContext(Dispatchers.Main){
//                musicProgressLiveData.value= MusicProgress(duration,currentPosition)
                mBinder.updateNotificationWithControls(duration,currentPosition)
            }
            delay(100)
        }
    }

    fun sendControlCommand(command:String){
        val intent=Intent("com.example.music_box.ACTION_CONTROL")
        intent.putExtra("command",command)
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    fun initPlayer() {
        mPlayer = MediaPlayer()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        mPlayer?.setAudioAttributes(attributes)
    }

    fun resolveMusicToList() {
        Log.e("MysService","读取音乐文件")
        val selection = MediaStore.Audio.Media.IS_MUSIC + "!=0"
        val sortOrder = MediaStore.MediaColumns.DISPLAY_NAME + ""
        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION
        )
        val contentResolver = context.contentResolver
        val cursor: Cursor? = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, sortOrder
        )

        cursor?.let {
            total_music = it.count
            it.moveToFirst()
            while (!it.isAfterLast) {
                val minfo = MusicInfo(
                    it.getString(0),
                    it.getString(2),
                    it.getString(3),
                    it.getString(1),
                    it.getString(4).toInt()
                )
                if (it.getString(4).toInt()!=0) {
                    musicList.add(minfo)
                    it.moveToNext()
                }else{
                    it.moveToNext()
                }
            }
        }
        cursor?.close()
    }

}