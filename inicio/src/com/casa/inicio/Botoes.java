package com.casa.inicio;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Remapeia os botões de atalho do controle TCL.
 *
 * Input é um código padrão do Android, então chega aqui pelo filtro de teclas da acessibilidade.
 * Netflix e voz são códigos próprios da TCL que o Android não entrega à acessibilidade; para esses,
 * o app se registra no serviço de teclas da TCL (IKeyManagerlInterface), que então pula a ação
 * padrão do botão e chama o nosso callback.
 */
public class Botoes extends AccessibilityService {
    private static final String TAG = "CasaBotoes";

    static final int TECLA_NETFLIX = 4062;
    static final int TECLA_VOZ = 4048;
    static final int TECLA_INPUT = KeyEvent.KEYCODE_TV_INPUT;

    static final String HDMI1 = "com.tcl.tvpassthrough/.TvPassThroughService/HW1413744128";
    static final String HDMI2 = "com.tcl.tvpassthrough/.TvPassThroughService/HW1413744384";
    static final String TV_ABERTA = "com.tcl.tvinput/.TDTVInputService/HW1413742848";
    /**
     * Canal mostrado na prévia da TV aberta (Globo HD). Fixo porque a lista de canais da TCL não é
     * legível por apps comuns; muda se a TV for ressintonizada.
     */
    static final long CANAL_PREVIA = 11878;

    private static final String TCL_GERENTE = "com.tcl.keycustomfunctionservice.IKeyManagerlInterface";
    private static final String TCL_CALLBACK = "com.tcl.keycustomfunctionservice.KeyCallback";
    private static final int TCL_REGISTER = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int TCL_UNREGISTER = IBinder.FIRST_CALL_TRANSACTION + 2;
    /** Bit 0 da flag: a TCL trata o botão como "interativo", não executa a função padrão e nos chama. */
    private static final byte FLAG_INTERATIVO = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private IBinder gerenteTcl;

    private final Binder callback = new Binder() {
        {
            attachInterface(null, TCL_CALLBACK);
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code != IBinder.FIRST_CALL_TRANSACTION) {
                return false;
            }
            data.enforceInterface(TCL_CALLBACK);
            final int tecla = data.readInt();
            if (reply != null) {
                reply.writeNoException();
            }
            Log.d(TAG, "callback da TCL: " + tecla);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    teclaTcl(tecla);
                }
            });
            return true;
        }
    };

    private final ServiceConnection conexaoTcl = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            gerenteTcl = service;
            registrar();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            gerenteTcl = null;
        }
    };

    @Override
    protected void onServiceConnected() {
        Intent i = new Intent("com.tcl.KeyAIDLService").setComponent(new ComponentName(
                "com.tcl.keycustomfunctionservice", "com.tcl.keycustomfunctionservice.service.DetectService"));
        boolean ok = bindService(i, conexaoTcl, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "bind no serviço de teclas da TCL: " + ok);
    }

    @Override
    public void onDestroy() {
        if (gerenteTcl != null) {
            chamarTcl(TCL_UNREGISTER, null);
        }
        try {
            unbindService(conexaoTcl);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    private void registrar() {
        chamarTcl(TCL_REGISTER, new int[] {TECLA_NETFLIX, TECLA_VOZ});
    }

    private void chamarTcl(int codigo, int[] teclas) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(TCL_GERENTE);
            data.writeString(getPackageName());
            if (teclas != null) {
                data.writeStrongBinder(callback);
                // List<ListeningKey> no formato de writeTypedList: tamanho, depois (1, keyCode, flag) por item.
                data.writeInt(teclas.length);
                for (int tecla : teclas) {
                    data.writeInt(1);
                    data.writeInt(tecla);
                    data.writeByte(FLAG_INTERATIVO);
                }
            }
            gerenteTcl.transact(codigo, data, reply, 0);
            reply.readException();
            Log.d(TAG, "chamada " + codigo + " ao serviço da TCL ok");
        } catch (Exception e) {
            Log.w(TAG, "falha ao falar com o serviço da TCL", e);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void teclaTcl(int tecla) {
        if (tecla == TECLA_NETFLIX) {
            abrir(entrada(HDMI1));
        } else if (tecla == TECLA_VOZ) {
            abrir(getPackageManager().getLeanbackLaunchIntentForPackage("com.google.android.youtube.tv"));
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != TECLA_INPUT) {
            return false;
        }
        if (e.getAction() == KeyEvent.ACTION_UP) {
            abrir(tvAberta());
            // A TCL abre o menu de fontes nesse botão por conta própria; fecha logo em seguida.
            fecharMenuFontes(300);
            fecharMenuFontes(900);
        }
        return true;
    }

    static Intent entrada(String inputId) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("content://android.media.tv/passthrough/" + Uri.encode(inputId)));
    }

    static Intent tvAberta() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("content://android.media.tv/channel?input=" + Uri.encode(TV_ABERTA)));
    }

    private void abrir(Intent intent) {
        if (intent == null) {
            return;
        }
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    private void fecharMenuFontes(long atraso) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendBroadcast(new Intent("tcl.sys.intent.action.ENTER_HOME").setComponent(
                        new ComponentName("com.tcl.sourcemananger", "com.tcl.insrc.ShortcutKeyReceiver")));
            }
        }, atraso);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
