package amit_yoav.deep_diving;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Vibrator;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.achievement.Achievement;
import com.google.android.gms.games.achievement.AchievementBuffer;
import com.google.android.gms.games.achievement.Achievements;

import java.lang.annotation.Retention;

import amit_yoav.deep_diving.data.Arrow;
import amit_yoav.deep_diving.data.Background;
import amit_yoav.deep_diving.data.BackgroundObject;
import amit_yoav.deep_diving.data.Character;
import amit_yoav.deep_diving.data.Coin;
import amit_yoav.deep_diving.data.Gun;
import amit_yoav.deep_diving.data.Life;
import amit_yoav.deep_diving.data.MainCharacter;
import amit_yoav.deep_diving.data.Shield;
import amit_yoav.deep_diving.data.StageLabel;
import amit_yoav.deep_diving.utilities.AsyncHandler;
import amit_yoav.deep_diving.utilities.CollisionUtil;
import amit_yoav.deep_diving.utilities.MillisecondsCounter;
import amit_yoav.deep_diving.utilities.Util;

import static amit_yoav.deep_diving.GameViewActivity.canShoot;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 *
 * View of the game.
 * It shows an undersea background with sea objects moving around
 */
public class GameView extends View {

    /*
     * View measurement values
     */
    public static final float WATER_SPEED = 0.3f, SAND_SPEED = 1.5f;
    public static float screenWidth, screenHeight, screenSand;

    /*
     * Draw objects
     */
    private Background waterBackground, sandBackground;
    private BackgroundObject[] objects;
    private Character[] characters;
    private MainCharacter mainChar;
    private Coin coin;
    private Life life;
    private Shield shield;
    private Gun gun;
    private Arrow arrow;
    private StageLabel[] stageLabels;
    private final int newRecordIndex = 10;
    private int mainCharResource, mainCharGunResource;
    private final int octopusIndex = 0, piranhaIndex = 9,
            whiteSharkIndex = 11, hammerSharkIndex = 10;

    /*
     * Other
     */
    public static boolean hit, isDark; // arrow hits character
    private Paint shootingCirclePaint = new Paint();
    private Vibrator vibrator;
    private GoogleApiClient mGoogleApiClient;


    /*
     * Stage related types
     */
    private int currentStage, mobsStartIndex;
    private int[] stageMobs = {3,4,5,6,7,7,8,9,10,/*11*/};         //Final Version
    //    private int[] stageMobs = {11,3,3,4,5,6,7,8,9/*,10,11,12,13*/}; //DEBUG
    public static boolean stagePassed = true;
    private boolean isStagedPlayedSound;

    /*
     * Score related types
     */
    public static int score;
    private boolean scoreChanged = true, isBestScoreUsed;
    private int bestScore;
    private Rect scoreRect = new Rect();
    private Paint scorePaint = new Paint(), alphaLifePaint = new Paint();
    public static StringBuilder sbScore = Util.acquireStringBuilder();

    /*
     * Time management
     */
    MillisecondsCounter protectCounter = new MillisecondsCounter();
    MillisecondsCounter shieldCounter = new MillisecondsCounter();
    MillisecondsCounter shieldBlinkCounter = new MillisecondsCounter();
    private boolean stopTimeFlag = true;

    /*
     * Background runnable updating each object on the view
     */
    Runnable updater = new Runnable() {
        @Override
        public void run() {
            while (GameViewActivity.gameRunning) {
                // game resumes, we want to resume the time
                if(!GameViewActivity.gamePaused && !stopTimeFlag) {
                    stopTime(false);
                    stopTimeFlag = true;
                }
                if (screenWidth != 0 && !GameViewActivity.gamePaused && stopTimeFlag) {
                    waterBackground.update();
                    sandBackground.update();
                    arrow.update();
                    for (int i = mobsStartIndex; i < stageMobs[currentStage]; i++) characters[i].update();
                    for (BackgroundObject ob : objects) ob.update();
                    stageLabels[currentStage].update();
                    stageLabels[newRecordIndex].update();
                    mainChar.update();
                    updateMainCharVulnerability();
                    shield.update();
                    coin.update();
                    life.update();
                    gun.update();
                    detectCollisions();
                    continue; // no need to go down..
                }
                // game stopped, we wanna stop the time
                if(GameViewActivity.gamePaused && stopTimeFlag) {
                    stopTime(true);
                    stopTimeFlag = false;
                }
            }
        }
    };


