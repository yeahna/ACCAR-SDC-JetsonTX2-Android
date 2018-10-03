package com.example.sm_pc.googlestt;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends AppCompatActivity {

    private TextView textView;
    TextView debug;
    private Button recogBtn;

    private Socket socket;
    private PrintWriter socketWriter;

    private String data;
    private Intent intent;
    private SpeechRecognizer mRecognizer;

    private final int MY_PERMISSIONS_RECORD_AUDIO = 1;
    public static int TO_GRID = 0;

    /*위치*/
    LocationManager lm;
    double longitude = 0;   // 위도
    double latitude = 0;    // 경도

    /*날씨*/
    Document doc = null;
    String weather = null;
    String temp = null;
    String hour = null;

    /*음성*/
    TextToSpeech tts;
    String address;

    /*노래*/
    private Intent musicIntent;

    @Override
    protected void onStop() { //socket 통신 종료 함수
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

        debug = (TextView) findViewById(R.id.debug);

        //set location config
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            }
        }

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


        /*노래*/
        musicIntent = new Intent("playmusic");
        musicIntent.setPackage(this.getPackageName());

        mRecognizer = SpeechRecognizer.createSpeechRecognizer(this);                    // 음성 인식 객체
        mRecognizer.setRecognitionListener(recognitionListener);

        recogBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mRecognizer.startListening(intent);
            }
        });

        /* 위치, 날씨 정보*/
        setLocation();

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) { //Google TTS 언어 설정
                if(status != TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.KOREAN);
                }
            }
        });
    }

    class SendThread extends Thread{ // socket 통신의 thread 문제 해결

        @Override
        public void run() {
            try {
                socket = new Socket("192.168.43.160", 8888); //이것을 내 포트로 바꾸면 된다 192.168.43.160
                //socket = new Socket("192.168.11.237", 8888);

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

    private RecognitionListener recognitionListener = new RecognitionListener() { //음성 인식 함수
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

            textView.setText(data); //인식한 명령어 출력

            // run sendSocket Thread
            if(data != null) {
                Log.w("TEST", " " + data);

                if(data.equals("날씨")){
                    GetXMLTask task = new GetXMLTask();
                    String str = "http://www.kma.go.kr/wid/queryDFS.jsp?";
                    LatXLngY tmp = convertGRID_GPS(TO_GRID, latitude, longitude);
                    str += "gridx=" + (int)tmp.x + "&gridy=" + (int)tmp.y;
                    Log.i("tmp", str);
                    task.execute(str);

                }else if(data.equals("음악 재생")){
                    startService(musicIntent);
                }else if(data.equals("음악 정지")){
                    stopService(musicIntent);
                }
                else {
                    SendThread thread = new SendThread();
                    thread.start();
                }
            }
        }

        @Override
        public void onPartialResults(Bundle bundle) {
        }

        @Override
        public void onEvent(int i, Bundle bundle) {
        }
    };


    private void setLocation(){     // 현재 위도, 경도 알아내기
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        try{
            debug.setText("수신중..");     // GPS 제공자의 정보가 바뀌면 콜백하도록 리스너 등록하기~!!!
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, // 등록할 위치제공자
                    100, // 통지사이의 최소 시간간격 (miliSecond)
                    1, // 통지사이의 최소 변경거리 (m)
                    mLocationListener);
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, // 등록할 위치제공자
                    100, // 통지사이의 최소 시간간격 (miliSecond)
                    1, // 통지사이의 최소 변경거리 (m)
                    mLocationListener);
        }catch(SecurityException ex){ }
    }

    private void speechWeather(){
        String str = "현재" + address + "의 " + hour + "시 온도는 " + temp + "도 날씨는 " + weather + "입니다"; // hour의 태그만 받아서 오면 시간까지 출력 가능
        tts.setPitch((float)0.1);
        tts.setSpeechRate((float)1.0);
        tts.speak(str, TextToSpeech.QUEUE_FLUSH, null);
    }

    private class GetXMLTask extends AsyncTask<String, Void, Document> {

        @Override
        protected Document doInBackground(String... urls) {
            URL url;
            try {
                url = new URL(urls[0]);
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder(); //XML문서 빌더 객체를 생성
                doc = db.parse(new InputSource(url.openStream())); //XML문서를 파싱한다.
                doc.getDocumentElement().normalize();

            } catch (Exception e) {
                Toast.makeText(getBaseContext(), "Parsing Error", Toast.LENGTH_SHORT).show();
            }
            return doc;
        }

        @Override
        protected void onPostExecute(Document doc) {

            //data태그가 있는 노드를 찾아서 리스트 형태로 만들어서 반환
            NodeList nodeList = doc.getElementsByTagName("data");
            //data 태그를 가지는 노드를 찾음, 계층적인 노드 구조를 반환

            Node node = nodeList.item(0);
            Element fstElmnt = (Element) node;
            NodeList nameList  = fstElmnt.getElementsByTagName("temp");
            Element nameElement = (Element) nameList.item(0);
            nameList = nameElement.getChildNodes();
            temp = ((Node) nameList.item(0)).getNodeValue();


            NodeList websiteList = fstElmnt.getElementsByTagName("wfKor");
            //<wfKor>맑음</wfKor> =====> <wfKor> 태그의 첫번째 자식노드는 TextNode 이고 TextNode의 값은 맑음
            weather = websiteList.item(0).getChildNodes().item(0).getNodeValue();

            NodeList hrList = fstElmnt.getElementsByTagName("hour");    //<hour>24</hour>
            hour = hrList.item(0).getChildNodes().item(0).getNodeValue();

            /* 날씨 TTS */
            debug.setText(hour + "," + temp + "," + weather);
            speechWeather();
            super.onPostExecute(doc);
        }
    }//end inner class - GetXMLTask

    private final LocationListener mLocationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            //여기서 위치값이 갱신되면 이벤트가 발생한다.
            //값은 Location 형태로 리턴되며 좌표 출력 방법은 다음과 같다.

            Log.d("test", "onLocationChanged, location:" + location);

            longitude = location.getLongitude(); //경도
            latitude = location.getLatitude();   //위도

            debug.setText("위도 : " + longitude + "\n경도 : " + latitude);

            address = getAddress(getApplicationContext(), latitude, longitude);
            Log.i("tmp", address);
        }
        public void onProviderDisabled(String provider) {
            // Disabled시
            Log.d("test", "onProviderDisabled, provider:" + provider);
        }

        public void onProviderEnabled(String provider) {
            // Enabled시
            Log.d("test", "onProviderEnabled, provider:" + provider);
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
            // 변경시
            Log.d("test", "onStatusChanged, provider:" + provider + ", status:" + status + " ,Bundle:" + extras);
        }
    };

    public static String getAddress(Context mContext,double lat, double lng) {
        String nowAddress ="현재 위치를 확인 할 수 없습니다.";
        Geocoder geocoder = new Geocoder(mContext, Locale.KOREA);
        List<Address> address;
        try {
            if (geocoder != null) {
                //세번째 파라미터는 좌표에 대해 주소를 리턴 받는 갯수로
                //한좌표에 대해 두개이상의 이름이 존재할수있기에 주소배열을 리턴받기 위해 최대갯수 설정
                address = geocoder.getFromLocation(lat, lng, 1);

                if (address != null && address.size() > 0) {
                    // 주소 받아오기
                    String currentLocationAddress = address.get(0).getAddressLine(0).toString();
                    nowAddress  = currentLocationAddress;

                }
            }
        } catch (IOException e) {
            Log.i("error", "주소를 가져 올 수 없습니다.");
            e.printStackTrace();
        }
        return nowAddress;
    }

    private LatXLngY convertGRID_GPS(int mode, double lat_X, double lng_Y ) {
        double RE = 6371.00877; // 지구 반경(km)
        double GRID = 5.0; // 격자 간격(km)
        double SLAT1 = 30.0; // 투영 위도1(degree)
        double SLAT2 = 60.0; // 투영 위도2(degree)
        double OLON = 126.0; // 기준점 경도(degree)
        double OLAT = 38.0; // 기준점 위도(degree)
        double XO = 43; // 기준점 X좌표(GRID)
        double YO = 136; // 기1준점 Y좌표(GRID)

        //
        // LCC DFS 좌표변환 ( code : "TO_GRID"(위경도->좌표, lat_X:위도,  lng_Y:경도), "TO_GPS"(좌표->위경도,  lat_X:x, lng_Y:y) )
        //

        double DEGRAD = Math.PI / 180.0;
        double RADDEG = 180.0 / Math.PI;

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
        LatXLngY rs = new LatXLngY();

        if (mode == TO_GRID) {
            rs.lat = lat_X;
            rs.lng = lng_Y;
            double ra = Math.tan(Math.PI * 0.25 + (lat_X) * DEGRAD * 0.5);
            ra = re * sf / Math.pow(ra, sn);
            double theta = lng_Y * DEGRAD - olon;
            if (theta > Math.PI) theta -= 2.0 * Math.PI;
            if (theta < -Math.PI) theta += 2.0 * Math.PI;
            theta *= sn;
            rs.x = Math.floor(ra * Math.sin(theta) + XO + 0.5);
            rs.y = Math.floor(ro - ra * Math.cos(theta) + YO + 0.5);
        }
        else {
            rs.x = lat_X;
            rs.y = lng_Y;
            double xn = lat_X - XO;
            double yn = ro - lng_Y + YO;
            double ra = Math.sqrt(xn * xn + yn * yn);
            if (sn < 0.0) {
                ra = -ra;
            }
            double alat = Math.pow((re * sf / ra), (1.0 / sn));
            alat = 2.0 * Math.atan(alat) - Math.PI * 0.5;

            double theta = 0.0;
            if (Math.abs(xn) <= 0.0) {
                theta = 0.0;
            }
            else {
                if (Math.abs(yn) <= 0.0) {
                    theta = Math.PI * 0.5;
                    if (xn < 0.0) {
                        theta = -theta;
                    }
                }
                else theta = Math.atan2(xn, yn);
            }
            double alon = theta / sn + olon;
            rs.lat = alat * RADDEG;
            rs.lng = alon * RADDEG;
        }
        return rs;
    }

    class LatXLngY {
        public double lat;
        public double lng;
        public double x;
        public double y;
    }
}