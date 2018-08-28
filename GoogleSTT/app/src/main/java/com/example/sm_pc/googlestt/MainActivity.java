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

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class MainActivity extends AppCompatActivity implements Button.OnClickListener {
    TextView textView, weather_view01, weather_view03;
    Button recogBtn, weatherBtn;
    private Socket socket;
    PrintWriter socketWriter;
    private String data;
    private Intent intent, intent2;
    private SpeechRecognizer mRecognizer;
    final int MY_PERMISSIONS_RECORD_AUDIO = 1;
    private final int END = 1, READY = 2; //핸들러 메시지. 음성인식 준비, 끝, 앱 종료
    String result = "";
    String day = "";
    String hour = "";
    String sky = "";
    String temp = "";
    //static JSONArray array = null;

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
        textView = findViewById(R.id.textView);
        recogBtn = findViewById(R.id.recogBtn);
        weatherBtn = findViewById(R.id.weather_btn);

        weather_view01 = findViewById(R.id.weather_view01);
        weather_view03 = findViewById(R.id.weather_view03);

        recogBtn.setOnClickListener(this);
        weatherBtn.setOnClickListener(this);

        // set speech recognizer config
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            if (!(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO))) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_RECORD_AUDIO);
            }
        }

        intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);              // 음성인식 intent생성
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());  // 데이터 설정
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");                  // 음성인식 언어
        mRecognizer = SpeechRecognizer.createSpeechRecognizer(this);                    // 음성 인식 객체
        mRecognizer.setRecognitionListener(recognitionListener);

        intent2 = new Intent("playmusic");
        intent2.setPackage(this.getPackageName());

        new Thread() {
            public void run() {
                String html = null;
                try {
                    html = loadKmaData();
                } catch (Exception e) {
                    System.out.println("loadKmaData() is not working");
                    e.printStackTrace();
                }
                //DOM 파싱.
                ByteArrayInputStream bai = new ByteArrayInputStream(html.getBytes());
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                //dbf.setIgnoringElementContentWhitespace(true);//화이트스패이스 생략
                DocumentBuilder builder = null;
                try {
                    builder = dbf.newDocumentBuilder();
                } catch (ParserConfigurationException e) {
                    e.printStackTrace();
                }
                Document parse = null;//DOM 파서
                try {
                    parse = builder.parse(bai);
                } catch (SAXException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //태그 검색
                NodeList datas = parse.getElementsByTagName("data");
                //String result = "data태그 수 =" + datas.getLength()+"\n";
                result = "";
                //17개의 data태그를 순차로 접근
                for (int idx = 0; idx < datas.getLength(); idx++) {
                    //필요한 정보들을 담을 변수 생성
                    Node node = datas.item(idx);//data 태그 추출
                    int childLength = node.getChildNodes().getLength();
                    //자식태그 목록 수정
                    NodeList childNodes = node.getChildNodes();
                    for (int childIdx = 0; childIdx < childLength; childIdx++) {
                        Node childNode = childNodes.item(childIdx);
                        int count = 0;
                        if(childNode.getNodeType() == Node.ELEMENT_NODE){
                            count ++;
                            //태그인 경우만 처리
                            //금일,내일,모레 구분(시간정보 포함)
                            if(childNode.getNodeName().equals("day")){
                                int su = Integer.parseInt(childNode.getFirstChild().getNodeValue());
                                switch(su){
                                    case 0 : day = "금일"; break;
                                    case 1 : day = "내일"; break;
                                    case 2 : day = "모레"; break;
                                }
                            }else if(childNode.getNodeName().equals("hour")){
                                hour = childNode.getFirstChild().getNodeValue();
                                //하늘상태코드 분석
                            }else if(childNode.getNodeName().equals("wfKor")){
                                sky = childNode.getFirstChild().getNodeValue();
                            }else if(childNode.getNodeName().equals("temp")){
                                temp = childNode.getFirstChild().getNodeValue();
                            }
                        }
                    }//end 안쪽 for문
                    result += day+" "+hour+"시 ("+sky+","+temp+"도)\n";
                }//end 바깥쪽 for문
            }
        }.start();
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case END:
                    sendEmptyMessageDelayed(READY, 1000);                //인식 시간 5초로 설정. 5초 지나면 신경안씀.
                    break;
                case READY:
                    //finish();
                    break;
            }
        }
    };

    public String loadKmaData() throws Exception {
        String page = "http://www.kma.go.kr/wid/queryDFS.jsp?gridx=61&gridy=125"; //일단 서초구 서초동
        URL url = new URL(page);
        HttpURLConnection urlConnection =(HttpURLConnection)url.openConnection();
        if(urlConnection == null){
            System.out.println("urlConnection is null");
            return null;
        }

        urlConnection.setConnectTimeout(10000);//최대 10초 대기
        urlConnection.setUseCaches(false);//매번 서버에서 읽어오기
        StringBuilder sb = new StringBuilder();//고속 문자열 결합체

        if(urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK){
            InputStream inputStream = urlConnection.getInputStream();
            InputStreamReader isr = new InputStreamReader(inputStream);

            //한줄씩 읽기
            BufferedReader br = new BufferedReader(isr);
            while(true){
                String line = br.readLine();//웹페이지의 html 코드 읽어오기

                if(line == null) {
                    System.out.println("line is null");
                    break;//스트림이 끝나면 null리턴
                }
                sb.append(line+"\n");
            }//end while
            br.close();
        }//end if
        return sb.toString();
    } //[출처] 기상청 날씨 파싱|작성자 Hanjoong

    @Override
    public void onClick(View view) {
            switch (view.getId()) {
                case R.id.recogBtn:
                    mRecognizer.startListening(intent);
                    break;

                case R.id.weather_btn:
                    weatherBtn.setText("pressed");
                    try {
                        weather_view01.setText(result);
                    } catch (Exception e) {
                        weather_view01.setText("오류"+e.getMessage());
                        e.printStackTrace();
                    } //[출처] 기상청 날씨 파싱|작성자 Hanjoong
                    break;
            }
        }

    class SendThread extends Thread {
            @Override
            public void run() {
                try {
                    socket = new Socket("192.168.11.237", 8888); //이것을 내 포트로 바꾸면 된다
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
                mHandler.sendEmptyMessage(READY);
            }

            @Override
            public void onBeginningOfSpeech() { }

            @Override
            public void onRmsChanged(float v) { }

            @Override
            public void onBufferReceived(byte[] bytes) { }

            @Override
            public void onEndOfSpeech() {
                mHandler.sendEmptyMessage(END);  //핸들러에 메시지 보냄
            }

            @Override
            public void onError(int i) {
                textView.setText("너무 늦게 말하면 오류가 뜹니다");
            }

            @Override
            public void onResults(Bundle bundle) {
                String key = SpeechRecognizer.RESULTS_RECOGNITION;
                ArrayList<String> mResult = bundle.getStringArrayList(key);

                assert mResult != null;
                String[] rs = new String[mResult.size()];
                mResult.toArray(rs);

                data = rs[0];

                textView.setText(data);

                // run sendSocket Thread
                if (data != null) {
                    if (data.equals("music")) {
                        startService(intent2);
                    } else if (data.equals("pause")) {
                        stopService(intent2);
                    } else if(data.equals("weather")) {
                        try {
                            weather_view01.setText(result);
                        } catch (Exception e) {
                            weather_view01.setText("오류"+e.getMessage());
                            e.printStackTrace();
                        } //[출처] 기상청 날씨 파싱|작성자 Hanjoong
                    }
                    Log.w("TEST", " " + data);
                    SendThread thread = new SendThread();
                    thread.start();
                }
            }

            @Override
            public void onPartialResults(Bundle bundle) { }

            @Override
            public void onEvent(int i, Bundle bundle) { }
        };

}