    /*
     * Achievements
     */
    private String achievementIdCoin = getResources().getString(R.string.achievement_beginner_collector);
    private String nextAchievementIdCoin = getResources().getString(R.string.achievement_amateur_collector);

    private String achievementIdShield = getResources().getString(R.string.achievement_defender);
    private String nextAchievementIdShield  = getResources().getString(R.string.achievement_protector);

    private String achievementIdLife = getResources().getString(R.string.achievement_life_saver);
    private String nextAchievementIdLife  = getResources().getString(R.string.achievement_life_expert);

    private String achievementIdFish = getResources().getString(R.string.achievement_fisherman);
    private String nextAchievementIdFish  = getResources().getString(R.string.achievement_expert_fisherman);

    private boolean firstTimeCoin = true, firstTimeShield = true, firstTimeLife = true,
            firstTimeFish = true, initAchs;

    private AchievementClass achievementClass = new AchievementClass();
    static final int COIN = 1;
    static final int FISH = 2;
    static final int SHIELD = 3;
    static final int LIFE = 4;
    @Retention(CLASS)
    @IntDef({
            COIN,
            FISH,
            SHIELD,
            LIFE
    })
    @interface AchCollectorKind {}

    private @GameView.AchCollectorKind int achCollectorKind;



    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);

        stagePassed = true;
        isDark = false; //isDark is static, so we have to initialize it back to false
        vibrator = ((GameViewActivity) context).vibrator;

        bestScore = ((GameViewActivity) context).getBestScore();

        mainCharResource = ((GameViewActivity) context).getMainCharResource();

        switch(mainCharResource) {
            case R.drawable.black_diver:
                mainCharGunResource = R.drawable.black_diver_gun;
                break;
            case R.drawable.magenta_diver:
                mainCharGunResource = R.drawable.magenta_diver_gun;
                break;
            case R.drawable.pink_diver:
                mainCharGunResource = R.drawable.pink_diver_gun;
                break;
            default: break;
        }

        initDrawObjects();

        mGoogleApiClient = ((GameViewActivity) context).mGoogleApiClient;

        initAchs();

        initPaints();

        updateScore(0);

        runUpdater();
    }


    private void initDrawObjects() {
        waterBackground = new Background(BitmapFactory.decodeResource(getResources(), R.drawable.background_water), WATER_SPEED, true);
        sandBackground = new Background(BitmapFactory.decodeResource(getResources(), R.drawable.background_sand), SAND_SPEED, false);
        mainChar = MainCharacter.prepareMainChar(BitmapFactory.decodeResource(getResources(), mainCharResource),
                BitmapFactory.decodeResource(getResources(), mainCharGunResource));
        characters = Character.prepareCharacters(
                BitmapFactory.decodeResource(getResources(), R.drawable.fish_green),
                BitmapFactory.decodeResource(getResources(), R.drawable.fish_shark),
                BitmapFactory.decodeResource(getResources(), R.drawable.fish_piranha),
                BitmapFactory.decodeResource(getResources(), R.drawable.fish_white_shark),
                BitmapFactory.decodeResource(getResources(), R.drawable.fish_red),
                BitmapFactory.decodeResource(getResources(), R.drawable.fish_gold),
                BitmapFactory.decodeResource(getResources(), R.drawable.fish_parrot),
                BitmapFactory.decodeResource(getResources(), R.drawable.fish_dog),
                BitmapFactory.decodeResource(getResources(), R.drawable.fish_lion),
                BitmapFactory.decodeResource(getResources(), R.drawable.fish_sword),
                BitmapFactory.decodeResource(getResources(), R.drawable.fish_cat),
                BitmapFactory.decodeResource(getResources(), R.drawable.fish_octopus_swim),
                BitmapFactory.decodeResource(getResources(), R.drawable.fish_octopus_attack),
                BitmapFactory.decodeResource(getResources(), R.drawable.octopus_ink));
        objects = BackgroundObject.prepareBackgroundObjects(
                BitmapFactory.decodeResource(getResources(), R.drawable.bubbles),
                BitmapFactory.decodeResource(getResources(), R.drawable.fishes_background));
        coin = Coin.prepareCoin(BitmapFactory.decodeResource(getResources(), R.drawable.coin));
        life = Life.prepareLife(BitmapFactory.decodeResource(getResources(), R.drawable.life));
        gun = Gun.prepareGun(BitmapFactory.decodeResource(getResources(), R.drawable.gun));
        arrow = Arrow.prepareArrow(BitmapFactory.decodeResource(getResources(), R.drawable.arrow));
        shield = Shield.prepareShield(BitmapFactory.decodeResource(getResources(), R.drawable.shield), mainChar.getBody());
        stageLabels = StageLabel.prepareStageLabels(BitmapFactory.decodeResource(getResources(), R.drawable.stage_labels));
    }

    private void initAchs() {

        initAchs = true;

        achCollectorKind = COIN;
        while(firstTimeCoin) Games.Achievements.load(mGoogleApiClient, true).setResultCallback(achievementClass);

        achCollectorKind = SHIELD;
        while(firstTimeShield) Games.Achievements.load(mGoogleApiClient, true).setResultCallback(achievementClass);

        achCollectorKind = LIFE;
        while(firstTimeLife) Games.Achievements.load(mGoogleApiClient, true).setResultCallback(achievementClass);

        achCollectorKind = FISH;
        while(firstTimeFish) Games.Achievements.load(mGoogleApiClient, true).setResultCallback(achievementClass);

        initAchs = false;
    }

    private void initPaints() {
        scorePaint.setTextSize(60);
        scorePaint.setAntiAlias(true);
        scorePaint.setTypeface(Typeface.DEFAULT_BOLD);
        scorePaint.setColor(0xff_dd_c4_52);
        alphaLifePaint.setAlpha(50);
        shootingCirclePaint.setColor(0xff_ff_00_00);
        shootingCirclePaint.setAntiAlias(true);
    }

    private void updateScore(int newScore) {
        score = newScore;
        sbScore.setLength(0);
        sbScore.append(String.valueOf(score)).append(" x");
        scoreChanged = true;
    }

    void runUpdater() {
        AsyncHandler.post(updater);
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        screenWidth = getWidth();
        screenHeight = getHeight();
        screenSand = screenHeight/5;
        coin.setScorePosition(screenWidth);
        life.setPoint(coin);
        stageLabels[currentStage].setToPopulate(true);
        super.onSizeChanged(w, h, oldw, oldh);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        waterBackground.draw(canvas);
        // drawing small fishes (start after bubbles index)
        for (int i = BackgroundObject.BUBBLE_LENGTH; i < objects.length; i++) objects[i].draw(canvas);
        coin.draw(canvas);
        arrow.draw(canvas);
        for (int i = mobsStartIndex; i < stageMobs[currentStage]; i++) characters[i].draw(canvas);
        mainChar.draw(canvas);
        shield.draw(canvas);
        // we want bubbles to come behind the sand
        for (int i = 0; i < BackgroundObject.BUBBLE_LENGTH; i++) objects[i].draw(canvas);
        sandBackground.draw(canvas);
        life.draw(canvas);
        gun.draw(canvas);

        drawScore(canvas);
        drawLife(canvas);
        if(mainChar.hasGun) drawShootingButton(canvas);

        if(stagePassed && stageLabels[currentStage].canDraw) {
            if(currentStage != 0 && !isStagedPlayedSound) {
                MainActivity.soundEffectsUtil.play(R.raw.level_complete);
                isStagedPlayedSound = true;
            }
        }
        stageLabels[currentStage].draw(canvas);
        if(stageLabels[newRecordIndex].canDraw) stageLabels[newRecordIndex].draw(canvas);
        postInvalidateOnAnimation();
    }

    private void drawShootingButton(Canvas canvas) {
        shootingCirclePaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(50, 50, 20, shootingCirclePaint);
        shootingCirclePaint.setStyle(Paint.Style.STROKE);
        shootingCirclePaint.setStrokeWidth(4);
        canvas.drawCircle(50, 50, 25, shootingCirclePaint);
        canvas.drawCircle(50, 50, 33, shootingCirclePaint);
    }

    private void drawScore(Canvas canvas) {
        if(scoreChanged) {
            scorePaint.getTextBounds(sbScore.toString(), 0, sbScore.length(), scoreRect);
            scoreChanged = false;
        }
        canvas.drawText(sbScore.toString(), screenWidth - scoreRect.width() - coin.getWidth()*1.5f,
                scoreRect.height() + coin.getWidth()/3, scorePaint);
    }

    private void drawLife(Canvas canvas) {
        if(life.getLife() == 3) {
            canvas.drawBitmap(life.bitmap, life.lifePoint.x, life.lifePoint.y, null);
            canvas.drawBitmap(life.bitmap, life.lifePoint.x - coin.getWidth(), life.lifePoint.y, null);
            canvas.drawBitmap(life.bitmap, life.lifePoint.x - coin.getWidth()*2, life.lifePoint.y, null);
        } else if(life.getLife() == 2) {
            canvas.drawBitmap(life.bitmap, life.lifePoint.x, life.lifePoint.y, null);
            canvas.drawBitmap(life.bitmap, life.lifePoint.x - coin.getWidth(), life.lifePoint.y, null);
            canvas.drawBitmap(life.bitmap, life.lifePoint.x - coin.getWidth()*2, life.lifePoint.y, alphaLifePaint);
        } else if(life.getLife() == 1){
            canvas.drawBitmap(life.bitmap, life.lifePoint.x, life.lifePoint.y, null);
            canvas.drawBitmap(life.bitmap, life.lifePoint.x - coin.getWidth(), life.lifePoint.y, alphaLifePaint);
            canvas.drawBitmap(life.bitmap, life.lifePoint.x - coin.getWidth()*2, life.lifePoint.y, alphaLifePaint);
        } else if(life.getLife() == 0) {
            ((GameViewActivity) getContext()).gameOver(score);
            life.setLife(-1);
        }
    }

    void detectCollisions() {
        for (int i = mobsStartIndex; i < stageMobs[currentStage]; i++) {
             if (mainChar.canGetHit && characters[i].populated && !characters[i].killed &&
                    CollisionUtil.isCollisionDetected(characters[i], mainChar)) {
                vibrator.vibrate(300);
                MainActivity.soundEffectsUtil.play(R.raw.hit);
                mainChar.canGetHit = false;
                life.setLife(life.getLife() == 0 ? 0 : life.getLife() - 1);
            }
            if (arrow.populated && CollisionUtil.isCollisionDetected(characters[i], arrow)) {
                hit = true;
                characters[i].killed = true;
                MainActivity.soundEffectsUtil.play(R.raw.killed);

                if(isSignedIn()) {
                    actionAchievement(INC, achievementIdFish);
                    achCollectorKind = FISH;
                    Games.Achievements.load(mGoogleApiClient, true).setResultCallback(achievementClass);
                    switch(i) {
                        case octopusIndex:
                            actionAchievement(UNLOCK, getResources().getString(R.string.achievement_kill_octopus));
                            break;
                        case piranhaIndex:
                            actionAchievement(UNLOCK, getResources().getString(R.string.achievement_kill_pirahna));
                            break;
                        case hammerSharkIndex:
                            actionAchievement(UNLOCK, getResources().getString(R.string.achievement_kill_hammer_shark));
                            break;
                        case whiteSharkIndex:
                            actionAchievement(UNLOCK, getResources().getString(R.string.achievement_kill_the_great_white_shark));
                            break;
                        default: break;
                    }
                }
            }
        }
        if(CollisionUtil.isCollisionDetected(coin, mainChar)) {
            if(!coin.isCollected()) {
                updateScore(score+1); // could do score++ but the function makes more sense like that
                MainActivity.soundEffectsUtil.play(R.raw.coin_collected);

                if(!isBestScoreUsed && score > bestScore) bestScore();
                if( (score == (currentStage+1) * 10) && (currentStage < 8) ) levelUp();

                if(isSignedIn()) {
                    actionAchievement(INC, achievementIdCoin);
                    achCollectorKind = COIN;
                    Games.Achievements.load(mGoogleApiClient, false).setResultCallback(achievementClass);
                }
            }
            coin.collected();
        }
        if(life.populated && CollisionUtil.isCollisionDetected(life, mainChar)) {
            MainActivity.soundEffectsUtil.play(R.raw.extra_life);
            life.setLife( (life.getLife() == 3) ? 3 : life.getLife()+1 );
            life.collected();

            if(isSignedIn()) {
                actionAchievement(INC, achievementIdLife);
                achCollectorKind = LIFE;
                Games.Achievements.load(mGoogleApiClient, true).setResultCallback(achievementClass);
            }
        }
        if(gun.populated && CollisionUtil.isCollisionDetected(gun, mainChar)) {
            MainActivity.soundEffectsUtil.play(R.raw.gun_collect);
            mainChar.setGun(gun, arrow);
            gun.collected();
            canShoot = true;
        }
        if(shield.populated && CollisionUtil.isCollisionDetected(shield, mainChar)) {
            MainActivity.soundEffectsUtil.play(R.raw.shield);
            shield.collected();
            mainChar.hasShield = true;
            mainChar.canGetHit = false;

            if(isSignedIn()) {
                actionAchievement(INC, achievementIdShield);
                achCollectorKind = SHIELD;
                Games.Achievements.load(mGoogleApiClient, true).setResultCallback(achievementClass);
            }
        }
    }

    private void updateMainCharVulnerability() {
        if(!mainChar.canGetHit) {
            if (mainChar.hasShield) {
                mainChar.makeVisible(); // if we got shield while we were blinking..
                if (shieldCounter.timePassed(7000)) {
                    mainChar.hasShield = false;
                    mainChar.canGetHit = true;
                    shield.restart();
                    protectCounter.restartCount();
                    shieldBlinkCounter.restartCount();
                } else if (shieldBlinkCounter.timePassed(5000)) {
                    shield.blink = true;
                }
            } // we're not hasShield, but we cannot get hit for 2sec after hit a fish
            else if (protectCounter.timePassed(2000)) { // after 2 seconds can get hit again
                mainChar.makeVisible();
                mainChar.canGetHit = true;
            } else mainChar.blink();
        }
    }

    private void bestScore() {
        isBestScoreUsed = true;
        MainActivity.soundEffectsUtil.play(R.raw.new_record);
        stageLabels[newRecordIndex].setToPopulate(true);
        stageLabels[newRecordIndex].canDraw = true;
    }

    private void levelUp() {
        stagePassed = true;
        currentStage++;
        if(currentStage == 4) {
            MainActivity.musicPlayer.switchMusic(R.raw.music_2);
            mobsStartIndex += 2;
        }
        if(currentStage == 8) {
            MainActivity.musicPlayer.switchMusic(R.raw.music_3);
            mobsStartIndex += 2;
        }
        isStagedPlayedSound = isDark = characters[Character.octopusIndex].term = false;
        for (int i = mobsStartIndex; i < stageMobs[currentStage]; i++) {
            characters[i].setFirstPopulation(true);
            characters[i].restartPopulation();
        }
        stageLabels[currentStage].setToPopulate(true);

        if(currentStage > 2 && isSignedIn()) {
            switch (currentStage) {
                case 3:
                    actionAchievement(UNLOCK, getResources().getString(R.string.achievement_complete_stage_3));
                    break;
                case 4:
                    actionAchievement(UNLOCK, getResources().getString(R.string.achievement_complete_stage_4));
                    break;
                case 5:
                    actionAchievement(UNLOCK, getResources().getString(R.string.achievement_complete_stage_5));
                    break;
                case 6:
                    actionAchievement(UNLOCK, getResources().getString(R.string.achievement_complete_stage_6));
                    break;
                case 7:
                    actionAchievement(UNLOCK, getResources().getString(R.string.achievement_complete_stage_7));
                    break;
                case 8:
                    actionAchievement(UNLOCK, getResources().getString(R.string.achievement_complete_stage_8));
                    break;
                case 9:
                    actionAchievement(UNLOCK, getResources().getString(R.string.achievement_complete_stage_9));
                    break;
                case 10:
                    actionAchievement(UNLOCK, getResources().getString(R.string.achievement_complete_stage_10));
                    break;
                default:
                    break;
            }
        }
    }

    public void stopTime(boolean isPaused) {
        gun.stopTime(isPaused);
        life.stopTime(isPaused);
        coin.stopTime(isPaused);
        protectCounter.stopTime(isPaused);
        shieldCounter.stopTime(isPaused);
        shieldBlinkCounter.stopTime(isPaused);
        for (int i = mobsStartIndex; i < stageMobs[currentStage]; i++)
            characters[i].stopTime(isPaused);
    }


    public boolean isSignedIn() {
        if(mGoogleApiClient == null)     Log.d("AMIT: ", "IT IS NULL");
        else if(mGoogleApiClient.isConnected()) Log.d("AMIT: ", "IT IS CONNECTED...!");
        return (mGoogleApiClient != null && mGoogleApiClient.isConnected());
    }


    static final int INC = 1;
    static final int REVEAL = 2;
    static final int UNLOCK = 3;
    @Retention(CLASS)
    @IntDef({
            INC,
            REVEAL,
            UNLOCK
    })
    @interface ActionKind {}

    public void actionAchievement(@ActionKind int code, String achievementId) {
        switch(code) {
            case INC:
                Games.Achievements.increment(mGoogleApiClient, achievementId, 1);
                break;

            case REVEAL:
                Games.Achievements.reveal(mGoogleApiClient, achievementId);
                break;

            case UNLOCK:
                Games.Achievements.unlock(mGoogleApiClient, achievementId);
                break;

            default: break;
            }
        }

    private class AchievementClass implements ResultCallback<Achievements.LoadAchievementsResult> {

        @Override
        public void onResult(@NonNull Achievements.LoadAchievementsResult result) {
            AchievementBuffer aBuffer = result.getAchievements();

            for (Achievement ach : aBuffer) {
                if (achievementIdCoin.equals(ach.getAchievementId())) {
                    if (ach.getState() == Achievement.STATE_UNLOCKED) {
                        switch(achCollectorKind) {
                            case COIN:
                                actionAchievement(REVEAL, nextAchievementIdCoin);

                                if(achievementIdCoin.equals(getResources().getString(R.string.achievement_beginner_collector))) {
                                    achievementIdCoin = nextAchievementIdCoin;
                                    nextAchievementIdCoin = getResources().getString(R.string.achievement_pro_collector);
                                }
                                else if(achievementIdCoin.equals(getResources().getString(R.string.achievement_amateur_collector))) {
                                    achievementIdCoin = nextAchievementIdCoin;
                                    nextAchievementIdCoin = getResources().getString(R.string.achievement_expert_collector);
                                }
                                else if(achievementIdCoin.equals(getResources().getString(R.string.achievement_pro_collector))) {
                                    achievementIdCoin = nextAchievementIdCoin;
                                    nextAchievementIdCoin = getResources().getString(R.string.achievement_treasure_collector);
                                }
                                else if(achievementIdCoin.equals(getResources().getString(R.string.achievement_expert_collector))) {
                                    achievementIdCoin = nextAchievementIdCoin;
                                    nextAchievementIdCoin = getResources().getString(R.string.achievement_deep_diver_collector);
                                }
                                else if(achievementIdCoin.equals(getResources().getString(R.string.achievement_treasure_collector))) {
                                    achievementIdCoin = nextAchievementIdCoin;
                                }
                                break;

                            case SHIELD:
                                actionAchievement(REVEAL, nextAchievementIdShield);
                                if(achievementIdShield.equals(getResources().getString(R.string.achievement_defender))) {
                                    achievementIdShield = nextAchievementIdShield;
                                    nextAchievementIdShield = getResources().getString(R.string.achievement_invincible);
                                }
                                else if(achievementIdShield.equals(getResources().getString(R.string.achievement_protector))) {
                                    achievementIdShield = nextAchievementIdShield;
                                }
                                break;

                            case LIFE:
                                actionAchievement(REVEAL, nextAchievementIdLife);
                                if(achievementIdLife.equals(getResources().getString(R.string.achievement_life_saver))) {
                                    achievementIdLife = nextAchievementIdLife;
                                    nextAchievementIdLife = getResources().getString(R.string.achievement_survivor);
                                }
                                else if(achievementIdLife.equals(getResources().getString(R.string.achievement_life_expert))) {
                                    achievementIdLife = nextAchievementIdLife;
                                }
                                break;

                            case FISH:
                                actionAchievement(REVEAL, nextAchievementIdFish);
                                if(achievementIdFish.equals(getResources().getString(R.string.achievement_fisherman))) {
                                    achievementIdFish = nextAchievementIdFish;
                                    nextAchievementIdFish = getResources().getString(R.string.achievement_killer_diver);
                                }
                                else if(achievementIdFish.equals(getResources().getString(R.string.achievement_expert_fisherman))) {
                                    achievementIdFish = nextAchievementIdFish;
                                }
                                break;
                            default: break;
                        }
                    }
                    else if(initAchs){
                        if(firstTimeFish) firstTimeFish = false;
                        if(firstTimeCoin) firstTimeCoin = false;
                        if(firstTimeLife) firstTimeLife = false;
                        if(firstTimeShield) firstTimeShield = false;
                    }
                    aBuffer.release();
                    break;
                }
            }
        }
    }
}