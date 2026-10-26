package com.casa.inicio;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

/**
 * O firmware da TCL zera a lista de serviços de acessibilidade no boot, desligando o {@link Botoes}.
 * Este receiver (boot) e a tela inicial religam o serviço. Requer WRITE_SECURE_SETTINGS, concedida
 * uma vez com: adb shell pm grant com.casa.inicio android.permission.WRITE_SECURE_SETTINGS
 */
public class Ativador extends BroadcastReceiver {
    private static final String SERVICO = "com.casa.inicio/com.casa.inicio.Botoes";

    @Override
    public void onReceive(Context context, Intent intent) {
        garantir(context);
    }

    static void garantir(Context context) {
        ContentResolver cr = context.getContentResolver();
        try {
            String atuais = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (atuais != null && atuais.contains(SERVICO)
                    && Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1) {
                return;
            }
            String novo = atuais == null || atuais.isEmpty() || "0".equals(atuais) || "null".equals(atuais)
                    ? SERVICO : atuais + ":" + SERVICO;
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, novo);
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            Log.d("CasaBotoes", "serviço dos botões religado");
        } catch (SecurityException e) {
            Log.w("CasaBotoes", "sem WRITE_SECURE_SETTINGS para religar o serviço dos botões", e);
        }
    }
}
