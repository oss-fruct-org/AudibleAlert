package org.fruct.oss.aa;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.support.v4.content.LocalBroadcastManager;

import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.gets.CategoriesList;
import org.fruct.oss.ikm.service.Direction;
import org.fruct.oss.ikm.service.DirectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 * Provides audio uri's to audio player
 *
 *
 */
public class AudioManager {

    /**
     * TODO: get this from strings file or wherever
     * TODO: add #welcome and #goodbye messages.
     */
    public static final String LEFT = "left";
    public static final String RIGHT = "right";
    public static final String FORWARD = "forward";
    public static final String BACK= "back";
    public static final String HIGH_CURB = "high_curb";

    private final static Logger log = LoggerFactory.getLogger(AudioPlayer.class);

    private String packagename;

    public Uri left_uri;
    public Uri right_uri;
    public Uri forward_uri;
    public Uri back_uri;
    public Uri high_curb_uri;

    private ArrayList<Uri> uris;
    private Uri playingNow;
    private AudioPlayer audioPlayer;

    private BroadcastReceiver audioFinishedReceiver;

    public AudioManager(Context ctx){
        uris = new ArrayList<Uri>();

        audioFinishedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                playNext();
                log.debug("AUDIOPLAYER: PLAYING NEXT ^^^^^^^^");
            }
        };
        ctx.registerReceiver(audioFinishedReceiver, new IntentFilter(AudioPlayer.BC_ACTION_STOP_PLAY));

        packagename = ctx.getPackageName();
        initUris();
        audioPlayer = new AudioPlayer(ctx);
        uris.add(left_uri);
        uris.add(left_uri);
        uris.add(right_uri);
        uris.add(right_uri);
        playNext();
    }

    public void queueToPlay(PointDesc point){
        Uri category, direction;
        category = getUriByCategory(point.getCategory());
        direction = getUriByDirection(point.getRelativeDirection());
        uris.add(category);
        uris.add(direction);


    }

    public void playNext(){

        if(uris.size() == 0){
            return;
        }
        playingNow = uris.get(0);
        if(audioPlayer.startAudioTrack(playingNow))
            uris.remove(playingNow); // playin started successfully

    }

    private Uri getUriByCategory(String cat){
        Uri uri = Uri.EMPTY;

        if(cat.equalsIgnoreCase(HIGH_CURB))
                return high_curb_uri;



        return uri;
    }

    private Uri getUriByDirection(Direction.RelativeDirection relDirection){
        Uri uri = Uri.EMPTY;
        String dir = (String) relDirection.getDescription();

        if(dir.equalsIgnoreCase(RIGHT))
            return right_uri;
        if(dir.equalsIgnoreCase(FORWARD))
            return forward_uri;
        if(dir.equalsIgnoreCase(LEFT))
            return left_uri;
        if(dir.equalsIgnoreCase(BACK))
            return back_uri;
        return uri;
    }

    private void initUris(){
        String folder = "android.resource://" + packagename + "/";
        left_uri = Uri.parse(folder + R.raw.left);
        right_uri = Uri.parse(folder + R.raw.right);
        back_uri = Uri.parse(folder + BACK);
        forward_uri = Uri.parse(folder + R.raw.forward);
        high_curb_uri = Uri.parse(folder + R.raw.high_curb);
    }


}



