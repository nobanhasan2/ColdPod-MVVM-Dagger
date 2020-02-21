package com.android.myapplication.coldpod.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import com.android.myapplication.coldpod.R;
import com.android.myapplication.coldpod.ui.playing.PlayingActivity;
import com.android.myapplication.coldpod.utils.Constants;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

import static com.android.myapplication.coldpod.utils.Constants.ACTION_RELEASE_OLD_PLAYER;
import static com.android.myapplication.coldpod.utils.Constants.FAST_FORWARD_INCREMENT;
import static com.android.myapplication.coldpod.utils.Constants.PLAYBACK_CHANNEL_ID;
import static com.android.myapplication.coldpod.utils.Constants.PLAYBACK_NOTIFICATION_ID;
import static com.android.myapplication.coldpod.utils.Constants.REWIND_INCREMENT;

public class PodcastService extends MediaBrowserServiceCompat implements Player.EventListener {
    private static MediaSessionCompat mMediaSession;
    private PlaybackStateCompat.Builder mStateBuilder;
    public static final String COLD_POD_ROOT_ID = "pod_root_id";
    public static final String COLD_POD_EMPTY_ROOT_ID = "empty_root_id";

    private SimpleExoPlayer mExoPlayer;
    private static final String TAG = PodcastService.class.getSimpleName();
    private String itemUrl;
    private String itemTitle;
    private String podCastTitle;
    private String podCastImage;

    public static final String EXTRA_ITEM_URL = "extra_item_url";
    public static final String EXTRA_ITEM_TITLE = "extra_item_title";
    public static final String EXTRA_PODCAST_TITLE = "extra_podcast_title";
    public static final String EXTRA_PODCAST_IMAGE = "extra_podcast_image";

    /** A notification manager to start, update and cancel a media style notification reflecting
     * the player state */
    private PlayerNotificationManager mPlayerNotificationManager;


    public static Intent getInstance(Context context , String itemUrl,String itemTitle,String podCastImage, String podCastTitle){
        Intent serviceIntent = new Intent(context, PodcastService.class);
        serviceIntent.putExtra(EXTRA_ITEM_URL, itemUrl);
        serviceIntent.putExtra(EXTRA_ITEM_TITLE,itemTitle);
        serviceIntent.putExtra(EXTRA_PODCAST_TITLE, podCastTitle);
        serviceIntent.putExtra(EXTRA_PODCAST_IMAGE, podCastImage);
        serviceIntent.setAction(ACTION_RELEASE_OLD_PLAYER);
        return serviceIntent;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize the media session
        initializeMediaSession();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Check if the old player should be released
        if (intent.getAction() != null && intent.getAction().equals(ACTION_RELEASE_OLD_PLAYER)) {
            if (mExoPlayer != null) {
                mExoPlayer.stop();
                releasePlayer();
            }
        }
        itemUrl = intent.getStringExtra(EXTRA_ITEM_URL);
        itemTitle = intent.getStringExtra(EXTRA_ITEM_TITLE);
        podCastImage = intent.getStringExtra(EXTRA_PODCAST_IMAGE);
        podCastTitle = intent.getStringExtra(EXTRA_PODCAST_TITLE);
        initializePlayer();
        // Initialize PlayerNotificationManager
        initializeNotificationManager(itemTitle);
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Initialize the media session.
     */
    private void initializeMediaSession() {
        // Create a MediaSessionCompat
        mMediaSession = new MediaSessionCompat(PodcastService.this, TAG);

        // Enable callbacks from MediaButtons and TransportControls
        mMediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
        mStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_REWIND |
                                PlaybackStateCompat.ACTION_FAST_FORWARD |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE);
        mMediaSession.setPlaybackState(mStateBuilder.build());

        // MySessionCallback() has methods that handle callbacks from a media controller
        mMediaSession.setCallback(new MySessionCallback());

        // Set the session's token so that client activities can communicate with it.
        setSessionToken(mMediaSession.getSessionToken());

        mMediaSession.setSessionActivity(PendingIntent.getActivity(this,
                11,
                new Intent(this, PlayingActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT));
    }
    /**
     * Initialize ExoPlayer.
     */
    private void initializePlayer() {
        if (mExoPlayer == null) {
            // Create an instance of the ExoPlayer
            DefaultRenderersFactory defaultRenderersFactory = new DefaultRenderersFactory(this);
            TrackSelector trackSelector = new DefaultTrackSelector();
            LoadControl loadControl = new DefaultLoadControl();
            mExoPlayer = ExoPlayerFactory.newSimpleInstance(this, defaultRenderersFactory,
                    trackSelector, loadControl);

            // Set the Player.EventListener
            mExoPlayer.addListener(this);

            // Prepare the MediaSource
            Uri mediaUri = Uri.parse(itemUrl);
            MediaSource mediaSource = buildMediaSource(mediaUri);
            //start buffering
            mExoPlayer.prepare(mediaSource);

            //play it when you finish buffering
            mExoPlayer.setPlayWhenReady(true);
        }
    }
    /**
     * Create a MediaSource.
     * @param mediaUri
     */
    private MediaSource buildMediaSource(Uri mediaUri) {
        String userAgent = Util.getUserAgent(this, getString(R.string.app_name));
        DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(
                this, userAgent);
        return new ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(mediaUri);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        mExoPlayer.stop(true);
        stopSelf();
    }


    @Override
    public void onDestroy() {
        mMediaSession.release();
        releasePlayer();
        // If the player is released it must be removed from the manager by calling setPlayer(null)
        // which will cancel the notification
        mPlayerNotificationManager.setPlayer(null);
        super.onDestroy();
    }

    /**
     * Release ExoPlayer.
     */
    private void releasePlayer() {
        mExoPlayer.release();
        mExoPlayer = null;
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        if(clientPackageName.equals(getApplication().getPackageName())){
            return new BrowserRoot(COLD_POD_ROOT_ID,null);
        }
        return new BrowserRoot(COLD_POD_EMPTY_ROOT_ID,null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        // Browsing not allowed
        if (TextUtils.equals(COLD_POD_EMPTY_ROOT_ID, parentId)) {
            result.sendResult(null);
            return;
        }

        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

        // Check if this is the root menu:
        if (COLD_POD_ROOT_ID.equals(parentId)) {
            // Build the MediaItem objects for the top level,
            // and put them in the mediaItems list...

        } else {
            // Examine the passed parentMediaId to see which submenu we're at,
            // and put the children of that menu in the mediaItems list...
        }
        result.sendResult(mediaItems);
    }
    /**
     * Media Session Callbacks, where all external clients control the player.
     */
    private class MySessionCallback extends MediaSessionCompat.Callback {

        @Override
        public void onPlay() {
            // onPlay() callback should include code that calls startService().
            startService(new Intent(getApplicationContext(), PodcastService.class));

            // Set the session active
            mMediaSession.setActive(true);

            // Start the player
            mExoPlayer.setPlayWhenReady(true);
        }

        @Override
        public void onPause() {
            mExoPlayer.setPlayWhenReady(false);

            stopForeground(false);
        }

        @Override
        public void onRewind() {
            mExoPlayer.seekTo(Math.max(mExoPlayer.getCurrentPosition() - REWIND_INCREMENT, 0));
            Timber.e("onRewind:");
        }

        @Override
        public void onFastForward() {
            long duration = mExoPlayer.getDuration();
            mExoPlayer.seekTo(Math.min(mExoPlayer.getCurrentPosition() + FAST_FORWARD_INCREMENT, duration));
        }

        @Override
        public void onStop() {
            // onStop() callback should call stopSelf().
            stopSelf();

            // Set the session inactive
            mMediaSession.setActive(false);

            // Stop the player
            mExoPlayer.stop();

            // Take the service out of the foreground
            stopForeground(true);
        }
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (playbackState == Player.STATE_READY && playWhenReady) {
            // When ExoPlayer is playing, update the PlaybackState
            mStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,
                    mExoPlayer.getCurrentPosition(), 1f);

            Timber.d("onPlayerStateChanged: we are playing");
        } else if (playbackState == Player.STATE_READY) {
            // When ExoPlayer is paused, update the PlaybackState
            mStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED,
                    mExoPlayer.getCurrentPosition(), 1f);

            Timber.d("onPlayerStateChanged: we are paused");
        }
        mMediaSession.setPlaybackState(mStateBuilder.build());
    }



