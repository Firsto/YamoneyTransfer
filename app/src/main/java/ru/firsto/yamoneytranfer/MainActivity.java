package ru.firsto.yamoneytranfer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.yandex.money.api.exceptions.InsufficientScopeException;
import com.yandex.money.api.exceptions.InvalidRequestException;
import com.yandex.money.api.exceptions.InvalidTokenException;
import com.yandex.money.api.methods.AccountInfo;
import com.yandex.money.api.methods.BaseProcessPayment;
import com.yandex.money.api.methods.ProcessPayment;
import com.yandex.money.api.methods.RequestPayment;
import com.yandex.money.api.methods.params.P2pTransferParams;
import com.yandex.money.api.net.DefaultApiClient;
import com.yandex.money.api.net.OAuth2Session;

import java.io.IOException;
import java.math.BigDecimal;

import ru.yandex.money.android.utils.Views;

public class MainActivity extends Activity {

    public static final String CLIENT_ID = "F2A08DEB06E303ECED4F480F4C92BB2D4DE68C5F3AC502C7116ED6DE2ECB0B24";

    public static final String ACC_MINE = "41001158150802";
    public static final String ACC_COLIPASS = "410013069217129";

    private OAuth2Session mSession = new OAuth2Session(new DefaultApiClient(CLIENT_ID));
    private AccountInfo mAccountInfo;

    SharedPreferences mPreferences;

    private TextView tvAccount, tvBalance;
    private EditText etPayee, etAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnAuth = findButton(R.id.auth_button);
        btnAuth.setClickable(true);
        btnAuth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(MainActivity.this, AuthActivity.class), 1);
            }
        });

        findButton(R.id.request_payment).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isValidFields()) {
                    new PaymentTask().execute();
                } else {
                    Toast.makeText(MainActivity.this, R.string.required_fields, Toast.LENGTH_SHORT).show();
                }
            }
        });
        findButton(R.id.revoke_auth).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new RevokeAuthTask().execute(mPreferences.getString("token", null));
            }
        });

        RelativeLayout account = (RelativeLayout) findViewById(R.id.account_info);

        tvAccount = findTextView(R.id.account);
        tvBalance = findTextView(R.id.account_balance);

        etPayee = findEditText(R.id.payee);
        etAmount = findEditText(R.id.amount);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String token;
        if ((token = mPreferences.getString("token", null)) != null) {
            btnAuth.setVisibility(View.GONE);
            account.setVisibility(View.VISIBLE);
            getAccountInfo(token);
        } else {
            btnAuth.setVisibility(View.VISIBLE);
            account.setVisibility(View.GONE);
            btnAuth.setClickable(true);
        }
    }

    private void getAccountInfo(String token) {
        new AccountInfoTask().execute(token);
    }

    private class AccountInfoTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {
            AccountInfo.Request request = new AccountInfo.Request();
            mSession.setAccessToken(params[0]);
            try {
                mAccountInfo = mSession.execute(request);
            } catch (IOException | InvalidRequestException | InsufficientScopeException | InvalidTokenException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (mAccountInfo != null) {
                tvAccount.setText("Account: " + mAccountInfo.account);
                tvBalance.setText("Balance: " + mAccountInfo.balance.toString());
            }
        }
    }

    private class RevokeAuthTask extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... token) {

            if (token.length != 0) {
                Request request = new Request.Builder()
                        .url("https://money.yandex.ru/api/revoke")
                        .addHeader("Authorization", "Bearer " + token[0])
                        .post(null)
                        .build();
                try {
                    Response response = new DefaultApiClient(CLIENT_ID).getHttpClient().newCall(request).execute();
                    return response.isSuccessful();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                Toast.makeText(MainActivity.this, "Authorization token revoked", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MainActivity.this, "Error. Token invalid or was already revoked", Toast.LENGTH_LONG).show();
            }
            mPreferences.edit().clear().apply();
            refresh();
        }
    }

    private class PaymentTask extends AsyncTask<Void, Void, ProcessPayment> {

        @Override
        protected ProcessPayment doInBackground(Void... params) {

            P2pTransferParams transferParams = new P2pTransferParams.Builder(getPayee())
                    .setAmount(getAmount())
                    .build();
//            transferParams.paymentParams.put("test_payment", "true");
//            transferParams.paymentParams.put("test_card", "available");
//            transferParams.paymentParams.put("test_result", "success");
            RequestPayment.Request request = RequestPayment.Request.newInstance(transferParams);
            RequestPayment requestPayment = null;
            ProcessPayment processPayment = null;
            if (mSession.isAuthorized()) {
                try {
                    requestPayment = mSession.execute(request);
                    if (requestPayment.requestId != null && !TextUtils.isEmpty(requestPayment.requestId)) {
                        ProcessPayment.Request requestProcess = new ProcessPayment.Request(requestPayment.requestId);
//                        requestProcess.setTestResult(ProcessPayment.TestResult.SUCCESS);
                        processPayment = mSession.execute(requestProcess);
                    }
                } catch (IOException | InvalidRequestException | InsufficientScopeException | InvalidTokenException e) {
                    e.printStackTrace();
                }
            }

            return processPayment;
        }

        @Override
        protected void onPostExecute(ProcessPayment processPayment) {
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Payment process status")
                    .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            getAccountInfo(mPreferences.getString("token", null));
                        }
                    });

            if (processPayment != null && processPayment.status == BaseProcessPayment.Status.SUCCESS) {
                dialogBuilder.setMessage(String.format("Payment to %s with amount of %.2f is successful!",
                        processPayment.payee, processPayment.creditAmount)).create().show();
            } else {
                dialogBuilder.setMessage("Payment was unsuccessful, something wrong...").create().show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (resultCode) {
            case RESULT_OK:
                String token = data.getStringExtra("token");
                mPreferences.edit().putString("token", token).apply();
                getAccountInfo(token);
                refresh();
                break;
            case RESULT_CANCELED:
                break;
            default:
                break;
        }
    }

    private Button findButton(int id) {
        return (Button) findViewById(id);
    }
    private TextView findTextView(int id) {
        return (TextView) findViewById(id);
    }
    private EditText findEditText(int id) {
        return (EditText) findViewById(id);
    }

    private boolean isValidFields() {
        return !TextUtils.isEmpty(Views.getTextSafely(etPayee)) &&
                !TextUtils.isEmpty(Views.getTextSafely(etAmount)) && getAmount().doubleValue() > 0;
    }

    private String getPayee() {
        return Views.getTextSafely(etPayee).replaceAll("\\D", "");
    }

    private BigDecimal getAmount() {
        return new BigDecimal(Views.getTextSafely(etAmount));
    }

    private void refresh() {
        Intent intent = getIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        finish();
        startActivity(intent);
    }
}
