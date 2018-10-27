package com.brentvatne.exoplayer;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.facebook.react.bridge.Dynamic;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableNativeMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.RCTEventEmitter;

import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.UIManagerModule;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewGroupManager;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.upstream.RawResourceDataSource;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.Nullable;

public class ReactExoplayerViewManager extends ViewGroupManager<ReactExoplayerView> {

    private static final HashMap<ReactExoplayerView, TimerTask> activeTasks = new HashMap<ReactExoplayerView, TimerTask>();

    private static final String REACT_CLASS = "RCTVideo";

    private static final String PROP_SRC = "src";
    private static final String PROP_SRC_URI = "uri";
    private static final String PROP_SRC_TYPE = "type";
    private static final String PROP_SRC_HEADERS = "requestHeaders";
    private static final String PROP_RESIZE_MODE = "resizeMode";
    private static final String PROP_REPEAT = "repeat";
    private static final String PROP_SELECTED_AUDIO_TRACK = "selectedAudioTrack";
    private static final String PROP_SELECTED_AUDIO_TRACK_TYPE = "type";
    private static final String PROP_SELECTED_AUDIO_TRACK_VALUE = "value";
    private static final String PROP_SELECTED_TEXT_TRACK = "selectedTextTrack";
    private static final String PROP_SELECTED_TEXT_TRACK_TYPE = "type";
    private static final String PROP_SELECTED_TEXT_TRACK_VALUE = "value";
    private static final String PROP_TEXT_TRACKS = "textTracks";
    private static final String PROP_PAUSED = "paused";
    private static final String PROP_MUTED = "muted";
    private static final String PROP_VOLUME = "volume";
    private static final String PROP_BUFFER_CONFIG = "bufferConfig";
    private static final String PROP_BUFFER_CONFIG_MIN_BUFFER_MS = "minBufferMs";
    private static final String PROP_BUFFER_CONFIG_MAX_BUFFER_MS = "maxBufferMs";
    private static final String PROP_BUFFER_CONFIG_BUFFER_FOR_PLAYBACK_MS = "bufferForPlaybackMs";
    private static final String PROP_BUFFER_CONFIG_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = "bufferForPlaybackAfterRebufferMs";
    private static final String PROP_PROGRESS_UPDATE_INTERVAL = "progressUpdateInterval";
    private static final String PROP_SEEK = "seek";
    private static final String PROP_RATE = "rate";
    private static final String PROP_MAXIMUM_BIT_RATE = "maxBitRate";
    private static final String PROP_PLAY_IN_BACKGROUND = "playInBackground";
    private static final String PROP_DISABLE_FOCUS = "disableFocus";
    private static final String PROP_FULLSCREEN = "fullscreen";
    private static final String PROP_USE_TEXTURE_VIEW = "useTextureView";
    private static final String PROP_HIDE_SHUTTER_VIEW = "hideShutterView";

    public static final int COMMAND_FADE_VOLUME = 1;

    private VideoEventEmitter eventEmitter;

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    protected ReactExoplayerView createViewInstance(ThemedReactContext themedReactContext) {
        this.eventEmitter = new VideoEventEmitter(themedReactContext);
        return new ReactExoplayerView(themedReactContext);
    }

    @Override
    public void onDropViewInstance(ReactExoplayerView view) {
        view.cleanUpResources();
    }

    @Override
    public @Nullable Map<String, Object> getExportedCustomDirectEventTypeConstants() {
        MapBuilder.Builder<String, Object> builder = MapBuilder.builder();
        for (String event : VideoEventEmitter.Events) {
            builder.put(event, MapBuilder.of("registrationName", event));
        }
        return builder.build();
    }

    @Override
    public @Nullable Map<String, Object> getExportedViewConstants() {
        return MapBuilder.<String, Object>of(
                "ScaleNone", Integer.toString(ResizeMode.RESIZE_MODE_FIT),
                "ScaleAspectFit", Integer.toString(ResizeMode.RESIZE_MODE_FIT),
                "ScaleToFill", Integer.toString(ResizeMode.RESIZE_MODE_FILL),
                "ScaleAspectFill", Integer.toString(ResizeMode.RESIZE_MODE_CENTER_CROP)
        );
    }

