package com.android.settings.overlay;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import androidx.core.app.NotificationCompat;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.nio.ByteBuffer;

public class FloatingService extends Service {
    private WindowManager wm;
    private View bubbleView, menuView, cropView;
    private WindowManager.LayoutParams bubbleParams, menuParams, cropParams;
    private MediaProjection projection;
    private ImageReader reader;
    private VirtualDisplay display;
    private int screenW, screenH, screenDpi;
    private FrameLayout cropContainer;
    private String mode = "ai";
    private final int baseWidth = 260, baseHeight = 180;
    private View webWindow;
    private WebView webView;
    private WindowManager.LayoutParams webParams;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    // STEALTH: bubble starts invisible, only shows on shake/trigger
    private boolean bubbleVisible = false;
    private static final long BUBBLE_SHOW_DELAY_MS = 800;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        screenW = dm.widthPixels; screenH = dm.heightPixels; screenDpi = dm.densityDpi;
        startForeground(1, buildStealthNotification());
        // STEALTH: delay bubble appearance so overlay is not active during process scan
        new Handler(Looper.getMainLooper()).postDelayed(this::showBubble, BUBBLE_SHOW_DELAY_MS);
        prefListener = (sp, key) -> applyBubbleStyle();
        BubblePrefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener);
    }

    // STEALTH LAYER 2: Notification disguised as system service
    private Notification buildStealthNotification() {
        // Channel ID looks like a system process
        String ch = "android.system.ui.service";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                ch,
                "System UI Service",          // Looks like system
                NotificationManager.IMPORTANCE_MIN  // MIN = no sound, no peek, minimal visibility
            );
            channel.setShowBadge(false);
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setDescription("");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
        return new NotificationCompat.Builder(this, ch)
                // STEALTH: generic system-looking title
                .setContentTitle("System UI")
                .setContentText("Running")
                // STEALTH: use built-in Android icon that looks system-native
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET) // Hidden on lockscreen
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    // STEALTH LAYER 3: Overlay window flags to reduce detectability
    private WindowManager.LayoutParams buildStealthParams(int w, int h, int extraFlags) {
        int type = getOverlayType();
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                  | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                  | extraFlags;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(w, h, type, flags, PixelFormat.TRANSLUCENT);
        // STEALTH: title empty string so window doesn't appear with app name in overlay lists
        p.setTitle("");
        return p;
    }

    private void showBubble() {
        bubbleView = new BubbleShapeView(this);
        bubbleParams = buildStealthParams(bubbleSize(), bubbleSize(), 0);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = screenW - bubbleSize()*2/3;
        bubbleParams.y = screenH / 2;
        wm.addView(bubbleView, bubbleParams);
        bubbleVisible = true;
        applyBubbleStyle();
        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            int initX, initY; float tx, ty; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: initX=bubbleParams.x; initY=bubbleParams.y; tx=e.getRawX(); ty=e.getRawY(); moved=false; return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx=(int)(e.getRawX()-tx), dy=(int)(e.getRawY()-ty);
                        if (Math.abs(dx)>8 || Math.abs(dy)>8) moved=true;
                        bubbleParams.x=initX+dx; bubbleParams.y=initY+dy; wm.updateViewLayout(bubbleView,bubbleParams); return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) { animateBubble(); showMenu(); } else snapToEdge(); return true;
                    default: return false;
                }
            }
        });
    }

    // STEALTH LAYER 4: Temporarily remove overlay when exam browser scans
    // Call hideBubbleTemporarily() if you add a shake/hardware-button trigger
    public void hideBubbleTemporarily(long durationMs) {
        if (bubbleView == null || !bubbleVisible) return;
        try { wm.removeView(bubbleView); } catch (Exception ignored) {}
        if (menuView != null) { try { wm.removeView(menuView); } catch (Exception ignored) {} menuView = null; }
        if (cropView != null) { try { wm.removeView(cropView); } catch (Exception ignored) {} cropView = null; }
        bubbleVisible = false;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                wm.addView(bubbleView, bubbleParams);
                bubbleVisible = true;
            } catch (Exception ignored) {}
        }, durationMs);
    }

    private void applyBubbleStyle() {
        if (bubbleView == null) return;
        ((BubbleShapeView) bubbleView).setStyle(BubblePrefs.shape(this), BubblePrefs.color(this));
        bubbleView.setAlpha(BubblePrefs.alpha(this) / 100f);
        int sz = bubbleSize();
        if (bubbleParams.width != sz) {
            boolean left = bubbleParams.x < screenW / 2;
            bubbleParams.width = sz;
            bubbleParams.height = sz;
            bubbleParams.x = left ? -sz / 3 : screenW - sz * 2 / 3;
            try { wm.updateViewLayout(bubbleView, bubbleParams); } catch (Exception ignored) {}
        }
    }

    private void animateBubble() {
        bubbleView.animate().scaleX(.85f).scaleY(.85f).setDuration(80).withEndAction(() ->
                bubbleView.animate().scaleX(1f).scaleY(1f).setDuration(80).start()).start();
    }

    private void snapToEdge() {
        int bw = bubbleView.getWidth() > 0 ? bubbleView.getWidth() : bubbleSize();
        bubbleParams.x = bubbleParams.x < screenW / 2 ? -bw / 3 : screenW - bw * 2 / 3;
        wm.updateViewLayout(bubbleView, bubbleParams);
    }

    private void showMenu() {
        if (menuView != null) { try { wm.removeView(menuView); } catch (Exception ignored) {} menuView = null; }
        int bw = bubbleSize(), mw = dp(200);
        menuView = LayoutInflater.from(this).inflate(R.layout.menu, null);
        menuParams = buildStealthParams(mw, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
        menuParams.gravity = Gravity.TOP | Gravity.START;
        int menuX = bubbleParams.x < screenW/2 ? bubbleParams.x+bw : bubbleParams.x-mw;
        menuParams.x = Math.max(dp(8), Math.min(menuX, screenW-mw-dp(8)));
        menuParams.y = Math.max(dp(8), Math.min(bubbleParams.y, screenH-dp(190)));
        wm.addView(menuView, menuParams);
        hideBubble();
        menuView.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_OUTSIDE) { closeMenu(true); return true; }
            return false;
        });
        menuView.findViewById(R.id.mGoogle).setOnClickListener(v->{mode="search";closeMenu(false);showCrop();});
        menuView.findViewById(R.id.mAI).setOnClickListener(v->{mode="ai";closeMenu(false);showCrop();});
        menuView.findViewById(R.id.mTranslate).setOnClickListener(v->{mode="translate";closeMenu(false);showCrop();});
    }

    private void closeMenu(boolean restore){
        if(menuView!=null){try{wm.removeView(menuView);}catch(Exception ignored){}menuView=null;}
        if(restore)restoreBubble();
    }

    private void hideBubble(){
        if(bubbleView==null)return;
        bubbleView.setVisibility(View.INVISIBLE);
        bubbleParams.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try{wm.updateViewLayout(bubbleView,bubbleParams);}catch(Exception ignored){}
    }

    private void restoreBubble(){
        if(bubbleView==null)return;
        bubbleParams.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        bubbleView.setVisibility(View.VISIBLE);
        try{wm.updateViewLayout(bubbleView,bubbleParams);}catch(Exception ignored){}
    }

    private void showCrop() {
        if(cropView!=null)wm.removeView(cropView);
        cropView=LayoutInflater.from(this).inflate(R.layout.crop_overlay,null);
        cropContainer=cropView.findViewById(R.id.cropContainer);
        cropParams = buildStealthParams(screenW, screenH,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        cropParams.gravity=Gravity.TOP|Gravity.START; wm.addView(cropView,cropParams);
        ViewGroup.LayoutParams lp=cropContainer.getLayoutParams();lp.width=baseWidth;lp.height=baseHeight;cropContainer.setLayoutParams(lp);
        cropContainer.setX((screenW-baseWidth)/2f);cropContainer.setY((screenH-baseHeight)/2f);
        cropContainer.setOnTouchListener(new View.OnTouchListener(){float downX,downY;float startX,startY;
            @Override public boolean onTouch(View v,MotionEvent e){switch(e.getAction()){case MotionEvent.ACTION_DOWN:downX=e.getRawX();downY=e.getRawY();startX=cropContainer.getX();startY=cropContainer.getY();return true;case MotionEvent.ACTION_MOVE:cropContainer.setX(startX+e.getRawX()-downX);cropContainer.setY(startY+e.getRawY()-downY);return true;default:return false;}}});
        ImageView handle=cropView.findViewById(R.id.resizeHandle);
        handle.setOnTouchListener(new View.OnTouchListener(){float downX,downY;int startW,startH;
            @Override public boolean onTouch(View v,MotionEvent e){switch(e.getAction()){case MotionEvent.ACTION_DOWN:downX=e.getRawX();downY=e.getRawY();startW=cropContainer.getWidth();startH=cropContainer.getHeight();return true;case MotionEvent.ACTION_MOVE:int newW=Math.max(120,startW+(int)(e.getRawX()-downX));int newH=Math.max(80,startH+(int)(e.getRawY()-downY));ViewGroup.LayoutParams lp2=cropContainer.getLayoutParams();lp2.width=newW;lp2.height=newH;cropContainer.setLayoutParams(lp2);return true;default:return false;}}});
        cropView.findViewById(R.id.btnCancel).setOnClickListener(v->{closeCrop();restoreBubble();});
        cropView.findViewById(R.id.btnConfirm).setOnClickListener(v->captureCrop());
    }

    private void closeCrop(){if(cropView!=null){wm.removeView(cropView);cropView=null;}}

    private void captureCrop(){
        if(projection==null){Toast.makeText(this,"Izin capture belum siap",Toast.LENGTH_SHORT).show();closeCrop();restoreBubble();return;}
        final int left=Math.max(0,(int)cropContainer.getX()),top=Math.max(0,(int)cropContainer.getY());
        final int w=Math.min(cropContainer.getWidth(),screenW-left),h=Math.min(cropContainer.getHeight(),screenH-top);
        closeCrop();
        if(w<=0||h<=0){restoreBubble();return;}
        new Handler(Looper.getMainLooper()).postDelayed(()->startCapture(left,top,w,h),250);
        new Handler(Looper.getMainLooper()).postDelayed(this::restoreBubble,3000);
    }

    private void startCapture(final int left,final int top,final int w,final int h){
        releaseCapture();
        try{
            reader=ImageReader.newInstance(screenW,screenH,PixelFormat.RGBA_8888,2);
            display=projection.createVirtualDisplay("nc",screenW,screenH,screenDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,null);
            reader.setOnImageAvailableListener(r->{
                Image img=r.acquireLatestImage();
                if(img==null)return;
                Bitmap full=imageToBitmap(img);
                img.close();
                releaseCapture();
                restoreBubble();
                Bitmap crop=Bitmap.createBitmap(full,left,top,w,h);
                if(crop!=full)full.recycle();
                runOcr(crop);
            },new Handler(Looper.getMainLooper()));
        }catch(Exception e){
            releaseCapture();
            restoreBubble();
            Toast.makeText(this,"Capture gagal",Toast.LENGTH_SHORT).show();
        }
    }

    private void releaseCapture(){
        if(display!=null){display.release();display=null;}
        if(reader!=null){reader.close();reader=null;}
    }

    private Bitmap imageToBitmap(Image image){Image.Plane[] planes=image.getPlanes();ByteBuffer buffer=planes[0].getBuffer();int pixelStride=planes[0].getPixelStride();int rowStride=planes[0].getRowStride();int rowPadding=rowStride-pixelStride*screenW;Bitmap bmp=Bitmap.createBitmap(screenW+rowPadding/pixelStride,screenH,Bitmap.Config.ARGB_8888);bmp.copyPixelsFromBuffer(buffer);return Bitmap.createBitmap(bmp,0,0,screenW,screenH);}

    private void runOcr(Bitmap bmp){InputImage image=InputImage.fromBitmap(bmp,0);TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).addOnSuccessListener(result->{String text=result.getText().trim();bmp.recycle();if(text.isEmpty()){Toast.makeText(this,"Tidak ada teks terdeteksi",Toast.LENGTH_SHORT).show();return;}openTarget(text);}).addOnFailureListener(e->{bmp.recycle();Toast.makeText(this,"OCR gagal",Toast.LENGTH_SHORT).show();});}

    private void openTarget(String text){
        if(text.length()>1500)text=text.substring(0,1500);
        String url,title;
        switch(mode){
            case "search":
                url="https://www.google.com/search?q="+Uri.encode(text.replaceAll("\\s+"," "));
                title="Google Search";break;
            case "translate":
                url="https://translate.google.com/?sl=auto&tl=id&text="+Uri.encode(text);
                title="Google Translate";break;
            default:
                url="https://www.google.com/search?udm=50&q="+Uri.encode(text.replaceAll("\\s+"," "));
                title="Google AI Mode";break;
        }
        showWebWindow(url,title);
    }

    private int bubbleSize(){return dp(BubblePrefs.size(this));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private void openExternal(String url){
        Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try{startActivity(i);}catch(Exception e){Toast.makeText(this,"Tidak ada browser",Toast.LENGTH_SHORT).show();}
    }

    private void showWebWindow(final String url,String title){
        closeWeb();
        try{
            Context themed=new ContextThemeWrapper(this,R.style.Theme_Nightcore);
            webWindow=LayoutInflater.from(themed).inflate(R.layout.web_window,null);
            webView=webWindow.findViewById(R.id.webView);
            WebSettings st=webView.getSettings();
            st.setJavaScriptEnabled(true);
            st.setDomStorageEnabled(true);
            st.setUserAgentString(st.getUserAgentString().replace("; wv","").replace("Version/4.0 ",""));
            CookieManager cm=CookieManager.getInstance();
            cm.setAcceptCookie(true);
            cm.setAcceptThirdPartyCookies(webView,true);
            webView.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                    String u=r.getUrl().toString();
                    return !(u.startsWith("http://")||u.startsWith("https://"));
                }
            });
            webView.setOnKeyListener((v,keyCode,ev)->{
                if(keyCode==KeyEvent.KEYCODE_BACK&&webView!=null&&webView.canGoBack()){
                    if(ev.getAction()==KeyEvent.ACTION_UP)webView.goBack();
                    return true;
                }
                return false;
            });
            ((TextView)webWindow.findViewById(R.id.webTitle)).setText(title);
            webWindow.findViewById(R.id.webClose).setOnClickListener(v->closeWeb());
            webWindow.findViewById(R.id.webBrowser).setOnClickListener(v->{
                String cur=(webView!=null&&webView.getUrl()!=null)?webView.getUrl():url;
                closeWeb();
                openExternal(cur);
            });
            int w=(int)(screenW*0.94f),h=(int)(screenH*0.6f);
            webParams = buildStealthParams(w, h,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            webParams.gravity=Gravity.TOP|Gravity.START;
            webParams.x=(screenW-w)/2;
            webParams.y=(int)(screenH*0.1f);
            webParams.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
            wm.addView(webWindow,webParams);
            webWindow.findViewById(R.id.webTitleBar).setOnTouchListener(new View.OnTouchListener(){
                int ix,iy;float tx,ty;
                @Override public boolean onTouch(View v,MotionEvent e){
                    switch(e.getAction()){
                        case MotionEvent.ACTION_DOWN:ix=webParams.x;iy=webParams.y;tx=e.getRawX();ty=e.getRawY();return true;
                        case MotionEvent.ACTION_MOVE:
                            webParams.x=ix+(int)(e.getRawX()-tx);
                            webParams.y=Math.max(0,iy+(int)(e.getRawY()-ty));
                            if(webWindow!=null)wm.updateViewLayout(webWindow,webParams);
                            return true;
                        default:return false;
                    }
                }
            });
            webWindow.findViewById(R.id.webResize).setOnTouchListener(new View.OnTouchListener(){
                int sw,sh;float tx,ty;
                @Override public boolean onTouch(View v,MotionEvent e){
                    switch(e.getAction()){
                        case MotionEvent.ACTION_DOWN:sw=webParams.width;sh=webParams.height;tx=e.getRawX();ty=e.getRawY();return true;
                        case MotionEvent.ACTION_MOVE:
                            webParams.width=Math.max(dp(200),Math.min(screenW,sw+(int)(e.getRawX()-tx)));
                            webParams.height=Math.max(dp(200),Math.min(screenH,sh+(int)(e.getRawY()-ty)));
                            if(webWindow!=null)wm.updateViewLayout(webWindow,webParams);
                            return true;
                        default:return false;
                    }
                }
            });
            webView.loadUrl(url);
        }catch(Exception e){
            closeWeb();
            openExternal(url);
        }
    }

    private void closeWeb(){
        if(webWindow!=null){try{wm.removeView(webWindow);}catch(Exception ignored){}webWindow=null;}
        if(webView!=null){try{webView.stopLoading();webView.destroy();}catch(Exception ignored){}webView=null;}
    }

    private int getOverlayType(){return Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;}

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&intent.hasExtra("data")){
            int code=intent.getIntExtra("code",0);
            Intent data=intent.getParcelableExtra("data");
            MediaProjectionManager mpm=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            if(data!=null)projection=mpm.getMediaProjection(code,data);
        }
        return START_STICKY;
    }

    @Override public void onDestroy(){
        closeWeb();
        if(prefListener!=null)BubblePrefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener);
        if(bubbleView!=null)try{wm.removeView(bubbleView);}catch(Exception ignored){}
        if(menuView!=null)try{wm.removeView(menuView);}catch(Exception ignored){}
        if(cropView!=null)try{wm.removeView(cropView);}catch(Exception ignored){}
        if(display!=null)display.release();
        if(reader!=null)reader.close();
        if(projection!=null)projection.stop();
        super.onDestroy();
    }
}
