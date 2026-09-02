package com.ict.trippamine;

import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ict.trippamine.databinding.ActivityMainBinding;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * TripPamine 웹뷰 랩핑(Wrapping) 앱의 메인(유일한) 화면.
 * <p>
 * 강사님이 만들어주신 WebView12 예제 프로젝트를 뼈대로 삼아서
 * 우리 TripPamine 프론트엔드(React + Vite, TripPamineFE)를 그대로 웹뷰 안에 띄우고,
 * 안드로이드 <-> 웹(자바스크립트) 양방향 통신까지 실습해볼 수 있게 만든 화면이다.
 * <p>
 * 화면 구성(activity_main.xml 참고)
 * 1) 브랜드 헤더 : 프론트 Header.tsx 로고("Trip"+"Pamine") 그대로 재현
 * 2) 주소창 : 주소 입력(EditText) + 이동(Go) + 뒤로가기(Back)
 * 3) 기능 테스트 버튼 3개 : 정적 HTML / 동적 HTML(차트) / 프론트와 통신 테스트
 * 4) 로딩 진행바 : 페이지 로딩 진행률 표시
 * 5) 당겨서 새로고침(SwipeRefreshLayout) + 웹뷰(WebView)
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "TRIPPAMINE_WEBVIEW";

    // findViewById() 대신 뷰에 바로 접근하기 위한 ViewBinding 객체
    // (activity_main.xml 의 id 들이 자동으로 바인딩 클래스의 필드로 생성된다. 예) binding.webview)
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 화면을 상태바/네비게이션바까지 꽉 채워서 그리도록 설정 (요즘 앱들의 기본 스타일)
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 시스템 바(상태바/제스처 영역)에 우리 콘텐츠가 가려지지 않도록 패딩을 넣어준다.
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 1. 웹뷰 설정 (JS 허용, 브릿지 등록, 알림창 커스터마이징 등)
        setupWebView();
        // 2. 버튼/스와이프 등 이벤트 리스너 부착
        setupListener();
        // 3. 하드웨어 뒤로가기 버튼 처리 (웹뷰 히스토리 우선 -> 없으면 앱 종료)
        setupBackPressedHandler();

        // 4. 앱을 켜자마자 기본 개발 서버 주소(TripPamineFE)를 자동으로 로드해준다.
        //    (강사님 예제는 버튼을 눌러야 로드됐지만, 실제 서비스 앱처럼 자동 로딩되게 개선했다.)
        binding.webview.loadUrl(binding.editUrl.getText().toString());

    } //// onCreate

    /**
     * 버튼, 스와이프 새로고침 등 사용자 조작에 대한 이벤트 리스너를 모아서 등록한다.
     */
    private void setupListener() {

        // ------------------------------------------------------------------
        // - 이동(Go) 버튼
        //   주소창(EditText)에 입력된 URL을 웹뷰에 로드한다.
        //   예) http://10.0.2.2:5173  (에뮬레이터에서 PC의 5173 포트 Vite 서버로 접속)
        //       http://192.168.0.5:5173 (같은 와이파이의 실제 폰에서 접속할 때)
        // ------------------------------------------------------------------
        binding.buttonGo.setOnClickListener(v -> {
            String url = binding.editUrl.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, "주소를 입력해주세요", Toast.LENGTH_SHORT).show();
                return;
            }
            Log.i(TAG, "이동 버튼 클릭. 로드할 URL: " + url);
            binding.webview.loadUrl(url);
        });

        // - 뒤로가기(Back) 버튼 : 웹뷰 내부의 방문 기록을 한 단계 되돌린다.
        binding.buttonBack.setOnClickListener(v -> {
            if (binding.webview.canGoBack()) {
                binding.webview.goBack();
            } else {
                Toast.makeText(this, "더 이상 이전 페이지가 없습니다", Toast.LENGTH_SHORT).show();
            }
        });

        // ------------------------------------------------------------------
        // - 오프라인 화면(정적 HTML) 로드
        //   인터넷이 안 되거나 서버 점검 중일 때 보여줄 수 있는 정적인 소개 페이지.
        //   assets/html/index.html 을 그대로 로드한다.
        //   (사전 작업 : app 우클릭 -> New -> Folder -> Assets Folder 로 assets 폴더 생성
        //               -> 그 아래 html, images 디렉토리 생성
        //               -> assets 폴더 자체는 URL에 안 쓰고 "file:///android_asset/" 로 접근한다.
        //                  (file:// 뒤에 android_asset 이지, 진짜 assets 폴더명이 아님에 주의!)
        // ------------------------------------------------------------------
        binding.btnStatic.setOnClickListener(v ->
                binding.webview.loadUrl("file:///android_asset/html/index.html"));

        // - 동적 HTML(차트) 로드 : 여행 경비 카테고리 데이터를 자바에서 만들어서 웹뷰에 주입
        binding.btnDynamic.setOnClickListener(v -> loadExpenseChart());

        // ------------------------------------------------------------------
        // - 프론트와 통신 테스트 (안드로이드 -> 웹 방향)
        //   1) 프론트(React) 화면이 웹뷰에 로드되어 있어야 하고
        //   2) 프론트 쪽 JS 전역에 window.receiveFromAndroid(message) 함수가 정의돼 있어야 한다.
        //      (예: index.html <script> 안에
        //           window.receiveFromAndroid = function(msg){ ... } 형태로 정의)
        //   3) 안드로이드에서는 webview.evaluateJavascript(...) 로 그 함수를 직접 호출한다.
        //   ※ 웹(반대 방향, 웹 -> 안드로이드) 통신은 아래 WebInterface 클래스 참고.
        // ------------------------------------------------------------------
        binding.btnCallJs.setOnClickListener(v -> {
            // UI 스레드에서 실행되도록 post() 안에서 evaluateJavascript() 호출
            binding.webview.post(() -> binding.webview.evaluateJavascript(
                    "javascript:window.receiveFromAndroid && window.receiveFromAndroid('안드로이드 앱에서 보낸 메시지입니다 \uD83D\uDC4B')",
                    null));
        });

        // - 당겨서 새로고침 : 웹뷰를 아래로 당기면 현재 페이지를 다시 로드한다.
        binding.swipeRefresh.setOnRefreshListener(() -> {
            binding.webview.reload();
        });

    } //// setupListener

    /**
     * 하드웨어(제스처) 뒤로가기 버튼을 눌렀을 때
     * - 웹뷰 안에 방문 기록이 남아있으면 웹뷰 안에서만 뒤로 이동하고
     * - 더 이상 뒤로 갈 곳이 없으면 그때는 앱을 종료(기본 동작)하도록 처리한다.
     * (안드로이드 최신 권장 방식인 OnBackPressedCallback 사용. deprecated 된
     *  onBackPressed() 오버라이드 대신 이 방식을 쓴다.)
     */
    private void setupBackPressedHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.webview.canGoBack()) {
                    binding.webview.goBack();
                } else {
                    // 콜백을 잠깐 비활성화하고 dispatcher에게 다시 뒤로가기를 위임 -> 앱 종료(기본 동작) 수행
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    /**
     * assets/html/template.html 을 읽어서 "여행 경비 카테고리별 지출 비율" 파이차트 데이터를
     * 문자열로 만들어 끼워넣은 뒤, 완성된 HTML 문자열을 웹뷰에 직접 로드한다.
     * (강사님 예제의 동적 HTML 로드 기법을 그대로 사용하되, 데이터만 우리 서비스의
     *  가계부(AccountBook) 기능과 어울리는 여행 경비 항목으로 바꿨다.)
     */
    private void loadExpenseChart() {
        AssetManager assetManager = getAssets();
        try (InputStream is = assetManager.open("html/template.html")) {

            byte[] bytes = new byte[is.available()];
            // 스트림에서 한 바이트씩 읽어서 bytes 배열에 저장
            is.read(bytes);
            String htmlSource = new String(bytes);

            // 여행 경비 카테고리별 데모 데이터 (단위: 만원)
            // LinkedHashMap 을 써서 항상 같은 순서로 차트가 그려지게 했다.
            Map<String, Integer> expenseMap = new LinkedHashMap<>();
            expenseMap.put("숙박비", 35);
            expenseMap.put("식비", 20);
            expenseMap.put("교통비", 15);
            expenseMap.put("액티비티", 25);
            expenseMap.put("기타", 5);

            // Map -> 구글차트가 이해하는 자바스크립트 배열 문자열 형태로 변환
            // 예) ['숙박비',35],['식비',20], ...
            StringBuilder pieData = new StringBuilder();
            Set<String> keys = expenseMap.keySet();
            for (String key : keys) {
                pieData.append(String.format("['%s',%d],", key, expenseMap.get(key)));
            }

            // template.html 안의 "placeholder" 문자열을 실제 데이터로 치환해서 웹뷰에 로드
            binding.webview.loadDataWithBaseURL(
                    "file:///android_asset/",
                    htmlSource.replace("placeholder", pieData.toString()),
                    "text/html",
                    "UTF-8",
                    null);

        } catch (IOException e) {
            Log.e(TAG, "template.html 로드 실패", e);
        }
    }

    /**
     * WebView 자체의 동작(설정, URL 로딩 규칙, JS 브릿지 등록 등)을 설정한다.
     */
    private void setupWebView() {

        WebSettings settings = binding.webview.getSettings();

        // 1. 필수 설정 -------------------------------------------------------
        // 자바스크립트가 실행되도록 설정 : 웹뷰는 기본이 자바스크립트 비활성화 상태다.
        // 우리 프론트(React)는 자바스크립트로 렌더링되는 SPA 이므로 반드시 켜줘야 한다.
        settings.setJavaScriptEnabled(true);
        // DOMStorage(localStorage/sessionStorage) 활성화 : React Router, 로그인 토큰 저장 등에 필수
        settings.setDomStorageEnabled(true);
        // 파일 접근 제한 : 웹뷰가 file://, content:// 로 기기 내부 파일에 접근하지 못하도록 차단(보안)
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        // 2. 필수 설정 -------------------------------------------------------
        // "웹뷰 영역 안"에서만 URL 이동이 일어나도록 설정. (상단 주소창/버튼 영역은 그대로 유지)
        // 아래를 생략하면 웹뷰가 새 탭/외부 브라우저를 열려고 하거나 레이아웃을 깨뜨릴 수 있다.
        binding.webview.setWebViewClient(new TripPamineWebViewClient());

        // 3. 필수 설정 -------------------------------------------------------
        // 아래를 설정하지 않으면 프론트에서 alert()/confirm() 을 호출해도 창이 뜨지 않는다.
        // 기본 WebChromeClient 는 웹 스타일 그대로라 모바일 UI와 안 어울리므로,
        // MaterialAlertDialogBuilder 로 안드로이드 스타일 다이얼로그로 바꿔치기한다.
        binding.webview.setWebChromeClient(new TripPamineWebChromeClient());

        // 4. 선택 설정 -------------------------------------------------------
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // 5. 프론트(웹)와 통신하기 위한 자바스크립트 브릿지 객체 등록 --------------
        //    두 번째 인자로 넣은 이름("androidBridge")이 프론트 쪽 window 객체에 그대로 연결된다.
        //    프론트(React)에서는 아래처럼 호출하면 안드로이드 네이티브 코드가 실행된다.
        //      if (window.androidBridge) {
        //          window.androidBridge.sendMessageToAndroid("장바구니에 담았어요!");
        //      }
        //    (window.androidBridge 존재 여부를 꼭 체크해야, 일반 브라우저에서 접속했을 때
        //     오류가 나지 않는다 - 안드로이드 앱 안에서만 이 객체가 존재하기 때문)
        binding.webview.addJavascriptInterface(new WebInterface(), "androidBridge");

    } //// setupWebView

    /**
     * URL 로딩 규칙 + 로딩 진행바(ProgressBar)/새로고침 상태 제어를 담당하는 WebViewClient.
     */
    private class TripPamineWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme(); // scheme = protocol (http, https, ws, tel, mailto ...)

            boolean isWebScheme = "http".equals(scheme)
                    || "https".equals(scheme)
                    || "ws".equals(scheme)
                    || "file".equals(scheme);

            if (!isWebScheme) {
                // http/https/ws/file 이 아닌 URL(예: tel:, mailto:, kakaomap: 등)은
                // 웹뷰가 직접 처리하지 못하므로, 해당 스킴을 처리할 수 있는 외부 앱으로 넘긴다.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception e) {
                    Log.w(TAG, "이 URL을 처리할 수 있는 앱이 없습니다: " + uri, e);
                }
                return true; // 웹뷰는 이 URL을 더 이상 로딩하지 않음 (화면에 표시 X)
            }
            return false; // 웹뷰가 직접 URL을 로딩함 (화면에 표시 O)
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            // 페이지 로딩을 시작하면 진행바를 보여준다.
            binding.progressBar.setVisibility(android.view.View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            // 로딩이 끝나면 진행바를 숨기고, 당겨서 새로고침 중이었다면 그 로딩 표시도 꺼준다.
            binding.progressBar.setVisibility(android.view.View.GONE);
            binding.swipeRefresh.setRefreshing(false);
        }
    }

    /**
     * 자바스크립트의 alert()/confirm() 창을 안드로이드(머티리얼) 스타일로 커스터마이징하고,
     * 페이지 로딩 진행률(onProgressChanged)을 상단 ProgressBar 에 반영하는 WebChromeClient.
     */
    private class TripPamineWebChromeClient extends WebChromeClient {

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            // String url     : 경고창을 띄운 페이지의 URL
            // String message : 프론트에서 alert()에 넘긴 문자열

            Log.i(TAG, "alert() 요청 URL: " + url);

            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle("TripPamine 알림")
                    .setMessage(message)
                    // result.confirm() 을 호출해야 웹 쪽 alert() 이 "확인 눌림" 상태로 넘어간다.
                    // 호출하지 않으면 확인 버튼을 누르지 않은 것처럼 웹 페이지의 다른 부분이 멈춘 것처럼 보인다.
                    .setPositiveButton("확인", (dialog, which) -> result.confirm())
                    .setCancelable(false)
                    .show();

            return true; // 기본 자바스크립트 alert 창 대신, 지금 만든 커스텀 다이얼로그를 사용
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
            // confirm() 도 alert()과 같은 방식으로 안드로이드 스타일 다이얼로그로 바꾸고 싶다면
            // 여기서 setPositiveButton -> result.confirm(), setNegativeButton -> result.cancel() 로
            // 직접 구현하면 된다. 지금은 기본 동작을 그대로 사용한다.
            return super.onJsConfirm(view, url, message, result);
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            // 페이지 로딩 진행률(0~100)을 상단 얇은 ProgressBar 에 그대로 반영
            binding.progressBar.setProgress(newProgress);
            if (newProgress >= 100) {
                binding.progressBar.setVisibility(android.view.View.GONE);
            }
        }
    }

    /**
     * 프론트엔드(React)에서 호출할 수 있는 메소드를 정의하는 자바스크립트 브릿지 클래스.
     * (웹 -> 안드로이드 방향 통신)
     * <p>
     * 주의 : 프론트에서 호출 가능하게 만들 메소드에는 반드시 @JavascriptInterface
     * 어노테이션을 붙여야 한다. 붙이지 않으면 웹 쪽에서 호출해도 아무 반응이 없다.
     */
    public class WebInterface {

        /**
         * 프론트(React)에서 window.androidBridge.sendMessageToAndroid("메시지") 형태로 호출하면
         * 안드로이드 네이티브 Toast 메시지로 보여준다.
         * 예) 장바구니 담기, 결제 완료, 퀘스트 클리어 알림 등 웹에서 발생한 이벤트를
         *     네이티브 UI(Toast, 진동, 알림 등)로 보여주고 싶을 때 이 방식을 활용하면 된다.
         */
        @JavascriptInterface
        public void sendMessageToAndroid(String message) {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
        }
    }

} //////////// class MainActivity
