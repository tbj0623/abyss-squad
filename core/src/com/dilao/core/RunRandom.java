package com.dilao.core;
import java.util.Random;

/** Explicit PRNG state makes a suspended expedition resume the exact same future. */
public final class RunRandom extends Random {
    private long state;
    public RunRandom(long seed) { super(0); setSeed(seed); }
    @Override public synchronized void setSeed(long seed) { seed=(seed^(seed>>>30))*0xbf58476d1ce4e5b9L;seed=(seed^(seed>>>27))*0x94d049bb133111ebL;seed^=seed>>>31;state=(seed^0x5DEECE66DL)&((1L<<48)-1); }
    @Override protected int next(int bits) { state=(state*0x5DEECE66DL+11)&((1L<<48)-1); return (int)(state>>>(48-bits)); }
    public long state(){return state;}
    public void restore(long s){state=s&((1L<<48)-1);}
}
