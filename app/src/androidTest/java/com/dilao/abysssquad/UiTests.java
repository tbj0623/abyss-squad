package com.dilao.abysssquad;

import android.test.ActivityInstrumentationTestCase2;
import android.graphics.*;
import android.os.SystemClock;
import android.view.MotionEvent;
import com.dilao.core.*;
import java.io.*;
import java.lang.reflect.*;

/** Emulator-only regression: real Android View, real touch coordinates, real on-disk save. */
@SuppressWarnings("deprecation")
public final class UiTests extends ActivityInstrumentationTestCase2<MainActivity> {
    private MainActivity activity;private GameView view;
    public UiTests(){super(MainActivity.class);}
    private Object get(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private void set(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private void ui(Work work){getInstrumentation().runOnMainSync(()->{try{work.run();}catch(Exception e){throw new RuntimeException(e);}});}
    interface Work {void run()throws Exception;}
    @Override protected void setUp()throws Exception{
        super.setUp();activity=getActivity();view=(GameView)get(activity,"view");
        Thread.sleep(400);
        ui(()->{set(view,"sound",false);set(view,"suspended",true);set(view,"lastNanos",0L);});
    }
    private void draw(String name)throws Exception{
        ui(()->{set(view,"lastNanos",0L);Bitmap b=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);view.draw(c);
            if(name!=null){File directory=activity.getExternalFilesDir("qa-v3");directory.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}}b.recycle();});
    }
    private void event(int action,float x,float y,long now){
        float scale=Math.min(view.getWidth()/1440f,view.getHeight()/810f),sx=(view.getWidth()-1440*scale)/2,sy=(view.getHeight()-810*scale)/2;
        MotionEvent e=MotionEvent.obtain(now,now,action,sx+x*scale,sy+y*scale,0);view.onTouchEvent(e);e.recycle();
    }
    private void tap(float x,float y)throws Exception{draw(null);ui(()->{long t=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,x,y,t);event(MotionEvent.ACTION_UP,x,y,t+1);});draw(null);}
    private void fixture(Game g)throws Exception{ui(()->{Session session=new Session();session.game=g;session.localId=g.captain;set(view,"session",session);set(view,"screen",1);set(view,"paused",false);set(view,"inventory",false);set(view,"recorded",false);set(view,"lastPhase",g.phase);set(view,"transition",0f);set(view,"arenaKey",-1);set(view,"lastNanos",0L);});draw(null);}
    private Game state()throws Exception{return ((Session)get(view,"session")).game;}
    private Game make(int role,int mode){Game g=new Game(784414);g.addPlayer(1,"探险者",role);g.startSolo(1,mode);return g;}
    public void testMenuModesAndEightRoles()throws Exception{
        ui(()->set(view,"screen",0));draw("01-home");tap(280,459);assertEquals(3,(int)get(view,"screen"));
        tap(1170,460);assertEquals(7,(int)get(view,"role"));tap(400,380);assertEquals(Game.MODE_DAILY,(int)get(view,"selectedMode"));draw("02-setup");
        // Primary flow executes the actual touch handler; clear only this test run's slot to avoid a confirmation dialog.
        ui(()->((SaveStore)get(view,"saves")).clear());tap(1170,756);assertEquals(Game.MAP,state().phase);assertEquals(7,state().player(1).role);assertEquals(Game.MODE_DAILY,state().mode);
        draw("03-map");tap(720,269);assertEquals(Game.FIGHT,state().phase);ui(()->set(view,"transition",0f));draw("04-first-room");
    }
    public void testCombatControlsAndCancel()throws Exception{
        Game g=make(6,0);g.selectRoute(0);g.spawnClock=1000;fixture(g);
        ui(()->{long t=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,124,619,t);event(MotionEvent.ACTION_MOVE,188,615,t+20);});draw(null);assertTrue(g.player(1).dx>.9f);
        ui(()->event(MotionEvent.ACTION_CANCEL,188,615,SystemClock.uptimeMillis()));assertEquals(0f,(float)get(view,"dx"));
        tap(1306,660);assertTrue(g.player(1).skillCd>0);assertEquals(1,g.turrets.size());tap(1172,704);assertTrue(g.player(1).dashCd>0);
        tap(1070,50);assertTrue((boolean)get(view,"inventory"));draw("05-build");tap(1290,52);assertFalse((boolean)get(view,"inventory"));
        ui(()->{view.suspend();});assertTrue((boolean)get(view,"paused"));draw("06-pause");tap(650,519);assertEquals(0,(int)get(view,"screen"));
        tap(428,151);assertEquals(Game.FIGHT,state().phase);assertTrue((boolean)get(view,"paused"));
        SaveStore disk=new SaveStore(activity);long end=System.currentTimeMillis()+4000;while(!disk.available()&&System.currentTimeMillis()<end){disk.close();Thread.sleep(100);disk=new SaveStore(activity);}assertTrue(disk.available());assertNotNull(disk.load());disk.close();
    }
    public void testEventsEconomyAndAllSceneRendering()throws Exception{
        for(int biome=0;biome<3;biome++){
            Game g=make(4+biome,0);g.roomsVisited=biome*4;g.room=biome*4;g.biome=biome;g.floor=biome+1;g.routeOptions[0]=Game.ROOM_CHALLENGE;g.selectRoute(0);
            for(int j=0;j<170;j++){g.player(1).invuln=1;g.update(Game.STEP);}fixture(g);draw("07-biome-"+biome);
        }
        Game g=make(0,0);g.routeOptions[0]=Game.ROOM_EVENT;g.selectRoute(0);g.eventType=1;g.coins=5;fixture(g);draw("08-event");tap(360,469);assertEquals(-1,g.eventChoice);tap(360,592);assertEquals(1,g.eventChoice);draw("09-event-result");tap(400,694);assertEquals(Game.MAP,g.phase);
        g.routeOptions[0]=Game.ROOM_SHOP;g.selectRoute(0);g.coins=500;fixture(g);int id=g.shopOffers[0];tap(240,440);assertEquals(1,g.player(1).relics[id]);assertEquals(-1,g.shopOffers[0]);draw("10-shop");tap(1180,670);assertEquals(Game.MAP,g.phase);
        g.routeOptions[0]=Game.ROOM_CAMP;g.selectRoute(0);fixture(g);draw("11-camp");tap(650,470);assertEquals(Game.ARMORY,g.phase);draw("11b-armory");tap(255,445);assertEquals(Game.MAP,g.phase);
        g.routeOptions[0]=Game.ROOM_TREASURE;g.selectRoute(0);g.spawned=g.target;g.enemies.clear();g.update(Game.STEP);fixture(g);draw("12-draft");tap(300,470);g.update(Game.STEP);assertEquals(Game.MAP,g.phase);
        g.phase=Game.WON;fixture(g);draw("13-result");
    }
    public void testCatalogAndMultitouch()throws Exception{
        ui(()->{set(view,"screen",2);set(view,"infoPage",1);set(view,"catalogPage",0);});draw("14-relics-1");tap(1260,716);assertEquals(1,(int)get(view,"catalogPage"));draw("15-relics-2");
        Game g=make(5,0);g.selectRoute(0);fixture(g);
        ui(()->{
            float scale=Math.min(view.getWidth()/1440f,view.getHeight()/810f),sx=(view.getWidth()-1440*scale)/2,sy=(view.getHeight()-810*scale)/2;long t=SystemClock.uptimeMillis();
            event(MotionEvent.ACTION_DOWN,126,617,t);
            MotionEvent.PointerProperties[] pp=new MotionEvent.PointerProperties[2];MotionEvent.PointerCoords[] pc=new MotionEvent.PointerCoords[2];
            for(int i=0;i<2;i++){pp[i]=new MotionEvent.PointerProperties();pp[i].id=i;pp[i].toolType=MotionEvent.TOOL_TYPE_FINGER;pc[i]=new MotionEvent.PointerCoords();pc[i].pressure=1;pc[i].size=1;}
            pc[0].x=sx+126*scale;pc[0].y=sy+617*scale;pc[1].x=sx+1306*scale;pc[1].y=sy+660*scale;
            MotionEvent event=MotionEvent.obtain(t,t+20,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,pp,pc,0,0,1,1,0,0,0,0);view.onTouchEvent(event);event.recycle();
        });draw(null);assertTrue("second finger activates skill, not move joystick",g.player(1).skillCd>0);assertEquals(0f,g.player(1).dx);
    }
    public void testDifficultyAndWeaponForms()throws Exception{
        ui(()->{set(view,"screen",3);set(view,"selectedMode",0);});tap(540,752);assertEquals(2,(int)get(view,"selectedContract"));draw("16-contract");
        Game g=make(0,0);g.phase=Game.CAMP;fixture(g);tap(700,450);assertEquals(Game.ARMORY,g.phase);draw("17-weapon-tuning");tap(1120,450);assertEquals(3,g.player(1).weaponForm);assertEquals(Game.MAP,g.phase);
        g.roomsVisited=3;g.biome=0;g.floor=1;g.routeOptions[0]=Game.ROOM_BOSS;g.routeCount=1;g.selectRoute(0);for(int j=0;j<45;j++)g.update(Game.STEP);
        fixture(g);draw("18-boss-guardian");assertTrue(g.enemies.size()>0);
        g.mode=Game.MODE_BOSS_RUSH;g.roomsVisited=4;g.phase=Game.MAP;g.routeOptions[0]=Game.ROOM_BOSS;g.selectRoute(0);for(int j=0;j<50;j++)g.update(Game.STEP);fixture(g);draw("19-boss-lantern");
    }
    @Override protected void tearDown()throws Exception{if(view!=null)ui(()->{set(view,"suspended",true);set(view,"sound",false);});super.tearDown();}
}
