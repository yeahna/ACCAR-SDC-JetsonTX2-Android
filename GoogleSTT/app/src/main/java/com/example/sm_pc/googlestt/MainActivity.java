package com.example.sm_pc.googlestt;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;


public class MainActivity extends AppCompatActivity {
    private TextView textView;
    private Button recogBtn, playBtn, stopBtn;

    private Socket socket;
    private PrintWriter socketWriter;

    private String data;
    private Intent intent;
    private SpeechRecognizer mRecognizer;
    private final int MY_PERMISSIONS_RECORD_AUDIO = 1;

    private final int END=1, FINISH = 2; //핸들러 메시지. 음성인식 준비, 끝, 앱 종료
    private Handler mHandler = new Handler(){
        public void handleMessage(Message msg){
            switch (msg.what) {
                case END:
                    sendEmptyMessageDelayed(FINISH, 5000);                //인식 시간 5초로 설정. 5초 지나면 신경안씀.
                    break;
                case FINISH:
                    finish();
                    break;
            }
        }
    };

    @Override
    protected void onStop() {
        super.onStop();

        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // get component
        textView = (TextView) findViewById(R.id.textView);
        recogBtn = (Button) findViewById(R.id.recogBtn);
        playBtn = (Button) findViewById(R.id.playBtn);
        stopBtn = (Button) findViewById(R.id.stopBtn);

        // set speech recognizer config
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {

            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_RECORD_AUDIO
                );
            }
        }

        intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);              // 음성인식 intent생성
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());  // 데이터 설정
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");                  // 음성인식 언어
        //intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");                  // 음성인식 언어

        mRecognizer = SpeechRecognizer.createSpeechRecognizer(this);                    // 음성 인식 객체
        mRecognizer.setRecognitionListener(recognitionListener);
        recogBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mRecognizer.startListening(intent);
            }
        });

    }

    class SendThread extends Thread{
        @Override
        public void run() {
            try {
                socket = new Socket("192.168.43.1671", 8888); //이것을 내 포트로 바꾸면 된다 192.168.43.160

                socketWriter = new PrintWriter(socket.getOutputStream(), true);
                socketWriter.println(data);
                socketWriter.flush();
                socketWriter.close();

                socket.close();

            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private RecognitionListener recognitionListener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle bundle) {
        }

        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onRmsChanged(float v) {
        }

        @Override
        public void onBufferReceived(byte[] bytes) {
        }

        @Override
        public void onEndOfSpeech() {
            mHandler.sendEmptyMessage(END);		//핸들러에 메시지 보냄
        }

        @Override
        public void onError(int i) {
            textView.setText("너무 늦게 말하면 오류가 뜹니다");

        }

        @Override
        public void onResults(Bundle bundle) {
            String key = "";
            key = SpeechRecognizer.RESULTS_RECOGNITION;
            ArrayList<String> mResult = bundle.getStringArrayList(key);

            String[] rs = new String[mResult.size()];
            mResult.toArray(rs);

            data = rs[0];

            textView.setText(data);

            // run sendSocket Thread
            if(data != null) {
                Log.w("TEST", " " + data);
                SendThread thread = new SendThread();
                thread.start();
            }
        }

        @Override
        public void onPartialResults(Bundle bundle) {
        }

        @Override
        public void onEvent(int i, Bundle bundle) {
        }
    };

    @Override
    public void finish(){
        if(mRecognizer!= null) mRecognizer.stopListening();						//음성인식 중지
        mHandler.removeMessages(END);			//메시지 삭제
        mHandler.removeMessages(FINISH);			//메시지 삭제
        super.finish();
    }
}