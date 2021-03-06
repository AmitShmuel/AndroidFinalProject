package amit_yoav.deep_diving.data;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import amit_yoav.deep_diving.MainActivity;
import amit_yoav.deep_diving.R;
import amit_yoav.deep_diving.utilities.MillisecondsCounter;

import static amit_yoav.deep_diving.GameView.isDark;
import static amit_yoav.deep_diving.GameView.screenHeight;
import static amit_yoav.deep_diving.GameView.screenSand;
import static amit_yoav.deep_diving.GameView.screenWidth;
import static amit_yoav.deep_diving.GameViewActivity.gamePaused;
import static amit_yoav.deep_diving.GameViewActivity.rand;

/**
 * Character
 * This class represents all moving fishes
 * which the user should avoid collide with.
 */
public class Character extends GameObject implements Collidable{

    private RectF bodyDst = new RectF();
    private Rect[] bodySrc;
    private float speed;
    private MillisecondsCounter frameCounter = new MillisecondsCounter();
    private MillisecondsCounter populateCounter = new MillisecondsCounter();
    private MillisecondsCounter firstPopulateCounter = new MillisecondsCounter();
    private int populateDuration, frameDuration, frame;
    private boolean firstPopulation = true, waitOnFirstPopulation;
    public boolean killed, populated;
    /*diagonal movement*/
    private boolean isMovingDiagonally, goingUp;
    /*octopus*/
    private float stopGoDown, left, top;
    private boolean isOctopus, drawInk, firstDraw, playSound;
    private Bitmap attackBitmap, swimBitmap, inkBitmap;
    private char alpha = 255;
    private Paint inkPaint;
    public boolean term; // a helpful flag which helps to determine if we draw the ink
    public static int octopusIndex = 10;

    private Character(float speed, int populateDuration) {
        this.speed = speed;
        this.populateDuration = populateDuration;
    }

    public void setPopulateDuration(int newPopulateDuration) {
        populateDuration = newPopulateDuration;
    }

    public static Character[] prepareCharacters(Bitmap greenFishBitmap,
                    Bitmap hammerFishBitmap,Bitmap piranhaFishBitmap,   Bitmap greatSharkFishBitmap,
                    Bitmap redFishBitmap,   Bitmap goldFishBitmap,      Bitmap parrotFishBitmap,
                    Bitmap dogFishBitmap,   Bitmap lionFishBitmap,      Bitmap swordFishBitmap,
                    Bitmap catFishBitmap,   Bitmap octopusSwimBitmap,   Bitmap octopusAttackBitmap,
                    Bitmap octopusInkBitmap, Bitmap angelFishBitmap, Bitmap clownFishBitmap,
                    Bitmap surgeonFishBitmap, Bitmap needleFishBitmap) {

        Character[] characters = new Character[]{
                new Character(4, 200)/*Gold Fish*/, //In stage 4 populate changes to 1000
                new Character(5.5f ,400)/*Dog Fish*/,   //In stage 4 populate changes to 1400
                new Character(6 ,600)/*Parrot Fish*/,   //In stage 8 populate changes to 1600
                new Character(5 ,800)/*Cat Fish*/,
                new Character(7, 1400)/*Lion Fish*/,
                new Character(7, 1200)/*Angel Fish - NEW*/,
                new Character(8, 1000)/*Clown Fish - NEW*/,
                new Character(9, 1100)/*Surgeon Fish - NEW*/,//7
                new Character(12, 2500)/*Needle Fish - NEW*/,
                new Character(8, 2000)/*Green fish*/, //9
                new Character(3.5f, 7000)/*Octopus*/, //10
                new Character(10, 2500)/*Sword Fish*/, //11
                new Character(11, 3000)/*Red Fish*/,
                new Character(15, 4000)/*Piranha Fish*/, //13
                new Character(16, 6000)/*Hammer Shark*/,
                new Character(21, 8000)/*Great White Shark*/
        };
        characters[octopusIndex].isOctopus = true;
        characters[octopusIndex].inkPaint = new Paint();
        characters[octopusIndex].swimBitmap = octopusSwimBitmap;
        characters[octopusIndex].attackBitmap = octopusAttackBitmap;
        characters[octopusIndex].inkBitmap = octopusInkBitmap;
        characters[7].isMovingDiagonally = characters[9].isMovingDiagonally = characters[11].isMovingDiagonally = characters[13].isMovingDiagonally = true;
        initSpecialFishes(characters, goldFishBitmap, 0, 5, 1, 60);
        initSpecialFishes(characters, dogFishBitmap, 1,5,1,60);
        initSpecialFishes(characters, parrotFishBitmap, 2, 5, 1, 60);
        initSpecialFishes(characters, catFishBitmap, 3, 5, 1, 60);
        initSpecialFishes(characters, lionFishBitmap, 4, 5, 1, 80);
        initSpecialFishes(characters, angelFishBitmap, 5, 5, 1, 50);  /*NEW*/
        initSpecialFishes(characters, clownFishBitmap, 6, 5, 1, 70); /*NEW*/
        initSpecialFishes(characters, surgeonFishBitmap, 7, 5, 1, 60);  /*NEW*/
        initSpecialFishes(characters, needleFishBitmap, 8, 5, 1, 100); /*NEW*/
        initSpecialFishes(characters, greenFishBitmap, 9, 6, 1, 50);
        initSpecialFishes(characters, octopusSwimBitmap, 10, 4, 4, 200);
        initSpecialFishes(characters, swordFishBitmap, 11, 4, 4, 100);
        initSpecialFishes(characters, redFishBitmap, 12, 5, 1, 100);
        initSpecialFishes(characters, piranhaFishBitmap, 13, 6, 1, 100);
        initSpecialFishes(characters, hammerFishBitmap, 14, 4, 4, 120);
        initSpecialFishes(characters, greatSharkFishBitmap, 15, 11, 1, 100);

        return characters;
    }


