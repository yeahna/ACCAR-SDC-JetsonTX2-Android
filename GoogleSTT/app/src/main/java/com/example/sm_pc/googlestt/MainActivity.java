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

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends AppCompatActivity implements Button.OnClickListener {
    public static int TO_GRID = 0;
    public static int TO_GPS = 1;
    TextView textView, weather_view01, weather_view03;
    Button recogBtn, playBtn, weatherBtn;
    private Socket socket;
    PrintWriter socketWriter;
    private String data;
    public static StringBuffer html;
    private Intent intent, intent2;
    private SpeechRecognizer mRecognizer;
    final int MY_PERMISSIONS_RECORD_AUDIO = 1;
    private final int END = 1, READY = 2; //핸들러 메시지. 음성인식 준비, 끝, 앱 종료
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
        playBtn = findViewById(R.id.playBtn);
        weatherBtn = findViewById(R.id.weather_btn);

        weather_view01 = findViewById(R.id.weather_view01);
        weather_view03 = findViewById(R.id.weather_view03);

        recogBtn.setOnClickListener(this);
        playBtn.setOnClickListener(this);
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
        }

    private static List<Map<String, Object>> getXml(String locate) {

        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();

        String location = locate;
        String json = "";
        StringBuilder sb = new StringBuilder();
        double v1 = 0.0;
        double v2 = 0.0;
        try {

            String addr = "http://maps.google.com/maps/api/geocode/json?address=";
            URL url = new URL(addr + URLEncoder.encode(location, "UTF-8"));
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(10000);
            con.setUseCaches(false);
            BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));

            while (true) {
                String line = br.readLine();
                if (line == null)
                    break;
                sb.append(line);
            }
            br.close();
            con.disconnect();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        json = sb.toString();
        System.out.println(json);
        JSONObject object = (JSONObject) JSONValue.parse(json);
        JSONArray array = (JSONArray) object.get("results");
        for (Object o : array) {
            JSONObject object2 = (JSONObject) o;
            String lat = ((JSONObject) (((JSONObject) object2.get("geometry")).get("location"))).get("lat").toString();
            String lng = ((JSONObject) (((JSONObject) object2.get("geometry")).get("location"))).get("lng").toString();

            v1 = Double.parseDouble(lat);
            v2 = Double.parseDouble(lng);
        }

        //위에서 얻은 위도 경도를 가지고 GridX,GridY좌표를 구합니다.
        Map<String, Object> map = getGridxy(v1, v2);

        String xml = "";
        //GridX, GridY좌표를 가지고 XML데이터를 가져옵니다.
        try {
            // 전국 날씨정보
            String addr = "http://www.kma.go.kr/wid/queryDFS.jsp?gridx=" + map.get("x") + "&gridy=" + map.get("y");
            URL url = new URL(addr);
            HttpURLConnection http = (HttpURLConnection) url.openConnection();
            http.setConnectTimeout(10000);
            http.setUseCaches(false);

            BufferedReader br = new BufferedReader(new InputStreamReader(http.getInputStream()));
            sb = new StringBuilder();
            while (true) {
                String line = br.readLine();
                if (line == null)
                    break;
                sb.append(line);
            }
            xml = sb.toString();
            br.close();
            http.disconnect();

        } catch (Exception e) {
            System.out.println("다운로드에러" + e.getMessage());

        }

        Map<String, Object> data = new HashMap<String, Object>(); //위에서 추출한 XML데이터를 파싱해봅시다.
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentbuilder = factory.newDocumentBuilder();
            InputStream is = new ByteArrayInputStream(xml.getBytes());
            Document doc = documentbuilder.parse(is);
            Element element = doc.getDocumentElement();

            NodeList list1 = element.getElementsByTagName("data");

            for (int i = 0; i < list1.getLength(); i++) {
                for (Node node = list1.item(i).getFirstChild(); node != null; node = node.getNextSibling()) {

                    if (node.getNodeName().equals("hour")) { //시간
                        data = new HashMap<String, Object>();
                        data.put("hour", node.getTextContent().toString());
                    }

                    if (node.getNodeName().equals("temp")) { //온도
                        data.put("temp", node.getTextContent().toString());
                    }

                    if (node.getNodeName().equals("reh")) { //습도
                        data.put("reh", node.getTextContent().toString());
                    }

                    if (node.getNodeName().equals("wfEn")) { //날씨 정보
                        data.put("wfEn", node.getTextContent().toString());
                        list.add(data);
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("파싱에러" + e.getMessage());
        }

        return list;
    }

    private static Map<String, Object> getGridxy(double v1, double v2) {
        double RE = 6371.00877; // 지구 반경(km)
        double GRID = 5.0; // 격자 간격(km)
        double SLAT1 = 30.0; // 투영 위도1(degree)
        double SLAT2 = 60.0; // 투영 위도2(degree)
        double OLON = 126.0; // 기준점 경도(degree)
        double OLAT = 38.0; // 기준점 위도(degree)
        double XO = 43; // 기준점 X좌표(GRID)
        double YO = 136; // 기1준점 Y좌표(GRID)
        double DEGRAD = Math.PI / 180.0;
        // double RADDEG = 180.0 / Math.PI;

        double re = RE / GRID;
        double slat1 = SLAT1 * DEGRAD;
        double slat2 = SLAT2 * DEGRAD;
        double olon = OLON * DEGRAD;
        double olat = OLAT * DEGRAD;

        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        ro = re * sf / Math.pow(ro, sn);
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("lat", v1);
        map.put("lng", v1);
        double ra = Math.tan(Math.PI * 0.25 + (v1) * DEGRAD * 0.5);
        ra = re * sf / Math.pow(ra, sn);
        double theta = v2 * DEGRAD - olon;
        if (theta > Math.PI)
            theta -= 2.0 * Math.PI;
        if (theta < -Math.PI)
            theta += 2.0 * Math.PI;
        theta *= sn;

        map.put("x", Math.floor(ra * Math.sin(theta) + XO + 0.5));
        map.put("y", Math.floor(ro - ra * Math.cos(theta) + YO + 0.5));

        return map;
    }

    public static void getData() {
        List<Map<String, Object>> list = getXml("서울 서초구 서초동");
        //출처: http://mainia.tistory.com/2237 [녹두장군 - 상상을 현실로]
        for (Map<String, Object> map : list) {
            Iterator<String> keySetIterator = map.keySet().iterator();
            while (keySetIterator.hasNext()) {
                String key = keySetIterator.next();
                String key2 = (key + " : " + map.get(key)).toString();
                html.append(key2);
            }
            html.append("\n");

            // 출처: http://mainia.tistory.com/2237 [녹두장군 - 상상을 현실로]
            // for (String key : map.keySet()){
            //     System.out.println("key:"+key+",value:"+map.get(key));
            // }
            //출처: http://hellogk.tistory.com/10 [IT Code Storage]
            //System.out.println(map);
        }
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

        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.recogBtn:
                    mRecognizer.startListening(intent);
                    break;

                case R.id.playBtn:
                    startService(intent2);
                    playBtn.setEnabled(false);
                    playBtn.setText("pressed.");
                    break;

                case R.id.weather_btn:
                    weatherBtn.setText("pressed");
                    //weather_view01.setText("Current Location is : 서울시 서초구 서초동 ");
                    try {
                        getData();
                        weather_view01.setText(html);
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
                        playBtn.setEnabled(false);
                        playBtn.setText("STOP");
                    } else if (data.equals("pause")) {
                        stopService(intent2);
                        playBtn.setEnabled(true);
                        playBtn.setText("PLAY");
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