    @Nullable
    @Override
    public Map<String, Integer> getCommandsMap() {
        return MapBuilder.of(
            "fadeVolume",
            COMMAND_FADE_VOLUME
        );
    }

    @Override
    public void receiveCommand(final ReactExoplayerView videoView, int commandId, @Nullable ReadableArray args) {
        // This will be called whenever a command is sent from react-native.
        switch (commandId) {
            case COMMAND_FADE_VOLUME:
                if (args != null) {
                    final int requestId = args.getInt(0);
                    final String type = args.getString(1);
                    final int steps = args.getInt(2);
                    final int frequency = args.getInt(3);
                    float initialVolume = (float) args.getDouble(4);

                    // Avoid overlapping fadeIn and fadeOut
                    if(activeTasks.containsKey(videoView)){
                        activeTasks.get(videoView).cancel();
                        Float currentVolume = videoView.getVolume();
                        if(currentVolume != null){
                            initialVolume = currentVolume.floatValue();
                        }
                    }

                    Timer timer = new Timer(); // Create a new timer which runs in it's own thread
                    TimerTask task = new FadeTimerTask(type, steps, initialVolume, videoView, requestId);
                    timer.scheduleAtFixedRate(task, 0, frequency);

                    activeTasks.put(videoView, task);
                }
            break;
        }
    }

    @ReactProp(name = PROP_SRC)
    public void setSrc(final ReactExoplayerView videoView, @Nullable ReadableMap src) {
        Context context = videoView.getContext().getApplicationContext();
        String uriString = src.hasKey(PROP_SRC_URI) ? src.getString(PROP_SRC_URI) : null;
        String extension = src.hasKey(PROP_SRC_TYPE) ? src.getString(PROP_SRC_TYPE) : null;
        Map<String, String> headers = src.hasKey(PROP_SRC_HEADERS) ? toStringMap(src.getMap(PROP_SRC_HEADERS)) : null;


        if (TextUtils.isEmpty(uriString)) {
            return;
        }

        if (startsWithValidScheme(uriString)) {
            Uri srcUri = Uri.parse(uriString);

            if (srcUri != null) {
                videoView.setSrc(srcUri, extension, headers);
            }
        } else {
            int identifier = context.getResources().getIdentifier(
                uriString,
                "drawable",
                context.getPackageName()
            );
            if (identifier == 0) {
                identifier = context.getResources().getIdentifier(
                    uriString,
                    "raw",
                    context.getPackageName()
                );
            }
            if (identifier > 0) {
                Uri srcUri = RawResourceDataSource.buildRawResourceUri(identifier);
                if (srcUri != null) {
                    videoView.setRawSrc(srcUri, extension);
                }
            }
        }
    }

    @ReactProp(name = PROP_RESIZE_MODE)
    public void setResizeMode(final ReactExoplayerView videoView, final String resizeModeOrdinalString) {
        videoView.setResizeModeModifier(convertToIntDef(resizeModeOrdinalString));
    }

    @ReactProp(name = PROP_REPEAT, defaultBoolean = false)
    public void setRepeat(final ReactExoplayerView videoView, final boolean repeat) {
        videoView.setRepeatModifier(repeat);
    }

    @ReactProp(name = PROP_SELECTED_AUDIO_TRACK)
    public void setSelectedAudioTrack(final ReactExoplayerView videoView,
                                     @Nullable ReadableMap selectedAudioTrack) {
        String typeString = null;
        Dynamic value = null;
        if (selectedAudioTrack != null) {
            typeString = selectedAudioTrack.hasKey(PROP_SELECTED_AUDIO_TRACK_TYPE)
                    ? selectedAudioTrack.getString(PROP_SELECTED_AUDIO_TRACK_TYPE) : null;
            value = selectedAudioTrack.hasKey(PROP_SELECTED_AUDIO_TRACK_VALUE)
                    ? selectedAudioTrack.getDynamic(PROP_SELECTED_AUDIO_TRACK_VALUE) : null;
        }
        videoView.setSelectedAudioTrack(typeString, value);
    }

