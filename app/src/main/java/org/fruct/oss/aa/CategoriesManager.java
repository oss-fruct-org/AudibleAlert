package org.fruct.oss.aa;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.poi.gets.CategoriesList;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

public class CategoriesManager {

    private static Logger log = LoggerFactory.getLogger(CategoriesManager.class);

    public static Uri left_uri;
    public static Uri right_uri;
    public static Uri forward_uri;
    public static Uri back_uri;
    private static Uri welcome_uri;
    private static Uri unknown_obj_uri;
    private static Uri goodbye_uri;

    private static Uri crosswalk_uri;
    private static Uri rough_roads_uri;
    private static Uri slope_uri;
    private static Uri ramp_uri;

    public static Uri left_uri_rus;
    public static Uri right_uri_rus;
    public static Uri forward_uri_rus;
    public static Uri back_uri_rus;
    public static Uri high_curb_uri_rus;
    private static Uri welcome_uri_rus;
    private static Uri unknown_obj_uri_rus;
    private static Uri unknown_dir_uri_rus;
    private static Uri goodbye_uri_rus;

    private static Uri crosswalk_uri_rus;
    private static Uri rough_roads_uri_rus;
    private static Uri slope_uri_rus;
    private static Uri ramp_uri_rus;



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
    public static final String CROSSWALK = "crosswalk";
    public static final String ROUGH_ROADS = "rough roads";
    public static final String RAMP = "ramp";
    public static final String SLOPE= "slope";

    public static final int ICON_HEIGHT= 40;
    public static final int ICON_WIDTH = 40;

    private static HashMap<String, Uri> uris;
    private static HashMap<String, Integer> radii;

    private static HashMap<String, Bitmap> icons;

    private static HashMap<String, String> local_names;

    private static final int DEFAULT_RADIUS = 30;

    private static String locale;

    public static void init(){
        assertUrisInit();
        assertRadiiInit();
        assertIconsInit();
        assertNamesInit();

    }

    private static void assertUrisInit(){
        if(uris != null)
            return;
        initUris();
    }

    private static void assertRadiiInit(){
        if(radii != null)
            return;
        initRadii();
    }

    private static void assertIconsInit(){
        if(icons!= null)
            return;
        initIcons();
    }

    private static void assertNamesInit(){
        if(local_names != null)
             return;

        locale = App.getContext().getResources().getConfiguration().locale.getLanguage();
        local_names = new HashMap<String, String>();

        local_names.put(CROSSWALK, "Пешеходный переход");
        local_names.put(ROUGH_ROADS, "Неровная дорога");
        local_names.put(RAMP, "Пандус");
        local_names.put(SLOPE, "Уклон");

        log.error("initial get " + local_names.get("SLOPE".toLowerCase()));

    }


    private static void initIcons() {
        icons = new HashMap<String, Bitmap>();

        icons.put(MUSEUMS, bitmapFromUrlSafe("http://kappa.cs.karelia.ru/~kolomens/museum.png"));
        icons.put(HOTELS, bitmapFromUrlSafe("http://kappa.cs.karelia.ru/~kolomens/hotel1.png"));

    }


