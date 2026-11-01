package com.dilao.abysssquad;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.content.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import com.dilao.core.*;

public final class MainActivity extends Activity {
    private GameView view;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if(Build.VERSION.SDK_INT>=28)getWindow().getAttributes().layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
        immersive();view=new GameView(this);setContentView(view);
    }
    void immersive(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    void joinDialog(int role){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(34,14,34,10);
        TextView tip=new TextView(this);tip.setText("连接同一 Wi-Fi / 热点，输入房主显示的 IPv4 地址。\n本版不含公网匹配；请勿连接陌生服务器。");l.addView(tip);
        EditText address=new EditText(this);address.setSingleLine(true);address.setHint("例如 192.168.1.8:45454");address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        address.setText(getPreferences(0).getString("lastAddress",""));l.addView(address);
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("昵称（最多 12 字）");name.setText(getPreferences(0).getString("name","探险者"));l.addView(name);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("加入小队").setView(l).setNegativeButton("取消",null).setPositiveButton("连接",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{
            String raw=address.getText().toString().trim(),host=raw;int port=Codec.PORT;
            try{int at=raw.indexOf(':');if(at>=0){host=raw.substring(0,at);port=Integer.parseInt(raw.substring(at+1));}if(host.isEmpty()||port<1||port>65535)throw new IllegalArgumentException();}
            catch(Exception e){address.setError("请输入正确的地址与端口");return;}
            String n=name.getText().toString().trim();if(n.isEmpty())n="探险者";if(n.length()>12)n=n.substring(0,12);
            getPreferences(0).edit().putString("lastAddress",raw).putString("name",n).apply();d.dismiss();view.join(host,port,n,role);immersive();
        }));d.show();
    }
    void confirmLeave(Runnable leave, boolean host){new AlertDialog.Builder(this).setTitle(host?"解散房间？":"离开远征？").setMessage(host?"房主离开会结束所有队员的本局战斗。":"当前远征进度不会保存，探索记录会保留。").setNegativeButton("继续",(d,w)->immersive()).setPositiveButton("离开",(d,w)->{leave.run();immersive();}).show();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)immersive();}
    @Override protected void onPause(){if(view!=null)view.suspend();super.onPause();}
    @Override protected void onResume(){super.onResume();if(view!=null)view.resume();immersive();}
    @Override protected void onDestroy(){if(view!=null)view.destroy();super.onDestroy();}
    @Override public void onBackPressed(){if(view!=null)view.back();}
}

