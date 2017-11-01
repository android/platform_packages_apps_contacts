package com.android.contacts.interactions;

import android.graphics.Point;

/**
 * Singleton class to keep track of where the user last touched the screen.
 *
 * Used to pass on to the InCallUI for animation.
 */
public class TouchPointManager {
    public static final String TOUCH_POINT = "touchPoint";

    private static TouchPointManager sInstance = new TouchPointManager();

    private Point mPoint = new Point();

    /**
     * Private constructor.  Instance should only be acquired through getInstance().
     */
    private TouchPointManager() {
    }

    public static TouchPointManager getInstance() {
        return sInstance;
    }

    public Point getPoint() {
        return mPoint;
    }

    public void setPoint(int x, int y) {
        mPoint.set(x, y);
    }

    /**
     * When a point is initialized, its value is (0,0). Since it is highly unlikely a user will
     * touch at that exact point, if the point in TouchPointManager is (0,0), it is safe to assume
     * that the TouchPointManager has not yet collected a touch.
     *
     * @return True if there is a valid point saved. Define a valid point as any point that is
     * not (0,0).
     */
    public boolean hasValidPoint() {
        return mPoint.x != 0 || mPoint.y != 0;
    }
}