    private static void initUris(){
        uris = new HashMap<String, Uri>();
        String folder = "android.resource://" + packagename + "/";
        left_uri = Uri.parse(folder + R.raw.left);
        right_uri = Uri.parse(folder + R.raw.right);
        back_uri = Uri.parse(folder + R.raw.back);
        forward_uri = Uri.parse(folder + R.raw.forward);
        welcome_uri = Uri.parse(folder + R.raw.welcome);
        unknown_obj_uri = Uri.parse(folder + R.raw.unknown_obj);
        goodbye_uri = Uri.parse(folder + R.raw.goodbye);

        crosswalk_uri = Uri.parse(folder + R.raw.crosswalk);
        ramp_uri = Uri.parse(folder + R.raw.ramp);
        slope_uri = Uri.parse(folder + R.raw.slope);
        rough_roads_uri = Uri.parse(folder + R.raw.rough_road);

        left_uri_rus = Uri.parse(folder + R.raw.left_rus);
        right_uri_rus = Uri.parse(folder + R.raw.right_rus);
        back_uri_rus = Uri.parse(folder + R.raw.back_rus);
        forward_uri_rus = Uri.parse(folder + R.raw.forward_rus);
        welcome_uri_rus = Uri.parse(folder + R.raw.welcome_rus);
        unknown_obj_uri_rus = Uri.parse(folder + R.raw.unknown_obj_rus);
        goodbye_uri_rus = Uri.parse(folder + R.raw.goodbye_rus);

        crosswalk_uri_rus = Uri.parse(folder + R.raw.crosswalk_rus);
        ramp_uri_rus = Uri.parse(folder + R.raw.ramp_rus);
        slope_uri_rus = Uri.parse(folder + R.raw.slope_rus);
        rough_roads_uri_rus = Uri.parse(folder + R.raw.rough_road_rus);

        assertNamesInit();

        if(!locale.equals("ru")) {
            // general uris
            uris.put(LEFT, left_uri);
            uris.put(RIGHT, right_uri);
            uris.put(BACK, back_uri);
            uris.put(FORWARD, forward_uri);
            uris.put(WELCOME, welcome_uri);
            uris.put(UNKNOWN_OBJECT, unknown_obj_uri);
            uris.put(GOODBYE, goodbye_uri);
            // category uris
            uris.put(CROSSWALK, crosswalk_uri);
            uris.put(RAMP, ramp_uri);
            uris.put(SLOPE, slope_uri);
            uris.put(ROUGH_ROADS, rough_roads_uri);
        }else{
            // general uris
            uris.put(LEFT, left_uri_rus);
            uris.put(RIGHT, right_uri_rus);
            uris.put(BACK, back_uri_rus);
            uris.put(FORWARD, forward_uri_rus);
            uris.put(WELCOME, welcome_uri_rus);
            uris.put(UNKNOWN_OBJECT, unknown_obj_uri_rus);
            uris.put(GOODBYE, goodbye_uri_rus);
            // category uris
            uris.put(getName(CROSSWALK).toLowerCase(), crosswalk_uri_rus);
            uris.put(getName(RAMP).toLowerCase(), ramp_uri_rus);
            uris.put(getName(SLOPE).toLowerCase(), slope_uri_rus);
            uris.put(getName(ROUGH_ROADS).toLowerCase(), rough_roads_uri_rus);
        }

    }

    public static String getName(String name){
        assertNamesInit();
        if(!locale.equals("ru"))
            return name;
        String result = local_names.get(name.toLowerCase());
        if(result != null) {
            return result;
        }else {
            return name;
        }
    }

    private static void initRadii(){
        radii = new HashMap<String, Integer>();
    }

    public static int getRadiusForCategory(String category){
        Integer result;
        if((result = radii.get(category.toLowerCase()))== null)
            result = DEFAULT_RADIUS;
        return result;
    }

    public static Bitmap getIconForCategory(String category){
        return icons.get(category.toLowerCase());
    }

    public static Uri getUriForDirection(String direction){
        assertUrisInit();
        Uri result = uris.get(direction.toLowerCase());
        return result;
    }

    public static Uri getUriForCategory(String category){
        assertUrisInit();
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
     *
     *   {\"description\":\"Marks different pits and holes\"
     */
    public static String checkDiscription(String description){
        String result = null;
        String iconUrl;
        try {
            JSONObject root = new JSONObject(description);
            result = root.getString("description");
            //log.error("Desc succesfully extracted");
        }catch (JSONException e) {
            e.printStackTrace();
            result = description;
        }
        return result;
    }

    /**
     * @param url
     * @return
     *
     *
     * Expecting this/same JSON:
     * {\"icon\":\"http://gets.cs.petrsu.ru/obstacle/icons/roughroads.png\"}
     */
    public static String getIconFromUrl(String url){
        String result = null;
        String iconUrl;
        try {
            JSONObject root = new JSONObject(url);
            result = root.getString("icon");
            //log.error("Icon succesfully extracted");
        }catch (JSONException e) {
            e.printStackTrace();
            result = url;
        }
        return result;
    }

    public static void loadCategories(List<CategoriesList.Category> categories){
        String name = "";
        String description = "";
        String iconURL = "";
        assertNamesInit();
        for(CategoriesList.Category c : categories){
            if(locale.equals("ru")) {
                name = getName(c.getName());
            }else{
                name = c.getName();
            }
            description = checkDiscription(c.getDescription().replace("\\", ""));
            iconURL = getIconFromUrl(c.getUrl().replace("\\", ""));

            if(iconURL.equalsIgnoreCase(c.getUrl().replace("\\", "")))
                continue;

            addNewIconFromUrl(iconURL, name);
        }
    }

    public static void addNewIconFromUrl(String url, String name){
        assertIconsInit();
        icons.put(name.toLowerCase(), bitmapFromUrlSafe(url));
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
            x = Bitmap.createScaledBitmap(x,ICON_WIDTH, ICON_HEIGHT, false);

        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
        return new BitmapDrawable(x);
    }


    public static Bitmap bitmapFromUrlSafe(String url){
        Bitmap x = null;
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.connect();
            InputStream input = connection.getInputStream();
            x = BitmapFactory.decodeStream(input);
            x = Bitmap.createScaledBitmap(x,ICON_WIDTH, ICON_HEIGHT, false);

        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
        return x;
    }



}
