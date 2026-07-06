package com.wechatauto.forwarder;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * Detects whether the phone is connected to a car head unit (Android Auto
 * projection or Android Automotive OS).
 *
 * This mirrors {@code androidx.car.app.connection.CarConnection} without pulling in
 * the whole Car App Library: it queries the car host's content provider directly.
 * Values and column name are taken from the AndroidX source.
 *
 * NOTE: On Android 11+ the app must declare visibility of this provider authority in
 * a {@code <queries>} manifest element, otherwise the query returns null (which we
 * treat as "not connected").
 */
public final class CarConnectionHelper {

    public static final String CAR_CONNECTION_AUTHORITY = "androidx.car.app.connection";

    /** Broadcast the car host sends when the connection state changes. */
    public static final String ACTION_CAR_CONNECTION_UPDATED =
            "androidx.car.app.connection.action.CAR_CONNECTION_UPDATED";

    private static final String CAR_CONNECTION_STATE = "CarConnectionState";

    private static final Uri PROJECTION_HOST_URI = new Uri.Builder()
            .scheme("content")
            .authority(CAR_CONNECTION_AUTHORITY)
            .build();

    public static final int CONNECTION_TYPE_NOT_CONNECTED = 0;
    public static final int CONNECTION_TYPE_NATIVE = 1;      // Android Automotive OS
    public static final int CONNECTION_TYPE_PROJECTION = 2;  // Android Auto (projected)

    private CarConnectionHelper() {}

    /** Returns the current car connection type, or NOT_CONNECTED on any error. */
    public static int getConnectionType(Context context) {
        try (Cursor c = context.getContentResolver().query(
                PROJECTION_HOST_URI,
                new String[]{CAR_CONNECTION_STATE},
                null, null, null)) {
            if (c == null) {
                return CONNECTION_TYPE_NOT_CONNECTED;
            }
            int col = c.getColumnIndex(CAR_CONNECTION_STATE);
            if (col < 0 || !c.moveToFirst()) {
                return CONNECTION_TYPE_NOT_CONNECTED;
            }
            return c.getInt(col);
        } catch (Exception e) {
            // Provider missing / not visible / any transient failure.
            return CONNECTION_TYPE_NOT_CONNECTED;
        }
    }

    /** True when connected to a car head unit (Android Auto projection or Automotive OS). */
    public static boolean isCarConnected(Context context) {
        return getConnectionType(context) != CONNECTION_TYPE_NOT_CONNECTED;
    }
}