    private static void initSpecialFishes(Character[] characters,Bitmap bitmap,
                  int characterIndex, int rowFrames, int columnFrames, int frameDuration) {
        int charWidth = bitmap.getWidth() / rowFrames;
        int charHeight = bitmap.getHeight() / columnFrames;

        characters[characterIndex].setBitmap(bitmap);
        characters[characterIndex].setSize(charWidth, charHeight);
        characters[characterIndex].bodySrc = new Rect[rowFrames*columnFrames];
        characters[characterIndex].frameDuration = frameDuration;
        int frame = 0;
        for (int x = 0; x < rowFrames; x++) { // bitmap row
            for(int y = 0; y < columnFrames; y++) {
                characters[characterIndex].bodySrc[frame] = new Rect(x * charWidth, y * charHeight, (x + 1) * charWidth,(y+1) * charHeight);
                frame++;
            }
        }
    }


    @Override
    public void draw(Canvas canvas) {
        int save = canvas.save();

        if(!firstPopulation) { // just draw..
            /* Octopus
             * once all "AND" conditions apply, term becomes the cond which determines if we enter this if or not
             */
            if(term || (drawInk && isOctopus && !killed && bodyDst.bottom >= stopGoDown)) {
                if(firstDraw) {
                    left = bodyDst.left;
                    top = bodyDst.top;
                    firstDraw = false;
                    isDark = term = playSound = true;
                }
                canvas.drawBitmap(inkBitmap, left, top, inkPaint);
                inkPaint.setAlpha(--alpha);
                if(alpha == 0) drawInk = term = false;
            }

            if(killed) canvas.rotate(180, bodyDst.centerX(), bodyDst.centerY());
            try {
                canvas.drawBitmap(bitmap, bodySrc[frame], bodyDst, null);
            }
            catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
                frame = 0;
                System.out.println("speed =" +speed);
                System.out.println("population =" +populateDuration);
            }
            if (!gamePaused) {
                if(killed || (isOctopus && bodyDst.bottom < stopGoDown)) {
                    bodyDst.offsetTo(bodyDst.left, bodyDst.top + speed);
                }
                else {
                    if(isMovingDiagonally){
                        if(goingUp) {
                            bodyDst.offsetTo(bodyDst.left - speed, bodyDst.top - speed/10);
                        }
                        else bodyDst.offsetTo(bodyDst.left - speed, bodyDst.top + speed/10);
                    }
                    else bodyDst.offsetTo(bodyDst.left - speed, bodyDst.top);
                }
            }
        } // wait before populate (new stage)
        else if (!gamePaused && (waitOnFirstPopulation || firstPopulateCounter.timePassed(4000))) {
            // after 4 seconds we want to go inside and wait populateDuration time
            waitOnFirstPopulation = true;
            if(populateCounter.timePassed(populateDuration)) {
                firstPopulation = waitOnFirstPopulation = false;
            }
        }
        canvas.restoreToCount(save);
    }

    @Override
    public void update() {
        if(bodyDst.right < 0 || bodyDst.top > screenHeight) {
            populated = false;
            if(isOctopus) isDark = term = false; // octopus isn't on screen so background back to light
            if(populateCounter.timePassed(populateDuration)) populate();
        }
        if(isOctopus && bodyDst.bottom >= stopGoDown) {
            setBitmap(attackBitmap);
            speed = 4;
            frameDuration = 100;
        }
        if(playSound) {
            MainActivity.soundEffectsUtil.play(R.raw.drop_inks);
            playSound = false;
        }
        // change frame each frameDuration milliseconds for animation
        if(frameCounter.timePassed(frameDuration)) frame = (frame+1 == bodySrc.length ? 0 : ++frame);
    }

    private void populate() {
        if(isOctopus) {
            bodyDst.set(screenWidth - width*2, -height, screenWidth - width, 0);
            stopGoDown = rand.nextFloat()*(screenHeight - screenSand - height) + height;
            setBitmap(swimBitmap);
            frameDuration = 84;
            drawInk = firstDraw = true;
            speed = 1.5f;
            alpha = 255;
        } else {
            float initY = rand.nextFloat()*(screenHeight - screenSand - height) + height;
            bodyDst.set(screenWidth, initY - height, screenWidth + width, initY);
            if(isMovingDiagonally) goingUp = bodyDst.top > screenHeight / 2;
        }
        killed = false;
        populated = true;
    }

    public void stopTime(boolean isPaused) {
        populateCounter.stopTime(isPaused);
        firstPopulateCounter.stopTime(isPaused);
    }

    public void setFirstPopulation(boolean value) { firstPopulation = value; }

    public void restartPopulation() {
        populateCounter.restartCount();
        populate();
    }

    @Override
    public RectF getBody() {
        return bodyDst;
    }

    @Override
    public Bitmap getBitmap() {
        return bitmap;
    }
}