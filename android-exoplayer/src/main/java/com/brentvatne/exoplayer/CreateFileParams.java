package com.brentvatne.exoplayer;

import com.facebook.react.bridge.ReactContext;
import android.net.Uri;
import com.google.android.exoplayer2.upstream.cache.*;
import com.google.android.exoplayer2.upstream.*;
import com.facebook.react.uimanager.ThemedReactContext;

class CreateFileParams{
    public static ThemedReactContext context;
    public static Cache cache;
    public static String relativePath;
    public static Uri uri;

    public CreateFileParams(ThemedReactContext context, Cache cache, String relativePath, Uri uri){
        this.context = context;
        this.cache = cache;
        this.relativePath = relativePath;
        this.uri = uri;
    }
}