    @ReactProp(name = PROP_SELECTED_TEXT_TRACK)
    public void setSelectedTextTrack(final ReactExoplayerView videoView,
                                     @Nullable ReadableMap selectedTextTrack) {
        String typeString = null;
        Dynamic value = null;
        if (selectedTextTrack != null) {
            typeString = selectedTextTrack.hasKey(PROP_SELECTED_TEXT_TRACK_TYPE)
                    ? selectedTextTrack.getString(PROP_SELECTED_TEXT_TRACK_TYPE) : null;
            value = selectedTextTrack.hasKey(PROP_SELECTED_TEXT_TRACK_VALUE)
                    ? selectedTextTrack.getDynamic(PROP_SELECTED_TEXT_TRACK_VALUE) : null;
        }
        videoView.setSelectedTextTrack(typeString, value);
    }

    @ReactProp(name = PROP_TEXT_TRACKS)
    public void setPropTextTracks(final ReactExoplayerView videoView,
                                  @Nullable ReadableArray textTracks) {
        videoView.setTextTracks(textTracks);
    }

    @ReactProp(name = PROP_PAUSED, defaultBoolean = false)
    public void setPaused(final ReactExoplayerView videoView, final boolean paused) {
        videoView.setPausedModifier(paused);
    }

    @ReactProp(name = PROP_MUTED, defaultBoolean = false)
    public void setMuted(final ReactExoplayerView videoView, final boolean muted) {
        videoView.setMutedModifier(muted);
    }

    @ReactProp(name = PROP_VOLUME, defaultFloat = 1.0f)
    public void setVolume(final ReactExoplayerView videoView, final float volume) {
        videoView.setVolumeModifier(volume);
    }

    @ReactProp(name = PROP_PROGRESS_UPDATE_INTERVAL, defaultFloat = 250.0f)
    public void setProgressUpdateInterval(final ReactExoplayerView videoView, final float progressUpdateInterval) {
        videoView.setProgressUpdateInterval(progressUpdateInterval);
    }

    @ReactProp(name = PROP_SEEK)
    public void setSeek(final ReactExoplayerView videoView, final float seek) {
        videoView.seekTo(Math.round(seek * 1000f));
    }

    @ReactProp(name = PROP_RATE)
    public void setRate(final ReactExoplayerView videoView, final float rate) {
        videoView.setRateModifier(rate);
    }

    @ReactProp(name = PROP_MAXIMUM_BIT_RATE)
    public void setMaxBitRate(final ReactExoplayerView videoView, final int maxBitRate) {
        videoView.setMaxBitRateModifier(maxBitRate);
    }

    @ReactProp(name = PROP_PLAY_IN_BACKGROUND, defaultBoolean = false)
    public void setPlayInBackground(final ReactExoplayerView videoView, final boolean playInBackground) {
        videoView.setPlayInBackground(playInBackground);
    }

    @ReactProp(name = PROP_DISABLE_FOCUS, defaultBoolean = false)
    public void setDisableFocus(final ReactExoplayerView videoView, final boolean disableFocus) {
        videoView.setDisableFocus(disableFocus);
    }

    @ReactProp(name = PROP_FULLSCREEN, defaultBoolean = false)
    public void setFullscreen(final ReactExoplayerView videoView, final boolean fullscreen) {
        videoView.setFullscreen(fullscreen);
    }

    @ReactProp(name = PROP_USE_TEXTURE_VIEW, defaultBoolean = true)
    public void setUseTextureView(final ReactExoplayerView videoView, final boolean useTextureView) {
        videoView.setUseTextureView(useTextureView);
    }

    @ReactProp(name = PROP_HIDE_SHUTTER_VIEW, defaultBoolean = false)
    public void setHideShutterView(final ReactExoplayerView videoView, final boolean hideShutterView) {
        videoView.setHideShutterView(hideShutterView);
    }

