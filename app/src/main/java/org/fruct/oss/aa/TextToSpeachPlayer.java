package org.fruct.oss.aa;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

public class TextToSpeachPlayer implements
        TextToSpeech.OnInitListener{

    private TextToSpeech mTTS;
    private String chosenLocale;
    private boolean initResult;
    public static final String TTS_PLAYER_RU = "ru";
    public static final String TTS_PLAYER_EN = "en";


    public TextToSpeachPlayer(Context context, String TTS_PLAYER_LOCALE){
        initResult = true;
        mTTS = new TextToSpeech(context,this);
        chosenLocale = TTS_PLAYER_LOCALE;
    }

    @Override
    public void onInit(int status) {
        // TODO Auto-generated method stub
        if (status == TextToSpeech.SUCCESS) {

            Locale locale = new Locale(chosenLocale);


            int result = mTTS.setLanguage(locale);

            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "This language is not supported");
                initResult = false;
            }

        } else {
            Log.e("TTS", "Error onInit in TTS");
            initResult = false;
        }
    }

    public void addUtterance(String text){
        mTTS.speak(text, TextToSpeech.QUEUE_ADD, null);

    }

    public void destroy(){
        if (mTTS != null) {
            mTTS.stop();
            mTTS.shutdown();
        }
    }

    public boolean checkInit(){
        return initResult;
    }


}
