package amit_yoav.deep_diving;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import amit_yoav.deep_diving.data.Background;
import amit_yoav.deep_diving.data.BackgroundObject;
import amit_yoav.deep_diving.data.Character;
import amit_yoav.deep_diving.data.Coin;
import amit_yoav.deep_diving.data.Life;
import amit_yoav.deep_diving.data.MainCharacter;
import amit_yoav.deep_diving.data.StageLabel;
import amit_yoav.deep_diving.utilities.AsyncHandler;
import amit_yoav.deep_diving.utilities.CollisionUtil;
import amit_yoav.deep_diving.utilities.MillisecondsCounter;
import amit_yoav.deep_diving.utilities.Util;

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
    private StageLabel[] stageLabels;
    private final int newRecordIndex = 10;

    /*
     * Stage related types
     */
    private int currentStage;
    private int[] stageMobs = {3,4,5,6,7,7,8,9,10,11};         //Final Version
    //    private int[] stageMobs = {11,3,3,4,5,6,7,8,9/*,10,11,12,13*/}; //DEBUG
    public static boolean stagePassed = true;
    private boolean isStagedPlayedSound;

    /*
     * Score related types
     */
    public static int score;
    private boolean scoreChanged = true, isBestScoreUsed;
    private int bestScore, mainCharResource;
    private Rect scoreRect = new Rect();
    private Paint scorePaint = new Paint(), alphaLifePaint = new Paint();
    public static StringBuilder sbScore = Util.acquireStringBuilder();

    /*
     * Time management
     */
    MillisecondsCounter mCounter = new MillisecondsCounter();
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
                    for (int i = 0; i < stageMobs[currentStage]; i++) characters[i].update();
                    for (BackgroundObject ob : objects) ob.update();
                    stageLabels[currentStage].update();
                    stageLabels[newRecordIndex].update();
                    mainChar.update();
                    coin.update();
                    life.update();
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


    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);

        stagePassed = true;

        bestScore = ((GameViewActivity) context).getBestScore();

        mainCharResource = ((GameViewActivity) context).getMainCharResource();

        initDrawObjects();

        initPaints();

        updateScore(0);

        runUpdater();
    }

    private void initDrawObjects() {
        waterBackground = new Background(BitmapFactory.decodeResource(getResources(), R.drawable.background_water), WATER_SPEED);
        sandBackground = new Background(BitmapFactory.decodeResource(getResources(), R.drawable.background_sand), SAND_SPEED);
        mainChar = MainCharacter.prepareMainChar(BitmapFactory.decodeResource(getResources(), mainCharResource));
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
                BitmapFactory.decodeResource(getResources(), R.drawable.fish_cat));
        objects = BackgroundObject.prepareBackgroundObjects(
                BitmapFactory.decodeResource(getResources(), R.drawable.bubbles),
                BitmapFactory.decodeResource(getResources(), R.drawable.fishes_background));
        coin = Coin.prepareCoin(BitmapFactory.decodeResource(getResources(), R.drawable.coin));
        life = Life.prepareLife(BitmapFactory.decodeResource(getResources(), R.drawable.life));
        stageLabels = StageLabel.prepareStageLabels(BitmapFactory.decodeResource(getResources(), R.drawable.stage_labels));
    }

    private void initPaints() {
        scorePaint.setTextSize(60);
        scorePaint.setAntiAlias(true);
        scorePaint.setTypeface(Typeface.DEFAULT_BOLD);
        scorePaint.setColor(0xff_dd_c4_52);
        alphaLifePaint.setAlpha(50);
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
        for (int i = 0; i < stageMobs[currentStage]; i++) characters[i].draw(canvas);
        mainChar.draw(canvas);
        // we want bubbles to come behind the sand
        for (int i = 0; i < BackgroundObject.BUBBLE_LENGTH; i++) objects[i].draw(canvas);
        sandBackground.draw(canvas);
        life.draw(canvas);

        drawScore(canvas);
        drawLife(canvas);
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
        for (int i = 0; i < stageMobs[currentStage]; i++) {
            if(mainChar.canGetHit()) {
                if(CollisionUtil.isCollisionDetected(characters[i], mainChar)) {
                    MainActivity.soundEffectsUtil.play(R.raw.hit);
                    mainChar.setCanGetHit(false);
                    life.setLife( life.getLife() == 0 ? 0 : life.getLife()-1 );
                }
            } // after 2 seconds can get hit again
            else if(mCounter.timePassed(2000)) {
                mainChar.makeVisible();
                mainChar.setCanGetHit(true);
            }
            else mainChar.blink();
        }
        if(CollisionUtil.isCollisionDetected(coin, mainChar)) {
            if(!coin.isCollected()) {
                updateScore(score+1); // could do score++ but the function makes more sense like that
                MainActivity.soundEffectsUtil.play(R.raw.coin_collected);

                if(!isBestScoreUsed && score > bestScore) bestScore();
                if( (score == (currentStage+1) * 10) && (currentStage < 9) ) levelUp();
            }
            coin.collected();
        }
        if(CollisionUtil.isCollisionDetected(life, mainChar)) {
            MainActivity.soundEffectsUtil.play(R.raw.extra_life);
            life.setLife( (life.getLife() == 3) ? 3 : life.getLife()+1 );
            life.collected();
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
        }

        if(currentStage == 8) {
            MainActivity.musicPlayer.switchMusic(R.raw.music_3);
        }

        isStagedPlayedSound = false;
        for (int i = 0; i < stageMobs[currentStage]; i++) {
            characters[i].setFirstPopulation(true);
            characters[i].restartPopulation();
        }
        stageLabels[currentStage].setToPopulate(true);
    }

    public void stopTime(boolean isPaused) {
        coin.stopTime(isPaused);
        for (int i = 0; i < stageMobs[currentStage]; i++) {
            characters[i].stopTime(isPaused);
        }
    }
}