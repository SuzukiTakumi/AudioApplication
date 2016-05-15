package com.example.admin.audioapplication;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static int SAMPLING_RATE = 44100;//サンプリングレート
    private static int BUFFER = 1024;//
    private static int TOUCH_COUNT = 1;//総計何回タッチしたか
    private static int INTERVAL = 10;//何回のイベント毎に音を鳴らすか

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);//端末を縦向きに固定
        setVolumeControlStream(AudioManager.STREAM_MUSIC);//音楽のヴォリュームを制御
        setContentView(new MainView(getApplicationContext()));//アクティビティーにビューを登録
    }

    public class MainView extends SurfaceView implements SurfaceHolder.Callback, Runnable{

        private AudioTrack track;
        private Paint paint;//javaで言うGraphics g ペンのようなもの
        private SurfaceHolder holder;//SurfaceViewの変化を監視するインターフェイス
        private Thread thread;//スレッド
        private short samples[];//
        private ArrayList<SoundData> container;

        public MainView(Context context){
            super(context);

            holder = null;
            thread = null;
            paint = new Paint();
            paint.setAntiAlias(true);
            samples = new short[BUFFER];
            container = new ArrayList<SoundData>();

            int minBufferSize = AudioTrack.getMinBufferSize(SAMPLING_RATE, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);
            //getMinBufferSize (int sampleRateInHz, int channelConfig, int audioFormat)
            //必要最低限のバッファサイズを算出
            track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLING_RATE, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize, AudioTrack.MODE_STREAM);
            //AudioTrack(int streamType, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes, int mode)
            //ストリームタイプ、サンプリングレート、オーディオチャネル、オーディオフォーマット、
            //オーディオデータのバッファサイズ、モード（ステレオかスタティック）
            track.play();

            getHolder().addCallback(this);
            //ホルダーのコールバックに自身をセットする
            //Surfaceの準備ができたタイミングでコールバックが呼ばれるようになる。
        }

        public void surfaceCreated(SurfaceHolder holder){
            this.holder = holder;
            thread = new Thread(this);
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height){
            if(thread != null) thread.start();
        }

        public void surfaceDestroyed(SurfaceHolder holder){
            thread = null;
            track.stop();
            track.release();
        }

        public  void run(){
            SoundData soundData;
            int size;

            while (thread != null){
                //fill white
                Canvas canvas = holder.lockCanvas();
                canvas.drawColor(Color.argb(255, 255, 255, 255));

                //create sound and write down
                for(int i = 0; i < BUFFER; i++){
                    float totalWave = 0.0f;
                    size = container.size();
                    while (size > 0){
                        soundData = container.get(size - 1);
                        soundData.position = (float) ((soundData.position + soundData.w) % (2 * Math.PI));

                        float eachWave;
                        if(soundData.position < Math.PI) eachWave = (float) (1 - 2 * soundData.position / Math.PI);
                        else eachWave = (float) (-1 + 2 * (soundData.position - Math.PI) / Math.PI);

                        totalWave += eachWave * 0.4 * soundData.volume;
                        size--;
                    }

                    //arrange data in area -0.9f <---> 0.9f
                    totalWave = (totalWave > -0.9) ? (totalWave) : (-0.9f);
                    totalWave = (totalWave < 0.9) ? (totalWave) : (0.9f);
                    samples[i] = (short) (Short.MAX_VALUE * totalWave);
                }
                track.write(samples, 0, samples.length);

                //update sound data and draw circle
                size = container.size();
                while (size > 0){
                    soundData = container.get(size - 1);
                    paint.setColor(Color.argb((int) (255 * soundData.volume), soundData.cr, 255, soundData.cb));
                    canvas.drawCircle(soundData.x, soundData.y, soundData.radius, paint);
                    soundData.render();
                    if(soundData.state == 3) container.remove(soundData);
                    size--;
                }
                holder.unlockCanvasAndPost(canvas);
            }
        }

        public boolean onTouchEvent(MotionEvent event){
            if(event.getAction() == MotionEvent.ACTION_DOWN){
                float frequency = (float) ((440 + event.getY() / 2 + event.getX() / 2) * Math.pow(2, ((1.0 / 12.0) * 20 /* Math.random()*/)));
                container.add(new SoundData(frequency, 0.4f, 0.01f, 1, 0.5f, event.getX(), event.getY()));
            }
            else if(event.getAction() == MotionEvent.ACTION_MOVE){
                if(TOUCH_COUNT % INTERVAL == 0){
                    float frequency = (float) ((440 + event.getY() / 2 + event.getX() / 2) * Math.pow(2, ((1.0 / 12.0) * 20 /* Math.random()*/) ));
                    container.add(new SoundData(frequency, 0.4f, 0.01f, 1, 0.5f, event.getX(), event.getY()));
                }
                TOUCH_COUNT++;
            }
            return true;
        }
    }

    public class SoundData{

        public float frequency;
        public float volume;
        public float position;
        public float x, y;
        public float w;
        public int state;
        public int cr, cb;
        public int radius;
        private int count;
        private float fadeIn, fadeOut;
        private float show, volMax;

        public SoundData(float frequency, float fadeIn, float fadeOut, int show, float volMax, float x, float y){

            this.frequency = frequency;
            this.fadeIn = fadeIn;
            this.fadeOut = fadeOut;
            this.show = show;
            this.volMax = volMax;
            this.x = x;
            this.y = y;

            position = 0;
            volume = state = count = radius = 0;
            w = (float) ((2 * Math.PI * frequency) / SAMPLING_RATE);
            cb = (int) (255 * Math.random());
            cr = (int) (255 * Math.random());
        }

        public void render(){

            radius += 5;

            switch(state){
                case 0://fade in
                    volume += fadeIn;
                    if(volume >= volMax){
                        volume = volMax;
                        state = 1;
                    }
                    break;
                case 1://sound
                    count++;
                    if(count == show) state = 2;
                    break;
                case 2://fade out
                    volume -= fadeOut;
                    if(volume <= 0){
                        state = 3;
                        volume = 0;
                    }
                    break;
            }
        }
    }
}