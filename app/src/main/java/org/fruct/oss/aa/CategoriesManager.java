package org.fruct.oss.aa;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

public class CategoriesManager {

    public static Uri left_uri;
    public static Uri right_uri;
    public static Uri forward_uri;
    public static Uri back_uri;
    public static Uri high_curb_uri;
    private static Uri welcome_uri;
    private static Uri unknown_obj_uri;
    private static Uri unknown_dir_uri;
    private static Uri goodbye_uri;
    private static Uri hotel_uri;
    private static Uri sight_uri;
    private static Uri museum_uri;

    private static final String packagename = "com.aa.audiblealert";

    public static final String LEFT = "left";
    public static final String RIGHT = "right";
    public static final String FORWARD = "forward";
    public static final String BACK= "back";
    public static final String HIGH_CURB = "high_curb";
    public static final String WELCOME = "welcome";
    public static final String GOODBYE = "goodbye";
    public static final String UNKNOWN_OBJECT = "unknown_obj";
    public static final String UNKNOWN_DIRECTION = "unknown_dir";
    public static final String MUSEUMS = "museums";
    public static final String HOTELS = "hotels";
    public static final String SIGHTS = "sights";

    public static final int ICON_HEIGHT= 40;
    public static final int ICON_WIDTH = 40;

    private static HashMap<String, Drawable> icons;
    private static HashMap<String, Uri> uris;
    private static HashMap<String, Integer> radii;
    private static boolean ready = false;

    private static final int DEFAULT_RADIUS = 30;

    public static void init(){
        if(icons != null && uris != null && radii != null && ready)
            return;

        uris = new HashMap<String, Uri>();
        icons = new HashMap<String, Drawable>();
        radii = new HashMap<String, Integer>();
        initUris();
        initRadii();
        ready = true;

    }

    private static void assertInit(){
        if(ready)
            return;
        init();
    }


    private static void initUris(){
        String folder = "android.resource://" + packagename + "/";
        left_uri = Uri.parse(folder + R.raw.left);
        right_uri = Uri.parse(folder + R.raw.right);
        back_uri = Uri.parse(folder + R.raw.back);
        forward_uri = Uri.parse(folder + R.raw.forward);
        high_curb_uri = Uri.parse(folder + R.raw.high_curb);
        welcome_uri = Uri.parse(folder + R.raw.welcome);
        unknown_obj_uri = Uri.parse(folder + R.raw.unknown_obj);
        unknown_dir_uri = Uri.parse(folder + R.raw.unknown_dir);
        goodbye_uri = Uri.parse(folder + R.raw.goodbye);
        hotel_uri = Uri.parse(folder + R.raw.hotel);
        sight_uri = Uri.parse(folder + R.raw.sight);
        museum_uri = Uri.parse(folder + R.raw.museum);

        uris.put(LEFT,left_uri);
        uris.put(RIGHT, right_uri);
        uris.put(BACK, back_uri);
        uris.put(FORWARD, forward_uri);
        uris.put(HIGH_CURB, high_curb_uri);
        uris.put(WELCOME, welcome_uri);
        uris.put(UNKNOWN_DIRECTION, unknown_dir_uri);
        uris.put(UNKNOWN_OBJECT, unknown_obj_uri);
        uris.put(GOODBYE, goodbye_uri);
        uris.put(HOTELS, hotel_uri);
        uris.put(SIGHTS, sight_uri);
        uris.put(MUSEUMS, museum_uri);

    }

    private static void initRadii(){
        radii.put(HOTELS, 30);
        radii.put(SIGHTS, 50);
        radii.put(MUSEUMS, 40);
    }

    public static int getRadiusForCategory(String category){
        Integer result;
        if((result = radii.get(category))== null)
            result = DEFAULT_RADIUS;
        return result;
    }



    public static Drawable getIconForCategory(String category){
        return icons.get(category.toLowerCase());
    }

    public static Uri getUriForDirection(String direction){
        Uri result = uris.get(direction.toLowerCase());
        if(result == null)
            result = uris.get(UNKNOWN_DIRECTION);
        return result;
    }

    public static Uri getUriForCategory(String category){
        Uri result = uris.get(category.toLowerCase());
        if(result == null)
            result = uris.get(UNKNOWN_OBJECT);
        return result;
    }


    /**
     * @param description
     * @return
     *
     *
     * Expecting this JSON:
     * {
     *"error": null,
     * "response": {
     * "description": {
     *  "description" : "hotels \ sights\ ...",
     *  "icon" : "icon_url"
     *   }}}
     */
    public static String checkDiscription(String description){
        String result = null;
        String iconUrl;
        try {
            JSONObject root = new JSONObject(description);
            JSONObject desc = root.getJSONObject("response")
                    .getJSONObject("description");

            result = desc.getString("description");
            iconUrl = desc.getString("icon");
            addNewIconFromUrl(result, iconUrl);



        }catch (JSONException e) {
            e.printStackTrace();
            result = description;
        }catch(IOException e){
            e.printStackTrace();
        }
        return result;
    }

    public static void addNewIconFromUrl(String url, String description) throws IOException{
        assertInit();
        icons.put(description, drawableFromUrl(url));
    }

    public static Drawable drawableFromUrl(String url) throws IOException {
        Bitmap x;

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.connect();
        InputStream input = connection.getInputStream();

        x = BitmapFactory.decodeStream(input);
        x = Bitmap.createScaledBitmap(x,ICON_HEIGHT, ICON_WIDTH, false);
        return new BitmapDrawable(x);
    }

    public static Drawable drawableFromUrlSafe(String url){
        Bitmap x = null;
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.connect();
            InputStream input = connection.getInputStream();
            x = BitmapFactory.decodeStream(input);
            x = Bitmap.createScaledBitmap(x,ICON_HEIGHT, ICON_WIDTH, false);

        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
        return new BitmapDrawable(x);
    }



}
