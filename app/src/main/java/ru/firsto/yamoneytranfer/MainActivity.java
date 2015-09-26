package ru.firsto.yamoneytranfer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
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
    public static final String FIRST_START = "first_start";

    private OAuth2Session mSession = new OAuth2Session(new DefaultApiClient(CLIENT_ID));
    private AccountInfo mAccountInfo;

    SharedPreferences mPreferences;
    SecurePreferences mSecurePreferences;

    private Button btnAuth;
    private RelativeLayout account;
    private TextView tvAccount, tvBalance;
    private EditText etPayee, etAmount, etAmountDue, etMessage, etExpiration;
    private CheckBox chbProtection;
    private String mPin;
    private int mPinAttempt = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnAuth = findButton(R.id.auth_button);
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

        account = (RelativeLayout) findViewById(R.id.account_info);

        btnAuth.setVisibility(View.INVISIBLE);
        account.setVisibility(View.INVISIBLE);

        tvAccount = findTextView(R.id.account);
        tvBalance = findTextView(R.id.account_balance);

        etPayee = findEditText(R.id.payee);
        etAmount = findEditText(R.id.amount);
        etAmountDue = findEditText(R.id.amount_due);
        etMessage = findEditText(R.id.comment);
        etExpiration = findEditText(R.id.expiration);

        chbProtection = (CheckBox) findViewById(R.id.protection);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (!mPreferences.getBoolean(FIRST_START, true)) {
            showPinDialog("Enter Pin to access:", false);
        } else {
            showPinDialog("Create Pin to encrypt your access token (4-6 digits):", true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
//        showPinDialog("Enter Pin to access:");
    }

    private void showPinDialog(String message, final boolean check) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Secret area");
        alert.setMessage(message);
        alert.setCancelable(false);

        View dialogView = this.getLayoutInflater().inflate(R.layout.field_pin, null);
        final EditText input = (EditText) dialogView.findViewById(R.id.field_pin);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTransformationMethod(PasswordTransformationMethod.getInstance());
        final EditText inputCheck = (EditText) dialogView.findViewById(R.id.field_pin_check);
        if (check) {
            inputCheck.setInputType(InputType.TYPE_CLASS_NUMBER);
            inputCheck.setTransformationMethod(PasswordTransformationMethod.getInstance());
            inputCheck.setVisibility(View.VISIBLE);
        }
        alert.setView(dialogView);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                mPin = input.getText().toString();
                mSecurePreferences = new SecurePreferences(MainActivity.this, "secret", mPin, true);
                if (check) {
                    mPreferences.edit().putBoolean(FIRST_START, false).apply();
                    mSecurePreferences.put("pin_check", "checked");
                }
                init();
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });

        final AlertDialog dialog = alert.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (check) {
                    if (s.toString().equals(inputCheck.getText().toString())) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!TextUtils.isEmpty(s) && s.length() > 3);
                    else dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                } else {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!TextUtils.isEmpty(s) && s.length() > 3);
                }
            }
        });

        if (check) {
            inputCheck.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (s.toString().equals(input.getText().toString())) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!TextUtils.isEmpty(s) && s.length() > 3);
                    else dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                }
            });
        }
    }

    private void init() {
        String token;
        if ((token = mSecurePreferences.getString("token")) != null) {
            btnAuth.setVisibility(View.GONE);
            account.setVisibility(View.VISIBLE);
            getAccountInfo(token);
        } else if (!mSecurePreferences.containsKey("pin_check")) {
            if (mPinAttempt == 0) {
                mPreferences.edit().clear().apply();
                mSecurePreferences.clear();
                finish();
            }
            showPinDialog("Enter correct Pin (" + mPinAttempt-- + " attempts remaining):", false);
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
            mSecurePreferences.clear();
            refresh();
        }
    }

    private class PaymentTask extends AsyncTask<Void, Void, ProcessPayment> {

        private boolean isCodePro = false;
        private String mComment = "Comment was not specified";

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            isCodePro = chbProtection.isChecked();
            String message = etMessage.getText().toString();
            if (!TextUtils.isEmpty(message)) mComment = message;
        }

        @Override
        protected ProcessPayment doInBackground(Void... params) {

            P2pTransferParams transferParams = new P2pTransferParams.Builder(getPayee())
                    .setAmount(getAmount()).setCodepro(isCodePro).setComment(mComment).setMessage(mComment)
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
                            getAccountInfo(mSecurePreferences.getString("token"));
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
                mSecurePreferences.put("token", token);
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
