package com.example.sm_pc.googlestt;


import android.app.Application;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.json.simple.JSONValue;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Weather extends Application {
    public static void main(String args[]) {
        List<Map<String, Object>> list = getXml("서울 서초구 서초동");
        for (Map<String, Object> map : list) {
            System.out.println(map);
        }
    }

    public static List<Map<String, Object>> getXml(String locate) {

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

    public static Map<String, Object> getGridxy(double v1, double v2) {
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
}
