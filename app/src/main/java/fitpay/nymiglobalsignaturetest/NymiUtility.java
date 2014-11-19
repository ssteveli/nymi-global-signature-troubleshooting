package fitpay.nymiglobalsignaturetest;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;

import com.bionym.ncl.Ncl;
import com.bionym.ncl.NclBool;
import com.bionym.ncl.NclCallback;
import com.bionym.ncl.NclEvent;
import com.bionym.ncl.NclEventType;
import com.bionym.ncl.NclMode;
import com.bionym.ncl.NclProvision;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;


public class NymiUtility extends Activity {
    private final static String TAG = NymiUtility.class.getSimpleName();

    private NclCallback nclCallback;

    private boolean initalized = false;
    private int nymiHandle = -1;
    private ArrayList<NclProvision> provisions;

    private Button btnDiscovery;
    private Button btnInit;
    private Button btnAgree;
    private Button btnValidate;
    private Button btnAuthenticate;
    private Switch swAutoConnect;

    private EditText log;

    private boolean manualDiscoveryStarted = false;
    private boolean partnerDiscoveryStarted = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nymi_utility);

        provisions = new ArrayList<NclProvision>();
        loadProvisions();

        btnInit = (Button)findViewById(R.id.initButton);
        btnInit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                nclInit();
            }
        });

        btnDiscovery = (Button)findViewById(R.id.discoveryButton);
        btnDiscovery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDiscovery();
            }
        });

        btnAgree = (Button)findViewById(R.id.agreeButton);
        btnAgree.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                agree();
            }
        });

        btnValidate = (Button)findViewById(R.id.validateButton);
        btnValidate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                validate();
            }
        });

        btnAuthenticate = (Button)findViewById(R.id.authButton);
        btnAuthenticate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                partnerAuthentication();
            }
        });

        swAutoConnect = (Switch)findViewById(R.id.autoConnect);

        log = (EditText)findViewById(R.id.log);

        ((Button)findViewById(R.id.disconnectButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                disconnect();
            }
        });

        ((Button)findViewById(R.id.clearLog)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                log.setText(null);
            }
        });

        ((Button)findViewById(R.id.globalSign)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                globalSign();
            }
        });

        ((Button)findViewById(R.id.partnerDiscovery)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                partnerDiscovery();
            }
        });

        nclInit();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.nymi_utility, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void nclInit() {
        nclCallback = new NclCallback(this, "handleCallback", NclEventType.NCL_EVENT_ANY);
        final Context context = this;
        final String path = Environment.getExternalStorageDirectory() + "";
        Log.d(TAG, "external storage path: " + path);
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean b = Ncl.init(context, nclCallback, null, "FitPay", NymiConfig.NYMULATER_IP_ADDRESS, 9089);
                logUIMessage(String.format("Ncl.init() called, result=%s", String.valueOf(b)));
            }
        }).start();
    }

    private void disconnect() {
        Ncl.disconnect(nymiHandle);
    }

    private void startDiscovery() {
        Log.d(TAG, "starting discovery");
        Ncl.addBehavior(nclCallback);
        boolean result = Ncl.startDiscovery();
        Log.d(TAG, "starting discovery result: " + result);
        logUIMessage("Ncl.startDiscovery() called, result=" + result);
    }

    private void stopDiscovery() {
        Log.d(TAG, "stopping discovery");
        boolean result = Ncl.stopScan();
        logUIMessage("Ncl.stopScan() called, result=" + result);
    }

    private void agree() {
        Log.d(TAG, "ncl agreement starting");
        boolean result = Ncl.agree(nymiHandle);
        logUIMessage("Ncl.agree() called, result=" + result);
    }

    private void validate() {
        Log.d(TAG, "ncl connecting to nymi");
        boolean result = Ncl.startFinding(provisions, provisions.size(), NclBool.NCL_TRUE);
        logUIMessage("Ncl.startFinding() with provisions called, result=" + result);
        if (result) {
            manualDiscoveryStarted = true;
        }
    }

    public void partnerAuthentication() {
        Log.d(TAG, "submitting create global sig key pair");
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean result = Ncl.createGlobalSigKeyPair(nymiHandle, NymiConfig.staticPublicKey, NymiConfig.staticBionymSignature);
                logUIMessage("Ncl.createGlobalSigKeyPair() called, result=" + result);
            }
        }).start();
    }

    public void partnerDiscovery() {
        if (nymiHandle != -1) {
            logUIMessage("we're connected to a nymi, disconnect first");
            return;
        }

        Log.d(TAG, "starting partner discovery");
        partnerDiscoveryStarted = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean result = Ncl.startFinding(null, 0, NclBool.NCL_TRUE);
                logUIMessage("Ncl.startFinding() result: " + result);
            }
        }).start();
    }

    public void globalSign() {
        if (nymiHandle == -1) {
            logUIMessage("no nymi handle available");
            return;
        }

        Log.d(TAG, "issuing global sign");
        new Thread(new Runnable() {
            @Override
            public void run() {
                char[] adv = Ncl.getAdvertisment(nymiHandle);
                char[] signedNonce = Ncl.signAdvertisment(adv, NymiConfig.staticNonce, NymiConfig.staticPrivateKey);

                logUIMessage("advertisement: " + readableKey(adv));
                logUIMessage("signedNonce: " + readableKey(signedNonce));

                boolean result = Ncl.globalSign(
                        nymiHandle,
                        signedNonce,
                        NymiConfig.staticPublicKey,
                        NymiConfig.staticNonce);
                logUIMessage("Ncl.globalSign() result: " + result);
            }
        }).start();
    }

    public void handleCallback(NclEvent e, Object userData) {
        Log.d(TAG, "NclEvent Received: " + e.type);

        switch (e.type) {
            case NCL_EVENT_INIT: {
                Log.i(TAG, "ncl init completed: " + NclBool.values()[e.init.success]);
                logUIMessage(String.format("NCL_EVENT_INIT result=" + e.init.success));
                initalized = true;
                break;
            }

            case NCL_EVENT_DISCONNECTION: {
                Log.i(TAG, "ncl disconnect");
                logUIMessage("NCL_EVENT_DISCONNECTION received, reason=" + e.disconnection);
                nymiHandle = -1;
                break;
            }

            case NCL_EVENT_DETECTION: {
                Ncl.stopScan();
                partnerDiscoveryStarted = false;
                nymiHandle = e.detection.nymiHandle;

                logUIMessage("nymi found, rssi: " + e.detection.rssi);
                break;
            }

            case NCL_EVENT_DISCOVERY: {
                Log.i(TAG, "ncl discovery, rssi: " + e.discovery.rssi);
                logUIMessage("NCL_EVENT_DISCOVERY received, rssi=" + e.discovery.rssi);
                nymiHandle = e.discovery.nymiHandle;
                stopDiscovery();

                agree();
                break;
            }

            case NCL_EVENT_AGREEMENT: {
                Log.i(TAG, "ncl agreement");
                Log.d(TAG, "led pattern: " + e.agreement.leds);

                Log.d(TAG, "sending provision");
                boolean result = Ncl.provision(nymiHandle);
                logUIMessage("NCL_EVENT_AGREEMENT received, Ncl.provision() called, result=" + result);
                break;
            }

            case NCL_EVENT_PROVISION: {
                Log.i(TAG, "ncl provision");
                logUIMessage("NCL_EVENT_PROVISION received, saving provision to file store");
                provisions.add(e.provision.provision);
                saveProvisions();
                break;
            }

            case NCL_EVENT_VALIDATION: {
                Log.i(TAG, "ncl validation: " + e.validation.nymiHandle);
                logUIMessage("NCL_EVENT_VALIDATION received, saving nymiHandle in memory");
                nymiHandle = e.validation.nymiHandle;
                break;
            }

            case NCL_EVENT_FIND: {
                Log.i(TAG, "ncl find: " + e.validation.nymiHandle);

                if (manualDiscoveryStarted || swAutoConnect.isChecked()) {
                    nymiHandle = e.find.nymiHandle;
                    boolean result = Ncl.validate(nymiHandle);
                    logUIMessage("NCL_EVENT_FIND received, Ncl.validate() called, result=" + result);
                    manualDiscoveryStarted = false;
                } else {
                    logUIMessage("NCL_EVENT_FIND received, ignoring because auto connect is turned off");
                }

                break;
            }

            case NCL_EVENT_GLOBAL_VK: {
                char[] globalVk = e.globalVk.vk.clone();
                char[] globalVkId = e.globalVk.id.clone();
                logUIMessage("NCL_EVENT_GLOBAL_VK, vkId=" + readableKey(globalVkId) + ", vkKey=" + readableKey(globalVk));
                break;
            }
        }
    }

    private void loadProvisions() {
        Log.i(TAG, "loading provisions");

        SharedPreferences sp = getSharedPreferences("fpctrl", MODE_PRIVATE);

        String pstr = sp.getString("provisions", "empty");
        if (!pstr.equals("empty")) {
            try {
                JSONArray parry = new JSONObject(pstr).getJSONArray("provisions");
                for (int i=0; i<parry.length(); i++) {
                    JSONObject jsonProvision = parry.getJSONObject(i);
                    JSONArray jsonKey = jsonProvision.getJSONArray("key");
                    JSONArray jsonId = jsonProvision.getJSONArray("id");

                    NclProvision p = new NclProvision();
                    for (int a=0; a<jsonKey.length(); a++) {
                        p.key[a] = (char)jsonKey.getInt(a);
                        p.id[a] = (char)jsonId.getInt(a);
                    }
                    provisions.add(p);
                }
            } catch (JSONException e) {
                Log.e(TAG, "error parsing provisions: " + pstr, e);
            }
        }

        Log.i(TAG, String.format("loaded %d provisions", provisions.size()));
    }

    private void saveProvisions() {
        SharedPreferences sp = getSharedPreferences("fpctrl", MODE_PRIVATE);

        JSONObject root = new JSONObject();
        JSONArray parry = new JSONArray();


        for (NclProvision p : provisions) {
            JSONObject jsonProvision = new JSONObject();
            JSONArray jsonKey = new JSONArray();
            JSONArray jsonId = new JSONArray();

            for (int a=0; a < NclProvision.NCL_PROVISION_KEY_SIZE; a++) {
                jsonKey.put(p.key[a]);
                jsonId.put(p.id[a]);
            }

            try {
                jsonProvision.putOpt("key", jsonKey);
                jsonProvision.putOpt("id", jsonId);
                parry.put(jsonProvision);
            } catch (JSONException e) {
                Log.e(TAG, "error jsonify provision key and id", e);
            }
        }

        try {
            root.putOpt("provisions", parry);
            Log.i(TAG, "saving provisions: " + root.toString());
        } catch (JSONException e) {
            Log.e(TAG, "error jsonify provisions", e);
        }

        SharedPreferences.Editor editor = sp.edit();
        editor.putString("provisions", root.toString());
        editor.commit();
    }

    private String readableKey(char[] ch) {
        StringBuffer buf = new StringBuffer();
        for (char c : ch) {
            buf.append((int)c);
        }
        return buf.toString();
    }

    private void logUIMessage(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                log.append(msg + "\n");
            }
        });
    }
}
