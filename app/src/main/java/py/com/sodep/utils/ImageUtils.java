package py.com.sodep.utils;

import android.content.Context;
import android.graphics.Point;
import android.view.Display;
import android.view.WindowManager;


/**
 * Created by afeltes on 6/29/15.
 */
public class ImageUtils {

    private static final String DEFAULT_FONT_SIZE = "2";
    private static final String HD_FONT_SIZE = "3";
    private static final String TABLET_FONT_SIZE = "4";
    public static final double HD_VERTICAL_RESOLUTION = 1280;
    public static final double TABLET_VERTICAL_RESOLUTION = 1920;


    private ImageUtils() {
    }


    public static Point getScreenSizeInPixels(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size;
    }

    public static String getHtmlScaledFontSize(Context context) {
        String fontSize = DEFAULT_FONT_SIZE;
        Point screenSize = getScreenSizeInPixels(context);
        if (screenSize != null) {
            if (TABLET_VERTICAL_RESOLUTION >= screenSize.y) {
                fontSize = TABLET_FONT_SIZE;
            } else if (HD_VERTICAL_RESOLUTION >= screenSize.y) {
                fontSize = HD_FONT_SIZE;
            }
        }
        return fontSize;
    }
}
