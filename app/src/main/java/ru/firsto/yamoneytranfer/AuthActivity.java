package ru.firsto.yamoneytranfer;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.yandex.money.api.exceptions.InsufficientScopeException;
import com.yandex.money.api.exceptions.InvalidRequestException;
import com.yandex.money.api.exceptions.InvalidTokenException;
import com.yandex.money.api.methods.Token;
import com.yandex.money.api.net.AuthorizationCodeResponse;
import com.yandex.money.api.net.DefaultApiClient;
import com.yandex.money.api.net.OAuth2Session;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Created by razor on 20.09.15.
 **/
public class AuthActivity extends Activity {

    public static final String CLIENT_ID = "F2A08DEB06E303ECED4F480F4C92BB2D4DE68C5F3AC502C7116ED6DE2ECB0B24";
    public static final String REDIRECT_URL = "https://money.yandex.ru";
    public static final String REDIRECT_URL_MOBILE = "https://m.money.yandex.ru";

    private DefaultApiClient defaultApiClient = new DefaultApiClient(CLIENT_ID);
    private AuthorizationCodeResponse mResponse;

    private WebView mWebView;
    private ProgressDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        initProgressDialog();
        if (!isFinishing())
            dialog.show();

        initAuthWeb();

        new AuthorizationTask().execute();
    }

    private void initProgressDialog() {
        dialog = new ProgressDialog(this);
        dialog.setTitle("Авторизация");
        dialog.setIcon(android.R.drawable.ic_secure);
        dialog.setMessage("Пожалуйста, подождите...");
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(true);

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                dialog.dismiss();
                Intent result = new Intent();
                setResult(Activity.RESULT_CANCELED, result);
                finish();
            }
        });
    }

    private void initAuthWeb() {
        mWebView = (WebView) findViewById(R.id.web);
        mWebView.setWebViewClient(new AuthWebViewClient());
        mWebView.setWebChromeClient(new AuthWebChromeClient());
        mWebView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        mWebView.getSettings().setRenderPriority(WebSettings.RenderPriority.HIGH);
        mWebView.getSettings().setJavaScriptEnabled(false);
        mWebView.getSettings().setBuiltInZoomControls(true);
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        this.setResult(Activity.RESULT_CANCELED, intent);
        finish();
    }

    private class AuthWebViewClient extends WebViewClient {

        private AuthWebViewClient() {
            super();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.contains(REDIRECT_URL)) {
                view.goBack();
                try {
                    mResponse = AuthorizationCodeResponse.parse(url);
                    new TokenObtainTask().execute(mResponse.code != null ? mResponse.code : "0");
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
                return false;
            }

            mWebView.loadUrl(url);
//            if (!url.contains(REDIRECT_URL_MOBILE)) view.goBack();
            return true;
        }
    }

    private class AuthWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (newProgress == 100)
                dialog.dismiss();
        }
    }

    private class AuthorizationTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... params) {
            RequestBody requestBody = new FormEncodingBuilder()
                    .add("client_id", CLIENT_ID)
                    .add("response_type", "code")
                    .add("redirect_uri", REDIRECT_URL)
                    .add("scope", "account-info operation-history operation-details payment-p2p.limit(1,10)")
                    .build();
            Request request = new Request.Builder()
                    .url("https://m.money.yandex.ru/oauth/authorize")
                    .post(requestBody)
                    .build();
            try {
                Response response = defaultApiClient.getHttpClient().newCall(request).execute();
                return response.headers().get("Location");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return REDIRECT_URL;
        }

        @Override
        protected void onPostExecute(String url) {
            mWebView.loadUrl(url);
        }
    }

    private class TokenObtainTask extends AsyncTask<String, Void, Token> {

        @Override
        protected void onPreExecute() {
            if (!isFinishing())
                dialog.show();
        }

        @Override
        protected void onPostExecute(Token token) {
            dialog.dismiss();
            if (token != null) {
                getAuthorizationStatusDialog(token).show();
            } else {
                Intent result = new Intent();
                setResult(Activity.RESULT_CANCELED, result);
                finish();
            }
        }

        @Override
        protected Token doInBackground(String... params) {
            return receiveToken(params[0]);
        }
    }

    private Token receiveToken(String code) {
        Token token = null;

        Token.Request request = new Token.Request(code, CLIENT_ID, REDIRECT_URL);
        OAuth2Session session = new OAuth2Session(defaultApiClient);
        try {
            token = session.execute(request);
        } catch (IOException | InvalidRequestException | InsufficientScopeException | InvalidTokenException e) {
            e.printStackTrace();
        }

        return token;
    }

    private AlertDialog getAuthorizationStatusDialog(final Token token) {
        AlertDialog.Builder builder;
        builder = new AlertDialog.Builder(this);
        builder.setIcon(android.R.drawable.ic_secure);
        builder.setTitle("Авторизация");
        if (token.error == null)
            builder.setMessage("Теперь вы можете переводить деньги.");
        else
            builder.setMessage("Ошибка: " + token.error.code);

        builder.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Intent authResult = new Intent();
                if (token.accessToken != null)
                    authResult.putExtra("token", token.accessToken);
                if (token.error != null)
                    authResult.putExtra("error", token.error.code);

                if (token.accessToken != null)
                    AuthActivity.this.setResult(RESULT_OK, authResult);
                else
                    AuthActivity.this.setResult(RESULT_CANCELED, authResult);
                finish();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        return dialog;
    }
}
