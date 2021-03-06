package amit_yoav.deep_diving;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.ndk.CrashlyticsNdk;

import amit_yoav.deep_diving.compat.Compat;
import amit_yoav.deep_diving.dialogs.InfoDialog;
import amit_yoav.deep_diving.dialogs.QuitDialog;
import amit_yoav.deep_diving.dialogs.SettingsDialog;
import amit_yoav.deep_diving.utilities.AsyncHandler;

import io.fabric.sdk.android.Fabric;
import java.io.IOException;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;
import com.google.example.games.basegameutils.BaseGameUtils;


public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        View.OnClickListener {

    public InfoDialog infoDialog;
    public static SettingsDialog settingsDialog;
    public QuitDialog quitDialog;

    public static MyMusicRunnable musicPlayer;
    public static MySFxRunnable soundEffectsUtil;

    private static float volume = 0;
    private static boolean isSoundOn, gameStarted, isFinished, isHowToPlayShown;

    private static TextView bestScoreText;

    private int[] divers = {
            R.drawable.black_diver_select, R.drawable.magenta_diver_select, R.drawable.pink_diver_select
    };
    private int diverPointer = 0;
    ImageView mainActivityDiver = null;
    ImageButton leftArrow = null;


    //GOOGLE PLAY SERVICES RELATED TYPES
    public GoogleApiClient mGoogleApiClient;
    private static final int REQUEST_ACHIEVEMENTS = 5001, REQUEST_LEADERBOARD = 5001;

    private static int RC_SIGN_IN = 9001;

    private boolean mResolvingConnectionFailure = false;
    private boolean mAutoStartSignInflow = true;
    private boolean mSignInClicked = false;

    boolean mExplicitSignOut = false;
    boolean mInSignInFlow = false;


    private boolean isAutoConnect;

    public void setDiver(View v) {
        soundEffectsUtil.play(R.raw.open_dialog);
        if(leftArrow == v) diverPointer = --diverPointer == -1 ? 2 : diverPointer;
        else diverPointer = (++diverPointer) % 3;
        mainActivityDiver.setImageResource(divers[diverPointer]);
        settingsDialog.setMainCharacter(diverPointer);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics(), new CrashlyticsNdk());

        setContentView(R.layout.activity_main);

        // Create the Google Api Client with access to the Play Games services
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Games.API).addScope(Games.SCOPE_GAMES)
                .build();

        findViewById(R.id.sign_in_button).setOnClickListener(this);
        findViewById(R.id.sign_out_button).setOnClickListener(this);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if(getSupportActionBar() != null) getSupportActionBar().hide();

        infoDialog = new InfoDialog(this);
        settingsDialog = new SettingsDialog(this);
        quitDialog = new QuitDialog(this);

        volume = settingsDialog.getVolume();
        isSoundOn = settingsDialog.getSound();
        isHowToPlayShown = settingsDialog.getHowToPlayUsed();
        isAutoConnect = settingsDialog.getIsAutoConnected();

        if (musicPlayer == null) {
            musicPlayer = new MyMusicRunnable(this);
        }
        AsyncHandler.post(musicPlayer);

        if (soundEffectsUtil == null) {
            soundEffectsUtil = new MySFxRunnable(this);
        }
        mainActivityDiver = (ImageView) this.findViewById(R.id.diver_select);
        leftArrow = (ImageButton) (this.findViewById(R.id.leftArrow));
        diverPointer = settingsDialog.getMainCharacter();
        mainActivityDiver.setImageResource(divers[diverPointer]);

        bestScoreText = (TextView) findViewById(R.id.high_score_main);
        bestScoreText.setText(String.valueOf(settingsDialog.getBestScore()));
        bestScoreText.setTypeface(Typeface.createFromAsset(getAssets(),"fonts/SF_Slapstick_Comic_Bold_Oblique.ttf"));
    }

    public void startGame(View view) {

        if(isHowToPlayShown) {
            soundEffectsUtil.play(R.raw.start_bubble);
            gameStarted = true;
            if(!isSignedIn()) settingsDialog.setIsAutoConnected(isAutoConnect = false);
            musicPlayer.switchMusic(R.raw.game);
            Intent intent = new Intent(this, GameViewActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }
        else {
            isHowToPlayShown = settingsDialog.showOnStart = true;
            settingsDialog.showHowToPlayDialog();
        }
    }

    public void quitGame(View view) {
        soundEffectsUtil.play(R.raw.quit_dialog);
        quitDialog.dismiss();
        if(isSignedIn()) mGoogleApiClient.disconnect();
        isFinished = true;
        finish();
    }

    public void openDialog(View view) {
        soundEffectsUtil.play(R.raw.open_dialog);
        if(findViewById(R.id.infoButton) == view) infoDialog.show();
        else settingsDialog.show();
    }

    public void closeDialog(View view) {
        soundEffectsUtil.play(R.raw.quit_dialog);
        if(infoDialog.isShowing()) infoDialog.dismiss();
        else if(settingsDialog.isShowing()) settingsDialog.dismiss();
        else quitDialog.dismiss();
    }

    public static void playToggleSoundEffect(boolean on) {
        boolean tmp = isSoundOn;
        isSoundOn = true;
        if(on) soundEffectsUtil.play(R.raw.toggle_on);
        else soundEffectsUtil.play(R.raw.toggle_off);
        isSoundOn = tmp;
    }

    public static void setVolumeMusic(float newVolume) {
        musicPlayer.mPlayer.setVolume((volume = newVolume), newVolume);
    }

    public static void setIsSoundOn(boolean sound) {
        isSoundOn = sound;
    }


    @Override
    protected void onStart() {
        super.onStart();
        if (!mInSignInFlow && !mExplicitSignOut && isAutoConnect) {
//             auto sign in
            mGoogleApiClient.connect();
        }
    }

    private boolean isPaused;
    @Override
    public void onResume() {
        super.onResume();
        if(isFinished) android.os.Process.killProcess(android.os.Process.myPid());
        gameStarted = false;
        if(!isPaused) musicPlayer.switchMusic(R.raw.welcome_screen);
//        musicPlayer.startMusic(true);
        else if(musicPlayer.mPlayer != null) {
            musicPlayer.startMusic();
            isPaused = false;
        }
        settingsDialog = new SettingsDialog(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(!gameStarted) {
            musicPlayer.stopMusic(isPaused = true);
            if(mGoogleApiClient.isConnected()) mGoogleApiClient.disconnect();
        }
//        if (mGoogleApiClient.isConnected() && !gameStarted) mGoogleApiClient.disconnect();
//        if(!gameStarted) musicPlayer.stopMusic(isPaused = true);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (musicPlayer != null && musicPlayer.mPlayer != null) musicPlayer.mPlayer.release();
    }

    @Override
    public void onBackPressed() {
        soundEffectsUtil.play(R.raw.open_dialog);
        quitDialog.show();
    }

    public void openAchievements(View view) {
        if(isSignedIn()) {
            startActivityForResult(Games.Achievements.getAchievementsIntent(mGoogleApiClient),
                    REQUEST_ACHIEVEMENTS);
        }
        else {
            BaseGameUtils.makeSimpleDialog(this, getString(R.string.achievements_not_available)).show();
        }
    }

    public void openLeaderboard(View view) {
        if(isSignedIn()) {
            startActivityForResult(Games.Leaderboards.getLeaderboardIntent(mGoogleApiClient,
                    getString(R.string.leaderboard_top_divers),
                    LeaderboardVariant.TIME_SPAN_ALL_TIME,
                    LeaderboardVariant.COLLECTION_PUBLIC),
                    REQUEST_LEADERBOARD);
        }
        else {
            BaseGameUtils.makeSimpleDialog(this, getString(R.string.leaderboard_not_available)).show();
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        // The player is signed in. Hide the sign-in button and allow the
        // player to proceed.
        // show sign-out button, hide the sign-in button
        findViewById(R.id.sign_in_button).setVisibility(View.GONE);
        findViewById(R.id.sign_out_button).setVisibility(View.VISIBLE);
        settingsDialog.setIsAutoConnected(isAutoConnect = true);

        // (your code here: update UI, enable functionality that depends on sign in, etc)
    }

    @Override
    public void onConnectionSuspended(int i) {
        // Attempt to reconnect
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
//        if(!connectionResult.isSuccess() && !mSignInClicked) {
//            System.out.println("YOU CANCELED THE CONNECTECTION!");//IF WE GET HERE, IT'S GOOD!
//            System.out.println(connectionResult.getErrorMessage());
//            settingsDialog.setIsAutoConnected(false); //IF WE GET HERE
//        }

        if (mResolvingConnectionFailure) {
//            System.out.println("YOU CANCELED THE CONNECTECTION!");//IF WE GET HERE, IT'S GOOD!
//            settingsDialog.setIsAutoConnected(false); //IF WE GET HERE
            // already resolving
            return;
        }
        // if the sign-in button was clicked or if auto sign-in is enabled,
        // launch the sign-in flow
        if (mSignInClicked || mAutoStartSignInflow) {
            mAutoStartSignInflow = false;
            mSignInClicked = false;
            mResolvingConnectionFailure = true;

            // Attempt to resolve the connection failure using BaseGameUtils.
            // The R.string.signin_other_error value should reference a generic
            // error string in your strings.xml file, such as "There was
            // an issue with sign-in, please try again later."
            if (!BaseGameUtils.resolveConnectionFailure(this,
                    mGoogleApiClient, connectionResult,
                    RC_SIGN_IN, "There was an issue with sign-in, please try again later.")) {
                mResolvingConnectionFailure = false;
            }
        }

        // Put code here to display the sign-in button
    }

    @Override
    protected void onActivityResult(final int requestCode,final int resultCode,final Intent intent) {
        if (requestCode == RC_SIGN_IN) {
            mSignInClicked = mResolvingConnectionFailure = false;
            if (resultCode == RESULT_OK) {
                mGoogleApiClient.connect();
            } else {
                // Bring up an error dialog to alert the user that sign-in
                // failed. The R.string.signin_failure should reference an error
                // string in your strings.xml file that tells the user they
                // could not be signed in, such as "Unable to sign in."
                BaseGameUtils.showActivityResultError(this, requestCode, resultCode,
                        R.string.signin_failure);
            }
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.sign_in_button) {
            // start the asynchronous sign in flow
            mSignInClicked = true;
            mGoogleApiClient.connect();
        }
        else if (view.getId() == R.id.sign_out_button) {

            // show sign-in button, hide the sign-out button
            findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
            findViewById(R.id.sign_out_button).setVisibility(View.GONE);

            // user explicitly signed out, so turn off auto sign in
            mExplicitSignOut = true;
            mSignInClicked = false;
            if (isSignedIn()) {
                settingsDialog.setIsAutoConnected(isAutoConnect = false);
                Games.signOut(mGoogleApiClient);
                mGoogleApiClient.disconnect();
            }
        }
    }

    public boolean isSignedIn() {
        return (mGoogleApiClient != null && mGoogleApiClient.isConnected());
    }

    public static void setBestScore(int bestScore) {
        bestScoreText.setText(String.valueOf(bestScore));
    }

    public static class MySFxRunnable implements Runnable {
        Context appContext;
        SoundPool soundPool;
        /**
         * like a hash map, but more efficient
         */
        SparseIntArray soundsMap = new SparseIntArray();
        private boolean prepared = false;

        MySFxRunnable(Context c) {
            // be careful not to leak the activity context.
            // can keep the app context instead.
            appContext = c.getApplicationContext();

            // init this object on a user thread.
            // The rest of the use of this class can be on the UI thread
            AsyncHandler.post(this);
        }

        /**
         * load and init the sound effects, so later I'll be able to play them instantly from the
         * UI thread.
         */
        @Override
        public void run() {

            soundPool = Compat.createSoundPool();

            /*
             * a callback when prepared -- we need to prevent playing before prepared
             */
            soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                    prepared = true;
                }
            });

            /*
             * the load() returns a stream id that can be used to play the sound.
             * I use the "R.raw.xyz" integer as key, because it's useless to invent new keys for
             * them
             */
            soundsMap.put(R.raw.open_dialog, soundPool.load(appContext, R.raw.open_dialog, 1));
            soundsMap.put(R.raw.quit_dialog, soundPool.load(appContext, R.raw.quit_dialog, 1));
            soundsMap.put(R.raw.start_bubble, soundPool.load(appContext, R.raw.start_bubble, 1));
            soundsMap.put(R.raw.toggle_on, soundPool.load(appContext, R.raw.toggle_on, 1));
            soundsMap.put(R.raw.toggle_off, soundPool.load(appContext, R.raw.toggle_off, 1));
            soundsMap.put(R.raw.coin_collected, soundPool.load(appContext, R.raw.coin_collected, 1));
            soundsMap.put(R.raw.hit, soundPool.load(appContext, R.raw.hit, 1));
            soundsMap.put(R.raw.disqualification, soundPool.load(appContext, R.raw.disqualification, 1));
            soundsMap.put(R.raw.level_complete, soundPool.load(appContext, R.raw.level_complete, 1));
            soundsMap.put(R.raw.new_record, soundPool.load(appContext, R.raw.new_record, 1));
            soundsMap.put(R.raw.extra_life, soundPool.load(appContext, R.raw.extra_life, 1));
            soundsMap.put(R.raw.gun_collect, soundPool.load(appContext, R.raw.gun_collect, 1));
            soundsMap.put(R.raw.shield, soundPool.load(appContext, R.raw.shield, 1));
            soundsMap.put(R.raw.shoot, soundPool.load(appContext, R.raw.shoot, 1));
            soundsMap.put(R.raw.killed, soundPool.load(appContext, R.raw.killed, 1));
            soundsMap.put(R.raw.drop_inks, soundPool.load(appContext, R.raw.drop_inks, 1));
            soundsMap.put(R.raw.game_finished, soundPool.load(appContext, R.raw.game_finished, 1));
        }

        public void play(int soundKey) {
            if (soundPool == null || !prepared || !isSoundOn) return;
            soundPool.play(soundsMap.get(soundKey), 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    public static class MyMusicRunnable implements Runnable {
        Context appContext;
        MediaPlayer mPlayer;
        private boolean musicIsPlaying = false;
        public int currentMusicPlaying = R.raw.welcome_screen, currentPosition;

        MyMusicRunnable(Context c) {
            // be careful not to leak the activity context.
            // can keep the app context instead.
            appContext = c.getApplicationContext();
        }

        public void stopMusic(boolean isPaused) {
            if(isPaused) {
                currentPosition = mPlayer.getCurrentPosition();
                mPlayer.pause();

            }
            else {
                currentPosition = 0;
                mPlayer.stop();
            }

        }

        public void startMusic() {
            mPlayer.seekTo(currentPosition);
            mPlayer.start();
        }

        /**
         * toggles the music player state
         * called asynchronously every time the play/pause button is pressed
         */
        @Override
        public void run() {

            if (musicIsPlaying) {
                try {
                    mPlayer.stop();
                } catch (IllegalStateException e) {
                    Log.d("musicPlayer:", "mPlayer is not initialized yet: " + e.getMessage(), e);
                }
                musicIsPlaying = false;
            } else {
                if (mPlayer == null) {
                    mPlayer = MediaPlayer.create(appContext, R.raw.welcome_screen);
                    mPlayer.setVolume(volume, volume);
                    mPlayer.setLooping(true);
                    mPlayer.start();
//                    mPlayer.setOnCompletionListener(this); // MediaPlayer.OnCompletionListener
                } else {
                    mPlayer.setVolume(volume, volume);
                    mPlayer.setLooping(true);
                    mPlayer.start();
                }
                musicIsPlaying = true;
            }
        }

        public void switchMusic(int id) {
            if(musicIsPlaying) {
                if(currentMusicPlaying == id) return;
                currentMusicPlaying = id;
                AssetFileDescriptor afd = appContext.getResources().openRawResourceFd(currentMusicPlaying);
                try {
                    mPlayer.reset();
                    mPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getDeclaredLength());
                    mPlayer.prepare();
                    mPlayer.setLooping(true);
                    mPlayer.start();
                }
                catch (IOException e) {
                    Log.d("musicPlayer:", "Unable to play audio queue do to exception: " + e.getMessage(), e);
                }
                finally {
                    try {
                        afd.close();
                    } catch (IOException e) {
                        Log.e("musicPlayer:", "Failed to close afd: " + e.getMessage(), e);

                    }

                }
            }
        }
    }
}