    @ReactProp(name = PROP_BUFFER_CONFIG)
    public void setBufferConfig(final ReactExoplayerView videoView, @Nullable ReadableMap bufferConfig) {
        int minBufferMs = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;
        int maxBufferMs = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
        int bufferForPlaybackMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
        int bufferForPlaybackAfterRebufferMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
        if (bufferConfig != null) {
            minBufferMs = bufferConfig.hasKey(PROP_BUFFER_CONFIG_MIN_BUFFER_MS)
                    ? bufferConfig.getInt(PROP_BUFFER_CONFIG_MIN_BUFFER_MS) : minBufferMs;
            maxBufferMs = bufferConfig.hasKey(PROP_BUFFER_CONFIG_MAX_BUFFER_MS)
                    ? bufferConfig.getInt(PROP_BUFFER_CONFIG_MAX_BUFFER_MS) : maxBufferMs;
            bufferForPlaybackMs = bufferConfig.hasKey(PROP_BUFFER_CONFIG_BUFFER_FOR_PLAYBACK_MS)
                    ? bufferConfig.getInt(PROP_BUFFER_CONFIG_BUFFER_FOR_PLAYBACK_MS) : bufferForPlaybackMs;
            bufferForPlaybackAfterRebufferMs = bufferConfig.hasKey(PROP_BUFFER_CONFIG_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
                    ? bufferConfig.getInt(PROP_BUFFER_CONFIG_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS) : bufferForPlaybackAfterRebufferMs;
            videoView.setBufferConfig(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs);
        }
    }

    public class FadeTimerTask extends TimerTask {
        private String type;
        private int steps;
        private int requestId;
        private float initialVolume;
        private ReactExoplayerView videoView;

        private int count;

        public FadeTimerTask(final String type, final int steps, final float initialVolume, final ReactExoplayerView videoView, final int requestId){
            this.type = type;
            this.steps = steps;
            this.initialVolume = initialVolume;
            this.videoView = videoView;
            this.requestId = requestId;

            if(type.equals("fadeIn")){
                this.count = steps - 1;
            }else if(type.equals("fadeOut")) {
                this.count = 1;
            }
        }

        @Override
        public void run() {
            float percent = (float)(steps - count) / steps;
            double fadeVolume = Math.pow(percent, 1.5);

            videoView.setVolumeModifier((float)fadeVolume * initialVolume);

            if(type.equals("fadeIn")){
                if(count == 0){
                    this.cancel();
                    activeTasks.remove(videoView);
                    eventEmitter.setViewId(videoView.getId());
                    eventEmitter.volumefaded(requestId);
                }
                count--;
            }else if(type.equals("fadeOut")) {
                if(count == steps) {
                    this.cancel();
                    activeTasks.remove(videoView);
                    eventEmitter.setViewId(videoView.getId());
                    eventEmitter.volumefaded(requestId);
                }
                count++;
            }
        }
    }

    private boolean startsWithValidScheme(String uriString) {
        return uriString.startsWith("http://")
                || uriString.startsWith("https://")
                || uriString.startsWith("content://")
                || uriString.startsWith("file://")
                || uriString.startsWith("asset://");
    }

    private @ResizeMode.Mode int convertToIntDef(String resizeModeOrdinalString) {
        if (!TextUtils.isEmpty(resizeModeOrdinalString)) {
            int resizeModeOrdinal = Integer.parseInt(resizeModeOrdinalString);
            return ResizeMode.toResizeMode(resizeModeOrdinal);
        }
        return ResizeMode.RESIZE_MODE_FIT;
    }

    /**
     * toStringMap converts a {@link ReadableMap} into a HashMap.
     *
     * @param readableMap The ReadableMap to be conveted.
     * @return A HashMap containing the data that was in the ReadableMap.
     * @see 'Adapted from https://github.com/artemyarulin/react-native-eval/blob/master/android/src/main/java/com/evaluator/react/ConversionUtil.java'
     */
    public static Map<String, String> toStringMap(@Nullable ReadableMap readableMap) {
        if (readableMap == null)
            return null;

        com.facebook.react.bridge.ReadableMapKeySetIterator iterator = readableMap.keySetIterator();
        if (!iterator.hasNextKey())
            return null;

        Map<String, String> result = new HashMap<>();
        while (iterator.hasNextKey()) {
            String key = iterator.nextKey();
            result.put(key, readableMap.getString(key));
        }

        return result;
    }
}
