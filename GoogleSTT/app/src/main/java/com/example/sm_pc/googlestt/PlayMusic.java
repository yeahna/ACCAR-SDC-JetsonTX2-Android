package com.example.sm_pc.googlestt;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class PlayMusic extends Service {
    private static final String TAG = "TestSound";
    MediaPlayer mp;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mp = MediaPlayer.create(this, R.raw.travel);
        mp.setLooping(true);
        mp.setVolume(100, 100);
        Toast.makeText(this, "Service started...", Toast.LENGTH_SHORT).show();
        mp.start();
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public IBinder onUnBind(Intent intent) {
        return null;
    }

    public void onStop() {
        Log.i(TAG, "onStop()");
    }
    public void onPause() {
        Log.i(TAG, "onPause()");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy()");
        mp.stop();
        mp.release(); // 자원 반납
    }

    @Override
    public void onLowMemory() {
        //Log.i(TAG, "onLowMemory()");
    }
}
