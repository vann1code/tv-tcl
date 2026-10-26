package br.com.globo.globotv.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** O botão Globoplay do controle abre esta tela; ela troca a TV para o HDMI2 (PC). */
public class SplashActivity extends Activity {
    private static final String HDMI2 = "com.tcl.tvpassthrough/.TvPassThroughService/HW1413744384";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("content://android.media.tv/passthrough/" + Uri.encode(HDMI2)));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception ignored) {
        }
        finish();
    }
}
