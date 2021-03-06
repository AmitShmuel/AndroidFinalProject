package amit_yoav.deep_diving.data;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;

import amit_yoav.deep_diving.utilities.MillisecondsCounter;

import static amit_yoav.deep_diving.GameView.screenHeight;
import static amit_yoav.deep_diving.GameView.screenSand;
import static amit_yoav.deep_diving.GameView.screenWidth;
import static amit_yoav.deep_diving.GameViewActivity.gamePaused;
import static amit_yoav.deep_diving.GameViewActivity.rand;

/**
 * Gun
 * This class represents the Gun of the main character
 * populates a Gun item in which the main character can collect in order to shoot the fishes
 */

public class Gun extends GameObject implements Collidable{

    private Rect bodySrc;
    private RectF bodyDst = new RectF();

    private MillisecondsCounter populationCounter = new MillisecondsCounter();
    private boolean canDraw, collected, firstTime = true;
    public boolean populated;
    private float speed = 6;
    private long populationTime = 25000;

    public void setPopulationTime(long time) {this.populationTime = time;}
    public void setSpeed(float speed) {this.speed = speed;}

    public static Gun prepareGun(Bitmap bitmap) {
        Gun gun = new Gun();
        int gunWidth = bitmap.getWidth();
        int gunHeight = bitmap.getHeight();
        gun.setBitmap(bitmap);
        gun.setSize(gunWidth, gunHeight);
        gun.bodySrc = new Rect(0, 0, gunWidth, gunHeight);

        return gun;
    }

    @Override
    public void draw(Canvas canvas) {
        if(canDraw) {
            canvas.drawBitmap(bitmap, bodySrc, bodyDst, null);
            if(!gamePaused) bodyDst.offsetTo(bodyDst.left - speed, bodyDst.top);
        }
    }

    @Override
    public void update() {
        if(!collected && populationCounter.timePassed(populationTime)) {
            populate();
            canDraw = true;
        }
        // diver did not collect and the gun passes the screen, we want to restart the time.
        if(bodyDst.right < 0 && canDraw) {
            populationCounter.restartCount();
            populated = false;
        }

        if(firstTime) {
            bodyDst.set(-screenWidth, 0, -screenWidth+width, 0); // out of screen
            firstTime = false;
        }
    }

    private void populate() {
        float initY = rand.nextFloat()*(screenHeight-screenSand-height) + height;
        bodyDst.set(screenWidth, initY - height, screenWidth + width, initY);
        populated = true;
    }

    public void collected() {
        bodyDst.set(-screenWidth, 0, -screenWidth+width, 0); // out of screen
        collected = true;
        canDraw = false;
    }

    void restart() {
        populationCounter.restartCount();
        collected = false;
    }

    @Override
    public RectF getBody() {
        return bodyDst;
    }

    @Override
    public Bitmap getBitmap() {
        return bitmap;
    }

    public void stopTime(boolean isPaused) { populationCounter.stopTime(isPaused);}
}
