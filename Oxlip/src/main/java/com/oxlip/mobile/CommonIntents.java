package com.oxlip.mobile;


import android.content.Context;
import android.content.Intent;

public class CommonIntents {
    public static final String MUSIC_SERVICE_NAME = "com.android.music.musicservicecommand";
    public static final String MUSIC_CMD_NAME = "command";
    public static final String MUSIC_CMD_TOGGLE_PAUSE = "togglepause";
    public static final String MUSIC_CMD_STOP = "stop";
    public static final String MUSIC_CMD_PAUSE = "pause";
    public static final String MUSIC_CMD_PLAY = "play";
    public static final String MUSIC_CMD_PREVIOUS = "previous";
    public static final String MUSIC_CMD_NEXT = "next";

    public static void playMusic() {
        Context context = ApplicationGlobals.getAppContext();
        Intent i = new Intent(MUSIC_SERVICE_NAME);
        i.putExtra(MUSIC_CMD_NAME, MUSIC_CMD_PLAY);
        context.sendBroadcast(i);
    }

    public static void stopMusic() {
        Context context = ApplicationGlobals.getAppContext();
        Intent i = new Intent(MUSIC_SERVICE_NAME);
        i.putExtra(MUSIC_CMD_NAME, MUSIC_CMD_STOP);
        context.sendBroadcast(i);
    }

    public static void pauseMusic() {
        Context context = ApplicationGlobals.getAppContext();
        Intent i = new Intent(MUSIC_SERVICE_NAME);
        i.putExtra(MUSIC_CMD_NAME, MUSIC_CMD_PAUSE);
        context.sendBroadcast(i);
    }

    public static void toggleMusic() {
        Context context = ApplicationGlobals.getAppContext();
        Intent i = new Intent(MUSIC_SERVICE_NAME);
        i.putExtra(MUSIC_CMD_NAME, MUSIC_CMD_TOGGLE_PAUSE);
        context.sendBroadcast(i);
    }

    public static void gotoNextMusic() {
        Context context = ApplicationGlobals.getAppContext();
        Intent i = new Intent(MUSIC_SERVICE_NAME);
        i.putExtra(MUSIC_CMD_NAME, MUSIC_CMD_NEXT);
        context.sendBroadcast(i);
    }

    public static void gotoPrevMusic() {
        Context context = ApplicationGlobals.getAppContext();
        Intent i = new Intent(MUSIC_SERVICE_NAME);
        i.putExtra(MUSIC_CMD_NAME, MUSIC_CMD_PREVIOUS);
        context.sendBroadcast(i);
    }
}
