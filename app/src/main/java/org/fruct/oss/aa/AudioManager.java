package org.fruct.oss.aa;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.support.v4.content.LocalBroadcastManager;

import org.fruct.oss.ikm.MainActivity;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.gets.CategoriesList;
import org.fruct.oss.ikm.service.Direction;
import org.fruct.oss.ikm.service.DirectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Text;

import java.util.ArrayList;

/**
 * Provides audio uri's to audio player
 *
 *
 */
public class AudioManager {

    private final static Logger log = LoggerFactory.getLogger(AudioPlayer.class);

    private String packagename;
    public static final String WELCOME = "welcome";
    public static final String GOODBYE = "goodbye";


    // TODO: in case of need to check whether you already left the zone of queued point, add list of points
    private ArrayList<Uri> uris;
    private Uri playingNow;
    private AudioPlayer audioPlayer;

    private Context context;

    private boolean TTS_enabled;
    public boolean keepPlaying = true;

    TextToSpeachPlayer ttsp;

    private BroadcastReceiver audioFinishedReceiver;
    private BroadcastReceiver stopTrackingReceiver;

    public AudioManager(Context ctx){
        log.error("Created AudioManager ^^^^^^^^^^^^^^^^^^");
        uris = new ArrayList<Uri>();

        CategoriesManager.init();
        context = ctx;
        audioFinishedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                playNext();
                //log.debug("AUDIOPLAYER: PLAYING NEXT ^^^^^^^^");
            }
        };
       context.registerReceiver(audioFinishedReceiver, new IntentFilter(AudioPlayer.BC_ACTION_STOP_PLAY));

        stopTrackingReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                    log.trace("Playing exiting message");
                    stopPlaying();
            }
        };

        LocalBroadcastManager.getInstance(context).registerReceiver(stopTrackingReceiver,
                new IntentFilter(MainActivity.STOP_TRACKING_SERVICE));

        audioPlayer = new AudioPlayer(context);
        ttsp = new TextToSpeachPlayer(context, TextToSpeachPlayer.TTS_PLAYER_EN);
        TTS_enabled = ttsp.checkInit();


    }

    public void startPlaying(){
        keepPlaying = true;
        uris.add(CategoriesManager.getUriForCategory(WELCOME));
        playNext();
    }


    public void queueToPlay(PointDesc point, String dir){
        Uri category, direction;
        category = getUriByCategory(point.getCategory());
        direction = getUriByDirection(dir);
        if(category!= Uri.EMPTY && direction != Uri.EMPTY) {
            uris.add(category);
            uris.add(direction);
        }else{
            log.error("Couldn't add point to play queue");
        }
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
        return  CategoriesManager.getUriForCategory(cat);
    }

    private Uri getUriByDirection(String dir){
        return CategoriesManager.getUriForDirection(dir);
    }


    public void stopPlaying(){
        keepPlaying = false;
        uris.clear();
        audioPlayer.stopAudioTrack();
        audioPlayer.startAudioTrack(CategoriesManager.getUriForCategory(GOODBYE));
    }

    public void onDestroy(){
        ttsp.destroy();
        context.unregisterReceiver(audioFinishedReceiver);
        LocalBroadcastManager.getInstance(context).unregisterReceiver(stopTrackingReceiver);
    }

}