    /**
     * Initialize PlayerNotificationManager.
     * References: @see "https://medium.com/google-exoplayer/playback-notifications-with-exoplayer-a2f1a18cf93b"
     * "https://www.youtube.com/watch?v=svdq1BWl4r8" "https://github.com/google/ExoPlayer/tree/io18"
     * "https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer2/ui/PlayerNotificationManager.html"
     */
    private void initializeNotificationManager(String itemTitle) {
        // Create a notification manager and a low-priority notification channel with the channel ID
        // and channel name
        mPlayerNotificationManager = PlayerNotificationManager.createWithNotificationChannel(
                this,
                PLAYBACK_CHANNEL_ID,
                R.string.playback_channel_name,
                PLAYBACK_NOTIFICATION_ID,
                // An adapter to provide descriptive data about the current playing item
                new PlayerNotificationManager.MediaDescriptionAdapter() {
                    @Override
                    public String getCurrentContentTitle(Player player) {
                        return itemTitle;
                    }

                    @Nullable
                    @Override
                    public PendingIntent createCurrentContentIntent(Player player) {
                        return null;
                    }

                    @Nullable
                    @Override
                    public String getCurrentContentText(Player player) {
                        return podCastTitle;
                    }

                    @Nullable
                    @Override
                    public Bitmap getCurrentLargeIcon(Player player, PlayerNotificationManager.BitmapCallback callback) {
                        return null;
                    }

                }
        );

        // A listener for start and cancellation of the notification
        mPlayerNotificationManager.setNotificationListener(new PlayerNotificationManager.NotificationListener() {
            // Called when the notification is initially created
            @Override
            public void onNotificationStarted(int notificationId, Notification notification) {
                startForeground(notificationId, notification);
            }

            // Called when the notification is cancelled
            @Override
            public void onNotificationCancelled(int notificationId) {
                stopSelf();
            }
        });

        // Once the notification manager is created, attach the player
        mPlayerNotificationManager.setPlayer(mExoPlayer);
        // Set the MediaSessionToken
        mPlayerNotificationManager.setMediaSessionToken(mMediaSession.getSessionToken());

        // Customize the notification
        // Set the small icon of the notification
        mPlayerNotificationManager.setSmallIcon(R.drawable.logo);
        // Set skip previous and next actions
        mPlayerNotificationManager.setUseNavigationActions(true);
        // Set the fast forward increment by 30 sec
        mPlayerNotificationManager.setFastForwardIncrementMs(FAST_FORWARD_INCREMENT);
        // Set the rewind increment by 10sec
        mPlayerNotificationManager.setRewindIncrementMs(REWIND_INCREMENT);
        // Omit the stop action
        mPlayerNotificationManager.setStopAction(null);
    }

}